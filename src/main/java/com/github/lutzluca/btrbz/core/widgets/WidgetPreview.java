package com.github.lutzluca.btrbz.core.widgets;

import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;
import java.util.Objects;

public record WidgetPreview<D>(D data, WidgetSession session, String placementProfile) {
    public WidgetPreview {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(placementProfile, "placementProfile");
    }
}
