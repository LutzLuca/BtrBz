package com.github.lutzluca.btrbz.cache;

import lombok.Getter;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.Nullable;

/** Client-thread-confined revision owned by one semantic state producer. */
@Accessors(fluent = true)
public final class CacheToken {
    @Getter
    private final String name;

    @Getter
    private long revision;

    @Getter
    private @Nullable String lastReason;

    private CacheToken(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Cache token name must not be blank");
        }

        this.name = name;
    }

    public static CacheToken named(String name) {
        return new CacheToken(name);
    }

    public void invalidate(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Invalidation reason must not be blank");
        }
        this.revision++;
        this.lastReason = reason;
    }
}
