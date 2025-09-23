package com.github.lutzluca.btrbz;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.vavr.control.Try;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

public final class ConversionLoader {

    private static final Path CACHE_DIR = Path.of(System.getProperty("user.home"), ".btrbz");
    private static final Path CACHE_FILE = CACHE_DIR.resolve("conversions.json");
    private static final String RESOURCE_PATH = "/conversions.json";
    private static final Gson GSON = new Gson();

    private ConversionLoader() { }

    public static Try<BiMap<String, String>> initialize() {
        return loadFromCache().recoverWith(err -> {
            Notifier.logWarn(
                "Failed to load local cache ({}). Trying to retrieve conversions from the bundled resources",
                err.getMessage()
            );
            return loadFromBundledResource().peek(ConversionLoader::cacheLocally);
        }).map(map -> {
            // apparently there are some duplicate names in the conversions
            // disregarding them "for now"
            // "ENCHANTED_CARROT_ON_A_STICK" -> "Enchanted Carrot on a Stick"
            // "ENCHANTED_CARROT_STICK" -> "Enchanted Carrot on a Stick"

            var conv = HashBiMap.<String, String>create();
            for (var entry : map.entrySet()) {
                if (conv.containsValue(entry.getValue())) {
                    Notifier.logWarn(
                        "Duplicate conversion name detected: '{}' -> '{}'. Ignoring new value of '{}'.",
                        entry.getKey(), conv.inverse().get(entry.getValue()), entry.getValue()
                    );

                    continue;
                }

                conv.put(entry.getKey(), entry.getValue());
            }
            return conv;
        });
    }

    private static Try<Map<String, String>> loadFromCache() {
        if (!Files.exists(CACHE_FILE)) {
            Notifier.logInfo("No local cache found at {}", CACHE_FILE);
            return Try.failure(new IOException("Cache file does not exist"));
        }

        Notifier.logDebug("Found local conversion cache at {}. Attempting to load.", CACHE_FILE);
        return Try
            .of(() -> Files.readString(CACHE_FILE, StandardCharsets.UTF_8))
            .flatMap(ConversionLoader::parseJsonToMap);
    }

    private static Try<Map<String, String>> loadFromBundledResource() {
        return Optional
            .ofNullable(ConversionLoader.class.getResourceAsStream(RESOURCE_PATH))
            .map(Try::success)
            .orElseGet(
                () -> Try.failure(new IOException("Bundled resource not found: " + RESOURCE_PATH)))
            .flatMap(ConversionLoader::readStream)
            .flatMap(ConversionLoader::parseJsonToMap);
    }

    private static Try<String> readStream(InputStream stream) {
        return Try.of(() -> {
            try (InputStream is = stream) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        });
    }

    private static void cacheLocally(Map<String, String> map) {
        String json = GSON.toJson(map);

        Try
            .run(() -> Files.createDirectories(CACHE_DIR))
            .andThenTry(() -> Files.writeString(CACHE_FILE, json, StandardCharsets.UTF_8))
            .onSuccess(v -> Notifier.logDebug("Wrote conversion cache to {}.", CACHE_FILE))
            .onFailure(
                err -> Notifier.logWarn("Failed to write conversion cache to {}: {}", CACHE_FILE,
                    err.getMessage()
                ));
    }

    private static Try<Map<String, String>> parseJsonToMap(String json) {
        // @formatter:off
        return Try.of(() -> Optional.ofNullable(GSON.fromJson(json, new TypeToken<Map<String, String>>() { })))
                  .flatMap(mappings ->
                      mappings
                          .map(Try::success)
                          .orElseGet(() -> Try.failure(new IOException("Parsed conversion JSON as null")))
                  );
        // @formatter:on
    }
}
