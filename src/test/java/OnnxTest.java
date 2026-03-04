import ai.onnxruntime.*;
import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.io.jvm.AudioDispatcherFactory;
import be.tarsos.dsp.mfcc.MFCC;

import java.nio.FloatBuffer;
import java.util.*;

public class OnnxTest {

    // 模型参数 (需与SpeechBrain导出时一致)
    private static final int SAMPLE_RATE = 16000;
    private static final int BUFFER_SIZE = 400; // 25ms
    private static final int OVERLAP = 240;    // 10ms step (400-160)
    private static final int N_MELS = 80;      // 80个Mel波束

    public static void main(String[] args) throws Exception {
        String modelPath = "ecapa_tdnn.onnx";
//        String audioPath1 = "audio1.wav";
//        String audioPath2 = "audio2.wav";
        String audioPath1 = "D:\\Projects\\repos\\Voice-Test\\data\\voice\\Accio 飞来术\\1.wav";
        String audioPath2 = "D:\\Projects\\repos\\Voice-Test\\data\\voice\\Accio 飞来术\\1.wav";
        System.out.printf("正在处理音频 1: %s%n", audioPath1);
        System.out.printf("正在处理音频 2: %s%n", audioPath2);

        // 1. 初始化 ONNX 环境
        try (OrtEnvironment env = OrtEnvironment.getEnvironment();
             OrtSession session = env.createSession(modelPath, new OrtSession.SessionOptions())) {

            System.out.println("正在提取音频 1 的特征...");
            float[] embedding1 = getEmbedding(session, env, audioPath1);

            System.out.println("正在提取音频 2 的特征...");
            float[] embedding2 = getEmbedding(session, env, audioPath2);

            // 2. 计算余弦相似度
            float similarity = cosineSimilarity(embedding1, embedding2);
            System.out.printf("\n音频相似度得分: %.4f\n", similarity);

            if (similarity > 0.8) {
                System.out.println("结论：声音匹配成功！");
            } else {
                System.out.println("结论：声音不匹配。");
            }
        }
    }

    /**
     * 核心逻辑：读取音频 -> TarsosDSP提取FBank -> ONNX推理
     */
    private static float[] getEmbedding(OrtSession session, OrtEnvironment env, String audioPath) throws Exception {
        List<float[]> features = new ArrayList<>();

        // 使用 TarsosDSP 提取 Mel Filterbank
        // 注意：ECAPA-TDNN 需要 Log-Mel 特征，这里借用 MFCC 类但调整内部逻辑
        AudioDispatcher dispatcher = AudioDispatcherFactory.fromPipe(audioPath, SAMPLE_RATE, BUFFER_SIZE, OVERLAP);

        // 我们需要 80 个 Mel 滤波器
        final MFCC mfccProcessor = new MFCC(BUFFER_SIZE, SAMPLE_RATE, N_MELS, N_MELS, 133, 8000);

        dispatcher.addAudioProcessor(new AudioProcessor() {
            @Override
            public boolean process(AudioEvent audioEvent) {
                mfccProcessor.process(audioEvent);
                // TarsosDSP 内部其实计算了 melFilterBankEnergies
                // 如果想获得最准的 Log-Mel 特征，建议手动对输入信号做：
                // 1. FFT -> 2. 映射到 Mel 刻度 -> 3. 取 Log
                // 既然目前为了快速实现，先按上面的修改法把维度对齐到 80。
                float[] mfcc = mfccProcessor.getMFCC();
                features.add(mfcc.clone());
                return true;
            }
            @Override public void processingFinished() {}
        });
        dispatcher.run();

        // 转换为模型要求的 Tensor 形状: [1, time, 80]
        int timeSteps = features.size();
        float[] flattened = new float[timeSteps * N_MELS];

        // 简单的归一化 (Z-Score)
        normalizeFeatures(features);

        for (int i = 0; i < timeSteps; i++) {
            System.arraycopy(features.get(i), 0, flattened, i * N_MELS, N_MELS);
        }

        OnnxTensor inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(flattened), new long[]{1, timeSteps, N_MELS});

        try (OrtSession.Result results = session.run(Collections.singletonMap("input", inputTensor))) {
            // 1. 获取三维数组 [Batch][Time][Dimension] -> [1][1][192]
            float[][][] output = (float[][][]) results.get(0).getValue();

            // 2. 提取出真正的 Embedding 向量 (第一个 Batch 的 第一个 TimeStep)
            float[] embedding = output[0][0];

            return embedding.clone();
        }
    }

    /**
     * 归一化特征 (按频率轴计算均值方差)
     */
    private static void normalizeFeatures(List<float[]> features) {
        if (features.isEmpty()) return;
        int steps = features.size();
        int featureDim = features.get(0).length; // 自动获取维度 (应该是80)

        for (int j = 0; j < featureDim; j++) {
            float sum = 0;
            for (float[] frame : features) sum += frame[j];
            float mean = sum / steps;

            float sqSum = 0;
            for (float[] frame : features) {
                sqSum += Math.pow(frame[j] - mean, 2);
            }
            float std = (float) Math.sqrt(sqSum / steps) + 1e-6f;

            for (float[] frame : features) {
                frame[j] = (frame[j] - mean) / std;
            }
        }
    }

    /**
     * 计算余弦相似度
     */
    private static float cosineSimilarity(float[] v1, float[] v2) {
        float dot = 0, n1 = 0, n2 = 0;
        for (int i = 0; i < v1.length; i++) {
            dot += v1[i] * v2[i];
            n1 += v1[i] * v1[i];
            n2 += v2[i] * v2[i];
        }
        return dot / (float) (Math.sqrt(n1) * Math.sqrt(n2));
    }
}