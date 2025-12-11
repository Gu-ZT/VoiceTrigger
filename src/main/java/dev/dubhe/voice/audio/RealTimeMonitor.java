package dev.dubhe.voice.audio;

import dev.dubhe.voice.VoiceTrigger;
import dev.dubhe.voice.event.AnalyzeAudioEvent;
import dev.dubhe.voice.event.AudioLevelUpdateEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.NotNull;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class RealTimeMonitor {
    private static final int BUFFER_SIZE = 4096;
    private static final float SAMPLE_RATE = 44100.0f;

    private TargetDataLine line;
    private boolean monitoring = false;
    private final CircularBuffer circularBuffer;
    private ScheduledExecutorService executor;
    public final SimilarityCalculator similarityCalculator = new SimilarityCalculator();

    public RealTimeMonitor(int bufferSeconds) {
        int bufferSize = (int) (SAMPLE_RATE * bufferSeconds);
        circularBuffer = new CircularBuffer(bufferSize);
    }

    public void startMonitoring() {
        try {
            AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

            if (!AudioSystem.isLineSupported(info)) {
                VoiceTrigger.LOGGER.error("Audio line not supported");
                return;
            }

            line = (TargetDataLine) AudioSystem.getLine(info);
            line.open(format);
            line.start();

            monitoring = true;

            // 启动音频捕获线程
            Thread captureThread = new Thread(this::captureAudio);
            captureThread.setDaemon(true);
            captureThread.start();

            // 启动分析线程（每100ms分析一次）
            executor = Executors.newSingleThreadScheduledExecutor();
            executor.scheduleAtFixedRate(this::analyzeBuffer, 0, 100, TimeUnit.MILLISECONDS);

        } catch (LineUnavailableException e) {
            VoiceTrigger.LOGGER.error(e.getMessage(), e);
        }
    }

    public void stopMonitoring() {
        monitoring = false;

        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                    VoiceTrigger.LOGGER.error("Failed to shutdown executor");
                }
            } catch (InterruptedException e) {
                VoiceTrigger.LOGGER.error(e.getMessage(), e);
                Thread.currentThread().interrupt();
            }
        }

        if (line != null) {
            line.stop();
            line.close();
        }
    }

    private void captureAudio() {
        byte[] buffer = new byte[BUFFER_SIZE];

        while (monitoring) {
            int bytesRead = line.read(buffer, 0, buffer.length);
            if (bytesRead > 0) {
                // 转换为double并添加到环形缓冲区
                double[] samples = AudioUtils.bytesToSamples(buffer, bytesRead);
                circularBuffer.add(samples);

                // 计算音频级别
                double level = calculateAudioLevel(samples);
                AudioLevelUpdateEvent event = new AudioLevelUpdateEvent(this, level);
                NeoForge.EVENT_BUS.post(event);
            }
        }
    }

    private void analyzeBuffer() {
        try {
            // 获取最近1秒的音频数据
            double[] recentAudio = circularBuffer.getRecentSamples((int) (SAMPLE_RATE));
            if (recentAudio.length < SAMPLE_RATE * 0.5) {
                return; // 数据不足
            }

            // 提取MFCC特征
            MFCCExtractor extractor = new MFCCExtractor();
            double[][] mfcc = extractor.extractMFCC(recentAudio, SAMPLE_RATE);

            AnalyzeAudioEvent event = new AnalyzeAudioEvent(this, mfcc);
            NeoForge.EVENT_BUS.post(event);
        } catch (Exception e) {
            VoiceTrigger.LOGGER.error(e.getMessage(), e);
        }
    }

    private double calculateAudioLevel(double @NotNull [] samples) {
        double sum = 0;
        for (double sample : samples) {
            sum += sample * sample;
        }
        double rms = Math.sqrt(sum / samples.length);
        return 20 * Math.log10(rms + 1e-10);
    }

    // 环形缓冲区实现
    private static class CircularBuffer {
        private final double[] buffer;
        private final int capacity;
        private int writeIndex = 0;
        private int size = 0;

        public CircularBuffer(int capacity) {
            this.capacity = capacity;
            this.buffer = new double[capacity];
        }

        public synchronized void add(double @NotNull [] samples) {
            for (double sample : samples) {
                buffer[writeIndex] = sample;
                writeIndex = (writeIndex + 1) % capacity;
                size = Math.min(size + 1, capacity);
            }
        }

        public synchronized double @NotNull [] getRecentSamples(int numSamples) {
            int samplesToGet = Math.min(numSamples, size);
            double[] result = new double[samplesToGet];

            int startIndex = (writeIndex - samplesToGet + capacity) % capacity;

            for (int i = 0; i < samplesToGet; i++) {
                result[i] = buffer[(startIndex + i) % capacity];
            }

            return result;
        }
    }
}
