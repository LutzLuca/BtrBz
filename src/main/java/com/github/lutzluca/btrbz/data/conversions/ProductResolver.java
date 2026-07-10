package com.github.lutzluca.btrbz.data.conversions;

import com.github.lutzluca.btrbz.data.ProductIdentity;
import com.github.lutzluca.btrbz.data.IndexedProduct;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.github.lutzluca.btrbz.utils.Utils;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

@Slf4j
final class ProductResolver {

    private static final Pattern ESSENCE_NAME = Pattern.compile("^(?<type>[A-Za-z]+) Essence(?: x\\d+)?$");

    private final ConversionIndexService service;
    private final Diagnostics diagnostics;

    ProductResolver(ConversionIndexService service) {
        this.service = service;
        this.diagnostics = new Diagnostics();
    }

    ProductIdentity resolveProduct(ItemStack stack, String displayNameEvidence) {
        return this.resolveProduct(
            stack,
            displayNameEvidence,
            Utils.matchingCustomNameLegacy(stack, displayNameEvidence).orElse(null)
        );
    }

    ProductIdentity resolveProduct(
        ItemStack stack,
        String displayNameEvidence,
        @Nullable String formattedNameEvidence
    ) {
        var displayName = Utils.cleanDisplayName(displayNameEvidence);
        var rawProductId = Utils.customDataId(stack).orElse(null);
        if (rawProductId != null && !this.isEnchantedBook(stack)) {
            var product = this.resolveKnownProductId(rawProductId, displayName);
            if (product.isPresent()) {
                return ProductIdentity.fromIndex(product.get());
            }

            this.diagnostics.unknownCustomDataId(rawProductId, displayName);
            return this.runtime(displayName, rawProductId, formattedNameEvidence);
        }

        if (this.isEnchantedBook(stack)) {
            return this.resolveEnchantedBook(stack, displayName, formattedNameEvidence)
                .orElseGet(() -> this.resolveStackFallback(stack, displayName, formattedNameEvidence));
        }

        if (stack.getItem() == Items.PLAYER_HEAD) {
            var essence = essenceProductId(displayName);
            if (essence.isPresent()) {
                return this.derivedProductIdentity(essence.get(), displayName, formattedNameEvidence, "essence name");
            }
        }

        // Ordinary stacks need custom-data evidence before their name can identify a product.
        return this.runtime(displayName, null, formattedNameEvidence);
    }

    ProductIdentity resolveProduct(String rawProductId, String displayName) {
        var cleanedName = Utils.cleanDisplayName(displayName);
        if (rawProductId != null && !rawProductId.isBlank()) {
            var product = this.resolveKnownProductId(rawProductId, cleanedName);
            if (product.isPresent()) {
                return ProductIdentity.fromIndex(product.get());
            }

            // ENCHANTED_BOOK is only the generic stack id; the enchantment identity comes from name/lore evidence.
            var enchantmentProduct = EnchantedBookIdParser.isGenericBookId(rawProductId)
                ? this.resolveEnchantedBookDisplayName(cleanedName, null)
                : Optional.<ProductIdentity>empty();
            if (enchantmentProduct.isPresent()) {
                return enchantmentProduct.get();
            }

            this.diagnostics.unknownCustomDataId(rawProductId, cleanedName);
            return EnchantedBookIdParser.isGenericBookId(rawProductId)
                ? ProductIdentity.fromName(cleanedName)
                : this.runtime(cleanedName, rawProductId, null);
        }

        return this.resolveProductName(cleanedName);
    }

    ProductIdentity resolveProductName(String displayName) {
        var cleanedName = Utils.cleanDisplayName(displayName);
        var index = this.service.currentIndex();
        var product = index.uniqueProductByName(cleanedName);
        if (product.isPresent()) {
            return ProductIdentity.fromIndex(product.get());
        }

        if (index.hasAmbiguousName(cleanedName)) {
            this.diagnostics.ambiguousName(cleanedName);
        } else {
            this.diagnostics.unresolvedName(cleanedName);
        }
        return ProductIdentity.fromName(cleanedName);
    }

    private Optional<IndexedProduct> resolveKnownProductId(String rawProductId, String displayName) {
        var product = this.service.productById(rawProductId);
        product.ifPresent(resolved -> this.logNameMismatch(rawProductId, displayName, resolved));
        return product;
    }

    private Optional<ProductIdentity> resolveEnchantedBook(
        ItemStack stack,
        String displayName,
        @Nullable String formattedNameEvidence
    ) {
        return this.resolveEnchantedBookFromCustomData(stack)
            .map(id -> this.derivedProductIdentity(id, displayName, formattedNameEvidence, "custom data"))
            .or(() -> this.resolveEnchantedBookDisplayName(displayName, formattedNameEvidence));
    }

    private Optional<String> resolveEnchantedBookFromCustomData(ItemStack stack) {
        return Optional
            .ofNullable(stack.get(DataComponents.CUSTOM_DATA))
            .map(CustomData::copyTag)
            .flatMap(EnchantedBookIdParser::fromCustomData);
    }

    private Optional<ProductIdentity> resolveEnchantedBookDisplayName(
        String displayName,
        @Nullable String formattedNameEvidence
    ) {
        var index = this.service.currentIndex();
        var bookName = EnchantedBookIdParser.stripActionPrefix(displayName);
        var exactName = index.uniqueProductByName(bookName);
        if (exactName.isPresent()) {
            return exactName.map(ProductIdentity::fromIndex);
        }

        var canonicalName = EnchantedBookIdParser
            .canonicalDisplayName(bookName)
            .flatMap(index::uniqueProductByName);
        if (canonicalName.isPresent()) {
            return canonicalName.map(ProductIdentity::fromIndex);
        }

        return EnchantedBookIdParser
            .fromDisplayName(bookName)
            .map(id -> this.service
                .productById(id)
                .map(ProductIdentity::fromIndex)
                .orElseGet(() -> this.runtime(bookName, null, formattedNameEvidence)));
    }

    private ProductIdentity resolveStackFallback(
        ItemStack stack,
        String displayName,
        @Nullable String formattedNameEvidence
    ) {
        if (!"Enchanted Book".equals(displayName)) {
            return this.runtime(displayName, null, formattedNameEvidence);
        }

        var matches = GameUtils
            .getLore(stack)
            .stream()
            .map(line -> this.resolveEnchantedBookDisplayName(line, formattedNameEvidence))
            .flatMap(Optional::stream)
            .distinct()
            .toList();

        return matches.size() == 1
            ? matches.getFirst()
            : this.runtime(displayName, null, formattedNameEvidence);
    }

    private boolean isEnchantedBook(ItemStack stack) {
        return stack.getItem() == Items.ENCHANTED_BOOK;
    }

    private ProductIdentity derivedProductIdentity(
        String bazaarProductId,
        String displayName,
        @Nullable String formattedNameEvidence,
        String source
    ) {
        return this.service.productById(bazaarProductId)
            .map(ProductIdentity::fromIndex)
            .orElseGet(() -> {
                var cleanedName = Utils.cleanDisplayName(displayName);
                this.diagnostics.derivedProductMissingIndex(bazaarProductId, cleanedName, source);
                return this.runtime(cleanedName, bazaarProductId, formattedNameEvidence);
            });
    }

    static Optional<String> essenceProductId(String displayName) {
        if (displayName == null) {
            return Optional.empty();
        }

        var matcher = ESSENCE_NAME.matcher(Utils.cleanDisplayName(displayName));
        return matcher.matches()
            ? Optional.of("ESSENCE_" + matcher.group("type").toUpperCase(Locale.ROOT))
            : Optional.empty();
    }

    private ProductIdentity runtime(
        String displayName,
        @Nullable String bazaarProductId,
        @Nullable String formattedNameEvidence
    ) {
        var cleanedName = Utils.cleanDisplayName(displayName);
        var formattedName = Optional
            .ofNullable(formattedNameEvidence)
            .filter(evidence -> Utils.normalizeDisplayName(evidence).equals(Utils.normalizeDisplayName(cleanedName)))
            .orElse(null);
        return ProductIdentity.fromRuntime(cleanedName, bazaarProductId, formattedName);
    }

    private void logNameMismatch(String rawProductId, String displayName, IndexedProduct resolved) {
        if (Utils.normalizeDisplayName(displayName).equals(Utils.normalizeDisplayName(resolved.strippedName()))) {
            return;
        }
        this.diagnostics.nameMismatch(rawProductId, displayName, resolved);
    }

    private static final class Diagnostics {

        private static final boolean LOG_UNKNOWN_RUNTIME_EVIDENCE = Boolean.getBoolean(
            "btrbz.conversions.logUnknownRuntimeEvidence"
        );

        private final Set<String> loggedKeys = ConcurrentHashMap.newKeySet();

        void unknownCustomDataId(String productId, String displayName) {
            this.logUnknownRuntimeEvidenceOnce(
                "UNKNOWN_ID|%s|%s".formatted(productId, displayName),
                "Observed custom_data id '{}' for display name '{}', but it is not present in the conversion index",
                productId,
                displayName
            );
        }

        void nameMismatch(String rawProductId, String parsedName, IndexedProduct resolved) {
            this.logWarnOnce(
                "MISMATCH|%s|%s|%s|%s".formatted(
                    rawProductId,
                    parsedName,
                    resolved.productId(),
                    resolved.strippedName()
                ),
                "Resolved Bazaar product id '{}' as {}, but parsed/display name was '{}'",
                rawProductId,
                resolved,
                parsedName
            );
        }

        void derivedProductMissingIndex(String productId, String displayName, String source) {
            this.logWarnOnce(
                "DERIVED_PRODUCT_MISSING|%s|%s".formatted(productId, displayName),
                "Derived Bazaar product id '{}' from {} for '{}', but it is not present in the conversion index",
                productId,
                source,
                displayName
            );
        }

        void ambiguousName(String displayName) {
            this.logDebugOnce(
                "AMBIGUOUS|%s".formatted(displayName),
                "Ambiguous Bazaar product display name '{}'; refusing to choose a product id",
                displayName
            );
        }

        void unresolvedName(String displayName) {
            this.logUnknownRuntimeEvidenceOnce(
                "UNRESOLVED|%s".formatted(displayName),
                "Could not resolve Bazaar product '{}'",
                displayName
            );
        }

        private void logUnknownRuntimeEvidenceOnce(String key, String message, Object... args) {
            if (!LOG_UNKNOWN_RUNTIME_EVIDENCE) {
                return;
            }

            this.logDebugOnce(key, message, args);
        }

        private void logDebugOnce(String key, String message, Object... args) {
            if (this.loggedKeys.add(key) && log.isDebugEnabled()) {
                log.debug(message, args);
            }
        }

        private void logWarnOnce(String key, String message, Object... args) {
            if (this.loggedKeys.add(key)) {
                log.warn(message, args);
            }
        }
    }
}
