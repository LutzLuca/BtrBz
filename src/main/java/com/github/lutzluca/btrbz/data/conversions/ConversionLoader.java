package com.github.lutzluca.btrbz.data.conversions;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.utils.Utils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.vavr.control.Try;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

@Slf4j
final class ConversionLoader {

    static final Gson GSON = new GsonBuilder()
        .registerTypeAdapter(ProductNameSource.class, new ProductNameSourceAdapter())
        .setPrettyPrinting()
        .create();

    private static final Identifier BUNDLED_INDEX_ID = Identifier.fromNamespaceAndPath(
        BtrBz.MOD_ID,
        "conversion-index.json"
    );

    private ConversionLoader() { }

    record LoadResult(ConversionIndex index, ConversionStatus.IndexLoadSource source) { }

    static Try<LoadResult> loadSync() {
        return loadFromLocalCache()
            .map(index -> new LoadResult(index, ConversionStatus.IndexLoadSource.LocalCache))
            .recoverWith(err -> {
                log.warn("Local conversion index unavailable: {}", err.getMessage());
                return loadFromBundledSeed()
                    .map(index -> new LoadResult(index, ConversionStatus.IndexLoadSource.BundledSeed));
            });
    }

    static Try<Path> persistIndex(ConversionIndex index) {
        var snapshot = ConversionIndexSnapshot.fromIndex(index);
        return Utils.atomicDumpToFile(localIndexPath(), GSON.toJson(snapshot));
    }

    private static Try<ConversionIndex> loadFromLocalCache() {
        return Try
            .of(() -> Files.readString(localIndexPath(), StandardCharsets.UTF_8))
            .flatMap(ConversionLoader::parseIndex);
    }

    private static Path localIndexPath() {
        return FabricLoader
            .getInstance()
            .getConfigDir()
            .resolve(BtrBz.MOD_ID)
            .resolve("conversion-index.json");
    }

    private static Try<ConversionIndex> loadFromBundledSeed() {
        return Try
            .of(() -> Minecraft
                .getInstance()
                .getResourceManager()
                .getResource(BUNDLED_INDEX_ID)
                .orElseThrow(() -> new IOException("Bundled conversion index resource not found"))
                .open())
            .flatMap(ConversionLoader::readStream)
            .flatMap(ConversionLoader::parseIndex)
            .onFailure(err -> log.error("Bundled conversion index unavailable", err));
    }

    private static Try<ConversionIndex> parseIndex(String content) {
        return Try.of(() -> {
            var snapshot = GSON.fromJson(content, ConversionIndexSnapshot.class);
            if (snapshot == null) {
                throw new IOException("Conversion index parsed to null");
            }
            return snapshot.toIndex();
        });
    }

    private static Try<String> readStream(InputStream stream) {
        return Try.of(() -> {
            try (InputStream is = stream) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        });
    }
}
