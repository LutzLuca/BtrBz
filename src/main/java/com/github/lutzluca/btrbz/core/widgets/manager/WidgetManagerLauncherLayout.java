package com.github.lutzluca.btrbz.core.widgets.manager;

import com.github.lutzluca.btrbz.core.widgets.layout.WidgetBounds;
import com.github.lutzluca.btrbz.core.widgets.layout.WidgetCanvas;
import com.github.lutzluca.btrbz.core.widgets.layout.WidgetPlacement;
import com.github.lutzluca.btrbz.core.widgets.layout.WidgetScaleResolver;

record WidgetManagerLauncherLayout(WidgetBounds localBounds, WidgetBounds screenBounds, double scale) {
    static WidgetManagerLauncherLayout resolve(
        WidgetCanvas canvas,
        WidgetPlacement placement,
        int logicalSize,
        double requestedScale
    ) {
        double scale = WidgetScaleResolver.fitToCanvas(
            Math.max(requestedScale, WidgetScaleResolver.MIN_SCALE),
            WidgetScaleResolver.MIN_SCALE,
            canvas.width(),
            canvas.height(),
            logicalSize,
            logicalSize
        );
        int physicalSize = Math.max(1, (int) Math.ceil(logicalSize * scale));
        var localBounds = placement.resolve(
            canvas.width(), canvas.height(), physicalSize, physicalSize
        );
        var screenBounds = new WidgetBounds(
            canvas.x() + localBounds.x(),
            canvas.y() + localBounds.y(),
            localBounds.width(),
            localBounds.height()
        );
        return new WidgetManagerLauncherLayout(localBounds, screenBounds, scale);
    }
}
