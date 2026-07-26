package com.github.lutzluca.btrbz.widgets.framework;

import io.wispforest.owo.ui.core.UIComponent;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class WidgetDefinition<T, A> {
    private final WidgetId id;
    private final String displayName;
    private final Map<String, WidgetPlacement> defaultPlacements;
    private final boolean defaultActive;
    private final Function<WidgetRenderContext, T> dataProvider;
    private final Function<WidgetRenderContext, T> previewDataProvider;
    private final Predicate<T> dataDisplayPredicate;
    private final BiFunction<T, WidgetBuildContext<A>, UIComponent> componentFactory;
    private final WidgetActionHandler<A> actionHandler;
    private final Predicate<WidgetRenderContext> displayPredicate;
    private final Supplier<? extends UIComponent> configurationPanel;
    private final WidgetAnchorSpace anchorSpace;
    private final Function<WidgetRenderContext, String> placementProfileResolver;
    private final int minWidth;
    private final int minHeight;

    private WidgetDefinition(Builder<T, A> builder) {
        this.id = Objects.requireNonNull(builder.id, "id");
        this.displayName = Objects.requireNonNull(builder.displayName, "displayName");
        this.defaultPlacements = Collections.unmodifiableMap(
            new LinkedHashMap<>(builder.defaultPlacements)
        );
        this.defaultActive = builder.defaultActive;
        this.dataProvider = Objects.requireNonNull(builder.dataProvider, "dataProvider");
        this.previewDataProvider = Objects.requireNonNull(builder.previewDataProvider, "previewDataProvider");
        this.dataDisplayPredicate = Objects.requireNonNull(builder.dataDisplayPredicate, "dataDisplayPredicate");
        this.componentFactory = Objects.requireNonNull(builder.componentFactory, "componentFactory");
        this.actionHandler = Objects.requireNonNull(builder.actionHandler, "actionHandler");
        this.displayPredicate = Objects.requireNonNull(builder.displayPredicate, "displayPredicate");
        this.configurationPanel = Objects.requireNonNull(builder.configurationPanel, "configurationPanel");
        this.anchorSpace = Objects.requireNonNull(builder.anchorSpace, "anchorSpace");
        this.placementProfileResolver = Objects.requireNonNull(
            builder.placementProfileResolver,
            "placementProfileResolver"
        );
        this.minWidth = Math.max(1, builder.minWidth);
        this.minHeight = Math.max(1, builder.minHeight);
    }

    public static <T, A> Builder<T, A> builder(WidgetId id, String displayName) {
        return new Builder<>(id, displayName);
    }

    public static <T> Builder<T, Void> readOnlyBuilder(
        WidgetId id,
        String displayName
    ) {
        return WidgetDefinition.builder(id, displayName);
    }

    private static <A> WidgetActionHandler<A> noOpActionHandler() {
        return (action, sourceSession, currentSession) -> {};
    }

    public WidgetId id() { return this.id; }
    public String displayName() { return this.displayName; }
    public boolean defaultActive() { return this.defaultActive; }
    public Function<WidgetRenderContext, T> dataProvider() { return this.dataProvider; }
    public Function<WidgetRenderContext, T> previewDataProvider() { return this.previewDataProvider; }
    public Predicate<T> dataDisplayPredicate() { return this.dataDisplayPredicate; }
    public BiFunction<T, WidgetBuildContext<A>, UIComponent> componentFactory() { return this.componentFactory; }
    public WidgetActionHandler<A> actionHandler() { return this.actionHandler; }
    public Predicate<WidgetRenderContext> displayPredicate() { return this.displayPredicate; }
    public UIComponent configurationPanel() { return this.configurationPanel.get(); }
    public WidgetAnchorSpace anchorSpace() { return this.anchorSpace; }
    public int minWidth() { return this.minWidth; }
    public int minHeight() { return this.minHeight; }

    public List<String> placementProfiles() {
        return List.copyOf(this.defaultPlacements.keySet());
    }

    public WidgetPlacement defaultPlacement(String profile) {
        return this.defaultPlacements.getOrDefault(
            profile,
            this.defaultPlacements.get(WidgetScreenSession.DEFAULT_PLACEMENT_PROFILE)
        );
    }

    public String placementProfile(WidgetRenderContext context) {
        var profile = this.placementProfileResolver.apply(context);
        return this.defaultPlacements.containsKey(profile)
            ? profile
            : WidgetScreenSession.DEFAULT_PLACEMENT_PROFILE;
    }

    public static final class Builder<T, A> {
        private final WidgetId id;
        private final String displayName;
        private final Map<String, WidgetPlacement> defaultPlacements = new LinkedHashMap<>();
        private boolean defaultActive = true;
        private Function<WidgetRenderContext, T> dataProvider;
        private Function<WidgetRenderContext, T> previewDataProvider;
        private Predicate<T> dataDisplayPredicate = ignored -> true;
        private BiFunction<T, WidgetBuildContext<A>, UIComponent> componentFactory;
        private WidgetActionHandler<A> actionHandler = noOpActionHandler();
        private Predicate<WidgetRenderContext> displayPredicate = ignored -> true;
        private Supplier<? extends UIComponent> configurationPanel = () -> null;
        private WidgetAnchorSpace anchorSpace = WidgetAnchorSpace.SCREEN;
        private Function<WidgetRenderContext, String> placementProfileResolver = context ->
            context.session().placementProfile();
        private int minWidth = 48;
        private int minHeight = 16;

        private Builder(WidgetId id, String displayName) {
            this.id = id;
            this.displayName = displayName;
            this.defaultPlacements.put(
                WidgetScreenSession.DEFAULT_PLACEMENT_PROFILE,
                WidgetPlacement.topLeft(0.0, 0.0)
            );
        }

        public Builder<T, A> defaultPlacement(WidgetPlacement placement) {
            return this.defaultPlacement(WidgetScreenSession.DEFAULT_PLACEMENT_PROFILE, placement);
        }

        public Builder<T, A> defaultPlacement(String profile, WidgetPlacement placement) {
            this.defaultPlacements.put(profile, placement);
            return this;
        }

        public Builder<T, A> defaultActive(boolean defaultActive) {
            this.defaultActive = defaultActive;
            return this;
        }

        public Builder<T, A> dataProvider(Function<WidgetRenderContext, T> dataProvider) {
            this.dataProvider = dataProvider;
            return this;
        }

        public Builder<T, A> previewDataProvider(Function<WidgetRenderContext, T> previewDataProvider) {
            this.previewDataProvider = previewDataProvider;
            return this;
        }

        public Builder<T, A> displayWhenData(Predicate<T> dataDisplayPredicate) {
            this.dataDisplayPredicate = dataDisplayPredicate;
            return this;
        }

        public Builder<T, A> componentFactory(
            BiFunction<T, WidgetBuildContext<A>, UIComponent> componentFactory
        ) {
            this.componentFactory = componentFactory;
            return this;
        }

        public Builder<T, A> actionHandler(WidgetActionHandler<A> actionHandler) {
            this.actionHandler = actionHandler;
            return this;
        }

        public Builder<T, A> displayWhen(Predicate<WidgetRenderContext> displayPredicate) {
            this.displayPredicate = displayPredicate;
            return this;
        }

        public Builder<T, A> configurationPanel(Supplier<? extends UIComponent> configurationPanel) {
            this.configurationPanel = configurationPanel;
            return this;
        }

        public Builder<T, A> anchorSpace(WidgetAnchorSpace anchorSpace) {
            this.anchorSpace = anchorSpace;
            return this;
        }

        public Builder<T, A> placementProfileResolver(
            Function<WidgetRenderContext, String> placementProfileResolver
        ) {
            this.placementProfileResolver = placementProfileResolver;
            return this;
        }

        public Builder<T, A> minSize(int minWidth, int minHeight) {
            this.minWidth = minWidth;
            this.minHeight = minHeight;
            return this;
        }

        public WidgetDefinition<T, A> build() {
            return new WidgetDefinition<>(this);
        }
    }
}
