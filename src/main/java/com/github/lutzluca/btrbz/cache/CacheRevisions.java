package com.github.lutzluca.btrbz.cache;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/** Allocation-free revision matching plus miss-only capture and diagnostics. */
public final class CacheRevisions {
    private CacheRevisions() {}

    public static boolean match(Snapshot captured, CacheDependencies current) {
        var tokens = current.tokens();

        if (captured.revisions.length != tokens.size()) {
            return false;
        }

        for (int index = 0; index < captured.revisions.length; index++) {
            if (captured.tokens.get(index) != tokens.get(index)
                || captured.revisions[index] != tokens.get(index).revision()) {
                return false;
            }
        }

        return true;
    }

    public static Snapshot capture(CacheDependencies current) {
        var tokens = current.tokens();
        var captured = new long[tokens.size()];

        for (int index = 0; index < captured.length; index++) {
            captured[index] = tokens.get(index).revision();
        }

        return new Snapshot(tokens, captured);
    }

    public static List<ChangedDependency> changes(Snapshot captured, CacheDependencies current) {
        var tokens = current.tokens();
        var changes = new ArrayList<ChangedDependency>();

        for (int index = 0; index < tokens.size(); index++) {
            var token = tokens.get(index);
            boolean sameToken = index < captured.tokens.size()
                && captured.tokens.get(index) == token;
            long previous = sameToken ? captured.revisions[index] : Long.MIN_VALUE;
            if (!sameToken || previous != token.revision()) {
                changes.add(new ChangedDependency(
                    token.name(), previous, token.revision(),
                    sameToken ? token.lastReason() : "dependency added or replaced"));
            }
        }

        for (int index = tokens.size(); index < captured.tokens.size(); index++) {
            changes.add(new ChangedDependency(
                captured.tokens.get(index).name(), captured.revisions[index], Long.MIN_VALUE,
                "dependency removed"));
        }

        return List.copyOf(changes);
    }

    /** Captured identities and revisions; mutable storage is never exposed. */
    public static final class Snapshot {
        private final List<CacheToken> tokens;
        private final long[] revisions;

        private Snapshot(List<CacheToken> tokens, long[] revisions) {
            this.tokens = tokens;
            this.revisions = revisions;
        }
    }

    public record ChangedDependency(
        String tokenName,
        long previousRevision,
        long currentRevision,
        @Nullable String reason
    ) {}
}
