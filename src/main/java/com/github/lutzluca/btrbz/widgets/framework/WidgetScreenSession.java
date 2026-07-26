package com.github.lutzluca.btrbz.widgets.framework;

/**
 * Immutable semantic description of the screen/workflow currently hosting widgets.
 * Integrating mods should provide an application-specific implementation rather than
 * retaining mutable screen-history objects in widget callbacks.
 */
public interface WidgetScreenSession {
    String DEFAULT_PLACEMENT_PROFILE = "default";

    long id();

    default String placementProfile() {
        return DEFAULT_PLACEMENT_PROFILE;
    }

    default WidgetCanvas anchorCanvas(WidgetAnchorSpace space, WidgetCanvas screenCanvas) {
        return screenCanvas;
    }
}
