package com.github.lutzluca.btrbz.core.widgets.runtime;

import com.github.lutzluca.btrbz.core.widgets.cache.CacheDependencies;
import com.github.lutzluca.btrbz.core.widgets.cache.CacheRevisions;
import com.github.lutzluca.btrbz.core.widgets.layout.WidgetCanvas;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;
import java.util.ArrayList;
import java.util.List;

/** Minecraft-independent stamps used by the host's single prepared cache entry. */
record PreparedCacheStamp(
    long sessionId,
    long sessionContextRevision,
    int canvasX,
    int canvasY,
    int canvasWidth,
    int canvasHeight,
    WidgetHostOptions options,
    String placementProfile,
    long[] dependencyRevisions
) {
    public static PreparedCacheStamp capture(
        WidgetSession session,
        WidgetCanvas canvas,
        WidgetHostOptions options,
        String placementProfile,
        CacheDependencies dependencies
    ) {
        return new PreparedCacheStamp(
            session.id(), session.contextRevision(),
            canvas.x(), canvas.y(), canvas.width(), canvas.height(), options,
            placementProfile, CacheRevisions.capture(dependencies)
        );
    }

    public boolean matches(
        WidgetSession session,
        WidgetCanvas canvas,
        WidgetHostOptions currentOptions,
        String profile,
        CacheDependencies dependencies
    ) {
        return this.sessionId == session.id()
            && this.sessionContextRevision == session.contextRevision()
            && this.canvasX == canvas.x()
            && this.canvasY == canvas.y()
            && this.canvasWidth == canvas.width()
            && this.canvasHeight == canvas.height()
            && this.options.equals(currentOptions)
            && this.placementProfile.equals(profile)
            && CacheRevisions.match(this.dependencyRevisions, dependencies);
    }

    public List<WidgetCacheMissCause> missCauses(
        WidgetSession session,
        WidgetCanvas canvas,
        WidgetHostOptions currentOptions,
        String profile,
        CacheDependencies dependencies
    ) {
        var causes = new ArrayList<WidgetCacheMissCause>();

        if (this.sessionId != session.id()) {
            causes.add(WidgetCacheMissCause.direct("semantic session changed"));
        }

        if (this.sessionContextRevision != session.contextRevision()) {
            causes.add(WidgetCacheMissCause.direct("session context changed"));
        }

        if (this.canvasX != canvas.x() || this.canvasY != canvas.y()) {
            causes.add(WidgetCacheMissCause.direct("canvas origin changed"));
        }

        if (this.canvasWidth != canvas.width() || this.canvasHeight != canvas.height()) {
            causes.add(WidgetCacheMissCause.direct("canvas size changed"));
        }

        if (!this.options.equals(currentOptions)) {
            causes.add(WidgetCacheMissCause.direct("host options changed"));
        }

        if (!this.placementProfile.equals(profile)) {
            causes.add(WidgetCacheMissCause.direct("placement profile changed"));
        }

        CacheRevisions.changes(this.dependencyRevisions, dependencies).stream()
            .map(WidgetCacheMissCause::dependency)
            .forEach(causes::add);

        return List.copyOf(causes);
    }
}
