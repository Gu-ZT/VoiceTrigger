package dev.dubhe.voice.audio;

import dev.dubhe.voice.VoiceTrigger;

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
    private volatile boolean isRecording = false;
    private File outputFile;

    /**
     * 开始录制
     *
     * @param outputFile 输出文件
     * @throws LineUnavailableException 音频线路不可用异常
     */
    public void startRecording(File outputFile) throws LineUnavailableException {
        if (isRecording) {
            VoiceTrigger.LOGGER.warn("Already recording");
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

        VoiceTrigger.LOGGER.info("Started recording to: {}", outputFile.getAbsolutePath());
    }

    /**
     * 停止录制
     */
    public void stopRecording() {
        if (!isRecording) {
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
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        VoiceTrigger.LOGGER.info("Stopped recording");
    }

    /**
     * 录制音频数据
     *
     * @throws IOException IO异常
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

        // 保存为WAV文件
        byte[] audioData = byteOutputStream.toByteArray();
        saveToWavFile(audioData);
    }

    /**
     * 保存音频数据为WAV文件
     *
     * @param audioData 音频数据
     * @throws IOException IO异常
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
        AudioInputStream audioInputStream = new AudioInputStream(
            byteInputStream,
            format,
            audioData.length / format.getFrameSize()
        );

        try {
            AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, outputFile);
            VoiceTrigger.LOGGER.info("Saved audio to: {}", outputFile.getAbsolutePath());
        } finally {
            audioInputStream.close();
        }
    }

    /**
     * 检查是否正在录制
     *
     * @return 是否正在录制
     */
    public boolean isRecording() {
        return isRecording;
    }

    /**
     * 获取输出文件
     *
     * @return 输出文件
     */
    public File getOutputFile() {
        return outputFile;
    }
}
