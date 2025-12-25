package dev.dubhe.voice;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.SilenceDetector;
import be.tarsos.dsp.io.jvm.AudioDispatcherFactory;
import be.tarsos.dsp.mfcc.MFCC;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.sound.sampled.UnsupportedAudioFileException;

/**
 * 基于 TarsosDSP 的语音对比系统
 * 按照 Tech.md 中的思路实现
 */
public class TarsosDSPVoiceComparison {

    private static final int SAMPLE_RATE = 44100;
    private static final int BUFFER_SIZE = 1024;
    private static final int OVERLAP = 512;
    private static final int MFCC_COEFFICIENTS = 13;

    // VAD 静音检测参数 (单位: dB)
    private static final double SILENCE_THRESHOLD = -70.0;

    public static void main(String[] args) {
        // 测试两个音频文件的相似度
        String audio1 = "D:\\Projects\\repos\\Voice-Test\\data\\voice\\Accio 飞来术\\1.wav";
        String audio2 = "D:\\Projects\\repos\\Voice-Test\\data\\voice\\Accio 飞来术\\2.wav";
        String audio3 = "D:\\Projects\\repos\\Voice-Test\\data\\voice\\Accio 飞来术\\3.wav";
        String audio4 = "D:\\Projects\\repos\\Voice-Test\\data\\voice\\Accio 飞来术\\4.wav";
        String audio5 = "D:\\Projects\\repos\\Voice-Test\\data\\voice\\Accio 飞来术\\5.wav";
        String audio6 = "D:\\Projects\\repos\\Voice-Test\\data\\voice\\Ascendio 齐齐高攀\\1.wav";
        String audio7 = "D:\\Projects\\repos\\Voice-Test\\data\\voice\\Ascendio 齐齐高攀\\2.wav";
        String audio8 = "D:\\Projects\\repos\\Voice-Test\\data\\voice\\Ascendio 齐齐高攀\\3.wav";
        String audio9 = "D:\\Projects\\repos\\Voice-Test\\data\\voice\\Ascendio 齐齐高攀\\4.wav";
        String audio0 = "D:\\Projects\\repos\\Voice-Test\\data\\voice\\Ascendio 齐齐高攀\\5.wav";


        TarsosDSPVoiceComparison.test(audio1, audio2);
        TarsosDSPVoiceComparison.test(audio1, audio3);
        TarsosDSPVoiceComparison.test(audio1, audio4);
        TarsosDSPVoiceComparison.test(audio1, audio5);
        TarsosDSPVoiceComparison.test(audio1, audio6);
        TarsosDSPVoiceComparison.test(audio1, audio7);
        TarsosDSPVoiceComparison.test(audio1, audio8);
        TarsosDSPVoiceComparison.test(audio1, audio9);
        TarsosDSPVoiceComparison.test(audio1, audio0);
        TarsosDSPVoiceComparison.test(audio2, audio3);
        TarsosDSPVoiceComparison.test(audio2, audio4);
        TarsosDSPVoiceComparison.test(audio2, audio5);
        TarsosDSPVoiceComparison.test(audio2, audio6);
        TarsosDSPVoiceComparison.test(audio2, audio7);
        TarsosDSPVoiceComparison.test(audio2, audio8);
        TarsosDSPVoiceComparison.test(audio2, audio9);
        TarsosDSPVoiceComparison.test(audio2, audio0);
        TarsosDSPVoiceComparison.test(audio3, audio4);
        TarsosDSPVoiceComparison.test(audio3, audio5);
        TarsosDSPVoiceComparison.test(audio3, audio6);
        TarsosDSPVoiceComparison.test(audio3, audio7);
        TarsosDSPVoiceComparison.test(audio3, audio8);
        TarsosDSPVoiceComparison.test(audio3, audio9);
        TarsosDSPVoiceComparison.test(audio3, audio0);
        TarsosDSPVoiceComparison.test(audio4, audio5);
        TarsosDSPVoiceComparison.test(audio4, audio6);
        TarsosDSPVoiceComparison.test(audio4, audio7);
        TarsosDSPVoiceComparison.test(audio4, audio8);
        TarsosDSPVoiceComparison.test(audio4, audio9);
        TarsosDSPVoiceComparison.test(audio4, audio0);
        TarsosDSPVoiceComparison.test(audio5, audio6);
        TarsosDSPVoiceComparison.test(audio5, audio7);
        TarsosDSPVoiceComparison.test(audio5, audio8);
        TarsosDSPVoiceComparison.test(audio5, audio9);
        TarsosDSPVoiceComparison.test(audio5, audio0);
        TarsosDSPVoiceComparison.test(audio6, audio7);
        TarsosDSPVoiceComparison.test(audio6, audio8);
        TarsosDSPVoiceComparison.test(audio6, audio9);
        TarsosDSPVoiceComparison.test(audio6, audio0);
        TarsosDSPVoiceComparison.test(audio7, audio8);
        TarsosDSPVoiceComparison.test(audio7, audio9);
        TarsosDSPVoiceComparison.test(audio7, audio0);
        TarsosDSPVoiceComparison.test(audio8, audio9);
        TarsosDSPVoiceComparison.test(audio8, audio0);
        TarsosDSPVoiceComparison.test(audio9, audio0);

    }

    public static void test(String audio1, String audio2) {
        try {
            System.out.println("=== TarsosDSP 语音相似度测试 ===\n");

            // 阶段 A：提取两段音频的 MFCC 特征 (包含 VAD 静音检测)
            System.out.println("阶段 A: 提取音频特征 (包含 VAD 静音检测)...");
            VoiceFeatureResult result1 = extractMFCCFeatures(audio1);
            System.out.println("音频 1: " + audio1);
            System.out.println("  总帧数: " + result1.totalFrames + ", 有效语音帧: " + result1.mfccFeatures.size() +
                               " (过滤了 " + (result1.totalFrames - result1.mfccFeatures.size()) + " 帧静音)\n");

            VoiceFeatureResult result2 = extractMFCCFeatures(audio2);
            System.out.println("音频 2: " + audio2);
            System.out.println("  总帧数: " + result2.totalFrames + ", 有效语音帧: " + result2.mfccFeatures.size() +
                               " (过滤了 " + (result2.totalFrames - result2.mfccFeatures.size()) + " 帧静音)\n");

            // 阶段 B：使用 DTW 算法计算相似度
            System.out.println("阶段 B: 计算相似度...");
            float dtwDistance = DTW.compute(result1.mfccFeatures, result2.mfccFeatures);
            System.out.println("DTW 距离: " + String.format("%.4f", dtwDistance));

            // 转换为相似度百分比（距离越小，相似度越高）
            double similarity = convertToSimilarity(dtwDistance);
            System.out.println("相似度: " + String.format("%.2f%%", similarity * 100));
            System.out.println("评级: " + getSimilarityLevel(similarity));

        } catch (Exception e) {
            System.err.println("错误: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 阶段 A：提取 MFCC 特征序列 (包含 VAD 静音检测)
     * 对应 Tech.md 中的 "玩家绑定语音（提取特征指纹）"
     * 使用 SilenceDetector 过滤静音帧，只保留有效语音帧
     */
    public static VoiceFeatureResult extractMFCCFeatures(String audioFilePath)
        throws IOException, UnsupportedAudioFileException {

        VoiceFeatureResult result = new VoiceFeatureResult();
        result.mfccFeatures = new ArrayList<>();
        result.totalFrames = 0;

        File audioFile = new File(audioFilePath);

        if (!audioFile.exists()) {
            throw new IOException("音频文件不存在: " + audioFilePath);
        }

        // 创建音频调度器
        AudioDispatcher dispatcher = AudioDispatcherFactory.fromPipe(
            audioFilePath,
            SAMPLE_RATE,
            BUFFER_SIZE,
            OVERLAP
        );

        // 添加 VAD 静音检测器 (在 MFCC 处理器之前)
        SilenceDetector silenceDetector = new SilenceDetector(SILENCE_THRESHOLD, false);
        dispatcher.addAudioProcessor(silenceDetector);

        // 创建 MFCC 处理器
        // 参数: bufferSize, sampleRate, numberOfCoefficients, numberOfFilters, lowerFilterFreq, upperFilterFreq
        MFCC mfccProcessor = new MFCC(
            BUFFER_SIZE,
            SAMPLE_RATE,
            MFCC_COEFFICIENTS,
            40,      // Mel滤波器数量
            133,     // 最低频率
            8000     // 最高频率
        );

        // 添加 MFCC 处理器
        dispatcher.addAudioProcessor(mfccProcessor);

        // 添加音频处理器来收集 MFCC 特征 (只收集非静音帧)
        dispatcher.addAudioProcessor(new AudioProcessor() {
            @Override
            public boolean process(AudioEvent audioEvent) {
                result.totalFrames++;

                // 只在检测到有效语音时收集 MFCC 特征
                // currentSPL() 返回当前帧的声压级 (Sound Pressure Level, dB)
                if (silenceDetector.currentSPL() > SILENCE_THRESHOLD) {
                    float[] currentMfcc = mfccProcessor.getMFCC();
                    if (currentMfcc != null && currentMfcc.length > 0) {
                        // 克隆数组以避免引用问题
                        result.mfccFeatures.add(currentMfcc.clone());
                    }
                }
                return true;
            }

            @Override
            public void processingFinished() {
                // 处理完成
            }
        });

        // 运行处理器
        dispatcher.run();

        return result;
    }

    /**
     * 将 DTW 距离转换为相似度分数 (0-1)
     * 使用指数衰减函数
     */
    private static double convertToSimilarity(float dtwDistance) {
        // 根据经验调整的阈值
        double threshold = 10.0;
        return Math.exp(-dtwDistance / threshold);
    }

    /**
     * 获取相似度等级描述
     */
    private static String getSimilarityLevel(double similarity) {
        if (similarity >= 0.85) return "非常相似 ⭐⭐⭐⭐⭐";
        if (similarity >= 0.70) return "相似 ⭐⭐⭐⭐";
        if (similarity >= 0.50) return "部分相似 ⭐⭐⭐";
        if (similarity >= 0.30) return "略有相似 ⭐⭐";
        return "不相似 ⭐";
    }
}

/**
 * DTW (Dynamic Time Warping) 算法实现
 * 对应 Tech.md 中的动态时间规整算法
 */
class DTW {

    /**
     * 计算两个 MFCC 序列之间的 DTW 距离
     *
     * @param seq1 第一个 MFCC 序列
     * @param seq2 第二个 MFCC 序列
     * @return 归一化的 DTW 距离
     */
    public static float compute(List<float[]> seq1, List<float[]> seq2) {
        if (seq1.isEmpty() || seq2.isEmpty()) {
            return Float.MAX_VALUE;
        }

        int n = seq1.size();
        int m = seq2.size();

        // 创建 DTW 矩阵
        float[][] dtw = new float[n + 1][m + 1];

        // 初始化边界
        for (int i = 1; i <= n; i++) {
            dtw[i][0] = Float.MAX_VALUE;
        }
        for (int j = 1; j <= m; j++) {
            dtw[0][j] = Float.MAX_VALUE;
        }
        dtw[0][0] = 0;

        // 动态规划填充 DTW 矩阵
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                float dist = euclideanDistance(seq1.get(i - 1), seq2.get(j - 1));

                // DTW 递推公式
                dtw[i][j] = dist + Math.min(
                    Math.min(dtw[i - 1][j], dtw[i][j - 1]),
                    dtw[i - 1][j - 1]
                );
            }
        }

        // 归一化距离（除以路径长度）
        return dtw[n][m] / (n + m);
    }

    /**
     * 计算两个 MFCC 向量之间的欧氏距离
     */
    private static float euclideanDistance(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("向量维度不匹配");
        }

        float sum = 0;
        for (int i = 0; i < a.length; i++) {
            float diff = a[i] - b[i];
            sum += diff * diff;
        }
        return (float) Math.sqrt(sum);
    }
}

/**
 * 语音特征提取结果
 * 包含 MFCC 特征和统计信息
 */
class VoiceFeatureResult {
    List<float[]> mfccFeatures;  // 有效语音帧的 MFCC 特征
    int totalFrames;              // 总帧数(包含静音帧)
}
