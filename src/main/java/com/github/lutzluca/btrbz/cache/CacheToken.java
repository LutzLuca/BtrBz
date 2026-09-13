package com.github.lutzluca.btrbz.cache;

import org.jetbrains.annotations.Nullable;

/** Client-thread-confined revision owned by one semantic state producer. */
public final class CacheToken {
    private final String name;
    private long revision;
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

    public String name() {
        return this.name;
    }

    public long revision() {
        return this.revision;
    }

    public @Nullable String lastReason() {
        return this.lastReason;
    }

    public void invalidate(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Invalidation reason must not be blank");
        }
        this.revision++;
        this.lastReason = reason;
    }
}
