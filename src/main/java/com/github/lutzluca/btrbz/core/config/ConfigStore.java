package com.github.lutzluca.btrbz.core.config;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.core.AlertManager.Alert;
import com.github.lutzluca.btrbz.core.widgets.bookmarks.BookmarksWidgetConfig.BookmarkedItem;
import com.github.lutzluca.btrbz.data.IndexedProduct;
import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.serializer.GsonConfigSerializerBuilder;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;
import net.fabricmc.loader.api.FabricLoader;

@Slf4j
public final class ConfigStore {

    private final ConfigClassHandler<Config> handler;

    public ConfigStore(Path path) {
        Objects.requireNonNull(path, "path cannot be null");
        this.handler = ConfigClassHandler
            .createBuilder(Config.class)
            .serializer(config -> GsonConfigSerializerBuilder
                .create(config)
                .appendGsonBuilder(builder -> builder
                    .registerTypeAdapter(Alert.class, new Alert.GsonAdapter())
                    .registerTypeAdapter(BookmarkedItem.class, new BookmarkedItem.GsonAdapter())
                    .registerTypeAdapter(IndexedProduct.class, new IndexedProduct.GsonAdapter()))
                .setPath(path)
                .build())
            .build();
    }

    public static ConfigStore get() {
        return Production.INSTANCE;
    }

    ConfigClassHandler<Config> handler() {
        return this.handler;
    }

    public Config config() {
        return this.handler.instance();
    }

    public boolean load() {
        boolean loaded = this.handler.load();
        if (loaded) {
            log.info("Successfully loaded config");
        } else {
            log.warn("Failed to load config");
        }
        return loaded;
    }

    public void save() {
        log.trace("Saving config");
        this.handler.save();
    }

    /** Saves immediately only when the updater reports a state change. */
    public boolean updateIfChanged(Predicate<Config> updater) {
        boolean changed = updater.test(this.config());
        if (changed) {
            this.save();
        }
        return changed;
    }

    // Constructing an independent store must not resolve the Fabric config directory.
    private static final class Production {
        private static final ConfigStore INSTANCE = new ConfigStore(FabricLoader
            .getInstance()
            .getConfigDir()
            .resolve(BtrBz.MOD_ID + ".json"));
    }
}
