package com.github.lutzluca.coflnet;

import java.net.URI;
import java.util.Objects;

/** A non-successful or malformed response from the Coflnet API. */
public final class CoflnetApiException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final int statusCode;
    private final URI requestUri;
    private final String responseBody;

    CoflnetApiException(String message, int statusCode, URI requestUri, String responseBody) {
        super(message);
        this.statusCode = statusCode;
        this.requestUri = Objects.requireNonNull(requestUri, "requestUri");
        this.responseBody = responseBody;
    }

    CoflnetApiException(String message, int statusCode, URI requestUri, String responseBody, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.requestUri = Objects.requireNonNull(requestUri, "requestUri");
        this.responseBody = responseBody;
    }

    /** HTTP status code, or {@code -1} when no HTTP response was received. */
    public int statusCode() {
        return statusCode;
    }

    public URI requestUri() {
        return requestUri;
    }

    public String responseBody() {
        return responseBody;
    }
}
