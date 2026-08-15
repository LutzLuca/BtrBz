package com.github.lutzluca.coflnet;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/** Asynchronous access to the public Coflnet bazaar REST API. */
public interface CoflnetBazaarClient extends AutoCloseable {
    URI DEFAULT_BASE_URI = URI.create("https://sky.coflnet.com/api/");

    CompletionStage<Optional<BazaarSnapshot>> snapshot(String itemTag);

    CompletionStage<List<BazaarHistoryPoint>> history(String itemTag, HistoryRange range);

    @Override
    void close();

    static CoflnetBazaarClient create() {
        return builder().build();
    }

    static Builder builder() {
        return new Builder();
    }

    /** Narrow construction surface; alternate base URIs are primarily useful for local tests. */
    final class Builder {
        private URI baseUri = DEFAULT_BASE_URI;

        private Builder() {}

        public Builder baseUri(URI baseUri) {
            this.baseUri = Objects.requireNonNull(baseUri, "baseUri");
            return this;
        }

        public CoflnetBazaarClient build() {
            return DefaultCoflnetBazaarClient.create(baseUri);
        }
    }
}
