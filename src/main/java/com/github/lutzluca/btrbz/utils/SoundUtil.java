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

    public static void playSound(SoundEvent sound, float volume) {
        long now = System.currentTimeMillis();
        if (now - lastPlayedTimes.getOrDefault(sound, 0L) > SOUND_COOLDOWN_MS) {
            lastPlayedTimes.put(sound, now);
            play(sound, volume);
        }
    }

    public static void playSound(Holder<SoundEvent> soundEntry, float volume) {
        playSound(soundEntry.value(), volume);
    }

    private static void play(SoundEvent sound, float volume) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            ClientTickDispatcher.submit(mc -> play(sound, volume), 20);
            return;
        }

        SimpleSoundInstance soundInstance = SimpleSoundInstance.forUI(sound, 1f, volume);
        client.getSoundManager().play(soundInstance);
    }

    public static void notifyMultipleTimes(int notifyNum, SoundEvent sound) {
        long now = System.currentTimeMillis();
        if (now - lastPlayedTimes.getOrDefault(sound, 0L) > SOUND_COOLDOWN_MS) {
            lastPlayedTimes.put(sound, now);

            for (int i = 0; i < notifyNum; i++) {
                if (i == 0) {
                    play(sound, 0.5f);
                    continue;
                }

                // 20tps = 1/20s = 50ms per tick => ~150ms delay between sounds
                ClientTickDispatcher.submit(mc -> play(sound, 0.5f), i * 3);
            }
        }
    }
}
