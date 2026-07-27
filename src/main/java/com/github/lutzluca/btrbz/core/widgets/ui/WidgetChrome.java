package com.github.lutzluca.btrbz.core.widgets.ui;

import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;

public final class WidgetChrome {
    public static final int DEFAULT_BACKGROUND = 0x840C0C0C;
    static final int CORNER_RADIUS = 5;

    private WidgetChrome() {}

    public static UIComponent wrap(UIComponent content) {
        FlowLayout layout = UIContainers.verticalFlow(Sizing.content(), Sizing.content());
        layout.padding(Insets.both(
            WidgetLayoutTokens.PANEL_HORIZONTAL_PADDING,
            WidgetLayoutTokens.PANEL_VERTICAL_PADDING
        ));
        layout.allowOverflow(true);
        layout.child(content);
        return layout;
    }
}
