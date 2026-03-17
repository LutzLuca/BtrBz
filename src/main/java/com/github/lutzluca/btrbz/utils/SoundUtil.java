package com.github.lutzluca.btrbz.utils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;

@Slf4j
public class SoundUtil {
    private static final Map<SoundEvent, Long> lastPlayedTimes = new ConcurrentHashMap<>();
    private static final long SOUND_COOLDOWN_MS = 500L;
    private static final int MAX_RETRIES = 5;

    public static void playSoundIf(boolean enabled, SoundEvent sound, float volume, int repeatCount) {
        if (!enabled) {
            return;
        }
        SoundUtil.playSound(sound, volume, repeatCount);
    }

    public static void playSoundIf(boolean enabled, Holder<SoundEvent> soundEntry, float volume, int repeatCount) {
        SoundUtil.playSoundIf(enabled, soundEntry.value(), volume, repeatCount);
    }

    public static void playSound(SoundEvent sound, float volume, int repeatCount) {
        long now = System.currentTimeMillis();
        if (now - lastPlayedTimes.getOrDefault(sound, 0L) > SOUND_COOLDOWN_MS) {
            lastPlayedTimes.put(sound, now);

            for (int i = 0; i < repeatCount; i++) {
                if (i == 0) {
                    SoundUtil.play(sound, volume);
                    continue;
                }

                ClientTickDispatcher.submit(mc -> SoundUtil.play(sound, volume), i * 3);
            }
        }
    }

    public static void playSound(SoundEvent sound, float volume) {
        SoundUtil.playSound(sound, volume, 1);
    }

    public static void playSound(Holder<SoundEvent> soundEntry, float volume) {
        SoundUtil.playSound(soundEntry.value(), volume);
    }

    private static void play(SoundEvent sound, float volume) {
        SoundUtil.play(sound, volume, MAX_RETRIES);
    }

    private static void play(SoundEvent sound, float volume, int attemptsLeft) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            if (attemptsLeft > 0) {
                ClientTickDispatcher.submit(mc -> SoundUtil.play(sound, volume, attemptsLeft - 1), 20);
            }
            return;
        }

        SimpleSoundInstance soundInstance = SimpleSoundInstance.forUI(sound, 1f, volume);
        client.getSoundManager().play(soundInstance);
    }
}
