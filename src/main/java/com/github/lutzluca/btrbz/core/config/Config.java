package com.github.lutzluca.btrbz.core.config;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.core.modules.BindModule;
import com.github.lutzluca.btrbz.core.modules.OrderLimitModule;
import com.github.lutzluca.btrbz.core.modules.OrderLimitModule.OrderLimitConfig;
import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.SerialEntry;
import dev.isxander.yacl3.config.v2.api.serializer.GsonConfigSerializerBuilder;
import lombok.extern.slf4j.Slf4j;
import net.fabricmc.loader.api.FabricLoader;

@Slf4j
public class Config {

    public static final ConfigClassHandler<Config> HANDLER = ConfigClassHandler
        .createBuilder(Config.class)
        .serializer(config -> GsonConfigSerializerBuilder
            .create(config)
            .setPath(FabricLoader
                .getInstance()
                .getConfigDir()
                .resolve(String.format("%s.json", BtrBz.modId)))
            .build())
        .build();

    @SerialEntry
    @BindModule(OrderLimitModule.class)
    public OrderLimitConfig orderLimit = new OrderLimitConfig();

    @SerialEntry
    public double tax = 1.125;

    public static Config get() {
        return HANDLER.instance();
    }

    public static void load() {
        var success = HANDLER.load();
        if (!success) {
            log.warn("Failed to load config");
            return;
        }

        log.info("Successfully loaded config");
        Runtime.getRuntime().addShutdownHook(new Thread(HANDLER::save));
    }
}
