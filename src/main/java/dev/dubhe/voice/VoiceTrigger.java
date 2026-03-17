package dev.dubhe.voice;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import dev.anvilcraft.lib.v2.config.ConfigManager;
import dev.anvilcraft.lib.v2.registrum.Registrum;
import dev.dubhe.voice.audio.VoiceListener;
import dev.dubhe.voice.audio.VoiceProfileManager;
import dev.dubhe.voice.data.VoiceTriggerDatagen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.client.event.ClientPauseChangeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.lwjgl.glfw.GLFW;
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

    /**
     * 按键控制语音监听（当 continuousMonitoring=OFF 时生效）
     */
    public static final KeyMapping VOICE_LISTEN = new KeyMapping(
        "key.voice_trigger.voice_listen",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_V,
        "key.categories.voice_trigger"
    );

    private boolean isVoiceListenKeyPressed = false;
    private boolean isInWorld = false;

    public VoiceTrigger(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::setup);
        modEventBus.addListener(this::clientSetup);
        modEventBus.addListener(this::registerKeyMapping);

        // 注册游戏事件监听器
        NeoForge.EVENT_BUS.addListener(this::onWorldLoad);
        NeoForge.EVENT_BUS.addListener(this::onWorldUnload);
        NeoForge.EVENT_BUS.addListener(this::onClientTick);
        NeoForge.EVENT_BUS.addListener(this::onClientPause);
        VoiceTriggerDatagen.init();
    }

    private void registerKeyMapping(RegisterKeyMappingsEvent event) {
        event.register(VOICE_LISTEN);
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
            isInWorld = true;
            // 只有在持续监听模式下才自动启动监听器
            if (CONFIG.continuousMonitoring == VoiceTriggerConfig.Mode.ON) {
                VoiceListener.getInstance().startListening();
            }
        }
    }

    /**
     * 当世界卸载时停止语音监听器
     */
    private void onWorldUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            LOGGER.info("World unloaded, stopping voice listener");
            isInWorld = false;
            VoiceListener.getInstance().stopListening();
        }
    }

    /**
     * 当游戏暂停时停止语音监听器
     */
    private void onClientPause(ClientPauseChangeEvent.Post event) {
        // 只有在持续监听模式下才处理暂停/恢复
        if (CONFIG.continuousMonitoring == VoiceTriggerConfig.Mode.ON) {
            if (event.isPaused()) {
                LOGGER.info("Client paused, stopping voice listener");
                VoiceListener.getInstance().stopListening();
            } else {
                LOGGER.info("Client resumed, starting voice listener");
                VoiceListener.getInstance().startListening();
            }
        }
    }

    public static void schedule(long delay, Runnable runnable) {
        SCHEDULES.computeIfAbsent(delay + Minecraft.getInstance().gui.getGuiTicks(), k -> new ArrayList<>()).add(runnable);
    }

    public void onClientTick(ClientTickEvent.Pre event) {
        // 处理计划任务
        List<Long> remove = new ArrayList<>();
        SCHEDULES.forEach((time, runnableList) -> {
            if (time > Minecraft.getInstance().gui.getGuiTicks()) return;
            remove.add(time);
            Minecraft.getInstance().execute(() -> runnableList.forEach(Runnable::run));
        });
        remove.forEach(SCHEDULES::remove);

        // 处理 V 键控制语音监听（仅在 continuousMonitoring=OFF 时生效）
        if (CONFIG.continuousMonitoring == VoiceTriggerConfig.Mode.OFF && isInWorld) {
            Minecraft minecraft = Minecraft.getInstance();
            // 确保不在任何 GUI 界面中
            if (minecraft.screen == null) {
                boolean keyPressed = VOICE_LISTEN.isDown();

                if (keyPressed && !isVoiceListenKeyPressed) {
                    // 按键刚被按下，开始监听
                    isVoiceListenKeyPressed = true;
                    VoiceListener.getInstance().startListening();
                    LOGGER.info("Voice listen key pressed, started listening");
                } else if (!keyPressed && isVoiceListenKeyPressed) {
                    // 按键刚被释放，停止监听
                    isVoiceListenKeyPressed = false;
                    VoiceListener.getInstance().stopListening();
                    LOGGER.info("Voice listen key released, stopped listening");
                }
            } else {
                // 如果打开了 GUI，释放按键状态并停止监听
                if (isVoiceListenKeyPressed) {
                    isVoiceListenKeyPressed = false;
                    VoiceListener.getInstance().stopListening();
                }
            }
        }
    }
}