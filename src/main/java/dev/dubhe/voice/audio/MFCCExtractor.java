package dev.dubhe.voice.audio;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.io.jvm.AudioDispatcherFactory;
import be.tarsos.dsp.mfcc.MFCC;
import dev.dubhe.voice.VoiceTrigger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.sound.sampled.LineUnavailableException;

/**
 * MFCC特征提取器
 * 负责从音频文件或音频流中提取MFCC特征
 */
public class MFCCExtractor {

    // 音频参数配置
    private static final int SAMPLE_RATE = 16000;      // 采样率（16kHz足够且计算快）
    private static final int BUFFER_SIZE = 1024;        // 缓冲区大小
    private static final int OVERLAP = 512;             // 重叠大小
    private static final int MFCC_COUNT = 13;           // MFCC系数数量
    private static final int MEL_FILTERS = 40;          // Mel滤波器数量
    private static final float MIN_FREQ = 133.0f;       // 最小频率
    private static final float MAX_FREQ = 8000.0f;      // 最大频率

    /**
     * 从音频文件中提取MFCC特征序列
     *
     * @param audioFile 音频文件
     * @return MFCC特征序列列表
     * @throws IOException 文件读取异常
     */
    public static List<float[]> extractFromFile(File audioFile) throws IOException {
        List<float[]> mfccFeatures = new ArrayList<>();

        try {
            AudioDispatcher dispatcher = AudioDispatcherFactory.fromPipe(
                audioFile.getAbsolutePath(),
                SAMPLE_RATE,
                BUFFER_SIZE,
                OVERLAP
            );

            MFCC mfccProcessor = new MFCC(
                BUFFER_SIZE,
                SAMPLE_RATE,
                MFCC_COUNT,
                MEL_FILTERS,
                MIN_FREQ,
                MAX_FREQ
            );

            dispatcher.addAudioProcessor(mfccProcessor);
            dispatcher.addAudioProcessor(new AudioProcessor() {
                @Override
                public boolean process(AudioEvent audioEvent) {
                    // 获取当前帧的MFCC特征
                    float[] currentMfcc = mfccProcessor.getMFCC();
                    if (currentMfcc != null && currentMfcc.length > 0) {
                        mfccFeatures.add(currentMfcc.clone());
                    }
                    return true;
                }

                @Override
                public void processingFinished() {
                    VoiceTrigger.LOGGER.info("MFCC extraction finished. Total frames: {}", mfccFeatures.size());
                }
            });

            dispatcher.run();

        } catch (Exception e) {
            VoiceTrigger.LOGGER.error("Error extracting MFCC from file: {}", audioFile.getAbsolutePath(), e);
            throw new IOException("Failed to extract MFCC features", e);
        }

        return mfccFeatures;
    }

    /**
     * 创建用于实时音频处理的MFCC处理器
     *
     * @return AudioDispatcher实例
     * @throws LineUnavailableException 音频线路不可用异常
     */
    public static AudioDispatcher createRealtimeDispatcher() throws LineUnavailableException {
        return AudioDispatcherFactory.fromDefaultMicrophone(
            SAMPLE_RATE,
            BUFFER_SIZE,
            OVERLAP
        );
    }

    /**
     * 创建MFCC处理器实例
     *
     * @return MFCC处理器
     */
    public static MFCC createMFCCProcessor() {
        return new MFCC(
            BUFFER_SIZE,
            SAMPLE_RATE,
            MFCC_COUNT,
            MEL_FILTERS,
            MIN_FREQ,
            MAX_FREQ
        );
    }

    /**
     * 获取采样率配置
     *
     * @return 采样率
     */
    public static int getSampleRate() {
        return SAMPLE_RATE;
    }

    /**
     * 获取缓冲区大小
     *
     * @return 缓冲区大小
     */
    public static int getBufferSize() {
        return BUFFER_SIZE;
    }

    /**
     * 获取重叠大小
     *
     * @return 重叠大小
     */
    public static int getOverlap() {
        return OVERLAP;
    }
}
