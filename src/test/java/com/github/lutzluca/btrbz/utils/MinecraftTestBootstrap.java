package com.github.lutzluca.btrbz.utils;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

public final class MinecraftTestBootstrap {

    private static boolean bootstrapped = false;

    private MinecraftTestBootstrap() { }

    public static synchronized void bootstrap() {
        if (bootstrapped) {
            return;
        }

        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        bootstrapped = true;
    }
}