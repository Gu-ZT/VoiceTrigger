package dev.dubhe.voice.data.lang;

import dev.anvilcraft.lib.v2.config.ConfigData;
import dev.anvilcraft.lib.v2.registrum.providers.RegistrumLangProvider;
import dev.dubhe.voice.VoiceTriggerConfig;

public class LangHandler {
    public static void init(RegistrumLangProvider provider) {
        ConfigData.readConfigClass(provider, VoiceTriggerConfig.class);
        provider.add("screen.voice_trigger.binding", "Voice Binding");
        provider.add("key.voice_trigger.voice_binding", "VoiceBinding");
        provider.add("key.categories.voice_trigger", "VoiceTrigger");
        provider.add("controls.record", "Record");
        provider.add("controls.stop_recording", "Stop");
        provider.add("controls.bound", "Bound");
        provider.add("controls.reset", "Reset");
        provider.add("voice_trigger.recording_started", "Recording started...");
        provider.add("voice_trigger.recording_stopped", "Recording stopped");
        provider.add("voice_trigger.profile_saved", "Voice profile saved");
        provider.add("voice_trigger.profile_failed", "Failed to save voice profile");
        provider.add("voice_trigger.listener_started", "Voice listener started");
        provider.add("voice_trigger.listener_stopped", "Voice listener stopped");
    }
}
