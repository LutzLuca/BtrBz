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
            case Once -> "Triggers once. Reminders continue until dismissed.";
            case Repeat -> "After dismissal, waits for the condition to reset and match again.";
        };
    }
}
