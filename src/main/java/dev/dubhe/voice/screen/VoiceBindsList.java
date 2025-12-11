package dev.dubhe.voice.screen;

import com.google.common.collect.ImmutableList;
import dev.dubhe.voice.VoiceTrigger;
import dev.dubhe.voice.audio.AudioRecorder;
import dev.dubhe.voice.audio.MFCCExtractor;
import dev.dubhe.voice.event.AnalyzeAudioEvent;
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
import net.neoforged.fml.loading.FMLPaths;
import org.apache.commons.lang3.ArrayUtils;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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

        public List<? extends GuiEventListener> children() {
            return Collections.emptyList();
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

        protected void refreshEntry() {
        }
    }

    @OnlyIn(Dist.CLIENT)
    public class KeyEntry extends Entry {
        private static final Path VOICE_DIR = FMLPaths.CONFIGDIR.get().resolve("voice_trigger");
        private static final Component RECORD_BUTTON_TITLE = Component.translatable("controls.record");
        private static final Component BOUND_BUTTON_TITLE = Component.translatable("controls.bound");
        private static final Component STOP_RECORDING_BUTTON_TITLE = Component.translatable("controls.stop_recording");
        private static final int PADDING = 10;
        private final KeyMapping key;
        private final Component name;
        private final Button showButton;
        private final Button recordButton;
        private final AudioRecorder audioRecorder;
        private File recordingFile;
        private double[][] mfccFeatures = null;

        KeyEntry(KeyMapping key, Component name) {
            this.key = key;
            this.name = name;
            this.showButton = Button.builder(
                    name,
                    (button) -> {
                    }
                )
                .bounds(0, 0, 75, 20)
                .build();
            this.showButton.active = false;

            this.audioRecorder = new AudioRecorder();

            this.recordButton = Button.builder(
                    RECORD_BUTTON_TITLE,
                    (button) -> handleRecordButtonPress()
                )
                .bounds(0, 0, 50, 20)
                .build();
            this.refreshEntry();
            if (loadVoiceProfile()) {
                this.recordButton.setMessage(BOUND_BUTTON_TITLE);
                this.recordButton.active = false;
                VoiceTrigger.addListener(this.key.getKey(), this::onAnalyze);
            }
        }

        public void onAnalyze(AnalyzeAudioEvent event) {
            if (this.mfccFeatures == null) return;
            double analyzed = event.analyzeBuffer(mfccFeatures);
            VoiceTrigger.LOGGER.info("Key: {}, Analyzed: {}", key.getKey().getName(), analyzed);
        }

        private void handleRecordButtonPress() {
            if (!audioRecorder.isRecording()) {
                // 开始录制
                try {
                    recordingFile = VOICE_DIR.resolve("%s.wav".formatted(key.getName())).toFile();
                    File parentFile = recordingFile.getParentFile();
                    if (parentFile.isDirectory() || parentFile.mkdirs()) {
                        if (recordingFile.isFile() || recordingFile.createNewFile()) {
                            VoiceTrigger.getRealTimeMonitor().stopMonitoring();
                            audioRecorder.startRecording();
                            recordButton.setMessage(STOP_RECORDING_BUTTON_TITLE);
                        }
                    }
                } catch (Exception e) {
                    VoiceTrigger.LOGGER.error(e.getMessage(), e);
                }
            } else {
                // 停止录制并保存特征
                byte[] audioData = audioRecorder.stopRecording();
                VoiceTrigger.getRealTimeMonitor().startMonitoring();
                recordButton.setMessage(BOUND_BUTTON_TITLE);

                // 提取特征并保存
                try {
                    // 保存录音文件
                    if (audioData != null && recordingFile != null) {
                        if (recordingFile.isFile() || recordingFile.createNewFile()) {
                            // 提取MFCC特征
                            double[] samples = audioRecorder.convertToDoubleArray(audioData);
                            MFCCExtractor extractor = new MFCCExtractor();
                            mfccFeatures = extractor.extractMFCC(samples, audioRecorder.getAudioFormat().getSampleRate());

                            // 保存特征到配置文件
                            saveVoiceProfile();
                            this.recordButton.active = false;
                            VoiceTrigger.addListener(this.key.getKey(), this::onAnalyze);

                            // 删除临时录音文件
                            if (!(recordingFile.exists() && recordingFile.delete())) {
                                VoiceTrigger.LOGGER.error("Failed to delete temporary recording file: {}", recordingFile.getAbsolutePath());
                            }
                        }
                    }
                } catch (Exception e) {
                    VoiceTrigger.LOGGER.error(e.getMessage(), e);
                }
            }
        }

        private void saveVoiceProfile() {
            // 这里应该保存特征到配置文件
            // 实现细节取决于具体需求
            String name = this.key.getKey().getName();
            Path path = VOICE_DIR.resolve(name + ".vcfg");
            try (BufferedWriter writer = Files.newBufferedWriter(path)) {
                for (double[] feature : mfccFeatures) {
                    for (double value : feature) {
                        writer.write(String.valueOf(value));
                        writer.write(",");
                    }
                    writer.newLine();
                }
            } catch (IOException e) {
                VoiceTrigger.LOGGER.error(e.getMessage(), e);
            }
        }

        private boolean loadVoiceProfile() {
            String name = this.key.getKey().getName();
            Path path = VOICE_DIR.resolve(name + ".vcfg");
            if (!Files.exists(path)) {
                return false;
            }
            try (BufferedReader reader = Files.newBufferedReader(path)) {
                List<double[]> features = new ArrayList<>();
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] values = line.split(",");
                    double[] feature = new double[values.length];
                    for (int i = 0; i < values.length; i++) {
                        feature[i] = Double.parseDouble(values[i]);
                    }
                    features.add(feature);
                }
                mfccFeatures = features.toArray(new double[0][]);
                return true;
            } catch (IOException e) {
                VoiceTrigger.LOGGER.error(e.getMessage(), e);
            }
            return false;
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
            int k = i - 5 - this.showButton.getWidth();
            this.showButton.setPosition(k, j);
            this.showButton.render(guiGraphics, mouseX, mouseY, partialTick);
            guiGraphics.drawString(VoiceBindsList.this.minecraft.font, this.name, left, top + height / 2 - 4, -1);
        }

        public List<? extends GuiEventListener> children() {
            return ImmutableList.of(this.showButton, this.recordButton);
        }

        public List<? extends NarratableEntry> narratables() {
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