package dev.dubhe.voice.audio;

import org.jetbrains.annotations.NotNull;

public class AudioUtils {

    public static double[] bytesToSamples(byte[] audioBytes, int length) {
        int numSamples = length / 2;
        double[] samples = new double[numSamples];

        for (int i = 0; i < numSamples; i++) {
            int low = audioBytes[2 * i] & 0xFF;
            int high = audioBytes[2 * i + 1];
            int sample = (high << 8) | low;

            if (sample >= 32768) {
                sample -= 65536;
            }

            samples[i] = sample / 32768.0;
        }

        return samples;
    }

    public static double @NotNull [] calculateAverageMFCC(double @NotNull [][] mfcc) {
        if (mfcc.length == 0) {
            return new double[0];
        }

        int numCoeffs = mfcc[0].length;
        double[] avg = new double[numCoeffs];

        for (double[] coeffs : mfcc) {
            for (int i = 0; i < numCoeffs; i++) {
                avg[i] += coeffs[i];
            }
        }

        for (int i = 0; i < numCoeffs; i++) {
            avg[i] /= mfcc.length;
        }

        return avg;
    }
}

