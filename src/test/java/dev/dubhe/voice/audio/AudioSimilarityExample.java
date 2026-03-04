package dev.dubhe.voice.audio;

import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.ZooModel;

import java.io.File;

/**
 * 音频相似度深度学习测试示例
 * 展示如何使用 AudioSimilarityDL 工具类进行音频相似度判断
 */
public class AudioSimilarityExample {

    public static void main(String[] args) {
        try {
            // 模型路径
            String modelPath = "src/main/resources/wav2vec2_feature_extractor.pt";

            // 检查模型文件是否存在
            File modelFile = new File(modelPath);
            if (!modelFile.exists()) {
                System.err.println("模型文件不存在：" + modelPath);
                System.err.println("请先下载或生成 Wav2Vec2 特征提取模型");
                return;
            }

            // 加载模型
            System.out.println("正在加载深度学习模型...");
            ZooModel<float[], float[]> model = AudioSimilarityDL.loadModel(modelPath);
            Predictor<float[], float[]> predictor = model.newPredictor();
            System.out.println("模型加载完成！");

            // 示例 1: 使用 WAV 文件进行相似度比较
            testWithWavFiles(predictor);

            // 清理资源
            predictor.close();
            model.close();

        } catch (Exception e) {
            System.err.println("测试失败：" + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 示例 1: 使用 WAV 文件进行相似度比较
     */
    private static void testWithWavFiles(Predictor<float[], float[]> predictor) throws Exception {
        System.out.println("\n=== 测试 1: WAV 文件相似度比较 ===");

        // 假设的音频文件路径（需要替换为实际路径）
        // String audio1 = "D:\\Projects\\repos\\Voice-Test\\data\\voice\\Accio 飞来术\\1.wav";
        // String audio2 = "D:\\Projects\\repos\\Voice-Test\\data\\voice\\Accio 飞来术\\5.wav";

        String audio1 = "D:\\Projects\\repos\\Voice-Test\\data\\voice\\Arania Exumai 驱逐蜘蛛\\1.wav";
        String audio2 = "D:\\Projects\\repos\\Voice-Test\\data\\voice\\Arania Exumai 驱逐蜘蛛\\3.wav";

        File file1 = new File(audio1);
        File file2 = new File(audio2);

        if (!file1.exists() || !file2.exists()) {
            System.out.println("音频文件不存在，跳过此测试");
            return;
        }

        // 读取并预处理音频
        float[] audioData1 = AudioSimilarityDL.readAndPreprocessWav(audio1);
        float[] audioData2 = AudioSimilarityDL.readAndPreprocessWav(audio2);

        System.out.printf("音频 1 数据长度：%d%n", audioData1.length);
        System.out.printf("音频 2 数据长度：%d%n", audioData2.length);

        // 提取特征向量
        float[] features1 = AudioSimilarityDL.extractFeatures(predictor, audioData1);
        float[] features2 = AudioSimilarityDL.extractFeatures(predictor, audioData2);

        if (features1 != null && features2 != null) {
            System.out.printf("特征向量 1 维度：%d%n", features1.length);
            System.out.printf("特征向量 2 维度：%d%n", features2.length);

            // 计算余弦相似度
            double similarity = AudioSimilarityDL.cosineSimilarity(features1, features2);
            System.out.printf("相似度得分：%.4f%n", similarity);

            // 判断是否相似
            boolean isSimilar = AudioSimilarityDL.isSimilar(similarity);
            System.out.println("结论：" + (isSimilar ? "这是高度相似的音频！" : "音频差异较大"));
        }
    }
}
