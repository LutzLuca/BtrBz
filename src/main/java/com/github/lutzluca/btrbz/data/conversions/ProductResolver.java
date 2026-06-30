package com.github.lutzluca.btrbz.data.conversions;

import com.github.lutzluca.btrbz.data.ProductIdentity;
import com.github.lutzluca.btrbz.data.ProductRef;
import com.github.lutzluca.btrbz.data.UnresolvedProduct;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.github.lutzluca.btrbz.utils.Utils;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

@Slf4j
final class ProductResolver {

    private final ConversionIndexService service;
    private final Diagnostics diagnostics;

    ProductResolver(ConversionIndexService service) {
        this.service = service;
        this.diagnostics = new Diagnostics();
    }

    ProductIdentity resolveProduct(ItemStack stack) {
        return this.resolveProduct(stack, stack.getHoverName().getString());
    }

    ProductIdentity resolveProduct(ItemStack stack, String displayNameEvidence) {
        var displayName = Utils.cleanDisplayName(displayNameEvidence);
        var rawProductId = Utils.customDataId(stack).orElse(null);
        if (rawProductId != null) {
            var product = this.resolveKnownProductId(rawProductId, displayName);
            if (product.isPresent()) {
                return product.get();
            }
        }

        if (this.isEnchantedBook(stack)) {
            var product = this.resolveEnchantedBook(stack, rawProductId, displayName);
            if (product.isPresent()) {
                return product.get();
            }
        } else if (rawProductId != null) {
            this.diagnostics.unknownCustomDataId(rawProductId, displayName);
            return new UnresolvedProduct(displayName, rawProductId);
        }

        var resolvedName = this.resolveProductName(displayName);
        return resolvedName.isResolved() ? resolvedName : this.resolveStackFallback(stack, rawProductId, displayName);
    }

    ProductIdentity resolveProduct(String rawProductId, String displayName) {
        var cleanedName = Utils.cleanDisplayName(displayName);
        if (rawProductId != null && !rawProductId.isBlank()) {
            var product = this.resolveKnownProductId(rawProductId, cleanedName);
            if (product.isPresent()) {
                return product.get();
            }

            var enchantmentProduct = EnchantedBookIdParser
                .fromRawProductId(rawProductId)
                .flatMap(this.service::productById)
                .or(() -> EnchantedBookIdParser.isGenericBookId(rawProductId)
                    ? this.resolveEnchantedBookDisplayName(cleanedName)
                    : Optional.empty());
            if (enchantmentProduct.isPresent()) {
                return enchantmentProduct.get();
            }

            this.diagnostics.unknownCustomDataId(rawProductId, cleanedName);
            return new UnresolvedProduct(cleanedName, rawProductId);
        }

        return this.resolveProductName(cleanedName);
    }

    ProductIdentity resolveProductName(String displayName) {
        var cleanedName = Utils.cleanDisplayName(displayName);
        var product = this.service.currentIndex().uniqueProductByName(cleanedName);
        if (product.isPresent()) {
            return product.get();
        }

        if (this.service.currentIndex().hasAmbiguousName(cleanedName)) {
            this.diagnostics.ambiguousName(cleanedName);
        } else {
            this.diagnostics.unresolvedName(cleanedName);
        }
        return new UnresolvedProduct(cleanedName, null);
    }

    private Optional<ProductRef> resolveKnownProductId(String rawProductId, String displayName) {
        var product = this.service.productById(rawProductId);
        product.ifPresent(resolved -> this.logNameMismatch(rawProductId, displayName, resolved));
        return product;
    }

    private Optional<ProductRef> resolveEnchantedBook(
        ItemStack stack,
        String rawProductId,
        String displayName
    ) {
        return Optional
            .ofNullable(stack.get(DataComponents.CUSTOM_DATA))
            .map(data -> data.copyTag())
            .flatMap(EnchantedBookIdParser::fromCustomData)
            .flatMap(this.service::productById)
            .or(() -> EnchantedBookIdParser
                .fromRawProductId(rawProductId)
                .flatMap(this.service::productById))
            .or(() -> this.resolveEnchantedBookDisplayName(displayName));
    }

    private Optional<ProductRef> resolveEnchantedBookDisplayName(String displayName) {
        var bookName = EnchantedBookIdParser.stripActionPrefix(displayName);
        return this.service
            .currentIndex()
            .uniqueProductByName(bookName)
            .or(() -> EnchantedBookIdParser
                .canonicalDisplayName(bookName)
                .flatMap(canonicalName -> this.service.currentIndex().uniqueProductByName(canonicalName)))
            .or(() -> EnchantedBookIdParser
                .fromDisplayName(bookName)
                .flatMap(this.service::productById));
    }

    private ProductIdentity resolveStackFallback(ItemStack stack, String rawProductId, String displayName) {
        if (!"Enchanted Book".equals(displayName)) {
            return new UnresolvedProduct(displayName, rawProductId);
        }

        var matches = GameUtils
            .getLore(stack)
            .stream()
            .map(this::resolveProductName)
            .flatMap(identity -> identity.resolvedProduct().stream())
            .distinct()
            .toList();

        return matches.size() == 1
            ? matches.getFirst()
            : new UnresolvedProduct(displayName, rawProductId);
    }

    private boolean isEnchantedBook(ItemStack stack) {
        return stack.getItem() == Items.ENCHANTED_BOOK;
    }

    private void logNameMismatch(String rawProductId, String displayName, ProductRef resolved) {
        if (Utils.normalizeDisplayName(displayName).equals(Utils.normalizeDisplayName(resolved.displayName()))) {
            return;
        }
        this.diagnostics.nameMismatch(rawProductId, displayName, resolved);
    }

    private static final class Diagnostics {

        private final Set<String> loggedKeys = ConcurrentHashMap.newKeySet();

        void unknownCustomDataId(String productId, String displayName) {
            this.logDebugOnce(
                "UNKNOWN_ID|%s|%s".formatted(productId, displayName),
                "Ignoring custom_data id '{}' for display name '{}' because it is not a known Bazaar product",
                productId,
                displayName
            );
        }

        void nameMismatch(String rawProductId, String parsedName, ProductRef resolved) {
            this.logWarnOnce(
                "MISMATCH|%s|%s|%s|%s".formatted(
                    rawProductId,
                    parsedName,
                    resolved.productId(),
                    resolved.displayName()
                ),
                "Resolved Bazaar product id '{}' as {}, but parsed/display name was '{}'",
                rawProductId,
                resolved,
                parsedName
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
            this.logDebugOnce(
                "UNRESOLVED|%s".formatted(displayName),
                "Could not resolve Bazaar product '{}'",
                displayName
            );
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
