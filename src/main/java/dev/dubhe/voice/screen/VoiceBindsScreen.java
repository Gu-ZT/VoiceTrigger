package dev.dubhe.voice.screen;

import com.mojang.blaze3d.platform.InputConstants;
import dev.dubhe.voice.VoiceTrigger;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;

@EventBusSubscriber(modid = VoiceTrigger.MOD_ID)
public class VoiceBindsScreen extends Screen {
    private VoiceBindsList keyBindsList;
    @Nullable
    public KeyMapping selectedKey;
    public long lastKeySelection;
    public final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    private InputConstants.Key lastPressedKey = InputConstants.UNKNOWN;
    private InputConstants.Key lastPressedModifier = InputConstants.UNKNOWN;
    private boolean isLastKeyHeldDown = false;
    private boolean isLastModifierHeldDown = false;
    protected final Options options;

    protected VoiceBindsScreen() {
        super(Component.translatable("screen.voice_trigger.binding"));
        this.options = Minecraft.getInstance().options;
    }

    @Override
    protected void init() {
        this.addTitle();
        this.addContents();
        this.addFooter();
        this.layout.visitWidgets(this::addRenderableWidget);
        this.repositionElements();
    }

    protected void addTitle() {
        this.layout.addTitleHeader(this.title, this.font);
    }

    protected void addContents() {
        this.keyBindsList = this.layout.addToContents(new VoiceBindsList(this, this.minecraft));
    }

    protected void addFooter() {
        LinearLayout linearlayout = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
        linearlayout.addChild(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose()).build());
    }

    @Override
    protected void repositionElements() {
        this.layout.arrangeElements();
        this.keyBindsList.updateSize(this.width, this.layout);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.selectedKey != null) {
            this.options.setKey(this.selectedKey, InputConstants.Type.MOUSE.getOrCreate(button));
            this.selectedKey = null;
            this.keyBindsList.resetMappingAndUpdateButtons();
            return true;
        } else {
            return super.mouseClicked(mouseX, mouseY, button);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.selectedKey != null) {
            var key = InputConstants.getKey(keyCode, scanCode);
            if (lastPressedModifier == InputConstants.UNKNOWN && net.neoforged.neoforge.client.settings.KeyModifier.isKeyCodeModifier(key)) {
                lastPressedModifier = key;
                isLastModifierHeldDown = true;
            } else {
                lastPressedKey = key;
                isLastKeyHeldDown = true;
            }
            return true;
        } else {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (this.selectedKey != null && (!net.minecraft.client.Minecraft.ON_OSX || scanCode != 63)) {
            if (keyCode == 256) {
                this.selectedKey.setKeyModifierAndCode(net.neoforged.neoforge.client.settings.KeyModifier.NONE, InputConstants.UNKNOWN);
                this.options.setKey(this.selectedKey, InputConstants.UNKNOWN);
                lastPressedKey = InputConstants.UNKNOWN;
                lastPressedModifier = InputConstants.UNKNOWN;
                isLastKeyHeldDown = false;
                isLastModifierHeldDown = false;
            } else {
                var key = InputConstants.getKey(keyCode, scanCode);
                if (lastPressedKey.equals(key)) {
                    isLastKeyHeldDown = false;
                } else if (lastPressedModifier.equals(key)) {
                    isLastModifierHeldDown = false;
                }

                if (!isLastKeyHeldDown && !isLastModifierHeldDown) {
                    if (!lastPressedKey.equals(InputConstants.UNKNOWN)) {
                        this.selectedKey.setKeyModifierAndCode(
                            net.neoforged.neoforge.client.settings.KeyModifier.getKeyModifier(
                                lastPressedModifier), lastPressedKey
                        );
                        this.options.setKey(this.selectedKey, lastPressedKey);
                    } else {
                        this.selectedKey.setKeyModifierAndCode(
                            net.neoforged.neoforge.client.settings.KeyModifier.NONE,
                            lastPressedModifier
                        );
                        this.options.setKey(this.selectedKey, lastPressedModifier);
                    }
                    lastPressedKey = InputConstants.UNKNOWN;
                    lastPressedModifier = InputConstants.UNKNOWN;
                } else {
                    return true;
                }
            }
            this.selectedKey = null;
            this.lastKeySelection = Util.getMillis();
            this.keyBindsList.resetMappingAndUpdateButtons();
            return true;
        } else {
            return super.keyReleased(keyCode, scanCode, modifiers);
        }
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    public static final KeyMapping VOICE_BINDING = new KeyMapping(
        "key.voice_trigger.voice_binding",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_B,
        "key.categories.voice_trigger"
    );

    @SubscribeEvent
    public static void registerKeyMapping(@NotNull RegisterKeyMappingsEvent event) {
        event.register(VOICE_BINDING);
    }

    @SubscribeEvent
    public static void onKeyPress(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        if (VOICE_BINDING.consumeClick()) {
            client.setScreen(new VoiceBindsScreen());
        }
    }
}
