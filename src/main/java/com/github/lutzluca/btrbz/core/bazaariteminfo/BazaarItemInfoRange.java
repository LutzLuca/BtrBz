package com.github.lutzluca.btrbz.core.bazaariteminfo;

import com.github.lutzluca.coflnet.HistoryRange;

/** Preset ranges exposed by the first version of the item-info screen. */
public enum BazaarItemInfoRange {
    Hour("Hour", HistoryRange.Preset.HOUR),
    Day("Day", HistoryRange.Preset.DAY),
    Week("Week", HistoryRange.Preset.WEEK);

    private final String label;
    private final HistoryRange sdkRange;

    BazaarItemInfoRange(String label, HistoryRange sdkRange) {
        this.label = label;
        this.sdkRange = sdkRange;
    }

    public String label() {
        return this.label;
    }

    public HistoryRange sdkRange() {
        return this.sdkRange;
    }
}
