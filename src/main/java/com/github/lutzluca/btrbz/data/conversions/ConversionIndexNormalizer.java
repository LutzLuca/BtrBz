package com.github.lutzluca.btrbz.data.conversions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class ConversionIndexNormalizer {

    private static final Set<String> LOGGED_GENERIC_FALLBACKS = ConcurrentHashMap.newKeySet();

    private ConversionIndexNormalizer() { }

    static ConversionIndex normalizeDerivedEntries(ConversionIndex index) {
        var changedExamples = new ArrayList<String>();
        var fallbackExamples = new ArrayList<String>();
        var products = new LinkedHashMap<String, ConversionProductEntry>();

        index.products().forEach((productId, entry) -> {
            if (!(entry.source() instanceof ProductNameSource.Derived)) {
                products.put(productId, entry);
                return;
            }

            var derived = ConversionNameDeriver.deriveDisplayName(productId);
            var normalized = new ConversionProductEntry(derived.displayName(), entry.source());
            products.put(productId, normalized);

            if (!entry.displayName().equals(normalized.displayName()) && changedExamples.size() < 20) {
                changedExamples.add("%s: '%s' -> '%s'".formatted(
                    productId,
                    entry.displayName(),
                    normalized.displayName()
                ));
            }

            if (derived.fallback()
                && fallbackExamples.size() < 20
                && LOGGED_GENERIC_FALLBACKS.add(productId)) {
                fallbackExamples.add("%s -> %s".formatted(productId, normalized.displayName()));
            }
        });

        if (!changedExamples.isEmpty()) {
            log.debug("Recomputed derived conversion names from current rules; sample: {}", changedExamples);
        }
        if (!fallbackExamples.isEmpty()) {
            log.warn("Generic title-case conversion fallback used; sample: {}", fallbackExamples);
        }

        return new ConversionIndex(
            index.schemaVersion(),
            index.generatedAt(),
            index.neuCommit().orElse(null),
            products
        );
    }
}
