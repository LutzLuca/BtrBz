package com.github.lutzluca.btrbz.data.conversions;

import com.github.lutzluca.btrbz.data.ProductRef;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class ResolutionDiagnostics {

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
        this.logDebugOnce(
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
}
