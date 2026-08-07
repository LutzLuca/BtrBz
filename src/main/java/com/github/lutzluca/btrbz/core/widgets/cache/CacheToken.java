package com.github.lutzluca.btrbz.core.widgets.cache;

import java.util.Objects;
import org.jetbrains.annotations.Nullable;

/** Client-thread-confined revision owned by one semantic state producer. */
public final class CacheToken {
    private final String name;
    private long revision;
    private @Nullable InvalidationReason lastReason;

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

    public @Nullable InvalidationReason lastReason() {
        return this.lastReason;
    }

    public void invalidate(InvalidationReason reason) {
        this.revision++;
        this.lastReason = Objects.requireNonNull(reason, "reason");
    }
}
