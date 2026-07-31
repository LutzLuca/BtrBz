package com.github.lutzluca.btrbz.core.widgets.bookmarks;

import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.widgets.WidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.WidgetId;
import com.github.lutzluca.btrbz.core.widgets.WidgetPreview;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetPreviewSessions;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetLayoutTokens;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import net.minecraft.resources.Identifier;

public final class BookmarksWidgetDefinition {
    public static final WidgetId ID = WidgetId.of(Identifier.fromNamespaceAndPath("btrbz", "bookmarks"));
    private BookmarksWidgetDefinition() {}
    public static WidgetDefinition<BookmarksWidgetData.Snapshot, BookmarksWidgetConfig, BookmarksAction> create(
        BookmarkComponent component
    ) {
        var provider = new BookmarksWidgetData(component);
        return WidgetDefinition.<BookmarksWidgetData.Snapshot, BookmarksWidgetConfig, BookmarksAction>builder(ID, "Bookmarks")
            .config(() -> ConfigManager.get().widgets.bookmarks, BookmarksWidgetConfig::new,
                config -> config.frame, BookmarksWidgetConfig::resetPreferences)
            .supports(WidgetSession::inBazaarContainer)
            .visibility((data, config, _) -> !config.hideWhenEmpty || !data.bookmarks().isEmpty())
            .runtimeData(_ -> provider.snapshot())
            .preview(() -> new WidgetPreview<>(BookmarksWidgetData.preview(), WidgetPreviewSessions.container(BazaarMenuType.Main), "default"))
            .viewFactory(BookmarksWidgetView::new)
            .actionHandler(new BookmarksActionHandler(component))
            .settingsPanel(BookmarksWidgetSettings::create)
            .minSize(WidgetLayoutTokens.panelWidth(180), 16)
            .build();
    }
}
