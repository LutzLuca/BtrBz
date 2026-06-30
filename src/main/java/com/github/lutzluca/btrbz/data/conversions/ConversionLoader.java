package com.github.lutzluca.btrbz.data.conversions;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.utils.Utils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import io.vavr.control.Try;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

@Slf4j
final class ConversionLoader {

    static final Gson GSON = new GsonBuilder()
        .registerTypeAdapter(ProductNameSource.class, new ProductNameSourceJsonAdapter())
        .setPrettyPrinting()
        .create();

    private static final Identifier BUNDLED_INDEX_ID = Identifier.fromNamespaceAndPath(
        BtrBz.MOD_ID,
        "conversion-index.json"
    );

    private ConversionLoader() { }

    record LoadResult(ConversionIndex index, ConversionStatus.IndexLoadSource source) { }

    record IndexSnapshot(
        int schemaVersion,
        String generatedAt,
        String neuCommit,
        Map<String, ConversionProductEntry> products
    ) {

        static IndexSnapshot fromIndex(ConversionIndex index) {
            return new IndexSnapshot(
                index.schemaVersion(),
                index.generatedAt(),
                index.neuCommit().orElse(null),
                index.products()
            );
        }

        ConversionIndex toIndex() throws IOException {
            if (this.schemaVersion != ConversionIndex.SCHEMA_VERSION) {
                throw new IOException("Unsupported conversion index schema version: " + this.schemaVersion);
            }
            if (this.products == null || this.products.isEmpty()) {
                throw new IOException("Conversion index contains no products");
            }

            try {
                return new ConversionIndex(
                    this.schemaVersion,
                    this.generatedAt,
                    this.neuCommit,
                    this.products
                );
            } catch (IllegalArgumentException err) {
                throw new IOException("Invalid conversion index", err);
            }
        }
    }

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
        var snapshot = IndexSnapshot.fromIndex(index);
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
            var snapshot = GSON.fromJson(content, IndexSnapshot.class);
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

    private static final class ProductNameSourceJsonAdapter
        implements JsonSerializer<ProductNameSource>, JsonDeserializer<ProductNameSource> {

        @Override
        public JsonElement serialize(ProductNameSource src, Type typeOfSrc, JsonSerializationContext context) {
            var json = new JsonObject();
            switch (src) {
                case ProductNameSource.HypixelItem _ -> json.addProperty("type", "hypixel-item");
                case ProductNameSource.Neu neu -> {
                    json.addProperty("type", "neu");
                    json.addProperty("neuId", neu.neuId());
                }
                case ProductNameSource.Derived _ -> json.addProperty("type", "derived");
            }
            return json;
        }

        @Override
        public ProductNameSource deserialize(
            JsonElement json,
            Type typeOfT,
            JsonDeserializationContext context
        ) throws JsonParseException {
            if (json == null || !json.isJsonObject()) {
                throw new JsonParseException("Product name source must be an object");
            }

            var obj = json.getAsJsonObject();
            if (!obj.has("type")) {
                throw new JsonParseException("Product name source is missing type");
            }

            return switch (obj.get("type").getAsString()) {
                case "hypixel-item" -> new ProductNameSource.HypixelItem();
                case "neu" -> {
                    if (!obj.has("neuId") || obj.get("neuId").getAsString().isBlank()) {
                        throw new JsonParseException("NEU product name source is missing neuId");
                    }
                    yield new ProductNameSource.Neu(obj.get("neuId").getAsString());
                }
                case "derived" -> new ProductNameSource.Derived();
                default -> throw new JsonParseException(
                    "Unknown product name source type: " + obj.get("type").getAsString()
                );
            };
        }
    }
}
