package com.github.lutzluca.btrbz.data.conversions;

import com.github.lutzluca.btrbz.data.ProductIdentity;
import com.github.lutzluca.btrbz.data.ProductRef;
import com.github.lutzluca.btrbz.data.UnresolvedProduct;
import com.github.lutzluca.btrbz.data.OrderInfoParser;
import com.github.lutzluca.btrbz.utils.Utils;
import net.minecraft.world.item.ItemStack;

final class ProductResolver {

    private final ConversionIndexService service;
    private final ResolutionDiagnostics diagnostics;

    ProductResolver(ConversionIndexService service, ResolutionDiagnostics diagnostics) {
        this.service = service;
        this.diagnostics = diagnostics;
    }

    ProductIdentity resolveProduct(ItemStack stack) {
        var rawProductId = Utils.customDataId(stack);
        if (rawProductId.isPresent()) {
            return this.resolveProduct(rawProductId.get(), stack.getHoverName().getString());
        }

        var resolvedName = this.resolveProductName(stack.getHoverName().getString());
        return resolvedName.isResolved() ? resolvedName : this.resolveStackFallback(stack);
    }

    ProductIdentity resolveProduct(String rawProductId, String displayName) {
        var cleanedName = Utils.cleanDisplayName(displayName);
        if (rawProductId != null && !rawProductId.isBlank()) {
            var product = this.service.productById(rawProductId);
            if (product.isPresent()) {
                this.logNameMismatch(rawProductId, cleanedName, product.get());
                return product.get();
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

    private ProductIdentity resolveStackFallback(ItemStack stack) {
        var displayName = stack.getHoverName().getString();
        if (!"Enchanted Book".equals(displayName)) {
            return new UnresolvedProduct(displayName, null);
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
            : new UnresolvedProduct(displayName, null);
    }

    private void logNameMismatch(String rawProductId, String displayName, ProductRef resolved) {
        if (Utils.normalizeDisplayName(displayName).equals(Utils.normalizeDisplayName(resolved.displayName()))) {
            return;
        }
        this.diagnostics.nameMismatch(rawProductId, displayName, resolved);
    }
}
