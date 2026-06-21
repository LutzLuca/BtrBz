package com.github.lutzluca.btrbz.data.conversions;

public class ConversionRefreshException extends Exception {

    private final ConversionFailurePhase phase;

    public ConversionRefreshException(ConversionFailurePhase phase, String message) {
        super(message);
        this.phase = phase;
    }

    public ConversionRefreshException(ConversionFailurePhase phase, String message, Throwable cause) {
        super(message, cause);
        this.phase = phase;
    }

    public ConversionFailurePhase phase() {
        return this.phase;
    }

    public String shortMessage() {
        return this.phase + ": " + this.getMessage();
    }
}
