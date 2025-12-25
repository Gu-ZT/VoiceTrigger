package dev.dubhe.voice;

import com.mojang.logging.LogUtils;
import dev.dubhe.voice.audio.VoiceListener;
import dev.dubhe.voice.audio.VoiceProfileManager;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.slf4j.Logger;

@Mod(VoiceTrigger.MOD_ID)
public class VoiceTrigger {
    public static final String MOD_ID = "voice_trigger";
    public static final Logger LOGGER = LogUtils.getLogger();

    public VoiceTrigger(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::setup);
        modEventBus.addListener(this::clientSetup);

        // 注册游戏事件监听器
        NeoForge.EVENT_BUS.addListener(this::onWorldLoad);
        NeoForge.EVENT_BUS.addListener(this::onWorldUnload);
    }

    private void setup(final FMLCommonSetupEvent event) {
        LOGGER.info("Setting up VoiceTrigger mod");
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        LOGGER.info("Setting up VoiceTrigger client components");
        // 客户端初始化时加载所有已保存的语音配置
        event.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.options != null && minecraft.options.keyMappings != null) {
                VoiceProfileManager.loadAllProfiles(minecraft.options.keyMappings);
            }
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
}