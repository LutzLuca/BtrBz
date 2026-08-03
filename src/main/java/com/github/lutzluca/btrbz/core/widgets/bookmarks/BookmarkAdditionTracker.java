package com.github.lutzluca.btrbz.core.widgets.bookmarks;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/** Detects actual bookmark additions while ignoring the retained view's first snapshot. */
final class BookmarkAdditionTracker {
    private Set<String> previousIds;

    boolean update(Collection<String> productIds) {
        var currentIds = Set.copyOf(productIds);
        boolean added = this.previousIds != null
            && currentIds.stream().anyMatch(id -> !this.previousIds.contains(id));
        this.previousIds = new HashSet<>(currentIds);
        return added;
    }
}
