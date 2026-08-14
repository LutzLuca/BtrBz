package com.github.lutzluca.btrbz.core.config;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.core.AlertManager.Alert;
import com.github.lutzluca.btrbz.core.widgets.bookmarks.BookmarksWidgetConfig.BookmarkedItem;
import com.github.lutzluca.btrbz.data.IndexedProduct;
import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.serializer.GsonConfigSerializerBuilder;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;
import net.fabricmc.loader.api.FabricLoader;

@Slf4j
public final class ConfigManager {

    static final ConfigClassHandler<Config> HANDLER = ConfigClassHandler
        .createBuilder(Config.class)
        .serializer(config -> GsonConfigSerializerBuilder
            .create(config)
            .appendGsonBuilder(builder -> builder
                .registerTypeAdapter(Alert.class, new Alert.GsonAdapter())
                .registerTypeAdapter(BookmarkedItem.class, new BookmarkedItem.GsonAdapter())
                .registerTypeAdapter(IndexedProduct.class, new IndexedProduct.GsonAdapter()))
            .setPath(FabricLoader
                .getInstance()
                .getConfigDir()
                .resolve(String.format("%s.json", BtrBz.MOD_ID)))
            .build())
        .build();

    private ConfigManager() {}

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

    /**
     * Saves immediately only when the updater reports a state change.
     */
    public static boolean updateIfChanged(Predicate<Config> updater) {
        boolean changed = updater.test(HANDLER.instance());
        if (changed) {
            save();
        }
        return changed;
    }

    public static void save() {
        log.trace("Saving config");
        HANDLER.save();
    }
}
