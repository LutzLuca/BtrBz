package com.github.lutzluca.btrbz.core.widgets;

import com.github.lutzluca.btrbz.core.widgets.config.WidgetConfigBinding;
import com.github.lutzluca.btrbz.core.widgets.config.WidgetConfigHandle;
import com.github.lutzluca.btrbz.core.widgets.config.WidgetFrameConfig;
import com.github.lutzluca.btrbz.core.widgets.cache.CacheDependencies;
import com.github.lutzluca.btrbz.core.widgets.cache.CacheToken;
import com.github.lutzluca.btrbz.core.widgets.cache.WidgetDataSource;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;
import io.wispforest.owo.ui.core.UIComponent;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

/** The complete typed recipe for one BtrBz widget. */
@Getter
public final class WidgetDefinition<D, C, A> {
    private final WidgetId id;
    private final String displayName;
    private final WidgetConfigHandle<C> configHandle;
    private final Predicate<WidgetSession> supports;
    private final WidgetVisibility<D, C> visibility;
    private final WidgetDataSource<D> dataSource;
    private final boolean preparedCacheEnabled;
    private final CacheDependencies cacheDependencies;
    private final Supplier<WidgetPreview<D>> preview;
    private final Supplier<WidgetView<D, C, A>> viewFactory;
    private final Function<WidgetConfigBinding<C>, UIComponent> settingsPanel;
    private final WidgetActionHandler<A> actionHandler;
    private final Map<String, String> placementProfiles;
    private final Function<WidgetSession, String> placementProfileResolver;
    private final int minWidth;
    private final int minHeight;

    private WidgetDefinition(Builder<D, C, A> builder) {
        this.id = Objects.requireNonNull(builder.id, "id");
        this.displayName = Objects.requireNonNull(builder.displayName, "displayName");
        this.configHandle = Objects.requireNonNull(builder.configHandle, "configHandle");
        this.supports = Objects.requireNonNull(builder.supports, "supports");
        this.visibility = Objects.requireNonNull(builder.visibility, "visibility");
        this.dataSource = Objects.requireNonNull(builder.dataSource, "dataSource");
        this.preparedCacheEnabled = builder.preparedCacheEnabled;
        this.cacheDependencies = this.dataSource.cacheDependencies()
            .and(CacheDependencies.of(this.configHandle.contentChanges()))
            .and(builder.additionalDependencies);
        this.preview = Objects.requireNonNull(builder.preview, "preview");
        this.viewFactory = Objects.requireNonNull(builder.viewFactory, "viewFactory");
        this.settingsPanel = Objects.requireNonNull(builder.settingsPanel, "settingsPanel");
        this.actionHandler = Objects.requireNonNull(builder.actionHandler, "actionHandler");
        this.placementProfiles = Collections.unmodifiableMap(new LinkedHashMap<>(builder.placementProfiles));
        this.placementProfileResolver = Objects.requireNonNull(
            builder.placementProfileResolver, "placementProfileResolver"
        );
        this.minWidth = Math.max(1, builder.minWidth);
        this.minHeight = Math.max(1, builder.minHeight);
    }

    public static <D, C, A> Builder<D, C, A> builder(WidgetId id, String displayName) {
        return new Builder<>(id, displayName);
    }

    public C config() { return this.configHandle.current(); }
    public C defaults() { return this.configHandle.defaults(); }
    public WidgetFrameConfig frame() { return this.configHandle.frame(); }
    public WidgetFrameConfig defaultFrame() { return this.configHandle.defaultFrame(); }
    public boolean supports(WidgetSession session) { return this.supports.test(session); }
    public WidgetPreview<D> captureRuntimePreview(WidgetSession session) {
        if (!this.supports(session)) {
            throw new IllegalArgumentException("Widget does not support this session: " + this.id);
        }
        var data = Objects.requireNonNull(this.dataSource.snapshot(session), "runtime widget data");
        return new WidgetPreview<>(data, session, this.placementProfile(session));
    }
    public boolean isVisible(WidgetPreview<D> preview) {
        return this.visibility.test(preview.data(), this.config(), preview.session());
    }
    public List<String> placementProfileKeys() { return List.copyOf(this.placementProfiles.keySet()); }
    public String placementProfileLabel(String profile) {
        return this.placementProfiles.getOrDefault(profile, this.placementProfiles.get("default"));
    }
    public String placementProfile(WidgetSession session) {
        var profile = this.placementProfileResolver.apply(session);
        return this.placementProfiles.containsKey(profile) ? profile : "default";
    }

    public WidgetConfigBinding<C> binding(Runnable changed) {
        return new WidgetConfigBinding<>(this.configHandle, changed);
    }

    public UIComponent settingsPanel(Runnable changed) {
        return this.settingsPanel.apply(this.binding(changed));
    }

    private static <A> WidgetActionHandler<A> noOpHandler() {
        return (action, source, current) -> {};
    }

    public static final class Builder<D, C, A> {
        private final WidgetId id;
        private final String displayName;
        private WidgetConfigHandle<C> configHandle;
        private Predicate<WidgetSession> supports = _ -> true;
        private WidgetVisibility<D, C> visibility = (data, config, session) -> true;
        private WidgetDataSource<D> dataSource;
        private boolean preparedCacheEnabled;
        private CacheDependencies additionalDependencies = CacheDependencies.none();
        private Supplier<WidgetPreview<D>> preview;
        private Supplier<WidgetView<D, C, A>> viewFactory;
        private Function<WidgetConfigBinding<C>, UIComponent> settingsPanel = _ -> null;
        private WidgetActionHandler<A> actionHandler = noOpHandler();
        private final Map<String, String> placementProfiles = new LinkedHashMap<>();
        private Function<WidgetSession, String> placementProfileResolver = WidgetSession::placementProfile;
        private int minWidth = 48;
        private int minHeight = 16;

        private Builder(WidgetId id, String displayName) {
            this.id = id;
            this.displayName = displayName;
            this.placementProfiles.put("default", "Default");
        }

        public Builder<D, C, A> config(WidgetConfigHandle<C> configHandle) {
            this.configHandle = configHandle;
            return this;
        }

        public Builder<D, C, A> supports(Predicate<WidgetSession> supports) {
            this.supports = supports;
            return this;
        }
        public Builder<D, C, A> visibility(WidgetVisibility<D, C> visibility) {
            this.visibility = visibility;
            return this;
        }
        public Builder<D, C, A> data(WidgetDataSource<D> dataSource) {
            this.dataSource = dataSource;
            return this;
        }

        public Builder<D, C, A> cachePrepared(CacheToken... additionalDependencies) {
            this.preparedCacheEnabled = true;
            this.additionalDependencies = CacheDependencies.of(additionalDependencies);
            return this;
        }

        public Builder<D, C, A> preview(Supplier<WidgetPreview<D>> preview) {
            this.preview = preview;
            return this;
        }
        public Builder<D, C, A> viewFactory(Supplier<WidgetView<D, C, A>> viewFactory) {
            this.viewFactory = viewFactory;
            return this;
        }
        public Builder<D, C, A> settingsPanel(Function<WidgetConfigBinding<C>, UIComponent> settingsPanel) {
            this.settingsPanel = settingsPanel;
            return this;
        }
        public Builder<D, C, A> actionHandler(WidgetActionHandler<A> actionHandler) {
            this.actionHandler = actionHandler;
            return this;
        }
        public Builder<D, C, A> placementProfile(String key, String label) {
            this.placementProfiles.put(key, label);
            return this;
        }
        public Builder<D, C, A> placementProfileResolver(Function<WidgetSession, String> resolver) {
            this.placementProfileResolver = resolver;
            return this;
        }
        public Builder<D, C, A> minSize(int width, int height) {
            this.minWidth = width;
            this.minHeight = height;
            return this;
        }
        public WidgetDefinition<D, C, A> build() { return new WidgetDefinition<>(this); }
    }
}
