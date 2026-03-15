package dev.dubhe.voice.audio;

import dev.dubhe.voice.VoiceTrigger;
import net.minecraft.client.KeyMapping;
import net.neoforged.fml.loading.FMLPaths;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 语音配置管理器
 * 负责语音配置文件（WAV 文件）的管理
 */
public class VoiceProfileManager {

    private static final Path VOICE_DIR = FMLPaths.CONFIGDIR.get().resolve("voice_trigger");
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
     * 删除语音配置
     *
     * @param keyMapping 按键映射
     * @return 是否删除成功
     */
    public static boolean deleteProfile(KeyMapping keyMapping) {
        String keyName = sanitizeKeyName(keyMapping.getName());
        Path audioPath = VOICE_DIR.resolve(keyName + AUDIO_EXTENSION);
    
        try {
            if (Files.exists(audioPath)) {
                Files.delete(audioPath);
                VoiceTrigger.LOGGER.info("Deleted voice profile for key: {}", keyName);
                return true;
            }
            return false;
    
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
        Path audioPath = VOICE_DIR.resolve(keyName + AUDIO_EXTENSION);
        return Files.exists(audioPath);
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
     * 从录音文件提取并保存 DL 特征
     *
     * @param keyMapping 按键映射
     * @param audioFile  音频文件
     * @return 是否成功
     */
    public static boolean extractAndSaveProfile(KeyMapping keyMapping, File audioFile) {
        // 直接注册到监听器（Wav2Vec2 直接从音频文件提取特征）
        VoiceListener.getInstance().registerTemplate(keyMapping, audioFile);
        return true;
    }

    /**
     * 加载所有已保存的语音配置到监听器
     *
     * @param keyMappings 所有按键映射
     */
    public static void loadAllProfiles(KeyMapping[] keyMappings) {
        int loaded = 0;
        for (KeyMapping keyMapping : keyMappings) {
            // 直接获取音频文件用于 DL 特征提取
            File audioFile = getAudioFile(keyMapping);
            if (audioFile.exists()) {
                VoiceListener.getInstance().registerTemplate(keyMapping, audioFile);
                loaded++;
            }
        }
        VoiceTrigger.LOGGER.info("Loaded {} voice profiles", loaded);
    }
}
