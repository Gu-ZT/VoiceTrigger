package dev.dubhe.voice.audio;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.SilenceDetector;
import be.tarsos.dsp.mfcc.MFCC;
import com.mojang.blaze3d.platform.InputConstants;
import dev.dubhe.voice.VoiceTrigger;
import lombok.Getter;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

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
 * 负责持续监听麦克风输入，检测并匹配用户定义的语音触发键
 */
public class VoiceListener {
    public static final float SILENCE_THRESHOLD = -43.0f;  // 静音检测阈值（dB）
    private static final int WINDOW_SIZE = 62;              // 滑动窗口大小（约2秒，16000/1024*62≈2秒）
    private static final float SIMILARITY_THRESHOLD = 18.0f; // 相似度阈值（DTW距离小于此值认为匹配）
    private static final int MIN_FRAMES_FOR_MATCH = 10;     // 最少需要的帧数才进行匹配
    // 单例模式
    private static VoiceListener instance;
    private final LinkedList<float[]> currentWindow = new LinkedList<>();
    private final ExecutorService matchExecutor;
    // 存储所有语音模板及其对应的按键
    private final Map<KeyMapping, List<float[]>> voiceTemplates = new ConcurrentHashMap<>();
    private AudioDispatcher dispatcher;
    private MFCC mfccProcessor;
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
        // 创建一个单线程执行器用于DTW匹配，避免阻塞音频线程
        matchExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "VoiceMatcher");
            t.setDaemon(true);
            return t;
        });
    }

    public static synchronized VoiceListener getInstance() {
        if (instance == null) {
            instance = new VoiceListener();
        }
        return instance;
    }

    /**
     * 注册语音模板
     *
     * @param keyMapping   按键映射
     * @param mfccFeatures MFCC特征序列
     */
    public void registerTemplate(KeyMapping keyMapping, @Nullable List<float[]> mfccFeatures) {
        if (mfccFeatures != null && !mfccFeatures.isEmpty()) {
            voiceTemplates.put(keyMapping, mfccFeatures);
            VoiceTrigger.LOGGER.info(
                "Registered voice template for key: {}, frames: {}",
                keyMapping.getName(), mfccFeatures.size()
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
            // 创建音频调度器
            dispatcher = MFCCExtractor.createRealtimeDispatcher();
            mfccProcessor = MFCCExtractor.createMFCCProcessor();

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

            // 添加MFCC处理器
            dispatcher.addAudioProcessor(mfccProcessor);

            // 添加主处理器
            dispatcher.addAudioProcessor(new AudioProcessor() {
                @Override
                public boolean process(AudioEvent audioEvent) {
                    processAudioFrame();
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
        currentWindow.clear();
        isSpeaking = false;
        VoiceTrigger.LOGGER.info("Voice listener stopped");
    }

    /**
     * 处理音频帧
     */
    private void processAudioFrame() {
        // 检测是否有声音（非静音）
        // 使用声压级别判断，高于阈值表示有声音
        boolean currentlySpeaking = currentSoundLevel > SILENCE_THRESHOLD;

        if (currentlySpeaking) {
            if (!isSpeaking) {
                // 开始说话
                isSpeaking = true;
                currentWindow.clear();
                VoiceTrigger.LOGGER.debug("Speech detected");
            }

            // 获取当前帧的MFCC特征
            float[] currentMfcc = mfccProcessor.getMFCC();
            if (currentMfcc != null && currentMfcc.length > 0) {
                currentWindow.add(currentMfcc.clone());

                // 保持窗口大小
                if (currentWindow.size() > WINDOW_SIZE) {
                    currentWindow.removeFirst();
                }

                // 如果窗口中有足够的帧，尝试匹配
                if (currentWindow.size() >= MIN_FRAMES_FOR_MATCH) {
                    tryMatch();
                }
            }
        } else {
            if (isSpeaking) {
                // 停止说话
                isSpeaking = false;
                VoiceTrigger.LOGGER.debug("Speech ended");
            }
        }
    }

    /**
     * 尝试匹配当前窗口与所有模板
     */
    private void tryMatch() {
        if (voiceTemplates.isEmpty()) {
            return;
        }

        // 复制当前窗口以避免并发修改
        List<float[]> windowCopy = new ArrayList<>(currentWindow);

        // 在单独的线程中执行DTW匹配
        matchExecutor.submit(() -> {
            for (Map.Entry<KeyMapping, List<float[]>> entry : voiceTemplates.entrySet()) {
                KeyMapping key = entry.getKey();
                List<float[]> template = entry.getValue();

                // 计算DTW距离
                float distance = DTW.compute(windowCopy, template);
                VoiceTrigger.LOGGER.debug(
                    "Voice match detected for key: {}, distance: {}",
                    key.getName(),
                    distance
                );
                // 如果距离小于阈值，认为匹配成功
                if (distance < SIMILARITY_THRESHOLD) {
                    triggerKey(key);

                    // 清空窗口，避免重复触发
                    currentWindow.clear();
                    break;
                }
            }
        });
    }

    /**
     * 触发按键
     *
     * @param keyMapping 要触发的按键
     */
    private void triggerKey(KeyMapping keyMapping) {
        // 在Minecraft主线程中执行按键操作
        Minecraft.getInstance().execute(() -> {
            try {
                // 模拟按键按下和释放
                keyMapping.setDown(true);
                InputEvent event;
                int key = keyMapping.getKey().getValue();
                if (keyMapping.getKey().getType() == InputConstants.Type.MOUSE) {
                    //noinspection UnstableApiUsage
                    event = new InputEvent.MouseButton.Pre(key, 1, 0);
                } else {
                    //noinspection UnstableApiUsage
                    event = new InputEvent.Key(key, GLFW.glfwGetKeyScancode(key), 1, 0);
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
                            event1 = new InputEvent.Key(key, GLFW.glfwGetKeyScancode(key), 0, 0);
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
        VoiceTrigger.LOGGER.info("Voice listener shut down");
    }
}
