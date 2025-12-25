package dev.dubhe.voice.audio;

import java.util.List;

/**
 * 动态时间规整（Dynamic Time Warping）算法实现
 * 用于计算两个不同长度的MFCC序列之间的相似度
 */
public class DTW {

    /**
     * 计算两个MFCC序列之间的DTW距离
     *
     * @param seq1 第一个MFCC序列
     * @param seq2 第二个MFCC序列
     * @return 归一化的DTW距离（越小越相似）
     */
    public static float compute(List<float[]> seq1, List<float[]> seq2) {
        if (seq1.isEmpty() || seq2.isEmpty()) {
            return Float.MAX_VALUE;
        }

        int n = seq1.size();
        int m = seq2.size();
        float[][] dtw = new float[n + 1][m + 1];

        // 初始化边界条件
        for (int i = 1; i <= n; i++) {
            dtw[i][0] = Float.MAX_VALUE;
        }
        for (int j = 1; j <= m; j++) {
            dtw[0][j] = Float.MAX_VALUE;
        }
        dtw[0][0] = 0;

        // 动态规划计算DTW矩阵
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                float dist = euclideanDistance(seq1.get(i - 1), seq2.get(j - 1));
                dtw[i][j] = dist + Math.min(
                    dtw[i - 1][j],      // 插入
                    Math.min(
                        dtw[i][j - 1],  // 删除
                        dtw[i - 1][j - 1] // 匹配
                    )
                );
            }
        }

        // 归一化距离（除以路径长度）
        return dtw[n][m] / (n + m);
    }

    /**
     * 计算两个MFCC特征向量之间的欧氏距离
     *
     * @param a 第一个特征向量
     * @param b 第二个特征向量
     * @return 欧氏距离
     */
    private static float euclideanDistance(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Feature vectors must have the same length");
        }

        float sum = 0;
        for (int i = 0; i < a.length; i++) {
            float diff = a[i] - b[i];
            sum += diff * diff;
        }
        return (float) Math.sqrt(sum);
    }

    /**
     * 计算两个序列的相似度分数（0-1之间，越大越相似）
     *
     * @param seq1      第一个MFCC序列
     * @param seq2      第二个MFCC序列
     * @param threshold 距离阈值（用于归一化）
     * @return 相似度分数
     */
    public static float similarity(List<float[]> seq1, List<float[]> seq2, float threshold) {
        float distance = compute(seq1, seq2);
        if (distance >= threshold) {
            return 0.0f;
        }
        return 1.0f - (distance / threshold);
    }
}
