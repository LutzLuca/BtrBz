package com.github.lutzluca.btrbz.data.conversions;

public record ConversionSourceCounts(int hypixelItem, int neu, int derived) {

    public int total() {
        return this.hypixelItem + this.neu + this.derived;
    }
}
