package dev.dubhe.voice.screen;

import com.google.common.collect.ImmutableList;
import dev.dubhe.voice.VoiceTrigger;
import dev.dubhe.voice.audio.VoiceListener;
import dev.dubhe.voice.audio.VoiceProfileManager;
import dev.dubhe.voice.audio.VoiceRecorder;
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

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import javax.sound.sampled.LineUnavailableException;

public class VoiceBindsList extends ContainerObjectSelectionList<VoiceBindsList.Entry> {
    private static final int ITEM_HEIGHT = 20;
    final VoiceBindsScreen keyBindsScreen;
    private int maxNameWidth;

    public VoiceBindsList(VoiceBindsScreen keyBindsScreen, Minecraft minecraft) {
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
        this.children().forEach(Entry::refreshEntry);
    }

    /**
     * 取消所有正在进行的录制
     * 在Screen关闭时调用
     */
    public void cancelAllRecordings() {
        this.children().forEach(entry -> {
            if (entry instanceof KeyEntry keyEntry) {
                keyEntry.cancelRecording();
            }
        });
    }

    public int getRowWidth() {
        return 340;
    }

    @OnlyIn(Dist.CLIENT)
    public abstract static class Entry extends ContainerObjectSelectionList.Entry<Entry> {
        abstract void refreshEntry();
    }

    @OnlyIn(Dist.CLIENT)
    public class CategoryEntry extends Entry {
        final Component name;
        private final int width;

        public CategoryEntry(Component name) {
            this.name = name;
            this.width = VoiceBindsList.this.minecraft.font.width(this.name);
        }

        public void render(
            GuiGraphics guiGraphics,
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
        public ComponentPath nextFocusPath(FocusNavigationEvent event) {
            return null;
        }

        public List<? extends NarratableEntry> narratables() {
            return ImmutableList.of(new NarratableEntry() {
                public NarratableEntry.NarrationPriority narrationPriority() {
                    return NarrationPriority.HOVERED;
                }

                public void updateNarration(NarrationElementOutput p_344973_) {
                    p_344973_.add(NarratedElementType.TITLE, CategoryEntry.this.name);
                }
            });
        }

        public List<? extends GuiEventListener> children() {
            return Collections.emptyList();
        }

        protected void refreshEntry() {
        }
    }

    @OnlyIn(Dist.CLIENT)
    public class KeyEntry extends Entry {
        private static final Component RECORD_BUTTON_TITLE = Component.translatable("controls.record");
        private static final Component BOUND_BUTTON_TITLE = Component.translatable("controls.bound");
        private static final Component STOP_RECORDING_BUTTON_TITLE = Component.translatable("controls.stop_recording");
        private static final Component RESET_BUTTON_TITLE = Component.translatable("controls.reset");
        private static final int PADDING = 10;
        private final KeyMapping key;
        private final Component name;
        private final Button showButton;
        private final Button recordButton;
        private final Button resetButton;
        private VoiceRecorder recorder;
        private boolean hasProfile = false;

        KeyEntry(KeyMapping key, Component name) {
            this.key = key;
            this.name = name;
            this.showButton = Button.builder(
                name, (button) -> {
                }
            ).bounds(0, 0, 75, 20).build();
            this.showButton.active = false;

            this.recordButton = Button.builder(RECORD_BUTTON_TITLE, (button) -> handleRecordButtonPress()).bounds(0, 0, 50, 20).build();
            this.resetButton = Button.builder(RESET_BUTTON_TITLE, (button) -> handleResetButtonPress()).bounds(0, 0, 50, 20).build();
            this.refreshEntry();

            // 检查是否已有语音配置
            this.hasProfile = VoiceProfileManager.hasProfile(this.key);
            if (this.hasProfile) {
                this.recordButton.setMessage(BOUND_BUTTON_TITLE);
            }
            updateResetButtonVisibility();
        }

        private void handleRecordButtonPress() {
            if (recorder != null && recorder.isRecording()) {
                // 停止录制
                stopRecording();
            } else {
                // 开始录制
                startRecording();
            }
        }

        private void handleResetButtonPress() {
            // 删除语音配置
            boolean deleted = VoiceProfileManager.deleteProfile(this.key);
            if (deleted) {
                this.hasProfile = false;
                this.recordButton.setMessage(RECORD_BUTTON_TITLE);
                updateResetButtonVisibility();

                // 从监听器中移除模板
                VoiceListener.getInstance().unregisterTemplate(this.key);
                VoiceTrigger.LOGGER.info("Reset voice profile for key: {}", this.key.getName());
            }
        }

        private void updateResetButtonVisibility() {
            this.resetButton.visible = this.hasProfile;
            this.resetButton.active = this.hasProfile;
        }

        private void startRecording() {
            try {
                // 停止全局音频监听以释放麦克风
                VoiceListener.getInstance().stopListening();
                VoiceTrigger.LOGGER.info("Stopped voice listener for recording");

                // 等待监听器完全停止
                Thread.sleep(100);

                recorder = new VoiceRecorder();
                File audioFile = VoiceProfileManager.getAudioFile(this.key);
                recorder.startRecording(audioFile);
                this.recordButton.setMessage(STOP_RECORDING_BUTTON_TITLE);
                VoiceTrigger.LOGGER.info("Started recording for key: {}", this.key.getName());
            } catch (LineUnavailableException e) {
                VoiceTrigger.LOGGER.error("Failed to start recording", e);
                recorder = null;
                // 如果录制失败，重新启动监听器
                VoiceListener.getInstance().startListening();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                VoiceTrigger.LOGGER.error("Recording start interrupted", e);
                recorder = null;
            }
        }

        private void stopRecording() {
            if (recorder == null) {
                return;
            }

            File audioFile = recorder.getOutputFile();
            recorder.stopRecording();
            this.recordButton.setMessage(RECORD_BUTTON_TITLE);

            // 在后台线程中处理MFCC提取，避免卡顿
            new Thread(
                () -> {
                    try {
                        Thread.sleep(100); // 等待文件写入完成

                        boolean success = VoiceProfileManager.extractAndSaveProfile(this.key, audioFile);

                        // 在主线程中更新UI
                        VoiceBindsList.this.minecraft.execute(() -> {
                            if (success) {
                                this.hasProfile = true;
                                this.recordButton.setMessage(BOUND_BUTTON_TITLE);
                                updateResetButtonVisibility();
                                VoiceTrigger.LOGGER.info("Successfully saved voice profile for key: {}", this.key.getName());
                            } else {
                                VoiceTrigger.LOGGER.error("Failed to save voice profile for key: {}", this.key.getName());
                            }

                            // 重新启动全局音频监听
                            dev.dubhe.voice.audio.VoiceListener.getInstance().startListening();
                            VoiceTrigger.LOGGER.info("Restarted voice listener after recording");
                        });
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        // 即使出错也要重启监听器
                        VoiceBindsList.this.minecraft.execute(() -> {
                            dev.dubhe.voice.audio.VoiceListener.getInstance().startListening();
                        });
                    }
                }, "VoiceProfileSaver"
            ).start();

            recorder = null;
        }

        /**
         * 取消正在进行的录制并丢弃录音内容
         * 用于处理Screen关闭时的清理
         */
        public void cancelRecording() {
            if (recorder == null || !recorder.isRecording()) {
                return;
            }

            File audioFile = recorder.getOutputFile();
            recorder.stopRecording();
            this.recordButton.setMessage(RECORD_BUTTON_TITLE);
            recorder = null;

            // 删除未完成的录音文件
            if (audioFile != null && audioFile.exists()) {
                audioFile.delete();
                VoiceTrigger.LOGGER.info("Cancelled recording and deleted file for key: {}", this.key.getName());
            }

            // 重新启动全局音频监听
            VoiceListener.getInstance().startListening();
            VoiceTrigger.LOGGER.info("Restarted voice listener after cancelling recording");
        }


        public void render(
            GuiGraphics guiGraphics,
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

            // 渲染重置按钮（在录制按钮左侧）
            int resetX = i - 5 - this.resetButton.getWidth();
            this.resetButton.setPosition(resetX, j);
            this.resetButton.render(guiGraphics, mouseX, mouseY, partialTick);

            int k = resetX - 5 - this.showButton.getWidth();
            this.showButton.setPosition(k, j);
            this.showButton.render(guiGraphics, mouseX, mouseY, partialTick);
            guiGraphics.drawString(VoiceBindsList.this.minecraft.font, this.name, left, top + height / 2 - 4, -1);
        }

        public List<? extends GuiEventListener> children() {
            return ImmutableList.of(this.showButton, this.resetButton, this.recordButton);
        }

        public List<? extends NarratableEntry> narratables() {
            return ImmutableList.of(this.showButton, this.resetButton, this.recordButton);
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