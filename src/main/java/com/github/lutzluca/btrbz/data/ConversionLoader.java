package com.github.lutzluca.btrbz.data;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.utils.MessageQueue;
import com.github.lutzluca.btrbz.utils.MessageQueue.Level;
import com.github.lutzluca.btrbz.utils.Utils;
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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

@Slf4j
public final class ConversionLoader {

    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private static final Path MOD_CONFIG_DIR = FabricLoader
        .getInstance()
        .getConfigDir()
        .resolve(BtrBz.MOD_ID);
    private static final Path LOCAL_CONVERSION_FILEPATH = MOD_CONFIG_DIR.resolve("conversions.json");

    //? if >=1.21.11 {
    /*private static final Identifier BUNDLED_CONVERSION_ID =  Identifier.fromNamespaceAndPath(
            BtrBz.MOD_ID,
            "conversions.json"
    );
    *///?} else {
    private static final ResourceLocation BUNDLED_CONVERSION_ID = ResourceLocation.fromNamespaceAndPath(
        BtrBz.MOD_ID,
        "conversions.json"
    );
    //?}

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

    public record LoadResult(BiMap<String, String> conversions, String contentHash) { }

    public static CompletableFuture<Try<LoadResult>> load() {

        var localResult = loadFromCache()
            .peek(data -> log.debug("Successfully loaded conversions from local cache"))
            .recoverWith(err -> {
                log.warn(
                    "Local cache unavailable: '{}', falling back to bundled resource",
                    err.getMessage()
                );
                log.warn("Full error:", err);

                return loadFromBundleResource()
                    .peek(data -> log.debug("Successfully loaded conversions from bundled resource"));
            });

        if (localResult.isSuccess()) {
            var data = localResult.get();
            return CompletableFuture.completedFuture(
                Try.success(new LoadResult(data.conversions, computeGitBlobHash(data.content)))
            );
        }

        log.warn(
            "Local and bundled resources unavailable: '{}', falling back to GitHub",
            localResult.getCause().getMessage()
        );
        log.warn("Full error:", localResult.getCause());

        return loadFromGithub().thenApply(result ->
            result
                .peek(data -> log.debug("Successfully loaded conversions from GitHub"))
                .map(data -> new LoadResult(data.conversions, computeGitBlobHash(data.content)))
                .onFailure(err -> log.error("Failed to load conversions from GitHub", err))
        );
    }

    public static CompletableFuture<Try<Optional<LoadResult>>> checkForConversionUpdates(String currHash) {
        return fetchGithubFileInfo().thenApply(fileInfo ->
            fileInfo.flatMap(info -> {
                if (currHash.equals(info.sha)) {
                    log.debug("Conversions are up to date");
                    return Try.<Optional<LoadResult>>success(Optional.empty());
                }

                log.debug("Update available, trying to fetch new conversions from github");
                return fetchUrl(info.download_url)
                    .flatMap(ConversionData::parseFrom)
                    .peek(newData -> {
                        Utils.atomicDumpToFile(LOCAL_CONVERSION_FILEPATH, newData.content)
                            .onSuccess(result -> MessageQueue.sendOrQueue("Updated local bazaar conversions", Level.Info))
                            .onFailure(err -> {
                                log.error("Failed to persist conversions to local cache, using in-memory only", err);
                                MessageQueue.sendOrQueue("Failed to cache conversions locally", Level.Warn);
                            });
                    })
                    .map(newData -> Optional.of(new LoadResult(
                        newData.conversions,
                        computeGitBlobHash(newData.content)
                    )));
            })
        );
    }

    private static Try<ConversionData> loadFromCache() {
        return Try
            .of(() -> Files.readString(LOCAL_CONVERSION_FILEPATH, StandardCharsets.UTF_8))
            .flatMap(ConversionData::parseFrom);
    }

    private static Try<ConversionData> loadFromBundleResource() {
        return Try
            .of(() -> Minecraft
                .getInstance()
                .getResourceManager()
                .getResource(BUNDLED_CONVERSION_ID)
                .orElseThrow(() -> new IOException("Bundled conversion resource not found"))
                .open())
            .flatMap(ConversionLoader::readStream)
            .flatMap(ConversionData::parseFrom)
            .peek(data -> {
                log.debug("Loaded conversions from bundle, caching locally");
                Utils.atomicDumpToFile(LOCAL_CONVERSION_FILEPATH, data.content)
                    .onFailure(err -> log.warn("Failed to cache conversions locally", err));
            });
    }

    private static CompletableFuture<Try<ConversionData>> loadFromGithub() {
        return fetchGithubFileInfo().thenApply(info -> info
            .map(GithubFileInfo::download_url)
            .flatMap(ConversionLoader::fetchUrl)
            .flatMap(ConversionData::parseFrom)
            .peek(data -> {
                log.debug("Loaded conversions from GitHub, caching locally");
                Utils.atomicDumpToFile(LOCAL_CONVERSION_FILEPATH, data.content)
                    .onFailure(err -> log.warn("Failed to cache conversions locally", err));
            }));
    }

    private static CompletableFuture<Try<GithubFileInfo>> fetchGithubFileInfo() {
        return CompletableFuture.supplyAsync(() -> {
            var req = HttpRequest
                .newBuilder()
                .uri(GITHUB_API_URI)
                .header("Accept", "application/vnd.github.v3+json")
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
                throw new IOException(
                    String.format("Failed to fetch URL '%s', status: %d body: %s",
                        url, resp.statusCode(), resp.body()
                    )
                );
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