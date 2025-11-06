package com.github.lutzluca.btrbz.utils;

import org.apache.commons.lang3.tuple.Pair;

public record Position(int x, int y) {

    public static Position from(Pair<Integer, Integer> pos) {
        return new Position(pos.getLeft(), pos.getRight());
    }
}
