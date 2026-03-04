package dev.dubhe.voice.audio;

import java.util.List;

/**
 * 动态时间规整（Dynamic Time Warping）算法实现
 * 用于计算两个不同长度的MFCC序列之间的相似度
 */
public class DTW {

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
                dtw[i][j] = dist + Math.min(Math.min(dtw[i - 1][j], dtw[i][j - 1]), dtw[i - 1][j - 1]);
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
