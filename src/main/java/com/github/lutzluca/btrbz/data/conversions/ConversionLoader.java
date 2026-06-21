package com.github.lutzluca.btrbz.data.conversions;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.utils.Utils;
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

    private static final Path MOD_CONFIG_DIR = FabricLoader
        .getInstance()
        .getConfigDir()
        .resolve(BtrBz.MOD_ID);
    private static final Path LOCAL_INDEX_FILEPATH = MOD_CONFIG_DIR.resolve("conversion-index.json");

    private static final Identifier BUNDLED_INDEX_ID = Identifier.fromNamespaceAndPath(
        BtrBz.MOD_ID,
        "conversion-index.json"
    );

    private ConversionLoader() { }

    record LoadResult(ConversionIndex index, IndexLoadSource source) { }

    static Try<LoadResult> loadSync() {
        return loadFromLocalCache()
            .map(index -> new LoadResult(index, IndexLoadSource.LocalCache))
            .recoverWith(err -> {
                log.warn("Local conversion index unavailable: {}", err.getMessage());
                return loadFromBundledSeed()
                    .map(index -> new LoadResult(index, IndexLoadSource.BundledSeed));
            });
    }

    static Try<Path> persistIndex(ConversionIndex index) {
        var snapshot = ConversionIndexSnapshot.fromIndex(index);
        return Utils.atomicDumpToFile(LOCAL_INDEX_FILEPATH, ConversionJson.GSON.toJson(snapshot));
    }

    private static Try<ConversionIndex> loadFromLocalCache() {
        return Try
            .of(() -> Files.readString(LOCAL_INDEX_FILEPATH, StandardCharsets.UTF_8))
            .flatMap(ConversionLoader::parseIndex)
            .map(ConversionIndexNormalizer::normalizeDerivedEntries);
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
            .map(ConversionIndexNormalizer::normalizeDerivedEntries)
            .onFailure(err -> log.error("Bundled conversion index unavailable", err));
    }

    private static Try<ConversionIndex> parseIndex(String content) {
        return Try.of(() -> {
            var snapshot = ConversionJson.GSON.fromJson(content, ConversionIndexSnapshot.class);
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
