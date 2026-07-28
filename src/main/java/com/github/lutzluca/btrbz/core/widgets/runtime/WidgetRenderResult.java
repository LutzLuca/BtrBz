package com.github.lutzluca.btrbz.core.widgets.runtime;

import com.github.lutzluca.btrbz.core.widgets.WidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.layout.WidgetBounds;

public record WidgetRenderResult(
    WidgetDefinition<?, ?, ?> definition,
    String placementProfile,
    WidgetBounds bounds,
    int logicalWidth,
    int logicalHeight,
    double scale
) {}
