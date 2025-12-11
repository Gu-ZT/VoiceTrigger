package dev.dubhe.voice.event;

import dev.dubhe.voice.VoiceTrigger;
import dev.dubhe.voice.audio.AudioUtils;
import dev.dubhe.voice.audio.RealTimeMonitor;
import net.neoforged.bus.api.Event;

public class AnalyzeAudioEvent extends Event {
    private final RealTimeMonitor realTimeMonitor;
    private final double[][] currentMFCC;

    public AnalyzeAudioEvent(RealTimeMonitor realTimeMonitor, double[][] currentMFCC) {
        this.realTimeMonitor = realTimeMonitor;
        this.currentMFCC = currentMFCC;
    }

    public double analyzeBuffer(double[][] referenceMFCC) {
        try {
            // 计算与参考样本的相似度
            double distance = realTimeMonitor.similarityCalculator.dtwDistance(currentMFCC, referenceMFCC);

            // 转换为相似度分数（0-1）
            double maxDistance = 50; // 可根据实际情况调整

            return Math.max(0, 1 - distance / maxDistance);

        } catch (Exception e) {
            VoiceTrigger.LOGGER.error(e.getMessage(), e);
        }
        return 0;
    }

    public double analyzeBuffer2(double[][] referenceMFCC) {
        try {
            // 计算与参考样本的相似度
            double[] averageMFCC = AudioUtils.calculateAverageMFCC(currentMFCC);
            double[] referenceAverageMFCC = AudioUtils.calculateAverageMFCC(referenceMFCC);
            return realTimeMonitor.similarityCalculator.cosineSimilarity(averageMFCC, referenceAverageMFCC);

        } catch (Exception e) {
            VoiceTrigger.LOGGER.error(e.getMessage(), e);
        }
        return 0;
    }
}
