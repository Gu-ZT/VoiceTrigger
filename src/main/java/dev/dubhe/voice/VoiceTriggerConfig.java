package dev.dubhe.voice;

import dev.anvilcraft.lib.v2.config.BoundedDiscrete;
import dev.anvilcraft.lib.v2.config.Comment;
import dev.anvilcraft.lib.v2.config.Config;
import net.neoforged.fml.config.ModConfig;

@Config(name = VoiceTrigger.MOD_ID, type = ModConfig.Type.CLIENT)
public class VoiceTriggerConfig {
    @Comment("Similarity threshold (considered a match if similarity exceeds this value)")
    @BoundedDiscrete(min = 0, max = 1)
    public double similarityThreshold = 0.75;

    @Comment("Silence detection threshold (dB)")
    public double silenceThreshold = -43.0f;

    @Comment("Buffer size (bytes)")
    @BoundedDiscrete(min = 1024, max = 16384)
    public int bufferSize = 4096;

    @Comment("Minimum frame for match")
    @BoundedDiscrete(min = 1, max = 16)
    public int minFramesForMatch = 3;

    @Comment("Continuously monitor microphone input and trigger automatically")
    public Mode continuousMonitoring = Mode.ON;

    public enum Mode {
        OFF,
        ON
    }
}
