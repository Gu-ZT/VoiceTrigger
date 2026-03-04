package dev.dubhe.voice.audio;

import dev.dubhe.voice.VoiceTrigger;
import lombok.Getter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;

/**
 * 语音录制器
 * 负责录制用户的语音并保存为WAV文件
 */
public class VoiceRecorder {
    private static final int SAMPLE_RATE = 16000;
    private static final int SAMPLE_SIZE_IN_BITS = 16;
    private static final int CHANNELS = 1; // 单声道
    private static final boolean SIGNED = true;
    private static final boolean BIG_ENDIAN = false;

    private TargetDataLine targetLine;
    private Thread recordingThread;
    /**
     * -- GETTER --
     *  检查是否正在录制
     */
    @Getter
    private volatile boolean isRecording = false;
    /**
     * -- GETTER --
     *  获取输出文件
     */
    @Getter
    private File outputFile;

    /**
     * 开始录制
     *
     * @param outputFile 输出文件
     * @throws LineUnavailableException 音频线路不可用异常
     */
    public void startRecording(File outputFile) throws LineUnavailableException {
        if (isRecording) {
            VoiceTrigger.LOGGER.warn("Already recording, ignoring start request");
            return;
        }

        this.outputFile = outputFile;

        // 配置音频格式
        AudioFormat format = new AudioFormat(
            SAMPLE_RATE,
            SAMPLE_SIZE_IN_BITS,
            CHANNELS,
            SIGNED,
            BIG_ENDIAN
        );

        // 获取目标数据线
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

        if (!AudioSystem.isLineSupported(info)) {
            VoiceTrigger.LOGGER.error("Audio line not supported for format: {}", format);
            throw new LineUnavailableException("Audio line not supported");
        }

        targetLine = (TargetDataLine) AudioSystem.getLine(info);
        targetLine.open(format);
        targetLine.start();

        isRecording = true;

        // 在新线程中录制
        recordingThread = new Thread(
            () -> {
                try {
                    recordAudio();
                } catch (IOException e) {
                    VoiceTrigger.LOGGER.error("Error during recording", e);
                }
            }, "AudioRecorder"
        );
        recordingThread.start();

        VoiceTrigger.LOGGER.info(
            "Started recording to: {} (format: {}Hz, {}bit, {}, {})",
            outputFile.getAbsolutePath(),
            format.getSampleRate(),
            format.getSampleSizeInBits(),
            CHANNELS == 1 ? "mono" : "stereo",
            format.isBigEndian() ? "big-endian" : "little-endian"
        );
    }

    /**
     * 停止录制
     */
    public void stopRecording() {
        if (!isRecording) {
            VoiceTrigger.LOGGER.warn("Not recording, ignoring stop request");
            return;
        }

        isRecording = false;

        if (targetLine != null) {
            targetLine.stop();
            targetLine.close();
        }

        if (recordingThread != null) {
            try {
                recordingThread.join(1000);
                VoiceTrigger.LOGGER.debug("Recording thread stopped successfully");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                VoiceTrigger.LOGGER.warn("Interrupted while waiting for recording thread to stop", e);
            }
        }

        VoiceTrigger.LOGGER.info("Stopped recording, file will be saved to: {}", outputFile.getAbsolutePath());
    }

    /**
     * 录制音频数据
     *
     * @throws IOException IO 异常
     */
    private void recordAudio() throws IOException {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
    
        while (isRecording) {
            int bytesRead = targetLine.read(buffer, 0, buffer.length);
            if (bytesRead > 0) {
                byteOutputStream.write(buffer, 0, bytesRead);
            }
        }
    
        // 保存为 WAV 文件
        byte[] audioData = byteOutputStream.toByteArray();
            
        // 移除开头和结尾的静音帧
        byte[] trimmedAudioData = trimSilence(audioData);
            
        saveToWavFile(trimmedAudioData);
    }

    /**
     * 保存音频数据为 WAV 文件
     *
     * @param audioData 音频数据
     * @throws IOException IO 异常
     */
    private void saveToWavFile(byte[] audioData) throws IOException {
        AudioFormat format = new AudioFormat(
            SAMPLE_RATE,
            SAMPLE_SIZE_IN_BITS,
            CHANNELS,
            SIGNED,
            BIG_ENDIAN
        );

        ByteArrayInputStream byteInputStream = new ByteArrayInputStream(audioData);

        try (
            AudioInputStream audioInputStream = new AudioInputStream(
                byteInputStream,
                format,
                audioData.length / format.getFrameSize()
            )
        ) {
            AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, outputFile);
            VoiceTrigger.LOGGER.info("Saved audio to: {}", outputFile.getAbsolutePath());
        }
    }

    /**
     * 移除音频数据开头和结尾的静音部分
     *
     * @param audioData 原始音频数据
     * @return 裁剪后的音频数据
     */
    private byte[] trimSilence(byte[] audioData) {
        if (audioData.length == 0) {
            VoiceTrigger.LOGGER.debug("Trim silence skipped: empty audio data");
            return audioData;
        }

        // 16-bit 采样，每个样本 2 字节
        int bytesPerSample = SAMPLE_SIZE_IN_BITS / 8;
        int totalSamples = audioData.length / bytesPerSample;
        
        if (totalSamples == 0) {
            VoiceTrigger.LOGGER.debug("Trim silence skipped: no samples");
            return audioData;
        }
        
        double originalDuration = totalSamples / (double)SAMPLE_RATE;
        VoiceTrigger.LOGGER.debug(
            "Trimming silence from {} samples ({}s, {} bytes)",
            totalSamples,
            originalDuration,
            audioData.length
        );

        // 静音阈值（振幅的百分比）
        double silenceThreshold = 0.02;
        
        // 找到第一个非静音样本（开头）
        int startIndex = 0;
        for (int i = 0; i < totalSamples; i++) {
            int byteIndex = i * bytesPerSample;
            // 读取 16-bit 有符号整数（小端序）
            short sample = (short) ((audioData[byteIndex + 1] << 8) | (audioData[byteIndex] & 0xFF));
            double normalizedAmplitude = Math.abs(sample) / 32768.0;
            
            if (normalizedAmplitude > silenceThreshold) {
                startIndex = i;
                break;
            }
        }

        // 找到最后一个非静音样本（结尾）
        int endIndex = totalSamples - 1;
        for (int i = totalSamples - 1; i >= 0; i--) {
            int byteIndex = i * bytesPerSample;
            // 读取 16-bit 有符号整数（小端序）
            short sample = (short) ((audioData[byteIndex + 1] << 8) | (audioData[byteIndex] & 0xFF));
            double normalizedAmplitude = Math.abs(sample) / 32768.0;
            
            if (normalizedAmplitude > silenceThreshold) {
                endIndex = i;
                break;
            }
        }

        // 如果整个音频都是静音，返回空数组
        if (endIndex < startIndex) {
            VoiceTrigger.LOGGER.warn(
                "Audio appears to be completely silent! Original: {} samples ({}s). " +
                "Possible causes: microphone not working, volume too low, or only recorded silence",
                totalSamples,
                originalDuration
            );
            return new byte[0];
        }

        // 计算裁剪后的字节数组
        int trimmedLength = (endIndex - startIndex + 1) * bytesPerSample;
        int removedFromStart = startIndex;
        int removedFromEnd = totalSamples - endIndex - 1;
        double trimmedDuration = (endIndex - startIndex + 1) / (double)SAMPLE_RATE;
        
        byte[] trimmedAudio = new byte[trimmedLength];
        System.arraycopy(audioData, startIndex * bytesPerSample, trimmedAudio, 0, trimmedLength);

        VoiceTrigger.LOGGER.info(
            "Trimmed silence: {}→{} samples ({}s→{}s), removed {} from start, {} from end",
            totalSamples,
            endIndex - startIndex + 1,
            originalDuration,
            trimmedDuration,
            removedFromStart,
            removedFromEnd
        );
        
        // 警告：如果裁剪后时长太短
        if (trimmedDuration < 1.0) {
            VoiceTrigger.LOGGER.warn(
                "Warning: Trimmed audio is very short ({}s). This may cause matching failures. " +
                "Recommended: record at least 2-3 seconds of speech.",
                trimmedDuration
            );
        }

        return trimmedAudio;
    }
}
