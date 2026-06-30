package com.github.lutzluca.btrbz.data.conversions;

import com.github.lutzluca.btrbz.utils.Utils;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class RemoteConversionIndexBuilder {

    private static final Gson GSON = new Gson();
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration JSON_REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration ZIP_REQUEST_TIMEOUT = Duration.ofSeconds(60);
    private static final HttpClient HTTP_CLIENT = HttpClient
        .newBuilder()
        .connectTimeout(CONNECT_TIMEOUT)
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    private static final URI HYPIXEL_BAZAAR_URI = URI.create("https://api.hypixel.net/v2/skyblock/bazaar");
    private static final URI HYPIXEL_ITEMS_URI = URI.create("https://api.hypixel.net/v2/resources/skyblock/items");
    private static final URI NEU_COMMIT_URI = URI.create(
        "https://api.github.com/repos/NotEnoughUpdates/NotEnoughUpdates-REPO/commits/master"
    );
    private static final String NEU_ZIP_URL =
        "https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO/archive/%s.zip";

    private RemoteConversionIndexBuilder() { }

    static ConversionIndex build(ConversionIndex current) throws ConversionRefreshException {
        var productIds = fetchBazaarProductIds();
        var hypixelItems = fetchHypixelItemNames();
        var neuCommit = fetchNeuCommit();
        var neuEntries = shouldReuseNeuEntries(current, neuCommit, productIds, hypixelItems)
            ? reusableNeuEntries(current)
            : fetchNeuEntries(neuCommit, productIds);

        var products = new LinkedHashMap<String, ConversionProductEntry>();
        var derivedFallbackExamples = new ArrayList<String>();

        for (var productId : productIds) {
            var hypixelName = hypixelItems.get(productId);
            if (hypixelName != null && !hypixelName.isBlank()) {
                products.put(
                    productId,
                    new ConversionProductEntry(hypixelName, new ProductNameSource.HypixelItem())
                );
                continue;
            }

            var neuEntry = neuEntries.get(productId);
            if (neuEntry != null) {
                products.put(productId, neuEntry);
                continue;
            }

            var derived = ConversionNameDeriver.deriveDisplayName(productId);
            if (derived.displayName().isBlank()) {
                throw new ConversionRefreshException(
                    ConversionRefreshException.Phase.Validate,
                    "Could not derive a display name for active Bazaar product " + productId
                );
            }
            if (derived.genericFallback() && derivedFallbackExamples.size() < 20) {
                derivedFallbackExamples.add("%s -> %s".formatted(productId, derived.displayName()));
            }
            products.put(
                productId,
                new ConversionProductEntry(derived.displayName(), new ProductNameSource.Derived())
            );
        }

        if (products.size() != productIds.size()) {
            throw new ConversionRefreshException(
                ConversionRefreshException.Phase.Validate,
                "Built conversion index is incomplete: " + products.size() + " / " + productIds.size()
            );
        }

        var index = new ConversionIndex(
            ConversionIndex.SCHEMA_VERSION,
            Instant.now().toString(),
            neuCommit,
            products
        );
        var counts = index.sourceCounts();
        log.debug(
            "Built Bazaar conversion index from {} products: hypixelItems={}, neu={}, derived={}, neuCommit={}",
            index.size(),
            counts.hypixelItem(),
            counts.neu(),
            counts.derived(),
            neuCommit
        );
        if (!derivedFallbackExamples.isEmpty()) {
            log.warn("Generic title-case conversion fallback used during refresh; sample: {}", derivedFallbackExamples);
        }
        return index;
    }

    static boolean shouldReuseNeuEntries(
        ConversionIndex current,
        String neuCommit,
        Set<String> productIds,
        Map<String, String> hypixelItems
    ) {
        if (current == null || current.neuCommit().filter(neuCommit::equals).isEmpty()) {
            return false;
        }

        return productIds
            .stream()
            .filter(productId -> {
                var hypixelName = hypixelItems.get(productId);
                return hypixelName == null || hypixelName.isBlank();
            })
            .allMatch(productId -> current.products().containsKey(productId));
    }

    private static Set<String> fetchBazaarProductIds() throws ConversionRefreshException {
        try {
            var body = fetchString(HYPIXEL_BAZAAR_URI, ConversionRefreshException.Phase.HypixelBazaar);
            var root = GSON.fromJson(body, JsonObject.class);
            if (root == null || !root.has("products") || !root.get("products").isJsonObject()) {
                throw new IOException("Invalid Hypixel Bazaar response");
            }
            return new HashSet<>(root.getAsJsonObject("products").keySet());
        } catch (IOException err) {
            throw new ConversionRefreshException(ConversionRefreshException.Phase.HypixelBazaar, err.getMessage(), err);
        }
    }

    private static Map<String, String> fetchHypixelItemNames() throws ConversionRefreshException {
        try {
            var body = fetchString(HYPIXEL_ITEMS_URI, ConversionRefreshException.Phase.HypixelItems);
            var root = GSON.fromJson(body, JsonObject.class);
            if (root == null || !root.has("items") || !root.get("items").isJsonArray()) {
                throw new IOException("Invalid Hypixel items response");
            }

            var names = new HashMap<String, String>();
            for (var itemElement : root.getAsJsonArray("items")) {
                if (!itemElement.isJsonObject()) {
                    continue;
                }
                var item = itemElement.getAsJsonObject();
                if (!item.has("id") || !item.has("name")) {
                    continue;
                }
                names.put(item.get("id").getAsString(), Utils.cleanDisplayName(item.get("name").getAsString()));
            }
            return names;
        } catch (IOException err) {
            throw new ConversionRefreshException(ConversionRefreshException.Phase.HypixelItems, err.getMessage(), err);
        }
    }

    private static String fetchNeuCommit() throws ConversionRefreshException {
        try {
            var body = fetchString(NEU_COMMIT_URI, ConversionRefreshException.Phase.NeuCommit);
            var root = GSON.fromJson(body, JsonObject.class);
            if (root == null || !root.has("sha")) {
                throw new IOException("Invalid NEU commit response");
            }
            return root.get("sha").getAsString();
        } catch (IOException err) {
            throw new ConversionRefreshException(ConversionRefreshException.Phase.NeuCommit, err.getMessage(), err);
        }
    }

    private static Map<String, ConversionProductEntry> reusableNeuEntries(ConversionIndex current) {
        var entries = new LinkedHashMap<String, ConversionProductEntry>();
        current.products().forEach((productId, entry) -> {
            if (entry.source() instanceof ProductNameSource.Neu) {
                entries.put(productId, entry);
            }
        });
        log.debug("NEU commit unchanged; reusing {} NEU conversion entries", entries.size());
        return entries;
    }

    private static Map<String, ConversionProductEntry> fetchNeuEntries(String commit, Set<String> productIds)
        throws ConversionRefreshException {
        Path zipPath;
        try {
            zipPath = Files.createTempFile("btrbz-neu-", ".zip");
        } catch (IOException err) {
            throw new ConversionRefreshException(
                ConversionRefreshException.Phase.NeuZip,
                "Failed to create temporary NEU zip file",
                err
            );
        }

        try {
            var req = baseRequest(URI.create(String.format(NEU_ZIP_URL, commit)))
                .timeout(ZIP_REQUEST_TIMEOUT)
                .setHeader("Accept", "application/zip, application/octet-stream")
                .GET()
                .build();
            var resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofFile(zipPath));
            if (resp.statusCode() != 200) {
                throw new IOException("Failed to download NEU zip: HTTP " + resp.statusCode());
            }

            try (var zip = new ZipFile(zipPath.toFile(), StandardCharsets.UTF_8)) {
                var entriesBySuffix = indexZipEntries(zip);
                var stockEntry = entriesBySuffix.get("constants/bazaarstocks.json");
                if (stockEntry == null) {
                    throw new IOException("NEU zip did not contain constants/bazaarstocks.json");
                }

                var stocks = readBazaarStocks(zip, stockEntry);
                var productEntries = new HashMap<String, ConversionProductEntry>();
                for (var stock : stocks) {
                    if (stock.stock == null || stock.id == null || !productIds.contains(stock.stock)) {
                        continue;
                    }

                    var itemEntry = entriesBySuffix.get("items/" + stock.id + ".json");
                    var displayName = itemEntry == null
                        ? ConversionNameDeriver.deriveDisplayName(stock.stock, stock.id).displayName()
                        : readNeuDisplayName(zip, itemEntry)
                            .orElseGet(() -> ConversionNameDeriver.deriveDisplayName(stock.stock, stock.id).displayName());

                    productEntries.put(
                        stock.stock,
                        new ConversionProductEntry(displayName, new ProductNameSource.Neu(stock.id))
                    );
                }
                log.debug("Read {} NEU bazaarstocks entries from {}", productEntries.size(), commit);
                return productEntries;
            }
        } catch (IOException err) {
            throw new ConversionRefreshException(ConversionRefreshException.Phase.NeuZip, err.getMessage(), err);
        } catch (InterruptedException err) {
            Thread.currentThread().interrupt();
            throw new ConversionRefreshException(ConversionRefreshException.Phase.NeuZip, err.getMessage(), err);
        } finally {
            try {
                Files.deleteIfExists(zipPath);
            } catch (IOException err) {
                log.warn("Failed to delete temporary NEU zip {}", zipPath, err);
            }
        }
    }

    private static Map<String, ZipEntry> indexZipEntries(ZipFile zip) {
        var entries = new HashMap<String, ZipEntry>();
        zip.stream().forEach(entry -> {
            var name = entry.getName();
            var constantsIdx = name.indexOf("constants/");
            if (constantsIdx >= 0) {
                entries.put(name.substring(constantsIdx), entry);
                return;
            }

            var itemIdx = name.indexOf("items/");
            if (itemIdx >= 0) {
                entries.put(name.substring(itemIdx), entry);
            }
        });
        return entries;
    }

    private static List<BazaarStock> readBazaarStocks(ZipFile zip, ZipEntry entry) throws IOException {
        try (Reader reader = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, new TypeToken<List<BazaarStock>>() { }.getType());
        }
    }

    private static Optional<String> readNeuDisplayName(ZipFile zip, ZipEntry entry) throws IOException {
        try (Reader reader = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) {
            var item = GSON.fromJson(reader, JsonObject.class);
            if (item == null) {
                return Optional.empty();
            }

            var displayName = item.has("displayname")
                ? Utils.cleanDisplayName(item.get("displayname").getAsString())
                : "";

            if (!displayName.equals("Enchanted Book")) {
                return Optional.of(displayName).filter(name -> !name.isBlank());
            }

            if (!item.has("lore") || !item.get("lore").isJsonArray()) {
                return Optional.empty();
            }

            for (var lineElement : item.getAsJsonArray("lore")) {
                var line = Utils.cleanDisplayName(lineElement.getAsString());
                if (line.isBlank() || line.equals("Combinable in Anvil")) {
                    continue;
                }
                return Optional.of(line);
            }

            return Optional.empty();
        }
    }

    private static String fetchString(URI uri, ConversionRefreshException.Phase phase) throws IOException {
        try {
            var req = baseRequest(uri).GET().build();
            var resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) {
                throw new IOException("Request failed for " + uri + ": HTTP " + resp.statusCode());
            }
            return resp.body();
        } catch (InterruptedException err) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while fetching " + uri, err);
        } catch (IOException err) {
            throw new IOException(phase + " request failed: " + err.getMessage(), err);
        }
    }

    private static HttpRequest.Builder baseRequest(URI uri) {
        return HttpRequest
            .newBuilder(uri)
            .timeout(JSON_REQUEST_TIMEOUT)
            .header("Accept", "application/json")
            .header("User-Agent", "BtrBz conversion-index updater");
    }

    private static final class BazaarStock {
        String stock;
        String id;
    }
}
