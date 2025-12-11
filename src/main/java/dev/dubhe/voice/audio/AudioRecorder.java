package dev.dubhe.voice.audio;

import dev.dubhe.voice.VoiceTrigger;

import javax.annotation.Nullable;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import java.io.ByteArrayOutputStream;

public class AudioRecorder {
    private static final float SAMPLE_RATE = 44100.0f;
    private static final int SAMPLE_SIZE = 16;
    private static final int CHANNELS = 1;
    private static final boolean SIGNED = true;
    private static final boolean BIG_ENDIAN = true;

    private TargetDataLine targetLine;
    private boolean isRecording = false;
    private ByteArrayOutputStream byteOutputStream;
    private final AudioFormat audioFormat;

    public AudioRecorder() {
        audioFormat = new AudioFormat(SAMPLE_RATE, SAMPLE_SIZE, CHANNELS, SIGNED, BIG_ENDIAN);
    }

    public void startRecording() {
        try {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, audioFormat);
            if (!AudioSystem.isLineSupported(info)) {
                VoiceTrigger.LOGGER.error("Line not supported");
                return;
            }

            targetLine = (TargetDataLine) AudioSystem.getLine(info);
            targetLine.open(audioFormat);
            targetLine.start();

            isRecording = true;
            byteOutputStream = new ByteArrayOutputStream();

            // 启动录制线程
            Thread recordingThread = new Thread(() -> {
                byte[] buffer = new byte[4096];
                int bytesRead;

                while (isRecording) {
                    bytesRead = targetLine.read(buffer, 0, buffer.length);
                    if (bytesRead > 0) {
                        byteOutputStream.write(buffer, 0, bytesRead);
                    }
                }
            });

            recordingThread.setDaemon(true);
            recordingThread.start();

        } catch (LineUnavailableException e) {
            VoiceTrigger.LOGGER.error(e.getMessage(), e);
        }
    }

    public @Nullable byte[] stopRecording() {
        if (!isRecording) {
            return null;
        }

        isRecording = false;

        if (targetLine != null) {
            targetLine.stop();
            targetLine.close();
        }

        return byteOutputStream.toByteArray();
    }

    public double[] convertToDoubleArray(byte[] audioBytes) {
        int numSamples = audioBytes.length / 2;
        double[] audioData = new double[numSamples];

        for (int i = 0; i < numSamples; i++) {
            int b1 = audioBytes[2 * i] & 0xFF;
            int b2 = audioBytes[2 * i + 1] & 0xFF;
            int sample = (b2 << 8) + b1;

            // 转换为有符号16位
            if (sample >= 32768) {
                sample -= 65536;
            }

            audioData[i] = sample / 32768.0;
        }

        return audioData;
    }

    public boolean isRecording() {
        return isRecording;
    }

    public AudioFormat getAudioFormat() {
        return audioFormat;
    }
}
