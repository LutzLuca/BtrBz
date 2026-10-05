package com.github.lutzluca.coflnet;

import java.time.Instant;
import java.util.List;

public record MayorTerm(Instant start, Instant end, String name, List<MayorPerk> perks) {
    public MayorTerm {
        perks = List.copyOf(perks);
    }
}
