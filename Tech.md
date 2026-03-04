采用 **TarsosDSP** 方案

当玩家录制一段语音作为指令时，需要提取其 **MFCC 序列** 并保存到内存以及文件中，且在下一次启动时从文件加载。
```java
// 核心逻辑：提取MFCC序列
List<float[]> templateMfccs = new ArrayList<>();

AudioDispatcher dispatcher = AudioDispatcherFactory.fromPipe(audioFilePath, 44100, 1024, 512);
MFCC mfccProcessor = new MFCC(1024, 44100, 13, 40, 133, 8000);

dispatcher.addAudioProcessor(mfccProcessor);
dispatcher.addAudioProcessor(new AudioProcessor() {
    @Override
    public boolean process(AudioEvent audioEvent) {
        // 获取当前帧的MFCC特征
        float[] currentMfcc = mfccProcessor.getMFCC();
        templateMfccs.add(currentMfcc.clone());
        return true;
    }
    @Override public void processingFinished() {}
});
dispatcher.run();
```

实时监听与匹配
在游戏运行期间，你需要从麦克风读取流数据。为了防止语速不一，我们需要实现 **DTW (Dynamic Time Warping)** 算法。

**1. 实时流处理：**
你需要一个“滑动窗口”（例如 2 秒的缓冲区），不断地将当前窗口内的 MFCC 序列与模板进行对比。

**2. DTW 相似度计算（Java 实现参考）：**
由于 Java 没有成熟的 `fastdtw` 库，你需要手写一个基础的 DTW 算法（其实逻辑很简单，就是动态规划）：

```java
public class DTW {
    public static float compute(List<float[]> seq1, List<float[]> seq2) {
        int n = seq1.size();
        int m = seq2.size();
        float[][] dtw = new float[n + 1][m + 1];

        for (int i = 1; i <= n; i++) dtw[i][0] = Float.MAX_VALUE;
        for (int j = 1; j <= m; j++) dtw[0][j] = Float.MAX_VALUE;
        dtw[0][0] = 0;

        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                float dist = euclideanDistance(seq1.get(i-1), seq2.get(j-1));
                dtw[i][j] = dist + Math.min(dtw[i-1][j], Math.min(dtw[i][j-1], dtw[i-1][j-1]));
            }
        }
        return dtw[n][m] / (n + m); // 归一化距离
    }

    private static float euclideanDistance(float[] a, float[] b) {
        float sum = 0;
        for (int i = 0; i < a.length; i++) {
            sum += Math.pow(a[i] - b[i], 2);
        }
        return (float) Math.sqrt(sum);
    }
}
```

优化方案：让匹配更流畅

#### 1. 加入 VAD (静音检测)
不要在任何时候都跑相似度比对。TarsosDSP 提供了 `SilenceDetector`。
*   只有当检测到“有人在说话”（能量超过阈值）时，才开始收集缓冲区并进行 DTW 匹配。
*   这样可以大幅降低 CPU 消耗，防止游戏卡顿。

#### 2. 性能建议
*   **MFCC 维度：** 建议使用 13 维 MFCC 即可，维度太高会增加 DTW 计算量。
*   **采样率：** 实时监听可以降低采样率到 16kHz，效果足够且计算更快。
*   **并发：** 将 DTW 匹配逻辑放在一个单独的线程中运行，不要阻塞游戏的主循环或音频采集线程。
