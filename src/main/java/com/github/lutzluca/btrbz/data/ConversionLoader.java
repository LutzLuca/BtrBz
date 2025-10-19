package com.github.lutzluca.btrbz.data;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.utils.MessageQueue;
import com.github.lutzluca.btrbz.utils.MessageQueue.Level;
import com.github.lutzluca.btrbz.utils.Util;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.vavr.control.Try;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

@Slf4j
public final class ConversionLoader {

    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private static final Path MOD_CONFIG_DIR = FabricLoader
        .getInstance()
        .getConfigDir()
        .resolve(BtrBz.MOD_ID);
    private static final Path LOCAL_CONVERSION_FILEPATH = MOD_CONFIG_DIR.resolve("conversions.json");
    private static final Identifier BUNDLED_CONVERSION_ID = Identifier.of(
        BtrBz.MOD_ID,
        "conversions.json"
    );

    private static final String GITHUB_OWNER = "LutzLuca";
    private static final String GITHUB_REPO = "BtrBz";
    private static final String GITHUB_PATH = "src/main/resources/assets/btrbz/conversions.json";
    private static final URI GITHUB_API_URI = URI.create(String.format(
        "https://api.github.com/repos/%s/%s/contents/%s",
        GITHUB_OWNER,
        GITHUB_REPO,
        GITHUB_PATH
    ));

    private ConversionLoader() { }

    public static void load() {
        log.info("Loading bazaar conversions");

        loadFromCache()
            .peek(data -> log.debug(
                "Loaded conversions ({} entries) from local cache",
                data.conversions.size()
            ))
            .recoverWith(cacheErr -> {
                log.warn(
                    "Local cache unavailable: '{}', falling back to bundled resource",
                    cacheErr.getMessage()
                );
                log.debug("Full error:", cacheErr);

                return loadFromBundleResource().peek(data -> log.debug(
                    "Loaded conversions ({} entries) from bundled resource",
                    data.conversions.size()
                ));
            })
            .onSuccess(data -> {
                BtrBz.bazaarData().setConversions(data.conversions);
                checkForUpdate(data);
            })
            .onFailure(bundleErr -> {
                log.error("Failed to load conversions from cache and bundle", bundleErr);
                MessageQueue.sendOrQueue(
                    """
                        Failed to load bazaar conversions that are needed for some features to work.
                        Trying to fetch the latest version from GitHub to resolve this issue.
                        """, Level.Warn
                );

                fetchFromGithubFallback();
            });
    }

    private static Try<ConversionData> loadFromCache() {
        return Try
            .of(() -> Files.readString(LOCAL_CONVERSION_FILEPATH, StandardCharsets.UTF_8))
            .flatMap(ConversionData::parseFrom);
    }

    private static Try<ConversionData> loadFromBundleResource() {
        return Try
            .of(() -> MinecraftClient
                .getInstance()
                .getResourceManager()
                .getResource(BUNDLED_CONVERSION_ID)
                .orElseThrow(() -> new IOException("Bundled conversion resource not found"))
                .getInputStream())
            .flatMap(ConversionLoader::readStream)
            .flatMap(ConversionData::parseFrom)
            .peek(data -> {
                log.debug("Loaded conversions from bundle, caching locally");
                Util.atomicDumpToFile(LOCAL_CONVERSION_FILEPATH, data.content);
            });
    }

    private static CompletableFuture<Try<ConversionData>> loadFromGithub() {
        return fetchGithubFileInfo().thenApply(info -> info
            .map(GithubFileInfo::download_url)
            .flatMap(ConversionLoader::fetchUrl)
            .flatMap(ConversionData::parseFrom));
    }

    private static void fetchFromGithubFallback() {
        log.debug("Attempting to fetch conversions from GitHub");

        loadFromGithub().thenAccept(res -> res.onSuccess(data -> {
            log.debug("Successfully fetched conversions from GitHub");
            BtrBz.bazaarData().setConversions(data.conversions);
            Util.atomicDumpToFile(LOCAL_CONVERSION_FILEPATH, data.content);

            MessageQueue.sendOrQueue(
                "Successfully loaded conversions from Github; everything should work as expected",
                Level.Info
            );
        }).onFailure(err -> {
            log.error("Failed to fetch conversions from GitHub", err);

            MessageQueue.sendOrQueue(
                "Failed to load bazaar conversions; some features will not work as expected",
                Level.Error
            );
        }));
    }

    private static void checkForUpdate(ConversionData data) {
        var localHash = computeGitBlobHash(data.content);

        fetchGithubFileInfo().thenAccept(fileInfo -> fileInfo.onSuccess(info -> {
            if (localHash.equals(info.sha)) {
                log.debug("Conversions are up to date");
                return;
            }

            log.debug("Update available, fetching new conversions");
            fetchUrl(info.download_url).flatMap(ConversionData::parseFrom).onSuccess(newData -> {
                BtrBz.bazaarData().setConversions(newData.conversions);
                Util.atomicDumpToFile(LOCAL_CONVERSION_FILEPATH, newData.content);

                MessageQueue.sendOrQueue("Updated bazaar conversions", Level.Info);
            }).onFailure(err -> log.warn("Failed to fetch or parse updated conversions", err));
        }).onFailure(err -> log.warn("Failed to check for updates", err)));
    }

    private static CompletableFuture<Try<GithubFileInfo>> fetchGithubFileInfo() {
        return CompletableFuture.supplyAsync(() -> {
            var req = HttpRequest
                .newBuilder()
                .uri(GITHUB_API_URI)
                .header("Accept", "application/vnd.github.v3+json")
                // TODO remove the Github access token once the repo is public
                .header("Authorization", "Bearer " + System.getenv("GITHUB_ACCESS_TOKEN"))
                .GET()
                .build();

            return Try
                .of(() -> HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString()))
                .mapTry(resp -> {
                    if (resp.statusCode() != 200) {
                        throw new IOException("GitHub API returned status: " + resp.statusCode());
                    }

                    var info = GSON.fromJson(resp.body(), GithubFileInfo.class);
                    if (info == null || info.download_url == null || info.sha == null) {
                        throw new IOException("Invalid GitHub API response: " + info);
                    }
                    return info;
                });
        });
    }

    private static Try<BiMap<String, String>> parseAsConversionMap(String json) {
        return Try.of(() -> {
            Map<String, String> map = GSON.fromJson(
                json,
                new TypeToken<Map<String, String>>() { }.getType()
            );

            if (map == null) {
                throw new IOException("Parsed JSON returned null");
            }

            BiMap<String, String> conversions = HashBiMap.create(map.size());

            map.forEach((id, name) -> {
                if (conversions.containsValue(name)) {
                    log.warn(
                        "Duplicate id: '{}' and '{}' for name '{}' (keeping first)",
                        conversions.inverse().get(name),
                        id,
                        name
                    );
                    return;
                }

                conversions.put(id, name);
            });

            return conversions;
        });
    }

    private static Try<String> fetchUrl(String url) {
        return Try.of(() -> {
            var req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();

            log.trace("Fetching URL: {}", url);
            var resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() != 200) {
                throw new IOException("Failed to fetch URL, status: " + resp.statusCode() + " body: " + resp.body());
            }

            return resp.body();
        });
    }

    private static String computeGitBlobHash(String content) {
        return Try.of(() -> {
            String header = "blob " + content.getBytes(StandardCharsets.UTF_8).length + "\0";
            var digest = MessageDigest.getInstance("SHA-1");
            digest.update(header.getBytes(StandardCharsets.UTF_8));
            digest.update(content.getBytes(StandardCharsets.UTF_8));

            return String.format("%040x", new BigInteger(1, digest.digest()));
        }).get();
    }

    private static Try<String> readStream(InputStream stream) {
        return Try.of(() -> {
            try (InputStream is = stream) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        });
    }

    private record GithubFileInfo(String download_url, String sha) { }

    private record ConversionData(String content, BiMap<String, String> conversions) {

        private static Try<ConversionData> parseFrom(String content) {
            return parseAsConversionMap(content).map(conversions -> new ConversionData(
                content,
                conversions
            ));
        }
    }
}
