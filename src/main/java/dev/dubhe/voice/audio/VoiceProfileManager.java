package dev.dubhe.voice.audio;

import dev.dubhe.voice.VoiceTrigger;
import net.minecraft.client.KeyMapping;
import net.neoforged.fml.loading.FMLPaths;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 语音配置管理器
 * 负责MFCC特征的保存和加载
 */
public class VoiceProfileManager {

    private static final Path VOICE_DIR = FMLPaths.CONFIGDIR.get().resolve("voice_trigger");
    private static final String PROFILE_EXTENSION = ".vcfg";
    private static final String AUDIO_EXTENSION = ".wav";

    static {
        // 确保目录存在
        try {
            if (!Files.exists(VOICE_DIR)) {
                Files.createDirectories(VOICE_DIR);
                VoiceTrigger.LOGGER.info("Created voice profiles directory: {}", VOICE_DIR);
            }
        } catch (IOException e) {
            VoiceTrigger.LOGGER.error("Failed to create voice profiles directory", e);
        }
    }

    /**
     * 保存语音配置
     *
     * @param keyMapping   按键映射
     * @param mfccFeatures MFCC特征序列
     * @return 是否保存成功
     */
    public static boolean saveProfile(KeyMapping keyMapping, List<float[]> mfccFeatures) {
        if (mfccFeatures == null || mfccFeatures.isEmpty()) {
            VoiceTrigger.LOGGER.warn("Cannot save empty MFCC features");
            return false;
        }

        String keyName = sanitizeKeyName(keyMapping.getName());
        Path profilePath = VOICE_DIR.resolve(keyName + PROFILE_EXTENSION);

        try (BufferedWriter writer = Files.newBufferedWriter(profilePath)) {
            // 写入特征数量
            writer.write(String.valueOf(mfccFeatures.size()));
            writer.newLine();

            // 写入每个特征向量
            for (float[] feature : mfccFeatures) {
                for (int i = 0; i < feature.length; i++) {
                    writer.write(String.valueOf(feature[i]));
                    if (i < feature.length - 1) {
                        writer.write(",");
                    }
                }
                writer.newLine();
            }

            VoiceTrigger.LOGGER.info(
                "Saved voice profile for key: {} ({} frames)",
                keyName, mfccFeatures.size()
            );
            return true;

        } catch (IOException e) {
            VoiceTrigger.LOGGER.error("Failed to save voice profile for key: {}", keyName, e);
            return false;
        }
    }

    /**
     * 加载语音配置
     *
     * @param keyMapping 按键映射
     * @return MFCC特征序列，如果不存在或加载失败则返回null
     */
    public static List<float[]> loadProfile(KeyMapping keyMapping) {
        String keyName = sanitizeKeyName(keyMapping.getName());
        Path profilePath = VOICE_DIR.resolve(keyName + PROFILE_EXTENSION);

        if (!Files.exists(profilePath)) {
            return null;
        }

        try (BufferedReader reader = Files.newBufferedReader(profilePath)) {
            List<float[]> features = new ArrayList<>();

            // 读取特征数量
            String countLine = reader.readLine();
            if (countLine == null) {
                return null;
            }

            // 读取每个特征向量
            String line;
            while ((line = reader.readLine()) != null) {
                String[] values = line.split(",");
                float[] feature = new float[values.length];
                for (int i = 0; i < values.length; i++) {
                    feature[i] = Float.parseFloat(values[i]);
                }
                features.add(feature);
            }

            VoiceTrigger.LOGGER.info(
                "Loaded voice profile for key: {} ({} frames)",
                keyName, features.size()
            );
            return features;

        } catch (IOException | NumberFormatException e) {
            VoiceTrigger.LOGGER.error("Failed to load voice profile for key: {}", keyName, e);
            return null;
        }
    }

    /**
     * 删除语音配置
     *
     * @param keyMapping 按键映射
     * @return 是否删除成功
     */
    public static boolean deleteProfile(KeyMapping keyMapping) {
        String keyName = sanitizeKeyName(keyMapping.getName());
        Path profilePath = VOICE_DIR.resolve(keyName + PROFILE_EXTENSION);
        Path audioPath = VOICE_DIR.resolve(keyName + AUDIO_EXTENSION);

        boolean deleted = false;

        try {
            if (Files.exists(profilePath)) {
                Files.delete(profilePath);
                deleted = true;
            }
            if (Files.exists(audioPath)) {
                Files.delete(audioPath);
            }

            if (deleted) {
                VoiceTrigger.LOGGER.info("Deleted voice profile for key: {}", keyName);
            }
            return deleted;

        } catch (IOException e) {
            VoiceTrigger.LOGGER.error("Failed to delete voice profile for key: {}", keyName, e);
            return false;
        }
    }

    /**
     * 检查是否存在语音配置
     *
     * @param keyMapping 按键映射
     * @return 是否存在
     */
    public static boolean hasProfile(KeyMapping keyMapping) {
        String keyName = sanitizeKeyName(keyMapping.getName());
        Path profilePath = VOICE_DIR.resolve(keyName + PROFILE_EXTENSION);
        return Files.exists(profilePath);
    }

    /**
     * 获取音频文件路径
     *
     * @param keyMapping 按键映射
     * @return 音频文件
     */
    public static File getAudioFile(KeyMapping keyMapping) {
        String keyName = sanitizeKeyName(keyMapping.getName());
        return VOICE_DIR.resolve(keyName + AUDIO_EXTENSION).toFile();
    }

    /**
     * 清理按键名称，移除特殊字符
     *
     * @param keyName 原始按键名称
     * @return 清理后的按键名称
     */
    private static String sanitizeKeyName(String keyName) {
        // 移除 "key.keyboard." 等前缀
        String cleaned = keyName.replaceAll("^key\\.(keyboard|mouse)\\.", "");
        // 替换特殊字符为下划线
        cleaned = cleaned.replaceAll("[^a-zA-Z0-9._-]", "_");
        return cleaned;
    }

    /**
     * 从录音文件提取并保存MFCC特征
     *
     * @param keyMapping 按键映射
     * @param audioFile  音频文件
     * @return 是否成功
     */
    public static boolean extractAndSaveProfile(KeyMapping keyMapping, File audioFile) {
        try {
            // 提取MFCC特征
            List<float[]> mfccFeatures = MFCCExtractor.extractFromFile(audioFile);

            if (mfccFeatures.isEmpty()) {
                VoiceTrigger.LOGGER.warn("No MFCC features extracted from audio file");
                return false;
            }

            // 保存配置
            boolean saved = saveProfile(keyMapping, mfccFeatures);

            if (saved) {
                // 注册到监听器
                VoiceListener.getInstance().registerTemplate(keyMapping, mfccFeatures);
            }

            return saved;

        } catch (IOException e) {
            VoiceTrigger.LOGGER.error("Failed to extract and save profile", e);
            return false;
        }
    }

    /**
     * 加载所有已保存的语音配置到监听器
     *
     * @param keyMappings 所有按键映射
     */
    public static void loadAllProfiles(KeyMapping[] keyMappings) {
        int loaded = 0;
        for (KeyMapping keyMapping : keyMappings) {
            List<float[]> features = loadProfile(keyMapping);
            if (features != null) {
                VoiceListener.getInstance().registerTemplate(keyMapping, features);
                loaded++;
            }
        }
        VoiceTrigger.LOGGER.info("Loaded {} voice profiles", loaded);
    }
}
