package dev.dubhe.voice.audio;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;

import java.util.Arrays;

public class MFCCExtractor {
    private static final int NUM_MFCC = 13;
    private static final int NUM_FILTERS = 26;
    private static final double PRE_EMPHASIS_ALPHA = 0.97;
    private static final int FFT_SIZE = 512;

    public double[][] extractMFCC(double[] audio, double sampleRate) {
        // 1. 预加重
        double[] emphasized = preEmphasis(audio);

        // 2. 分帧
        int frameLength = (int) (0.025 * sampleRate); // 25ms
        int frameShift = (int) (0.01 * sampleRate);   // 10ms
        double[][] frames = frameSignal(emphasized, frameLength, frameShift);

        // 3. 计算每帧的MFCC
        double[][] mfccs = new double[frames.length][NUM_MFCC];
        double[] melFilter = createMelFilterBank(sampleRate);

        for (int i = 0; i < frames.length; i++) {
            // 加窗
            double[] windowed = applyHammingWindow(frames[i]);

            // 填充到FFT长度
            double[] padded = new double[FFT_SIZE];
            System.arraycopy(windowed, 0, padded, 0, Math.min(windowed.length, FFT_SIZE));

            // FFT
            double[] magnitudeSpectrum = computeMagnitudeSpectrum(padded);

            // 应用梅尔滤波器组
            double[] melSpectrum = applyMelFilterBank(magnitudeSpectrum, melFilter);

            // 取对数
            double[] logMel = new double[melSpectrum.length];
            for (int j = 0; j < melSpectrum.length; j++) {
                logMel[j] = Math.log(melSpectrum[j] + 1e-10);
            }

            // DCT得到MFCC
            mfccs[i] = applyDCT(logMel);
        }

        return mfccs;
    }

    private double[] preEmphasis(double[] signal) {
        double[] emphasized = new double[signal.length];
        emphasized[0] = signal[0];

        for (int i = 1; i < signal.length; i++) {
            emphasized[i] = signal[i] - PRE_EMPHASIS_ALPHA * signal[i - 1];
        }

        return emphasized;
    }

    private double[][] frameSignal(double[] signal, int frameSize, int hopSize) {
        int numFrames = (int) Math.ceil((double) (signal.length - frameSize) / hopSize) + 1;
        double[][] frames = new double[numFrames][frameSize];

        for (int i = 0; i < numFrames; i++) {
            int start = i * hopSize;
            int end = Math.min(start + frameSize, signal.length);

            if (end - start < frameSize) {
                // 最后一帧用零填充
                Arrays.fill(frames[i], 0);
                System.arraycopy(signal, start, frames[i], 0, end - start);
            } else {
                System.arraycopy(signal, start, frames[i], 0, frameSize);
            }
        }

        return frames;
    }

    private double[] applyHammingWindow(double[] frame) {
        double[] windowed = new double[frame.length];
        int N = frame.length - 1;

        for (int i = 0; i < frame.length; i++) {
            windowed[i] = frame[i] * (0.54 - 0.46 * Math.cos(2 * Math.PI * i / N));
        }

        return windowed;
    }

    private double[] computeMagnitudeSpectrum(double[] signal) {
        FastFourierTransformer fft = new FastFourierTransformer(DftNormalization.STANDARD);
        Complex[] spectrum = fft.transform(signal, TransformType.FORWARD);

        double[] magnitude = new double[spectrum.length / 2];
        for (int i = 0; i < magnitude.length; i++) {
            magnitude[i] = spectrum[i].abs();
        }

        return magnitude;
    }

    private double[] createMelFilterBank(double sampleRate) {
        // 计算梅尔频率范围
        double lowMel = hzToMel(300);
        double highMel = hzToMel(sampleRate / 2);

        // 均匀分布的梅尔频率点
        double[] melPoints = new double[NUM_FILTERS + 2];
        for (int i = 0; i < melPoints.length; i++) {
            melPoints[i] = lowMel + i * (highMel - lowMel) / (melPoints.length - 1);
        }

        // 转换回线性频率
        double[] hzPoints = new double[melPoints.length];
        for (int i = 0; i < hzPoints.length; i++) {
            hzPoints[i] = melToHz(melPoints[i]);
        }

        return hzPoints;
    }

    private double[] applyMelFilterBank(double[] magnitudeSpectrum, double[] hzPoints) {
        double[] melSpectrum = new double[NUM_FILTERS];
        double fftSize = magnitudeSpectrum.length * 2;

        for (int m = 0; m < NUM_FILTERS; m++) {
            double sum = 0;

            for (int k = 0; k < magnitudeSpectrum.length; k++) {
                double freq = k * 44100 / fftSize;
                double weight = triangularFilterWeight(freq, hzPoints[m], hzPoints[m + 1], hzPoints[m + 2]);
                sum += weight * magnitudeSpectrum[k] * magnitudeSpectrum[k];
            }

            melSpectrum[m] = sum;
        }

        return melSpectrum;
    }

    private double triangularFilterWeight(double freq, double left, double center, double right) {
        if (freq < left || freq > right) {
            return 0;
        } else if (freq < center) {
            return (freq - left) / (center - left);
        } else {
            return (right - freq) / (right - center);
        }
    }

    private double[] applyDCT(double[] logMelSpectrum) {
        int N = logMelSpectrum.length;
        double[] mfcc = new double[NUM_MFCC];

        for (int n = 0; n < NUM_MFCC; n++) {
            double sum = 0;

            for (int m = 0; m < N; m++) {
                sum += logMelSpectrum[m] * Math.cos(Math.PI * n * (2 * m + 1) / (2 * N));
            }

            mfcc[n] = Math.sqrt(2.0 / N) * sum;
        }

        return mfcc;
    }

    private double hzToMel(double hz) {
        return 2595 * Math.log10(1 + hz / 700.0);
    }

    private double melToHz(double mel) {
        return 700 * (Math.pow(10, mel / 2595.0) - 1);
    }
}