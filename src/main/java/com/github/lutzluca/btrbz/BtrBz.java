package com.github.lutzluca.btrbz;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BtrBz implements ClientModInitializer {

    public static final String MOD_ID = "btrbz";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        LOGGER.info("[BtrBz] Initializing Mod...");
        new BzPoller();
    }
}
