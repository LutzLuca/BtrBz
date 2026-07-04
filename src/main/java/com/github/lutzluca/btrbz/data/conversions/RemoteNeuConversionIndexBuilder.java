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
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class RemoteNeuConversionIndexBuilder {

    private static final Gson GSON = new Gson();
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration JSON_REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration ZIP_REQUEST_TIMEOUT = Duration.ofSeconds(60);
    private static final int LOG_SAMPLE_LIMIT = Integer.getInteger("btrbz.conversions.logSampleLimit", 20);
    static final int BUILDER_VERSION = 1;
    private static final HttpClient HTTP_CLIENT = HttpClient
        .newBuilder()
        .connectTimeout(CONNECT_TIMEOUT)
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    private static final URI HYPIXEL_BAZAAR_URI = URI.create("https://api.hypixel.net/v2/skyblock/bazaar");
    private static final URI NEU_COMMIT_URI = URI.create(
        "https://api.github.com/repos/NotEnoughUpdates/NotEnoughUpdates-REPO/commits/master"
    );
    private static final String NEU_ZIP_URL =
        "https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO/archive/%s.zip";

    // Bazaar still exposes a few legacy product ids whose NEU item json uses a different id
    // Direct NEU item files are checked first, so these aliases only cover known mismatches
    private static final Map<String, String> STATIC_NEU_ALIASES = Map.ofEntries(
        Map.entry("INK_SACK:3", "INK_SACK-3"),
        Map.entry("INK_SACK:4", "INK_SACK-4"),
        Map.entry("LOG:1", "LOG-1"),
        Map.entry("LOG:2", "LOG-2"),
        Map.entry("LOG:3", "LOG-3"),
        Map.entry("LOG_2:1", "LOG_2-1"),
        Map.entry("RAW_FISH:1", "RAW_FISH-1"),
        Map.entry("RAW_FISH:2", "RAW_FISH-2"),
        Map.entry("RAW_FISH:3", "RAW_FISH-3"),
        Map.entry("SAND:1", "SAND-1"),
        Map.entry("BAZAAR_COOKIE", "BOOSTER_COOKIE"),
        Map.entry("ENCHANTED_CARROT_ON_A_STICK", "ENCHANTED_CARROT_STICK")
    );

    private RemoteNeuConversionIndexBuilder() { }

    static ConversionIndex build(ConversionIndex current) throws ConversionRefreshException {
        var productIds = fetchBazaarProductIds();
        var neuCommit = fetchNeuCommit();
        var products = shouldReuseNeuEntries(current, neuCommit, productIds)
            ? reusableEntries(current, productIds)
            : fetchNeuEntries(neuCommit, productIds);

        validateCompleteIndex(productIds, products);

        var index = new ConversionIndex(
            ConversionIndex.SCHEMA_VERSION,
            BUILDER_VERSION,
            Instant.now().toString(),
            neuCommit,
            products
        );
        var counts = index.sourceCounts();
        log.info(
            "Built Bazaar conversion index from {} products: neu={}, derived={}, neuCommit={}",
            index.size(),
            counts.neu(),
            counts.derived(),
            neuCommit
        );
        return index;
    }

    static boolean shouldReuseNeuEntries(
        ConversionIndex curr,
        String neuCommit,
        Set<String> productIds
    ) {
        if (curr == null || curr.neuCommit().filter(neuCommit::equals).isEmpty()) {
            return false;
        }

        if (curr.builderVersion() != BUILDER_VERSION) {
            return false;
        }

        return productIds.stream().allMatch(productId -> curr.products().containsKey(productId));
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

    private static Map<String, ConversionProductEntry> reusableEntries(
        ConversionIndex current,
        Set<String> productIds
    ) {
        var entries = new LinkedHashMap<String, ConversionProductEntry>();
        new TreeSet<>(productIds).forEach(productId -> entries.put(productId, current.products().get(productId)));
        log.debug("NEU commit unchanged; reusing {} conversion entries", entries.size());
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
                var stockIds = new HashMap<String, String>();
                for (var stock : stocks) {
                    if (stock.stock != null && stock.id != null) {
                        stockIds.put(stock.stock, stock.id);
                    }
                }

                var productEntries = new LinkedHashMap<String, ConversionProductEntry>();
                var derivedFallbackExamples = new ArrayList<String>();
                var staticAliasCount = 0;
                for (var productId : new TreeSet<>(productIds)) {
                    var productEntry = resolveEntry(zip, entriesBySuffix, stockIds, productId);
                    if (productEntry.isEmpty()) {
                        continue;
                    }

                    productEntries.put(productId, productEntry.get());

                    if (productEntry.get().source() instanceof ProductNameSource.Derived
                        && derivedFallbackExamples.size() < LOG_SAMPLE_LIMIT) {
                        derivedFallbackExamples.add("%s -> %s".formatted(productId, productEntry.get().strippedName()));
                    }
                    if (STATIC_NEU_ALIASES.containsKey(productId)) {
                        staticAliasCount++;
                    }
                }

                log.debug(
                    "Read {} Bazaar conversion entries from NEU commit {} (staticAliases={})",
                    productEntries.size(),
                    commit,
                    staticAliasCount
                );
                if (!derivedFallbackExamples.isEmpty()) {
                    log.info(
                        "Derived Bazaar conversion names used during refresh; sample: {}",
                        derivedFallbackExamples
                    );
                }
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

    private static void validateCompleteIndex(
        Set<String> productIds,
        Map<String, ConversionProductEntry> products
    ) throws ConversionRefreshException {
        var missing = new TreeSet<>(productIds);
        missing.removeAll(products.keySet());
        if (missing.isEmpty()) {
            return;
        }

        var sample = missing.stream().limit(LOG_SAMPLE_LIMIT).toList();
        log.warn(
            "Could not complete Bazaar conversion index refresh; {} products are missing NEU display metadata. Sample: {}",
            missing.size(),
            sample
        );
        if (log.isDebugEnabled()) {
            log.debug("Missing Bazaar conversion products: {}", missing);
        }

        throw new ConversionRefreshException(
            ConversionRefreshException.Phase.Validate,
            "Built conversion index is incomplete: missing " + missing.size() + " products"
        );
    }

    private static Optional<ConversionProductEntry> resolveEntry(
        ZipFile zip,
        Map<String, ZipEntry> entriesBySuffix,
        Map<String, String> stockIds,
        String productId
    ) throws IOException {
        var directItem = entriesBySuffix.get("items/" + productId + ".json");
        if (directItem != null) {
            return entryFromItem(zip, directItem, productId, productId)
                .or(() -> derivedEnchantmentEntry(productId));
        }

        var stockNeuId = stockIds.get(productId);
        if (stockNeuId != null && !stockNeuId.isBlank()) {
            return entryFromNeuId(zip, entriesBySuffix, productId, stockNeuId);
        }

        var aliasNeuId = STATIC_NEU_ALIASES.get(productId);
        if (aliasNeuId != null) {
            return entryFromNeuId(zip, entriesBySuffix, productId, aliasNeuId);
        }

        return derivedEnchantmentEntry(productId);
    }

    private static Optional<ConversionProductEntry> entryFromNeuId(
        ZipFile zip,
        Map<String, ZipEntry> entriesBySuffix,
        String productId,
        String neuId
    ) throws IOException {
        var itemEntry = entriesBySuffix.get("items/" + neuId + ".json");
        if (itemEntry == null) {
            return derivedEnchantmentEntry(productId);
        }

        return entryFromItem(zip, itemEntry, productId, neuId)
            .or(() -> derivedEnchantmentEntry(productId));
    }

    private static Optional<ConversionProductEntry> entryFromItem(
        ZipFile zip,
        ZipEntry itemEntry,
        String productId,
        String neuId
    ) throws IOException {
        return readNeuFormattedName(zip, itemEntry)
            .map(formattedName -> new ConversionProductEntry(
                formattedName,
                new ProductNameSource.Neu(neuId)
            ));
    }

    static Optional<ConversionProductEntry> derivedEnchantmentEntry(String productId) {
        return deriveEnchantmentDisplayName(productId)
            .map(name -> new ConversionProductEntry(name, new ProductNameSource.Derived()));
    }

    static Optional<String> deriveEnchantmentDisplayName(String productId) {
        if (productId == null || !productId.startsWith("ENCHANTMENT_")) {
            return Optional.empty();
        }

        var withoutPrefix = productId.substring("ENCHANTMENT_".length());
        var delimiter = withoutPrefix.lastIndexOf('_');
        if (delimiter <= 0 || delimiter == withoutPrefix.length() - 1) {
            return Optional.empty();
        }

        var levelText = withoutPrefix.substring(delimiter + 1);
        if (levelText.chars().anyMatch(ch -> !Character.isDigit(ch))) {
            return Optional.empty();
        }

        int level;
        try {
            level = Integer.parseInt(levelText);
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }

        if (level < 0 || level > 3999) {
            return Optional.empty();
        }

        var name = Utils.titleCase(withoutPrefix.substring(0, delimiter).replace('_', ' '));
        var formattedLevel = level == 0 ? "0" : Utils.intToRoman(level);
        return Optional.of(name + " " + formattedLevel);
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

    private static Optional<String> readNeuFormattedName(ZipFile zip, ZipEntry entry) throws IOException {
        try (Reader reader = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) {
            var item = GSON.fromJson(reader, JsonObject.class);
            if (item == null) {
                return Optional.empty();
            }

            var displayName = item.has("displayname")
                ? item.get("displayname").getAsString()
                : "";

            if (!Utils.cleanDisplayName(displayName).equals("Enchanted Book")) {
                return Optional.of(displayName).filter(name -> !Utils.cleanDisplayName(name).isBlank());
            }

            if (!item.has("lore") || !item.get("lore").isJsonArray()) {
                return Optional.empty();
            }

            for (var lineElement : item.getAsJsonArray("lore")) {
                var rawLine = lineElement.getAsString();
                var stripped = Utils.cleanDisplayName(rawLine);
                if (stripped.isBlank() || stripped.equals("Combinable in Anvil")) {
                    continue;
                }
                return Optional.of(formatEnchantedBookName(displayName, rawLine));
            }

            return Optional.empty();
        }
    }

    static String formatEnchantedBookName(String genericBookName, String rawLoreName) {
        var strippedName = Utils.cleanDisplayName(rawLoreName);
        if (strippedName.isBlank()) {
            return "";
        }

        // NEU stores Bazaar enchantment books as a generic "Enchanted Book" item plus the
        // enchantment name in lore. For normal enchantments that lore name is always legacy
        // color code 9, even when the real Bazaar item rarity is common/uncommon/etc.
        // The generic book display name carries that rarity color, so borrow its leading
        // formatting only for the generic blue lore path. Non-blue lore names, mostly
        // ultimate enchantments like Bank/Chimera, carry deliberate formatting and stay as-is.
        if (startsWithLegacyColor(rawLoreName, '9')) {
            var genericPrefix = leadingLegacyFormats(genericBookName);
            return genericPrefix.isEmpty() ? strippedName : genericPrefix + strippedName;
        }

        return rawLoreName;
    }

    private static boolean startsWithLegacyColor(String value, char code) {
        return value != null
            && value.length() >= 2
            && value.charAt(0) == '§'
            && Character.toLowerCase(value.charAt(1)) == Character.toLowerCase(code);
    }

    private static String leadingLegacyFormats(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }

        var prefix = new StringBuilder();
        for (var i = 0; i + 1 < value.length(); i += 2) {
            if (value.charAt(i) != '§' || !isLegacyFormattingCode(value.charAt(i + 1))) {
                break;
            }
            prefix.append(value, i, i + 2);
        }
        return prefix.toString();
    }

    private static boolean isLegacyFormattingCode(char code) {
        return "0123456789abcdefklmnor".indexOf(Character.toLowerCase(code)) >= 0;
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
