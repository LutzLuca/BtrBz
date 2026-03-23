package com.github.lutzluca.btrbz.core;

import com.github.lutzluca.btrbz.data.BazaarData;
import java.util.Objects;

public record ModContext(BazaarData bazaarData) {

    public ModContext {
        bazaarData = Objects.requireNonNull(bazaarData, "bazaarData cannot be null");
    }
}
