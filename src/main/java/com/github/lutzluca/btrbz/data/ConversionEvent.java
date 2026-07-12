package com.github.lutzluca.btrbz.data;

import org.jetbrains.annotations.NotNull;

public record ConversionEvent(@NotNull Kind kind, boolean manual, @NotNull String message) {
    public enum Kind {
        LoadFailure,
        RefreshAlreadyRunning,
        RefreshSuccess,
        PersistFailure,
        RefreshFailure
    }
}
