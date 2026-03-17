package dev.dubhe.voice;

import com.mojang.logging.LogUtils;
import dev.anvilcraft.lib.v2.config.ConfigManager;
import dev.anvilcraft.lib.v2.registrum.Registrum;
import dev.dubhe.voice.audio.VoiceListener;
import dev.dubhe.voice.audio.VoiceProfileManager;
import dev.dubhe.voice.data.VoiceTriggerDatagen;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.client.event.ClientPauseChangeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mod(VoiceTrigger.MOD_ID)
public class VoiceTrigger {
    public static final String MOD_ID = "voice_trigger";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final VoiceTriggerConfig CONFIG = ConfigManager.register(VoiceTrigger.MOD_ID, VoiceTriggerConfig::new);
    private static final Map<Long, List<Runnable>> SCHEDULES = new HashMap<>();
    public static final Registrum REGISTRUM = Registrum.create(VoiceTrigger.MOD_ID);

    public VoiceTrigger(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::setup);
        modEventBus.addListener(this::clientSetup);

        // 注册游戏事件监听器
        NeoForge.EVENT_BUS.addListener(this::onWorldLoad);
        NeoForge.EVENT_BUS.addListener(this::onWorldUnload);
        NeoForge.EVENT_BUS.addListener(this::onClientTick);
        NeoForge.EVENT_BUS.addListener(this::onClientPause);
        VoiceTriggerDatagen.init();
    }

    private void setup(final FMLCommonSetupEvent event) {
        LOGGER.info("Setting up VoiceTrigger mod");
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        LOGGER.info("Setting up VoiceTrigger client components");
        // 客户端初始化时加载所有已保存的语音配置
        event.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            VoiceProfileManager.loadAllProfiles(minecraft.options.keyMappings);
        });
    }

    /**
     * 当世界加载时启动语音监听器
     */
    private void onWorldLoad(LevelEvent.Load event) {
        if (event.getLevel().isClientSide()) {
            LOGGER.info("World loaded, starting voice listener");
            VoiceListener.getInstance().startListening();
        }
    }

    /**
     * 当世界卸载时停止语音监听器
     */
    private void onWorldUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            LOGGER.info("World unloaded, stopping voice listener");
            VoiceListener.getInstance().stopListening();
        }
    }

    /**
     * 当游戏暂停时停止语音监听器
     */
    private void onClientPause(ClientPauseChangeEvent.Post event) {
        if (event.isPaused()) {
            LOGGER.info("Client paused, stopping voice listener");
            VoiceListener.getInstance().stopListening();
        } else {
            LOGGER.info("Client resumed, starting voice listener");
            VoiceListener.getInstance().startListening();
        }
    }

    public static void schedule(long delay, Runnable runnable) {
        SCHEDULES.computeIfAbsent(delay + Minecraft.getInstance().gui.getGuiTicks(), k -> new ArrayList<>()).add(runnable);
    }

    public void onClientTick(ClientTickEvent.Pre event) {
        List<Long> remove = new ArrayList<>();
        SCHEDULES.forEach((time, runnableList) -> {
            if (time > Minecraft.getInstance().gui.getGuiTicks()) return;
            remove.add(time);
            Minecraft.getInstance().execute(() -> runnableList.forEach(Runnable::run));
        });
        remove.forEach(SCHEDULES::remove);
    }
}