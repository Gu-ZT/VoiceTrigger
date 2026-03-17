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

import java.io.File;
import javax.annotation.Nullable;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

/**
 * 基于深度学习的音频相似度计算工具
 * 使用 Wav2Vec2 模型直接处理原始音频波形（Raw Audio Waveforms），
 * 通过余弦相似度判断音频相似性。Wav2Vec2 是端到端模型，不需要 MFCC 等传统特征。
 */
public class AudioSimilarityDL {
    // Wav2Vec2 要求的标准：16kHz 采样率
    private static final int TARGET_SAMPLE_RATE = 16000;

    public static float[] standardize(float[] data) {
        double sum = 0;
        for (float x : data) sum += x;
        float mean = (float) (sum / data.length);

        double sumSq = 0;
        for (float x : data) sumSq += (x - mean) * (x - mean);
        float std = (float) Math.sqrt(sumSq / data.length + 1e-7);

        float[] standardized = new float[data.length];
        for (int i = 0; i < data.length; i++) {
            standardized[i] = (data[i] - mean) / std;
        }
        return standardized;
    }

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
        bytes = VoiceRecorder.trimSilence(bytes);
        targetStream.close();
        originalStream.close();

        // 2. 严格转换：Short (Little Endian) -> Float -> Standardize
        float[] floatArray = new float[bytes.length / 2];
        for (int i = 0; i < floatArray.length; i++) {
            // 手动处理字节，确保不出错
            int low = bytes[i * 2] & 0xff;
            int high = bytes[i * 2 + 1] << 8;
            short s = (short) (high | low);
            floatArray[i] = s / 32768.0f; // 先归一化到 [-1, 1]
        }

        // 3. 标准化
        return standardize(floatArray);
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
     * 计算两个特征向量的余弦相似度
     * 因为 Python 模型末尾已经做了 L2 归一化，所以直接计算点积即为余弦相似度
     */
    public static float calculateSimilarity(float[] feat1, float[] feat2) {
        float dotProduct = 0;
        for (int i = 0; i < feat1.length; i++) {
            dotProduct += feat1[i] * feat2[i];
        }
        return dotProduct;
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
}
