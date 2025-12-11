package dev.dubhe.voice;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import dev.dubhe.voice.audio.RealTimeMonitor;
import dev.dubhe.voice.event.AnalyzeAudioEvent;
import lombok.Getter;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Mod(VoiceTrigger.MOD_ID)
public class VoiceTrigger {
    public static final String MOD_ID = "voice_trigger";
    public static final Logger LOGGER = LogUtils.getLogger();
    @Getter
    private static RealTimeMonitor realTimeMonitor;
    private static final Map<InputConstants.Key, Consumer<AnalyzeAudioEvent>> added = new ConcurrentHashMap<>();

    public VoiceTrigger(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::setup);
        modEventBus.addListener(this::clientSetup);
        NeoForge.EVENT_BUS.addListener(this::onAnalyze);

        if (FMLEnvironment.dist.isClient()) {
            realTimeMonitor = new RealTimeMonitor(3);
        }
    }

    public static void addListener(InputConstants.Key key, Consumer<AnalyzeAudioEvent> consumer) {
        if (VoiceTrigger.added.containsKey(key)) return;
        VoiceTrigger.added.put(key, consumer);
    }

    public static void removeListener(InputConstants.Key key) {
        if (!VoiceTrigger.added.containsKey(key)) return;
        VoiceTrigger.added.remove(key);
    }

    public void onAnalyze(AnalyzeAudioEvent event) {
        for (Consumer<AnalyzeAudioEvent> value : VoiceTrigger.added.values()) {
            value.accept(event);
        }
    }

    private void setup(final FMLCommonSetupEvent event) {
        LOGGER.info("Setting up VoiceTrigger mod");
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        LOGGER.info("Setting up VoiceTrigger client components");
        // 在客户端初始化时启动音频监听器
        event.enqueueWork(() -> {
            if (realTimeMonitor != null) {
                realTimeMonitor.startMonitoring();
            }
        });
    }
}