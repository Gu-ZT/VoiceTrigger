package dev.dubhe.voice.screen;

import com.google.common.collect.ImmutableList;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class VoiceBindsList extends ContainerObjectSelectionList<VoiceBindsList.Entry> {
    private static final int ITEM_HEIGHT = 20;
    final VoiceBindsScreen keyBindsScreen;
    private int maxNameWidth;

    public VoiceBindsList(@NotNull VoiceBindsScreen keyBindsScreen, Minecraft minecraft) {
        super(
            minecraft,
            keyBindsScreen.width,
            keyBindsScreen.layout.getContentHeight(),
            keyBindsScreen.layout.getHeaderHeight(),
            ITEM_HEIGHT
        );
        this.keyBindsScreen = keyBindsScreen;
        KeyMapping[] akeymapping = ArrayUtils.clone(minecraft.options.keyMappings);
        Arrays.sort(akeymapping);
        String s = null;

        for (KeyMapping keymapping : akeymapping) {
            String s1 = keymapping.getCategory();
            if (!s1.equals(s)) {
                s = s1;
                this.addEntry(new VoiceBindsList.CategoryEntry(Component.translatable(s1)));
            }

            Component component = keymapping.getDisplayName();
            int i = minecraft.font.width(component);
            if (i > this.maxNameWidth) {
                this.maxNameWidth = i;
            }

            this.addEntry(new VoiceBindsList.KeyEntry(keymapping, component));
        }

    }

    public void resetMappingAndUpdateButtons() {
        KeyMapping.resetMapping();
        this.refreshEntries();
    }

    public void refreshEntries() {
        this.children().forEach(VoiceBindsList.Entry::refreshEntry);
    }

    public int getRowWidth() {
        return 340;
    }

    @OnlyIn(Dist.CLIENT)
    public class CategoryEntry extends VoiceBindsList.Entry {
        final Component name;
        private final int width;

        public CategoryEntry(Component name) {
            this.name = name;
            this.width = VoiceBindsList.this.minecraft.font.width(this.name);
        }

        public void render(
            @NotNull GuiGraphics guiGraphics,
            int index,
            int top,
            int left,
            int width,
            int height,
            int mouseX,
            int mouseY,
            boolean hovering,
            float partialTick
        ) {
            guiGraphics.drawString(
                VoiceBindsList.this.minecraft.font,
                this.name,
                VoiceBindsList.this.width / 2 - this.width / 2,
                top + height - 9 - 1,
                -1,
                false
            );
        }

        @Nullable
        public ComponentPath nextFocusPath(@NotNull FocusNavigationEvent event) {
            return null;
        }

        public @NotNull List<? extends GuiEventListener> children() {
            return Collections.emptyList();
        }

        public @NotNull List<? extends NarratableEntry> narratables() {
            return ImmutableList.of(new NarratableEntry() {
                public NarratableEntry.@NotNull NarrationPriority narrationPriority() {
                    return NarrationPriority.HOVERED;
                }

                public void updateNarration(@NotNull NarrationElementOutput p_344973_) {
                    p_344973_.add(NarratedElementType.TITLE, VoiceBindsList.CategoryEntry.this.name);
                }
            });
        }

        protected void refreshEntry() {
        }
    }

    @OnlyIn(Dist.CLIENT)
    public abstract static class Entry extends ContainerObjectSelectionList.Entry<VoiceBindsList.Entry> {
        abstract void refreshEntry();
    }

    @OnlyIn(Dist.CLIENT)
    public class KeyEntry extends VoiceBindsList.Entry {
        private static final Component RECORD_BUTTON_TITLE = Component.translatable("controls.record");
        private static final int PADDING = 10;
        private final KeyMapping key;
        private final Component name;
        private final Button showButton;
        private final Button recordButton;

        KeyEntry(KeyMapping key, Component name) {
            this.key = key;
            this.name = name;
            this.showButton = Button.builder(
                    name,
                    (p_345593_) -> {
                    }
                )
                .bounds(0, 0, 75, 20)
                .build();
            this.showButton.active = false;
            this.recordButton = Button.builder(
                    RECORD_BUTTON_TITLE,
                    (p_345591_) -> {
                    }
                )
                .bounds(0, 0, 50, 20)
                .build();
            this.refreshEntry();
        }

        public void render(
            @NotNull GuiGraphics guiGraphics,
            int index,
            int top,
            int left,
            int width,
            int height,
            int mouseX,
            int mouseY,
            boolean hovering,
            float partialTick
        ) {
            int i = VoiceBindsList.this.getScrollbarPosition() - this.recordButton.getWidth() - PADDING;
            int j = top - 2;
            this.recordButton.setPosition(i, j);
            this.recordButton.render(guiGraphics, mouseX, mouseY, partialTick);
            int k = i - 5 - this.showButton.getWidth();
            this.showButton.setPosition(k, j);
            this.showButton.render(guiGraphics, mouseX, mouseY, partialTick);
            guiGraphics.drawString(VoiceBindsList.this.minecraft.font, this.name, left, top + height / 2 - 4, -1);
        }

        public @NotNull List<? extends GuiEventListener> children() {
            return ImmutableList.of(this.showButton, this.recordButton);
        }

        public @NotNull List<? extends NarratableEntry> narratables() {
            return ImmutableList.of(this.showButton, this.recordButton);
        }

        protected void refreshEntry() {
            this.showButton.setMessage(this.key.getTranslatedKeyMessage());
            MutableComponent mutablecomponent = Component.empty();
            if (!this.key.isUnbound()) {
                for (KeyMapping keymapping : VoiceBindsList.this.minecraft.options.keyMappings) {
                    if (keymapping != this.key && this.key.same(keymapping) || keymapping.hasKeyModifierConflict(this.key)) {
                        mutablecomponent.append(keymapping.getDisplayName());
                    }
                }
            }
        }
    }
}
