package com.github.lutzluca.btrbz.core.widgets.config;

import com.github.lutzluca.btrbz.core.widgets.WidgetPlacement;
import java.util.LinkedHashMap;
import java.util.Map;

/** Persisted placement and chrome preferences shared by every BtrBz widget. */
public final class WidgetFrameConfig {
    public boolean enabled = true;
    public Map<String, WidgetPlacement> placements = new LinkedHashMap<>();
    public double scale = 1.0;
    public Integer background = null;

    public WidgetFrameConfig(WidgetPlacement defaultPlacement) {
        this.placements.put("default", defaultPlacement);
    }

    public WidgetFrameConfig(
        WidgetPlacement defaultPlacement,
        String alternateProfile,
        WidgetPlacement alternatePlacement
    ) {
        this(defaultPlacement);
        this.placements.put(alternateProfile, alternatePlacement);
    }
}
