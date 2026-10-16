package com.github.lutzluca.btrbz.core.widgets.productcontext;

import com.github.lutzluca.btrbz.core.widgets.WidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.WidgetId;
import com.github.lutzluca.btrbz.core.widgets.WidgetPreview;
import com.github.lutzluca.btrbz.core.widgets.cache.MemoizedWidgetDataSource;
import com.github.lutzluca.btrbz.core.widgets.config.WidgetConfigHandle;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetPreviewSessions;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetLayoutTokens;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.screen.ScreenTracker.BazaarMenuType;
import java.util.function.Supplier;
import net.minecraft.resources.Identifier;

public final class ProductContextWidgetDefinition {
    public static final WidgetId ID = WidgetId.of(Identifier.fromNamespaceAndPath("btrbz", "product_context"));

    private ProductContextWidgetDefinition() {}

    public static WidgetDefinition<ProductContextWidgetData.Snapshot, ProductContextWidgetConfig, Void> create(
        BazaarData market,
        Supplier<ProductContextWidgetConfig> configSupplier
    ) {
        var config = new WidgetConfigHandle<>(ID,
            configSupplier, ProductContextWidgetConfig::new,
            value -> value.frame, ProductContextWidgetConfig::resetPreferences);
        var provider = new MemoizedWidgetDataSource<>(new ProductContextWidgetData(market));

        return WidgetDefinition.<ProductContextWidgetData.Snapshot, ProductContextWidgetConfig, Void>builder(ID,
            "Product Context")
            .description("Current product quotes and their difference, with optional whole-market context. "
                + "Inventory totals use the quote spread before fees.")
            .config(config)
            .supports(ProductContextWidgetDefinition::supportsSession)
            .visibility((data, _, _) -> ProductContextWidgetDefinition.isVisible(data))
            .data(provider)
            .cachePrepared()
            .preview(() -> new WidgetPreview<>(ProductContextWidgetData.preview(),
                WidgetPreviewSessions.container(BazaarMenuType.Item), "default"))
            .viewFactory(ProductContextWidgetView::new)
            .settingsPanel(ProductContextWidgetSettings::create)
            .minSize(WidgetLayoutTokens.panelWidth(180), 36)
            .build();
    }

    public static boolean supportsSession(WidgetSession session) {
        return session.inBazaarMenu(BazaarMenuType.Item);
    }

    public static boolean isVisible(ProductContextWidgetData.Snapshot data) {
        return data.product().isPresent();
    }
}
