package com.github.lutzluca.btrbz.core.config;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.core.modules.BookmarkModule.BookmarkedItem;
import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.serializer.GsonConfigSerializerBuilder;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import net.fabricmc.loader.api.FabricLoader;

@Slf4j
public final class ConfigManager {

    static final ConfigClassHandler<Config> HANDLER = ConfigClassHandler
        .createBuilder(Config.class)
        .serializer(config -> GsonConfigSerializerBuilder
            .create(config)
            .appendGsonBuilder(gsonBuilder -> gsonBuilder.registerTypeAdapter(
                BookmarkedItem.class,
                new BookmarkedItem.BookmarkedItemSerializer()
            ))
            .setPath(FabricLoader
                .getInstance()
                .getConfigDir()
                .resolve(String.format("%s.json", BtrBz.MOD_ID)))
            .build())
        .build();

    private ConfigManager() { }

    public static void load() {
        if (!HANDLER.load()) {
            log.warn("Failed to load config");
        } else {
            log.info("Successfully loaded config");
        }

        Runtime.getRuntime().addShutdownHook(new Thread(HANDLER::save));
    }

    public static Config get() {
        return HANDLER.instance();
    }

    public static void withConfig(Consumer<Config> consumer) {
        consumer.accept(HANDLER.instance());
        save();
    }

    public static <R> R compute(Function<Config, R> function) {
        R ret = function.apply(HANDLER.instance());
        save();
        return ret;
    }

    public static void save() {
        log.trace("Saving config");
        HANDLER.save();
    }
}

