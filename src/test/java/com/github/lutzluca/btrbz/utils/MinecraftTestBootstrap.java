package com.github.lutzluca.btrbz.utils;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

final class MinecraftTestBootstrap {

    private static boolean bootstrapped = false;

    private MinecraftTestBootstrap() { }

    static synchronized void bootstrap() {
        if (bootstrapped) {
            return;
        }

        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        bootstrapped = true;
    }
}