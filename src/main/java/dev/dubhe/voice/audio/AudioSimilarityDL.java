package dev.dubhe.voice.audio;

import ai.djl.Device;
import ai.djl.inference.Predictor;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.NoBatchifyTranslator;
import ai.djl.translate.TranslatorContext;
import dev.dubhe.voice.VoiceTrigger;

import javax.annotation.Nullable;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.List;

/**
 * 基于深度学习的音频相似度计算工具
 * 使用 Wav2Vec2 模型直接处理原始音频波形（Raw Audio Waveforms），
 * 通过余弦相似度判断音频相似性。Wav2Vec2 是端到端模型，不需要 MFCC 等传统特征。
 */
public class AudioSimilarityDL {

    // Wav2Vec2 要求的标准：16kHz 采样率
    private static final int TARGET_SAMPLE_RATE = 16000;

    // 相似度阈值（余弦相似度大于此值认为匹配）
    private static final double SIMILARITY_THRESHOLD = 0.96;

    /**
     * 1. 预处理音频：读取 WAV 文件，转为 16kHz 单声道的 float 数组
     * Wav2Vec2 要求输入为 16kHz 采样率的原始波形数据
     *
     * @param filePath 音频文件路径
     * @return 预处理后的 float 数组
     * @throws Exception 音频处理异常
     */
    public static float[] readAndPreprocessWav(String filePath) throws Exception {
        File audioFile = new File(filePath);
        AudioInputStream originalStream = AudioSystem.getAudioInputStream(audioFile);
        AudioFormat originalFormat = originalStream.getFormat();

        // 目标格式：16kHz, 16 位 (2 字节), 单声道，线性 PCM, 小端序
        AudioFormat targetFormat = new AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            TARGET_SAMPLE_RATE,
            16,
            1,
            2,
            TARGET_SAMPLE_RATE,
            false
        );

        // 重采样转换
        AudioInputStream targetStream = AudioSystem.getAudioInputStream(targetFormat, originalStream);

        // 读取所有字节
        byte[] bytes = targetStream.readAllBytes();
        targetStream.close();
        originalStream.close();

        // 将 16-bit PCM byte 转换为 float (范围 -1.0 到 1.0)
        float[] floatArray = new float[bytes.length / 2];
        ShortBuffer shortBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
        for (int i = 0; i < floatArray.length; i++) {
            floatArray[i] = shortBuffer.get(i) / 32768.0f; // 归一化
        }

        return floatArray;
    }

    /**
     * 2. 从原始音频字节数据转换为模型输入
     * 用于实时录音场景：将录音的 PCM 字节数据转为 16kHz 的 float 数组
     *
     * @param audioData 原始音频字节数据（PCM 格式）
     * @param sampleRate 原始采样率
     * @return 预处理后的 float 数组（16kHz）
     */
    public static float[] preprocessRawAudio(byte[] audioData, int sampleRate) {
        if (audioData.length == 0) {
            return new float[0];
        }

        // 如果是 16-bit PCM 数据
        if (sampleRate != TARGET_SAMPLE_RATE || audioData.length % 2 != 0) {
            // 需要重采样或数据长度为奇数，返回空数组
            VoiceTrigger.LOGGER.warn("Sample rate mismatch: {} != {} or odd data length", sampleRate, TARGET_SAMPLE_RATE);
            return new float[0];
        }

        // 将 16-bit PCM byte 转换为 float (范围 -1.0 到 1.0)
        float[] floatArray = new float[audioData.length / 2];
        ShortBuffer shortBuffer = ByteBuffer.wrap(audioData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
        for (int i = 0; i < floatArray.length; i++) {
            floatArray[i] = shortBuffer.get(i) / 32768.0f; // 归一化
        }

        return floatArray;
    }

    /**
     * 2. 定义 DJL 翻译器 (将 float[] 转为模型需要的 NDArray，并将输出的 NDArray 转回 float[])
     */
    static class AudioTranslator implements NoBatchifyTranslator<float[], float[]> {
        @Override
        public NDList processInput(TranslatorContext ctx, float[] input) {
            NDManager manager = ctx.getNDManager();
            // 模型需要的 input_values shape 为 (batch_size, sequence_length) -> (1, sequence_length)
            NDArray array = manager.create(input).expandDims(0);
            return new NDList(array);
        }

        @Override
        public float[] processOutput(TranslatorContext ctx, NDList list) {
            // 输出的 pooled_features shape 为 (1, 768)，提取为一维 float 数组
            return list.singletonOrThrow().toFloatArray();
        }
    }

    /**
     * 3. 计算余弦相似度
     *
     * @param vectorA 向量 A
     * @param vectorB 向量 B
     * @return 余弦相似度（0-1 之间，越接近 1 越相似）
     */
    public static double cosineSimilarity(float[] vectorA, float[] vectorB) {
        if (vectorA.length != vectorB.length) {
            throw new IllegalArgumentException("向量维度不一致：" + vectorA.length + " vs " + vectorB.length);
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += Math.pow(vectorA[i], 2);
            normB += Math.pow(vectorB[i], 2);
        }

        if (normA == 0 || normB == 0) return 0;

        double similarity = dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));

        // 确保结果在 0-1 范围内
        return Math.max(0.0, Math.min(1.0, similarity));
    }

    /**
     * 加载模型并创建预测器
     *
     * @param modelPath 模型文件路径
     * @return ZooModel 实例
     * @throws Exception 模型加载异常
     */
    public static ZooModel<float[], float[]> loadModel(String modelPath) throws Exception {
        Criteria<float[], float[]> criteria = Criteria.builder()
            .setTypes(float[].class, float[].class)
            .optModelPath(new File(modelPath).toPath())
            .optEngine("PyTorch")
            .optTranslator(new AudioTranslator())
            .optDevice(Device.cpu())
            .build();

        VoiceTrigger.LOGGER.info("Loading audio similarity model from: {}", modelPath);
        return criteria.loadModel();
    }

    /**
     * 使用模型提取音频特征
     *
     * @param predictor 预测器
     * @param audioData 音频数据
     * @return 特征向量（768 维）
     */
    public static @Nullable float[] extractFeatures(Predictor<float[], float[]> predictor, float[] audioData) {
        try {
            return predictor.predict(audioData);
        } catch (Exception e) {
            VoiceTrigger.LOGGER.error("Failed to extract audio features", e);
            return null;
        }
    }

    /**
     * 判断两个音频是否相似
     *
     * @param similarity 相似度分数
     * @return 是否相似
     */
    public static boolean isSimilar(double similarity) {
        return similarity >= SIMILARITY_THRESHOLD;
    }

    /**
     * 获取当前相似度阈值
     *
     * @return 阈值
     */
    public static double getSimilarityThreshold() {
        return SIMILARITY_THRESHOLD;
    }

    /**
     * 设置相似度阈值（用于调试或动态调整）
     *
     * @param threshold 新阈值
     */
    public static void setSimilarityThreshold(double threshold) {
        VoiceTrigger.LOGGER.info("Similarity threshold changed from {} to {}", SIMILARITY_THRESHOLD, threshold);
        // 注意：由于 SIMILARITY_THRESHOLD 是 final 的，这个方法主要用于日志记录
        // 实际使用时应该直接修改阈值常量或使用配置系统
    }
}
