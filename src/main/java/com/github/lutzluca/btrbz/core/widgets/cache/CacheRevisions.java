package com.github.lutzluca.btrbz.core.widgets.cache;

import java.util.ArrayList;
import java.util.List;

/** Allocation-free revision matching plus miss-only capture and diagnostics. */
public final class CacheRevisions {
    private CacheRevisions() {
    }

    public static boolean match(long[] captured, CacheDependencies current) {
        var tokens = current.tokens();

        if (captured.length != tokens.size()) {
            return false;
        }

        for (int index = 0; index < captured.length; index++) {
            if (captured[index] != tokens.get(index).revision()) {
                return false;
            }
        }

        return true;
    }

    public static long[] capture(CacheDependencies current) {
        var tokens = current.tokens();
        var captured = new long[tokens.size()];

        for (int index = 0; index < captured.length; index++) {
            captured[index] = tokens.get(index).revision();
        }

        return captured;
    }

    public static List<ChangedDependency> changes(long[] captured, CacheDependencies current) {
        var tokens = current.tokens();
        var changes = new ArrayList<ChangedDependency>();

        for (int index = 0; index < tokens.size(); index++) {
            var token = tokens.get(index);
            long previous = index < captured.length ? captured[index] : Long.MIN_VALUE;
            if (previous != token.revision()) {
                changes.add(new ChangedDependency(
                    token.name(), previous, token.revision(), token.lastReason()
                ));
            }
        }

        return List.copyOf(changes);
    }

    public record ChangedDependency(
        String tokenName,
        long previousRevision,
        long currentRevision,
        @org.jetbrains.annotations.Nullable InvalidationReason reason
    ) {}
}
