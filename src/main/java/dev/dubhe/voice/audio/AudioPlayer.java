package dev.dubhe.voice.audio;

import dev.dubhe.voice.VoiceTrigger;
import lombok.Getter;

import java.io.File;
import java.io.IOException;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;

/**
 * 音频播放器
 * 负责播放WAV音频文件
 */
public class AudioPlayer {
    private Clip clip;
    /**
     * -- GETTER --
     * 检查是否正在播放
     */
    @Getter
    private volatile boolean isPlaying = false;
    private Runnable onPlaybackComplete;

    /**
     * 播放音频文件
     *
     * @param audioFile 音频文件
     * @throws UnsupportedAudioFileException 不支持的音频格式异常
     * @throws IOException                   IO 异常
     * @throws LineUnavailableException      音频线路不可用异常
     */
    public void play(File audioFile) throws UnsupportedAudioFileException, IOException, LineUnavailableException {
        play(audioFile, null);
    }

    /**
     * 播放音频文件
     *
     * @param audioFile          音频文件
     * @param onComplete         播放完成回调
     * @throws UnsupportedAudioFileException 不支持的音频格式异常
     * @throws IOException                   IO 异常
     * @throws LineUnavailableException      音频线路不可用异常
     */
    public void play(File audioFile, Runnable onComplete) throws UnsupportedAudioFileException, IOException, LineUnavailableException {
        if (isPlaying) {
            VoiceTrigger.LOGGER.warn("Already playing audio, stopping previous playback");
            stop();
        }

        if (!audioFile.exists()) {
            VoiceTrigger.LOGGER.error("Audio file does not exist: {}", audioFile.getAbsolutePath());
            throw new IOException("Audio file not found: " + audioFile.getAbsolutePath());
        }

        this.onPlaybackComplete = onComplete;

        // 获取音频输入流
        AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(audioFile);
        AudioFormat format = audioInputStream.getFormat();

        // 获取剪辑
        DataLine.Info info = new DataLine.Info(Clip.class, format);

        if (!AudioSystem.isLineSupported(info)) {
            VoiceTrigger.LOGGER.error("Audio line not supported for format: {}", format);
            throw new LineUnavailableException("Audio line not supported for format: " + format);
        }

        clip = (Clip) AudioSystem.getLine(info);
        clip.open(audioInputStream);

        // 添加播放完成监听器
        clip.addLineListener(event -> {
            if (event.getType() == LineEvent.Type.STOP) {
                isPlaying = false;
                cleanup();
                if (onPlaybackComplete != null) {
                    onPlaybackComplete.run();
                }
            }
        });

        isPlaying = true;
        clip.start();

        VoiceTrigger.LOGGER.info(
            "Started playing audio: {} (format: {}Hz, {}bit, {})",
            audioFile.getName(),
            format.getSampleRate(),
            format.getSampleSizeInBits(),
            format.getChannels() == 1 ? "mono" : "stereo"
        );
    }

    /**
     * 停止播放
     */
    public void stop() {
        if (!isPlaying || clip == null) {
            return;
        }

        isPlaying = false;
        clip.stop();
        cleanup();
        VoiceTrigger.LOGGER.info("Stopped audio playback");
    }

    /**
     * 清理资源
     */
    private void cleanup() {
        if (clip != null) {
            clip.close();
            clip = null;
        }
    }
}
