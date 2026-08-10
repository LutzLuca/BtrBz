package com.github.lutzluca.btrbz.core.widgets.cache;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** Immutable, identity-ordered collection of cache tokens. */
public final class CacheDependencies {
    private static final CacheDependencies NONE = new CacheDependencies(List.of());
    private final List<CacheToken> tokens;

    private CacheDependencies(List<CacheToken> tokens) {
        this.tokens = List.copyOf(tokens);
    }

    public static CacheDependencies none() {
        return NONE;
    }

    public static CacheDependencies of(CacheToken... tokens) {
        Objects.requireNonNull(tokens, "tokens");
        var unique = new LinkedHashSet<CacheToken>();

        for (var token : tokens) {
            unique.add(Objects.requireNonNull(token, "token"));
        }

        return unique.isEmpty() ? NONE : new CacheDependencies(List.copyOf(unique));
    }

    public CacheDependencies and(CacheDependencies other) {
        Objects.requireNonNull(other, "other");

        if (other.tokens.isEmpty()) {
            return this;
        }

        if (this.tokens.isEmpty()) {
            return other;
        }

        var combined = new LinkedHashSet<>(this.tokens);
        combined.addAll(other.tokens);

        return new CacheDependencies(List.copyOf(combined));
    }

    public List<CacheToken> tokens() {
        return this.tokens;
    }
}
