package com.github.lutzluca.btrbz.data.conversions;

public record ConversionSourceCounts(int neu, int derived) {

    public int total() {
        return this.neu + this.derived;
    }
}
