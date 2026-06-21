package com.github.lutzluca.btrbz.data.conversions;

import java.util.Optional;
import org.jetbrains.annotations.Nullable;

public record ConversionStatus(
    IndexLoadSource activeLoadSource,
    int productCount,
    ConversionSourceCounts sourceCounts,
    Optional<String> neuCommit,
    String generatedAt,
    @Nullable String lastSuccessfulRefreshAt,
    Optional<ConversionRefreshException> lastFailure,
    boolean refreshInFlight
) {

    static ConversionStatus from(
        IndexLoadSource source,
        ConversionIndex index,
        @Nullable String lastSuccessfulRefreshAt,
        Optional<ConversionRefreshException> lastFailure,
        boolean refreshInFlight
    ) {
        return new ConversionStatus(
            source,
            index.size(),
            index.sourceCounts(),
            index.neuCommit(),
            index.generatedAt(),
            lastSuccessfulRefreshAt,
            lastFailure,
            refreshInFlight
        );
    }
}
