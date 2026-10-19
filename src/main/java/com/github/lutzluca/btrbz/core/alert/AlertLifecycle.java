package com.github.lutzluca.btrbz.core.alert;

public enum AlertLifecycle {
    Once,
    Repeat;

    public String label() {
        return switch (this) {
            case Once -> "Once";
            case Repeat -> "Repeat after dismissal";
        };
    }

    public String description() {
        return switch (this) {
            case Once -> "Triggers once. Stays in your Inbox until dismissed.";
            case Repeat -> "After dismissal, waits for the condition to reset and match again.";
        };
    }
}
