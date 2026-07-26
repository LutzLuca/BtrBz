package com.github.lutzluca.btrbz.core.widgets.hud;

import com.github.lutzluca.btrbz.core.widgets.config.BazaarWidgetOptions;
import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetViewData;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarStyles;
import com.github.lutzluca.btrbz.widgets.framework.ui.WidgetLayoutTokens;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import io.wispforest.owo.ui.core.VerticalAlignment;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import static com.github.lutzluca.btrbz.core.widgets.ui.BazaarUi.*;

public final class BazaarHudWidget {
    private BazaarHudWidget() {}

    public static UIComponent render(
        BazaarWidgetViewData.OrdersData data,
        int availablePanelHeight,
        BazaarWidgetOptions.Hud options
    ) {
        if (options.mode() == BazaarWidgetOptions.HudMode.StatusCounts) {
            return statusCountsHud(data, options.contentWidth());
        }
        var orders = data.orders();
        var layout = UIContainers.verticalFlow(Sizing.fixed(options.contentWidth()), Sizing.content());
        layout.allowOverflow(true);
        layout.gap(WidgetLayoutTokens.SECTION_GAP);

        var header = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        header.allowOverflow(true);
        header.verticalAlignment(VerticalAlignment.CENTER);
        header.child(label("Bazaar Orders", BazaarStyles.PRIMARY_TEXT).shadow(false));
        header.child(spacer());
        header.child(label(orderCountText(data), BazaarStyles.MUTED_TEXT));
        layout.child(header);

        if (orders.isEmpty()) {
            layout.child(label(emptyText(data), BazaarStyles.MUTED_TEXT));
            return layout;
        }

        int rowHeight = BazaarHudOrderRowComponent.HEIGHT;
        int visibleRows = BazaarOrderCountPolicy.visibleCount(
            options.visibleOrders(),
            orders.size(),
            availablePanelHeight,
            rowHeight,
            Minecraft.getInstance().font.lineHeight
        );
        var rows = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        rows.allowOverflow(true);
        rows.gap(WidgetLayoutTokens.LIST_GAP);
        for (int index = 0; index < visibleRows; index++) {
            rows.child(new BazaarHudOrderRowComponent(orders.get(index), options));
        }

        layout.child(rows);
        if (orders.size() > visibleRows) {
            layout.child(overflowLegend(orders.subList(visibleRows, orders.size())));
        }
        return layout;
    }

    public static String emptyText(BazaarWidgetViewData.OrdersData data) {
        return data.filledOrderCount() == 0 ? "No active or filled orders" : "No active orders";
    }

    public static List<StatusEntry> visibleStatusEntries(BazaarWidgetViewData.OrdersData data) {
        var counts = data.counts();
        var entries = new ArrayList<StatusEntry>();
        addStatusEntry(entries, "Undercut", counts.undercut(), BazaarStyles.STATUS_UNDERCUT);
        addStatusEntry(entries, "Matched", counts.matched(), BazaarStyles.STATUS_MATCHED);
        addStatusEntry(entries, "Best", counts.top(), BazaarStyles.STATUS_TOP);
        addStatusEntry(entries, "Filled", data.filledOrderCount(), BazaarStyles.STATUS_FILLED);
        addStatusEntry(entries, "Unknown", counts.unknown(), BazaarStyles.STATUS_UNKNOWN);
        return List.copyOf(entries);
    }

    private static UIComponent statusCountsHud(BazaarWidgetViewData.OrdersData data, int contentWidth) {
        var layout = UIContainers.verticalFlow(Sizing.fixed(contentWidth), Sizing.content());
        layout.allowOverflow(true);
        layout.gap(WidgetLayoutTokens.SECTION_GAP);
        var header = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        header.allowOverflow(true);
        header.child(label("Bazaar Orders", BazaarStyles.PRIMARY_TEXT));
        header.child(spacer());
        header.child(label(data.counts().total() + " active", BazaarStyles.MUTED_TEXT));
        layout.child(header);
        layout.child(statusCountsStrip(data, contentWidth));
        return layout;
    }

    public static UIComponent statusCountsStrip(BazaarWidgetViewData.OrdersData data, int contentWidth) {
        var entries = visibleStatusEntries(data);
        if (entries.isEmpty()) return label("No active or filled orders", BazaarStyles.MUTED_TEXT);

        int columnWidth = Math.max(1, (contentWidth - WidgetLayoutTokens.HEADER_GAP) / 2);
        var grid = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        grid.gap(WidgetLayoutTokens.LINE_GAP);
        for (int index = 0; index < entries.size(); index += 2) {
            var right = index + 1 < entries.size() ? entries.get(index + 1) : null;
            grid.child(statusCountsRow(entries.get(index), right, columnWidth));
        }
        return grid;
    }

    private static UIComponent statusCountsRow(StatusEntry left, StatusEntry right, int columnWidth) {
        var row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(9));
        row.allowOverflow(true);
        row.verticalAlignment(VerticalAlignment.CENTER);
        row.child(statusSummary(left, columnWidth));
        row.child(spacer());
        if (right != null) row.child(statusSummary(right, columnWidth));
        return row;
    }

    private static void addStatusEntry(List<StatusEntry> entries, String label, int count, int color) {
        if (count > 0) entries.add(new StatusEntry(label, count, color));
    }

    private static UIComponent statusSummary(StatusEntry status, int width) {
        var entry = UIContainers.horizontalFlow(Sizing.fixed(width), Sizing.fixed(9));
        entry.allowOverflow(true);
        entry.verticalAlignment(VerticalAlignment.CENTER);
        entry.child(boldLabel(status.label(), status.color()));
        entry.child(spacer());
        entry.child(boldLabel(status.count() + "x", status.color()));
        return entry;
    }

    private static String orderCountText(BazaarWidgetViewData.OrdersData data) {
        return data.counts().total() + " active · " + data.filledOrderCount() + " filled";
    }

    private static UIComponent overflowLegend(List<BazaarWidgetViewData.Order> overflow) {
        long undercut = overflow.stream().filter(order -> order.status() == BazaarWidgetViewData.OrderStatus.Undercut).count();
        long matched = overflow.stream().filter(order -> order.status() == BazaarWidgetViewData.OrderStatus.Matched).count();
        long top = overflow.stream().filter(order -> order.status() == BazaarWidgetViewData.OrderStatus.Top).count();
        long unknown = overflow.stream().filter(order -> order.status() == BazaarWidgetViewData.OrderStatus.Unknown).count();

        var legend = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        legend.allowOverflow(true);
        legend.verticalAlignment(VerticalAlignment.CENTER);
        legend.gap(4);
        legend.child(label("+" + overflow.size() + " more ·", BazaarStyles.MUTED_TEXT));
        addStatusCount(legend, undercut, "outdated");
        addStatusCount(legend, matched, "matched");
        addStatusCount(legend, top, "best_order");
        addStatusCount(legend, unknown, "unknown");
        return legend;
    }

    private static void addStatusCount(io.wispforest.owo.ui.container.FlowLayout legend, long count, String iconName) {
        if (count > 0) legend.child(statusCount(count, iconName));
    }

    private static UIComponent statusCount(long count, String iconName) {
        var entry = UIContainers.horizontalFlow(Sizing.content(), Sizing.fixed(9));
        entry.allowOverflow(true);
        entry.verticalAlignment(VerticalAlignment.CENTER);
        entry.gap(2);
        entry.child(label(Long.toString(count), BazaarStyles.SECONDARY_TEXT));
        var icon = UIComponents.texture(
            Identifier.fromNamespaceAndPath("btrbz", "textures/gui/status/" + iconName + ".png"),
            0, 0, 9, 9, 9, 9
        );
        icon.sizing(Sizing.fixed(9), Sizing.fixed(9));
        entry.child(icon);
        return entry;
    }

    public record StatusEntry(String label, int count, int color) {}
}
