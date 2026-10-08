package com.github.lutzluca.coflnet;

import java.net.URI;

public final class CoflnetException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public enum Kind {
        HTTP, TRANSPORT, PARSING, BODY_LIMIT, QUEUE_FULL, CLOSED
    }

    private final Kind kind;
    private final URI endpoint;
    private final int status;

    public CoflnetException(Kind kind, URI endpoint, int status, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
        this.endpoint = endpoint;
        this.status = status;
    }

    public Kind kind() {
        return this.kind;
    }

    public URI endpoint() {
        return this.endpoint;
    }

    public int status() {
        return this.status;
    }
}
