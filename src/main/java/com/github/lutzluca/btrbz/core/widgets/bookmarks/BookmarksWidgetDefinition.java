package com.github.lutzluca.btrbz.core.widgets.bookmarks;

import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.widgets.WidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.WidgetId;
import com.github.lutzluca.btrbz.core.widgets.WidgetPreview;
import com.github.lutzluca.btrbz.core.widgets.cache.MemoizedWidgetDataSource;
import com.github.lutzluca.btrbz.core.widgets.config.WidgetConfigHandle;
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
        var config = new WidgetConfigHandle<>(ID,
            () -> ConfigManager.get().widgets.bookmarks, BookmarksWidgetConfig::new,
            value -> value.frame, BookmarksWidgetConfig::resetPreferences);
        var provider = new MemoizedWidgetDataSource<>(new BookmarksWidgetData(component));
        return WidgetDefinition.<BookmarksWidgetData.Snapshot, BookmarksWidgetConfig, BookmarksAction>builder(ID, "Bookmarks")
            .description("Provides quick access to bookmarked Bazaar products and marks products with active orders.")
            .config(config)
            .supports(BookmarksWidgetDefinition::supportsSession)
            .visibility((data, _, _) -> !data.bookmarks().isEmpty())
            .data(provider)
            .cachePrepared()
            .preview(() -> new WidgetPreview<>(BookmarksWidgetData.preview(), WidgetPreviewSessions.container(BazaarMenuType.Main), "default"))
            .viewFactory(BookmarksWidgetView::new)
            .actionHandler(new BookmarksActionHandler(component))
            .settingsPanel(BookmarksWidgetSettings::create)
            .minSize(WidgetLayoutTokens.panelWidth(200), 16)
            .build();
    }

    public static boolean supportsSession(WidgetSession session) { return session.inBazaarContainer(); }
}
