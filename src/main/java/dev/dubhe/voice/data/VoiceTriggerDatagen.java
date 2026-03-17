package dev.dubhe.voice.data;

import dev.anvilcraft.lib.v2.registrum.providers.ProviderType;
import dev.dubhe.voice.data.lang.LangHandler;

import static dev.dubhe.voice.VoiceTrigger.REGISTRUM;

public class VoiceTriggerDatagen {
    /**
     * 初始化生成器
     */
    public static void init() {
        REGISTRUM.addDataGenerator(ProviderType.LANG, LangHandler::init);
    }
}
