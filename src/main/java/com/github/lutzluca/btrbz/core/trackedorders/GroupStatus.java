package com.github.lutzluca.btrbz.core.trackedorders;

public sealed interface GroupStatus {

    // No grouped "best" state: only one equal-price order gets filled first, and the UI does not distinguish it per order
    record Undercut(double amount) implements GroupStatus { }

    record Matched() implements GroupStatus { }

    record SelfMatched(int orderCount) implements GroupStatus { }
}
