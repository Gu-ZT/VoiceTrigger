package dev.dubhe.voice.audio;

import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.ZooModel;
import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.SilenceDetector;
import be.tarsos.dsp.io.jvm.AudioDispatcherFactory;
import com.mojang.blaze3d.platform.InputConstants;
import dev.dubhe.voice.VoiceTrigger;
import lombok.Getter;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.annotation.Nullable;
import javax.sound.sampled.LineUnavailableException;

/**
 * 实时语音监听器
 * 负责持续监听麦克风输入，使用 Wav2Vec2 深度学习模型进行语音匹配
 */
public class VoiceListener {
    public static final float SILENCE_THRESHOLD = -43.0f;  // 静音检测阈值（dB）
    private static final int BUFFER_SIZE = 1024;            // 音频缓冲区大小
    private static final int OVERLAP = 512;                 // 重叠大小
    private static final int SAMPLE_RATE = 16000;           // 采样率 16kHz
    private static final double SIMILARITY_THRESHOLD = 0.825; // Wav2Vec2 相似度阈值
    private static final int MIN_FRAMES_FOR_MATCH = 1;     // 最少需要的帧数才进行匹配（约 1 秒）

    // 深度学习模型相关
    private ZooModel<float[], float[]> dlModel;
    private Predictor<float[], float[]> dlPredictor;
    // 单例模式
    private static VoiceListener instance;
    private final LinkedList<float[]> rawAudioCache = new LinkedList<>(); // 原始 PCM 数据缓存 (float 格式)
    private final ExecutorService matchExecutor;
    // 存储所有语音模板及其对应的按键和 DL 特征向量
    private final Map<KeyMapping, float[]> voiceTemplates = new ConcurrentHashMap<>();
    private AudioDispatcher dispatcher;
    /**
     * -- GETTER --
     * 获取当前是否正在监听
     */
    @Getter
    private boolean isListening = false;
    private boolean isSpeaking = false;
    // 用于静音检测的能量阈值
    private double currentSoundLevel = 0.0;

    private VoiceListener() {
        // 创建一个单线程执行器用于匹配，避免阻塞音频线程
        matchExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "VoiceMatcher");
            t.setDaemon(true);
            return t;
        });

        // 初始化深度学习模型
        initializeDeepLearningModel();
    }

    public static synchronized VoiceListener getInstance() {
        if (instance == null) {
            instance = new VoiceListener();
        }
        return instance;
    }

    /**
     * 初始化深度学习模型
     */
    private void initializeDeepLearningModel() {
        try {
            // 从类路径加载资源（适用于开发环境和打包后的 JAR）
            String resourcePath = "/wav2vec2_feature_extractor.pt";
            java.net.URL resourceUrl = getClass().getResource(resourcePath);
                
            if (resourceUrl == null) {
                // 尝试从文件系统加载（开发环境备用方案）
                File fallbackFile = new File("src/main/resources/wav2vec2_feature_extractor.pt");
                if (fallbackFile.exists()) {
                    dlModel = AudioSimilarityDL.loadModel(fallbackFile.getAbsolutePath());
                    dlPredictor = dlModel.newPredictor();
                    VoiceTrigger.LOGGER.info("Loaded model from filesystem: {}", fallbackFile.getAbsolutePath());
                    return;
                } else {
                    VoiceTrigger.LOGGER.error("Deep learning model not found at: {}", resourcePath);
                    VoiceTrigger.LOGGER.error("Also checked fallback path: {}", fallbackFile.getAbsolutePath());
                    VoiceTrigger.LOGGER.error("Wav2Vec2 model is required for voice matching");
                    return;
                }
            }
                
            // 将 URL 转换为临时文件路径（DJL 需要文件系统路径）
            String modelPath;
            if ("jar".equals(resourceUrl.getProtocol()) || resourceUrl.getPath().contains("!")) {
                // 如果在 JAR 包中，需要解压到临时文件
                try(InputStream inputStream = VoiceListener.class.getClassLoader().getResourceAsStream(resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath)) {
                    if (inputStream == null) {
                        VoiceTrigger.LOGGER.error("Cannot read model from classpath: {}", resourcePath);
                        return;
                    }

                    // 创建临时文件
                    File tempFile = File.createTempFile("wav2vec2_", ".pt");
                    tempFile.deleteOnExit();

                    try (FileOutputStream outputStream = new FileOutputStream(tempFile)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                        }
                    } catch (Exception e) {
                        VoiceTrigger.LOGGER.error("Failed to extract model from JAR", e);
                        return;
                    }

                    modelPath = tempFile.getAbsolutePath();
                    VoiceTrigger.LOGGER.info("Extracted model to temporary file: {}", modelPath);
                } catch (Exception e) {
                    VoiceTrigger.LOGGER.error("Failed to extract model from JAR", e);
                    return;
                }
            } else {
                // 普通文件路径直接使用 URL 解码后的路径
                modelPath = java.net.URLDecoder.decode(resourceUrl.getPath(), java.nio.charset.StandardCharsets.UTF_8.name());
            }
                
            dlModel = AudioSimilarityDL.loadModel(modelPath);
            dlPredictor = dlModel.newPredictor();
            VoiceTrigger.LOGGER.info("Deep learning model loaded successfully from: {}", resourcePath);
        } catch (Exception e) {
            VoiceTrigger.LOGGER.error("Failed to load deep learning model", e);
        }
    }

    /**
     * 注册语音模板
     *
     * @param keyMapping 按键映射
     * @param audioFile  原始音频文件（用于提取 DL 特征）
     */
    public void registerTemplate(KeyMapping keyMapping, @Nullable File audioFile) {
        if (audioFile == null || !audioFile.exists()) {
            VoiceTrigger.LOGGER.warn("Cannot register template without audio file for key: {}", keyMapping.getName());
            return;
        }

        VoiceTrigger.LOGGER.info(
            "Registering voice template for key '{}', audio file: {} ({} bytes)",
            keyMapping.getName(),
            audioFile.getAbsolutePath(),
            audioFile.length()
        );

        // 如果深度学习模型可用，提取 DL 特征向量
        if (dlPredictor != null) {
            try {
                // 直接从 WAV 文件读取原始波形数据
                long startTime = System.currentTimeMillis();
                float[] audioData = AudioSimilarityDL.readAndPreprocessWav(audioFile.getAbsolutePath());
                long loadTime = System.currentTimeMillis() - startTime;

                VoiceTrigger.LOGGER.info(
                    "Loaded audio data for '{}': {} samples ({}s) in {}ms",
                    keyMapping.getName(),
                    audioData.length,
                    audioData.length / (double) SAMPLE_RATE,
                    loadTime
                );

                if (audioData.length > 0) {
                    startTime = System.currentTimeMillis();
                    float[] features = AudioSimilarityDL.extractFeatures(dlPredictor, audioData);
                    long extractTime = System.currentTimeMillis() - startTime;

                    if (features != null) {
                        voiceTemplates.put(keyMapping, features);
                        VoiceTrigger.LOGGER.info(
                            "✓ Successfully registered voice template for key '{}': feature dim = {}, extraction took {}ms",
                            keyMapping.getName(),
                            features.length,
                            extractTime
                        );
                    } else {
                        VoiceTrigger.LOGGER.error(
                            "✗ Failed to extract features for key '{}': feature extraction returned null. " +
                            "Possible causes: invalid audio format, audio too short, or model error",
                            keyMapping.getName()
                        );
                    }
                } else {
                    VoiceTrigger.LOGGER.error(
                        "✗ Failed to load audio data for key '{}': empty audio. " +
                        "Check if the WAV file is valid and not corrupted",
                        keyMapping.getName()
                    );
                }
            } catch (Exception e) {
                VoiceTrigger.LOGGER.error("Failed to extract DL features for key: {}", keyMapping.getName(), e);
            }
        } else {
            VoiceTrigger.LOGGER.error(
                "Cannot register template for key '{}': DL predictor not initialized. " +
                "Make sure the Wav2Vec2 model is properly loaded",
                keyMapping.getName()
            );
        }
    }

    /**
     * 移除语音模板
     *
     * @param keyMapping 按键映射
     */
    public void unregisterTemplate(KeyMapping keyMapping) {
        voiceTemplates.remove(keyMapping);
        VoiceTrigger.LOGGER.info("Unregistered voice template for key: {}", keyMapping.getName());
    }

    /**
     * 启动实时监听
     */
    public void startListening() {
        if (isListening) {
            VoiceTrigger.LOGGER.warn("Voice listener is already running");
            return;
        }

        try {
            // 创建音频调度器（直接从麦克风获取原始 PCM 数据）
            dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(
                SAMPLE_RATE,
                BUFFER_SIZE,
                OVERLAP
            );

            // 创建静音检测器
            SilenceDetector silenceDetector = new SilenceDetector(SILENCE_THRESHOLD, false);

            // 添加静音检测处理器
            dispatcher.addAudioProcessor(silenceDetector);
            dispatcher.addAudioProcessor(new AudioProcessor() {
                @Override
                public boolean process(AudioEvent audioEvent) {
                    // 更新当前声音级别
                    currentSoundLevel = silenceDetector.currentSPL();
                    return true;
                }

                @Override
                public void processingFinished() {
                }
            });

            // 添加主处理器（缓存原始 PCM 数据并进行匹配）
            dispatcher.addAudioProcessor(new AudioProcessor() {
                @Override
                public boolean process(AudioEvent audioEvent) {
                    processAudioFrame(audioEvent);
                    return true;
                }

                @Override
                public void processingFinished() {
                    VoiceTrigger.LOGGER.info("Voice listening stopped");
                }
            });

            isListening = true;

            // 在新线程中运行调度器
            new Thread(
                () -> {
                    VoiceTrigger.LOGGER.info("Voice listener started");
                    dispatcher.run();
                }, "VoiceListener"
            ).start();

        } catch (LineUnavailableException e) {
            VoiceTrigger.LOGGER.error("Failed to start voice listener", e);
        }
    }

    /**
     * 停止实时监听
     */
    public void stopListening() {
        if (!isListening) {
            return;
        }

        isListening = false;
        if (dispatcher != null) {
            try {
                dispatcher.stop();
                // 等待调度器完全停止
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        rawAudioCache.clear();
        isSpeaking = false;
        VoiceTrigger.LOGGER.info("Voice listener stopped");
    }

    /**
     * 处理音频帧
     */
    private void processAudioFrame(AudioEvent audioEvent) {
        // 缓存原始 PCM 数据（用于 Wav2Vec2）
        // AudioEvent 中的 getFloatBuffer() 返回归一化的 float 数组 (-1.0 到 1.0)
        float[] pcmData = audioEvent.getFloatBuffer().clone();
        rawAudioCache.addLast(pcmData);

        // 保持缓存窗口大小（约 2 秒的音频数据）
        if (rawAudioCache.size() > 31) { // 16000/1024 * 2 ≈ 31 帧
            rawAudioCache.removeFirst();
        }

        // 检测是否有声音（非静音）
        boolean currentlySpeaking = currentSoundLevel > SILENCE_THRESHOLD;

        if (currentlySpeaking) {
            if (!isSpeaking) {
                // 开始说话
                isSpeaking = true;
                rawAudioCache.clear(); // 清空缓存，从新开始记录
                VoiceTrigger.LOGGER.debug("Speech detected - started speaking, cleared cache");
            }

            // Debug 输出：当前缓存帧数和声音级别
            VoiceTrigger.LOGGER.debug(
                "Audio status: SoundLevel={}, CacheFrames={}, MinRequired={}",
                currentSoundLevel, rawAudioCache.size(), MIN_FRAMES_FOR_MATCH
            );

            // 如果缓存中有足够的帧，尝试匹配
            if (rawAudioCache.size() >= MIN_FRAMES_FOR_MATCH) {
                VoiceTrigger.LOGGER.debug(
                    "Attempting voice match with {} frames (~{} seconds)",
                    rawAudioCache.size(),
                    rawAudioCache.size() * (double) (BUFFER_SIZE - OVERLAP) / SAMPLE_RATE
                );
                tryMatch();
            } else {
                VoiceTrigger.LOGGER.debug(
                    "Not enough frames for match: have {}, need {}",
                    rawAudioCache.size(),
                    MIN_FRAMES_FOR_MATCH
                );
            }
        } else {
            if (isSpeaking) {
                // 停止说话
                isSpeaking = false;
                int cachedFrames = rawAudioCache.size();
                double duration = cachedFrames * (double) (BUFFER_SIZE - OVERLAP) / SAMPLE_RATE;
                VoiceTrigger.LOGGER.debug(
                    "Speech ended - cached {} frames (~{} seconds). Reason: sound level {}dB < threshold {}dB",
                    cachedFrames, duration, currentSoundLevel, SILENCE_THRESHOLD
                );

                // 检查是否因为时长不足导致无法匹配
                if (cachedFrames < MIN_FRAMES_FOR_MATCH) {
                    VoiceTrigger.LOGGER.warn(
                        "Speech too short for matching! Got {} frames ({}s), need at least {} frames ({}s). " +
                        "Try speaking longer or reduce MIN_FRAMES_FOR_MATCH.",
                        cachedFrames,
                        duration,
                        MIN_FRAMES_FOR_MATCH,
                        MIN_FRAMES_FOR_MATCH * (double) (BUFFER_SIZE - OVERLAP) / SAMPLE_RATE
                    );
                }
            }
        }
    }

    /**
     * 尝试匹配当前窗口与所有模板
     */
    private void tryMatch() {
        if (voiceTemplates.isEmpty()) {
            VoiceTrigger.LOGGER.debug("Cannot match: no templates registered");
            return;
        }

        if (dlPredictor == null) {
            VoiceTrigger.LOGGER.debug("Cannot match: DL predictor not initialized");
            return;
        }

        // 复制当前缓存以避免并发修改
        List<float[]> audioCacheCopy = new ArrayList<>(rawAudioCache);

        VoiceTrigger.LOGGER.debug(
            "Starting voice matching against {} templates with {} audio frames",
            voiceTemplates.size(),
            audioCacheCopy.size()
        );

        // 在单独的线程中执行匹配
        matchExecutor.submit(() -> {
            for (Map.Entry<KeyMapping, float[]> entry : voiceTemplates.entrySet()) {
                KeyMapping key = entry.getKey();
                float[] templateFeatures = entry.getValue();

                try {
                    // 从缓存的 PCM 数据重构原始波形
                    float[] currentAudioData = mergePcmData(audioCacheCopy);

                    VoiceTrigger.LOGGER.debug(
                        "Matching for key '{}': Merged audio data length = {} samples ({}s)",
                        key.getName(),
                        currentAudioData.length,
                        currentAudioData.length / (double) SAMPLE_RATE
                    );

                    if (currentAudioData.length > 0) {
                        // 提取当前音频的特征
                        long startTime = System.currentTimeMillis();
                        float[] currentFeatures = AudioSimilarityDL.extractFeatures(dlPredictor, currentAudioData);
                        long extractTime = System.currentTimeMillis() - startTime;

                        if (currentFeatures != null) {
                            VoiceTrigger.LOGGER.debug(
                                "Feature extraction for '{}' took {}ms, feature dim = {}",
                                key.getName(),
                                extractTime,
                                currentFeatures.length
                            );

                            // 计算余弦相似度
                            double similarity = AudioSimilarityDL.cosineSimilarity(currentFeatures, templateFeatures);

                            VoiceTrigger.LOGGER.info(
                                "Voice match for key '{}': Similarity = {} (threshold: {})",
                                key.getName(),
                                similarity,
                                SIMILARITY_THRESHOLD
                            );

                            // 如果相似度达到阈值，触发按键
                            if (similarity >= SIMILARITY_THRESHOLD) {
                                VoiceTrigger.LOGGER.info(
                                    "✓ MATCH SUCCESS for key '{}' (Similarity: {} >= {})",
                                    key.getName(),
                                    similarity,
                                    SIMILARITY_THRESHOLD
                                );
                                triggerKey(key);

                                // 清空缓存，避免重复触发
                                rawAudioCache.clear();
                                break;
                            } else {
                                double diff = SIMILARITY_THRESHOLD - similarity;
                                VoiceTrigger.LOGGER.debug(
                                    "✗ Match failed for '{}': similarity {} is {} below threshold {}",
                                    key.getName(),
                                    similarity,
                                    diff,
                                    SIMILARITY_THRESHOLD
                                );
                            }
                        } else {
                            VoiceTrigger.LOGGER.warn(
                                "Feature extraction returned null for key '{}'. Possible causes: " +
                                "audio too short, invalid audio data, or model error",
                                key.getName()
                            );
                        }
                    } else {
                        VoiceTrigger.LOGGER.warn(
                            "Empty audio data for key '{}'. This should not happen!",
                            key.getName()
                        );
                    }
                } catch (Exception e) {
                    VoiceTrigger.LOGGER.warn("Matching failed for key: {}", key.getName(), e);
                }
            }
        });
    }

    /**
     * 合并缓存的 PCM 数据为连续的 float 数组
     *
     * @param pcmCache PCM 数据缓存（float 格式）
     * @return 合并后的 float 数组
     */
    private float[] mergePcmData(List<float[]> pcmCache) {
        if (pcmCache.isEmpty()) {
            return new float[0];
        }

        // 计算总长度
        int totalLength = 0;
        for (float[] chunk : pcmCache) {
            totalLength += chunk.length;
        }

        // 合并所有 PCM 数据
        float[] merged = new float[totalLength];
        int index = 0;
        for (float[] chunk : pcmCache) {
            System.arraycopy(chunk, 0, merged, index, chunk.length);
            index += chunk.length;
        }

        return merged;
    }

    /**
     * 触发按键
     *
     * @param keyMapping 要触发的按键
     */
    private void triggerKey(KeyMapping keyMapping) {
        // 在 Minecraft 主线程中执行按键操作
        Minecraft.getInstance().execute(() -> {
            try {
                // 模拟按键按下和释放
                keyMapping.setDown(true);
                InputEvent event;
                int key = keyMapping.getKey().getValue();
                int scancode = key >= 0 ? GLFW.glfwGetKeyScancode(key) : -1;
                if (keyMapping.getKey().getType() == InputConstants.Type.MOUSE) {
                    //noinspection UnstableApiUsage
                    event = new InputEvent.MouseButton.Pre(key, 1, 0);
                } else {
                    //noinspection UnstableApiUsage
                    event = new InputEvent.Key(key, scancode, 1, 0);
                }
                NeoForge.EVENT_BUS.post(event);
                // 延迟一小段时间后释放
                VoiceTrigger.schedule(
                    10, () -> {
                        InputEvent event1;
                        if (keyMapping.getKey().getType() == InputConstants.Type.MOUSE) {
                            //noinspection UnstableApiUsage
                            event1 = new InputEvent.MouseButton.Pre(key, 0, 0);
                        } else {
                            //noinspection UnstableApiUsage
                            event1 = new InputEvent.Key(key, scancode, 0, 0);
                        }
                        keyMapping.setDown(false);
                        NeoForge.EVENT_BUS.post(event1);
                    }
                );
                VoiceTrigger.LOGGER.info("Triggered key: {}", keyMapping.getName());
            } catch (Exception e) {
                VoiceTrigger.LOGGER.error("Error triggering key: {}", keyMapping.getName(), e);
            }
        });
    }

    /**
     * 获取已注册的模板数量
     *
     * @return 模板数量
     */
    public int getTemplateCount() {
        return voiceTemplates.size();
    }

    /**
     * 清空所有模板
     */
    public void clearAllTemplates() {
        voiceTemplates.clear();
        VoiceTrigger.LOGGER.info("Cleared all voice templates");
    }

    /**
     * 关闭监听器
     */
    public void shutdown() {
        stopListening();
        matchExecutor.shutdown();

        // 释放深度学习模型资源
        if (dlPredictor != null) {
            dlPredictor.close();
        }
        if (dlModel != null) {
            dlModel.close();
        }

        VoiceTrigger.LOGGER.info("Voice listener shut down");
    }
}
