package com.github.lutzluca.coflnet;

import java.time.Instant;
import java.util.List;

public record MayorResponse(List<MayorTerm> terms, Instant checkedAt) {
    public MayorResponse {
        terms = List.copyOf(terms);
    }
}
