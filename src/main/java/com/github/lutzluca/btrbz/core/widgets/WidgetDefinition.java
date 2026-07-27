package com.github.lutzluca.btrbz.core.widgets;

import com.github.lutzluca.btrbz.core.widgets.config.WidgetConfigBinding;
import com.github.lutzluca.btrbz.core.widgets.config.WidgetFrameConfig;
import com.github.lutzluca.btrbz.core.widgets.config.WidgetPreferenceReset;
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

/** The complete typed recipe for one BtrBz widget. */
@Getter
public final class WidgetDefinition<D, C, A> {
    private final WidgetId id;
    private final String displayName;
    private final Supplier<C> currentConfig;
    private final Supplier<C> freshDefaults;
    private final Function<C, WidgetFrameConfig> frameConfig;
    private final WidgetPreferenceReset<C> resetPreferences;
    private final Predicate<WidgetSession> supports;
    private final WidgetVisibility<D, C> visibility;
    private final Function<WidgetSession, D> runtimeData;
    private final Supplier<WidgetPreview<D>> preview;
    private final Supplier<WidgetView<D, C, A>> viewFactory;
    private final Function<WidgetConfigBinding<C>, UIComponent> settingsPanel;
    private final WidgetActionHandler<A> actionHandler;
    private final Map<String, String> placementProfiles;
    private final Function<WidgetSession, String> placementProfileResolver;
    private final WidgetAnchorSpace anchorSpace;
    private final int minWidth;
    private final int minHeight;

    private WidgetDefinition(Builder<D, C, A> builder) {
        this.id = Objects.requireNonNull(builder.id, "id");
        this.displayName = Objects.requireNonNull(builder.displayName, "displayName");
        this.currentConfig = Objects.requireNonNull(builder.currentConfig, "currentConfig");
        this.freshDefaults = Objects.requireNonNull(builder.freshDefaults, "freshDefaults");
        this.frameConfig = Objects.requireNonNull(builder.frameConfig, "frameConfig");
        this.resetPreferences = Objects.requireNonNull(builder.resetPreferences, "resetPreferences");
        this.supports = Objects.requireNonNull(builder.supports, "supports");
        this.visibility = Objects.requireNonNull(builder.visibility, "visibility");
        this.runtimeData = Objects.requireNonNull(builder.runtimeData, "runtimeData");
        this.preview = Objects.requireNonNull(builder.preview, "preview");
        this.viewFactory = Objects.requireNonNull(builder.viewFactory, "viewFactory");
        this.settingsPanel = Objects.requireNonNull(builder.settingsPanel, "settingsPanel");
        this.actionHandler = Objects.requireNonNull(builder.actionHandler, "actionHandler");
        this.placementProfiles = Collections.unmodifiableMap(new LinkedHashMap<>(builder.placementProfiles));
        this.placementProfileResolver = Objects.requireNonNull(
            builder.placementProfileResolver, "placementProfileResolver"
        );
        this.anchorSpace = Objects.requireNonNull(builder.anchorSpace, "anchorSpace");
        this.minWidth = Math.max(1, builder.minWidth);
        this.minHeight = Math.max(1, builder.minHeight);
    }

    public static <D, C, A> Builder<D, C, A> builder(WidgetId id, String displayName) {
        return new Builder<>(id, displayName);
    }

    public C config() { return Objects.requireNonNull(this.currentConfig.get(), "current widget config"); }
    public C defaults() { return Objects.requireNonNull(this.freshDefaults.get(), "fresh widget defaults"); }
    public WidgetFrameConfig frame() { return this.frameConfig.apply(this.config()); }
    public WidgetFrameConfig defaultFrame() { return this.frameConfig.apply(this.defaults()); }
    public boolean supports(WidgetSession session) { return this.supports.test(session); }
    public List<String> placementProfileKeys() { return List.copyOf(this.placementProfiles.keySet()); }
    public String placementProfileLabel(String profile) {
        return this.placementProfiles.getOrDefault(profile, this.placementProfiles.get("default"));
    }
    public String placementProfile(WidgetSession session) {
        var profile = this.placementProfileResolver.apply(session);
        return this.placementProfiles.containsKey(profile) ? profile : "default";
    }
    public WidgetConfigBinding<C> binding(Runnable changed) {
        return new WidgetConfigBinding<>(
            this.currentConfig, this.freshDefaults, this.frameConfig, this.resetPreferences, changed
        );
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
        private Supplier<C> currentConfig;
        private Supplier<C> freshDefaults;
        private Function<C, WidgetFrameConfig> frameConfig;
        private WidgetPreferenceReset<C> resetPreferences = (current, defaults) -> {};
        private Predicate<WidgetSession> supports = _ -> true;
        private WidgetVisibility<D, C> visibility = (data, config, session) -> true;
        private Function<WidgetSession, D> runtimeData;
        private Supplier<WidgetPreview<D>> preview;
        private Supplier<WidgetView<D, C, A>> viewFactory;
        private Function<WidgetConfigBinding<C>, UIComponent> settingsPanel = _ -> null;
        private WidgetActionHandler<A> actionHandler = noOpHandler();
        private final Map<String, String> placementProfiles = new LinkedHashMap<>();
        private Function<WidgetSession, String> placementProfileResolver = WidgetSession::placementProfile;
        private WidgetAnchorSpace anchorSpace = WidgetAnchorSpace.Screen;
        private int minWidth = 48;
        private int minHeight = 16;

        private Builder(WidgetId id, String displayName) {
            this.id = id;
            this.displayName = displayName;
            this.placementProfiles.put("default", "Default");
        }

        public Builder<D, C, A> config(
            Supplier<C> currentConfig,
            Supplier<C> freshDefaults,
            Function<C, WidgetFrameConfig> frameConfig,
            WidgetPreferenceReset<C> resetPreferences
        ) {
            this.currentConfig = currentConfig;
            this.freshDefaults = freshDefaults;
            this.frameConfig = frameConfig;
            this.resetPreferences = resetPreferences;
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
        public Builder<D, C, A> runtimeData(Function<WidgetSession, D> runtimeData) {
            this.runtimeData = runtimeData;
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
        public Builder<D, C, A> anchorSpace(WidgetAnchorSpace anchorSpace) {
            this.anchorSpace = anchorSpace;
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
