package com.github.lutzluca.btrbz.data.conversions;

import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.github.lutzluca.btrbz.data.OrderInfoParser;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import com.github.lutzluca.btrbz.data.ProductRef;
import com.github.lutzluca.btrbz.data.UnresolvedProduct;
import com.github.lutzluca.btrbz.utils.Utils;

final class ProductResolver {

    private final ConversionIndexService service;
    private final ResolutionDiagnostics diagnostics;

    ProductResolver(ConversionIndexService service, ResolutionDiagnostics diagnostics) {
        this.service = service;
        this.diagnostics = diagnostics;
    }

    ProductIdentity resolveProduct(ItemStack stack) {
        var displayName = Utils.cleanDisplayName(stack.getHoverName().getString());
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
        return resolvedName.isResolved() ? resolvedName : this.resolveStackFallback(stack, rawProductId);
    }

    ProductIdentity resolveProduct(String rawProductId, String displayName) {
        var cleanedName = Utils.cleanDisplayName(displayName);
        if (rawProductId != null && !rawProductId.isBlank()) {
            var product = this.resolveKnownProductId(rawProductId, cleanedName);
            if (product.isPresent()) {
                return product.get();
            }

            var enchantmentProduct = EnchantedBookProductIds
                .fromRawProductId(rawProductId)
                .flatMap(this.service::productById)
                .or(() -> EnchantedBookProductIds.isGenericBookId(rawProductId)
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
            .flatMap(EnchantedBookProductIds::fromCustomData)
            .flatMap(this.service::productById)
            .or(() -> EnchantedBookProductIds
                .fromRawProductId(rawProductId)
                .flatMap(this.service::productById))
            .or(() -> this.resolveEnchantedBookDisplayName(displayName));
    }

    private Optional<ProductRef> resolveEnchantedBookDisplayName(String displayName) {
        var bookName = EnchantedBookProductIds.stripActionPrefix(displayName);
        return this.service
            .currentIndex()
            .uniqueProductByName(bookName)
            .or(() -> EnchantedBookProductIds
                .canonicalDisplayName(bookName)
                .flatMap(canonicalName -> this.service.currentIndex().uniqueProductByName(canonicalName)))
            .or(() -> EnchantedBookProductIds
                .fromDisplayName(bookName)
                .flatMap(this.service::productById));
    }

    private ProductIdentity resolveStackFallback(ItemStack stack, String rawProductId) {
        var displayName = Utils.cleanDisplayName(stack.getHoverName().getString());
        if (!"Enchanted Book".equals(displayName)) {
            return new UnresolvedProduct(displayName, rawProductId);
        }

        var matches = OrderInfoParser
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
}
