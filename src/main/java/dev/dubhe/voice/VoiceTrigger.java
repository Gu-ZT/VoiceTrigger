package dev.dubhe.voice;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(VoiceTrigger.MOD_ID)
public class VoiceTrigger {
    public static final String MOD_ID = "voice_trigger";
    private static final Logger LOGGER = LogUtils.getLogger();

    public VoiceTrigger(IEventBus modEventBus, ModContainer modContainer) {
    }
}
