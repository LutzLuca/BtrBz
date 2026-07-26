package com.github.lutzluca.btrbz.widgets.framework;

public record WidgetRenderResult(
    WidgetDefinition<?, ?> definition,
    String placementProfile,
    WidgetBounds bounds,
    int logicalWidth,
    int logicalHeight,
    double scale
) {}
