package com.github.lutzluca.btrbz.data.conversions;

/** Legacy NEU item data used to construct a modern stack when no compatible overlay exists. */
public record LegacyProductStackData(String itemId, int damage, String nbtTag) {

    public LegacyProductStackData {
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("itemId must not be blank");
        }
        if (damage < Short.MIN_VALUE || damage > Short.MAX_VALUE) {
            throw new IllegalArgumentException("damage must fit in a legacy short");
        }
        if (nbtTag == null || nbtTag.isBlank()) {
            throw new IllegalArgumentException("nbtTag must not be blank");
        }
        itemId = itemId.trim();
        nbtTag = nbtTag.trim();
    }
}
