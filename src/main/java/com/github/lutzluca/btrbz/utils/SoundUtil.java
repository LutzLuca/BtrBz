package com.github.lutzluca.btrbz.utils;

import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;

@Slf4j
public class SoundUtil {
    private static final Map<SoundEvent, Long> lastPlayedTimes = new HashMap<>();
    private static final long SOUND_COOLDOWN_MS = 500L;

    public static void playSoundIf(boolean enabled, SoundEvent sound, float volume, int repeatCount) {
        if (!enabled) {
            return;
        }
        playSound(sound, volume, repeatCount);
    }

    public static void playSoundIf(boolean enabled, Holder<SoundEvent> soundEntry, float volume, int repeatCount) {
        playSoundIf(enabled, soundEntry.value(), volume, repeatCount);
    }

    public static void playSound(SoundEvent sound, float volume, int repeatCount) {
        long now = System.currentTimeMillis();
        if (now - lastPlayedTimes.getOrDefault(sound, 0L) > SOUND_COOLDOWN_MS) {
            lastPlayedTimes.put(sound, now);

            for (int i = 0; i < repeatCount; i++) {
                if (i == 0) {
                    play(sound, volume);
                    continue;
                }

                ClientTickDispatcher.submit(mc -> play(sound, volume), i * 3);
            }
        }
    }

    public static void playSound(SoundEvent sound, float volume) {
        playSound(sound, volume, 1);
    }

    public static void playSound(Holder<SoundEvent> soundEntry, float volume) {
        playSound(soundEntry.value(), volume);
    }

    private static void play(SoundEvent sound, float volume) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            ClientTickDispatcher.submit(mc -> play(sound, volume), 20);
            return;
        }

        SimpleSoundInstance soundInstance = SimpleSoundInstance.forUI(sound, 1f, volume);
        client.getSoundManager().play(soundInstance);
    }
}
