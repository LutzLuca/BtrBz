package com.github.lutzluca.btrbz.data.conversions;

import lombok.Getter;
import lombok.experimental.Accessors;

public class ConversionRefreshException extends Exception {

    @Getter
    @Accessors(fluent = true)
    private final Phase phase;

    public ConversionRefreshException(Phase phase, String message) {
        super(message);
        this.phase = phase;
    }

    public ConversionRefreshException(Phase phase, String message, Throwable cause) {
        super(message, cause);
        this.phase = phase;
    }

    public String shortMessage() {
        return this.phase + ": " + this.getMessage();
    }

    public enum Phase {
        LoadLocalCache,
        LoadBundledSeed,
        HypixelBazaar,
        NeuCommit,
        NeuZip,
        Parse,
        Validate,
        Persist
    }
}
