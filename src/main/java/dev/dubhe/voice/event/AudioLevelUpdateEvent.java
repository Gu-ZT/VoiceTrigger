package dev.dubhe.voice.event;

import dev.dubhe.voice.audio.RealTimeMonitor;
import lombok.Getter;
import net.neoforged.bus.api.Event;

@Getter
public class AudioLevelUpdateEvent extends Event {
    private final RealTimeMonitor realTimeMonitor;
    private final double level;

    public AudioLevelUpdateEvent(RealTimeMonitor realTimeMonitor, double level) {
        this.realTimeMonitor = realTimeMonitor;
        this.level = level;
    }
}
