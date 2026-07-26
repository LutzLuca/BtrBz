package com.github.lutzluca.btrbz.widgets.framework;

/** Minimal fallback session for hosts without application-specific screen facts. */
public record EmptyWidgetScreenSession(long id, String placementProfile) implements WidgetScreenSession {
    public EmptyWidgetScreenSession {
        placementProfile = placementProfile == null || placementProfile.isBlank()
            ? DEFAULT_PLACEMENT_PROFILE
            : placementProfile;
    }
}
