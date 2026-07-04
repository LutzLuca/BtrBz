package com.github.lutzluca.btrbz.data.conversions;

import java.util.Optional;

public record ConversionStatus(
    IndexLoadSource activeLoadSource,
    ConversionSourceCounts sourceCounts,
    Optional<String> neuCommit,
    String generatedAt,
    Optional<String> lastSuccessfulRefreshAt,
    Optional<ConversionRefreshException> lastFailure,
    boolean refreshInFlight
) {

    public enum IndexLoadSource {
        LocalCache,
        BundledSeed,
        RemoteRefresh,
        Unavailable
    }

    static ConversionStatus from(
        IndexLoadSource source,
        ConversionIndex index,
        Optional<String> lastSuccessfulRefreshAt,
        Optional<ConversionRefreshException> lastFailure,
        boolean refreshInFlight
    ) {
        return new ConversionStatus(
            source,
            index.sourceCounts(),
            index.neuCommit(),
            index.generatedAt(),
            lastSuccessfulRefreshAt,
            lastFailure,
            refreshInFlight
        );
    }
}
