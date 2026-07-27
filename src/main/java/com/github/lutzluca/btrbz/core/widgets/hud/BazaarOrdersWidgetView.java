package com.github.lutzluca.btrbz.core.widgets.hud;

import com.github.lutzluca.btrbz.core.widgets.WidgetView;
import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetViewData;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarStyles;
import com.github.lutzluca.btrbz.core.widgets.ui.RetainedFlowLayout;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetLayoutTokens;
import com.github.lutzluca.btrbz.data.OrderModels.TrackedOrderId;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import io.wispforest.owo.ui.core.VerticalAlignment;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import static com.github.lutzluca.btrbz.core.widgets.ui.BazaarUi.boldLabel;
import static com.github.lutzluca.btrbz.core.widgets.ui.BazaarUi.label;
import static com.github.lutzluca.btrbz.core.widgets.ui.BazaarUi.spacer;

final class BazaarOrdersWidgetView implements WidgetView<
    BazaarWidgetViewData.OrdersData, BazaarOrdersWidgetConfig, Void
> {
    private final RetainedFlowLayout root = RetainedFlowLayout.vertical(Sizing.fixed(1), Sizing.content());
    private final Detailed detailed = new Detailed();
    private final Counts counts = new Counts();

    BazaarOrdersWidgetView() {
        this.root.allowOverflow(true);
    }

    @Override
    public UIComponent root() {
        return this.root;
    }

    @Override
    public void update(
        BazaarWidgetViewData.OrdersData data,
        BazaarOrdersWidgetConfig config,
        WidgetSession session,
        Consumer<Void> actions
    ) {
        this.root.horizontalSizing(Sizing.fixed(config.contentWidth()));
        this.root.clearChildren();
        if (config.mode() == BazaarOrdersWidgetConfig.HudMode.StatusCounts) {
            this.counts.update(data, config.contentWidth());
            this.root.child(this.counts.root);
        } else {
            this.detailed.update(data, config);
            this.root.child(this.detailed.root);
        }
    }

    private static final class Detailed {
        private final RetainedFlowLayout root = RetainedFlowLayout.vertical(Sizing.fill(100), Sizing.content());
        private final RetainedFlowLayout header = RetainedFlowLayout.horizontal(Sizing.fill(100), Sizing.content());
        private final LabelComponent count = label("", BazaarStyles.MUTED_TEXT);
        private final LabelComponent empty = label("", BazaarStyles.MUTED_TEXT);
        private final RetainedFlowLayout rows = RetainedFlowLayout.vertical(Sizing.fill(100), Sizing.content());
        private final Map<TrackedOrderId, BazaarHudOrderRowComponent> rowsById = new HashMap<>();
        private final Overflow overflow = new Overflow();

        private Detailed() {
            this.root.allowOverflow(true);
            this.root.gap(WidgetLayoutTokens.SECTION_GAP);
            this.rows.allowOverflow(true);
            this.rows.gap(WidgetLayoutTokens.LIST_GAP);
            this.header.allowOverflow(true);
            this.header.verticalAlignment(VerticalAlignment.CENTER);
            this.header.child(label("Bazaar Orders", BazaarStyles.PRIMARY_TEXT));
            this.header.child(spacer());
            this.header.child(this.count);
        }

        private void update(BazaarWidgetViewData.OrdersData data, BazaarOrdersWidgetConfig config) {
            this.count.text(Component.literal(
                data.counts().total() + " active · " + data.filledOrderCount() + " filled"
            ));
            this.empty.text(Component.literal(BazaarHudWidget.emptyText(data)));
            int visible = BazaarOrderCountPolicy.visibleCount(
                config.visibleOrders(), data.orders().size(), Integer.MAX_VALUE,
                BazaarHudOrderRowComponent.HEIGHT, Minecraft.getInstance().font.lineHeight
            );
            this.rows.clearChildren();
            for (int index = 0; index < visible; index++) {
                var order = data.orders().get(index);
                var row = this.rowsById.computeIfAbsent(
                    order.id(), _ -> new BazaarHudOrderRowComponent(order, config)
                );
                row.update(order, config);
                this.rows.child(row);
            }
            var currentIds = data.orders().stream().map(BazaarWidgetViewData.Order::id).toList();
            this.rowsById.keySet().removeIf(id -> !currentIds.contains(id));

            this.root.clearChildren();
            this.root.child(this.header);
            if (data.orders().isEmpty()) {
                this.root.child(this.empty);
                return;
            }
            this.root.child(this.rows);
            if (data.orders().size() > visible) {
                this.overflow.update(data.orders().subList(visible, data.orders().size()));
                this.root.child(this.overflow.root);
            }
        }
    }

    private static final class Counts {
        private final RetainedFlowLayout root = RetainedFlowLayout.vertical(Sizing.fill(100), Sizing.content());
        private final RetainedFlowLayout header = RetainedFlowLayout.horizontal(Sizing.fill(100), Sizing.content());
        private final LabelComponent active = label("", BazaarStyles.MUTED_TEXT);
        private final LabelComponent empty = label("No active or filled orders", BazaarStyles.MUTED_TEXT);
        private final RetainedFlowLayout grid = RetainedFlowLayout.vertical(Sizing.fill(100), Sizing.content());
        private final List<StatusSummary> summaries = new ArrayList<>();
        private final List<RetainedFlowLayout> rows = List.of(
            RetainedFlowLayout.horizontal(Sizing.fill(100), Sizing.fixed(9)),
            RetainedFlowLayout.horizontal(Sizing.fill(100), Sizing.fixed(9)),
            RetainedFlowLayout.horizontal(Sizing.fill(100), Sizing.fixed(9))
        );
        private final List<UIComponent> rowSpacers = List.of(spacer(), spacer(), spacer());

        private Counts() {
            this.root.allowOverflow(true);
            this.root.gap(WidgetLayoutTokens.SECTION_GAP);
            this.grid.gap(WidgetLayoutTokens.LINE_GAP);
            this.header.allowOverflow(true);
            this.header.child(label("Bazaar Orders", BazaarStyles.PRIMARY_TEXT));
            this.header.child(spacer());
            this.header.child(this.active);
            for (int index = 0; index < 5; index++) this.summaries.add(new StatusSummary());
            for (var row : this.rows) {
                row.allowOverflow(true);
                row.verticalAlignment(VerticalAlignment.CENTER);
            }
        }

        private void update(BazaarWidgetViewData.OrdersData data, int contentWidth) {
            this.active.text(Component.literal(data.counts().total() + " active"));
            var entries = BazaarHudWidget.visibleStatusEntries(data);
            int columnWidth = Math.max(1, (contentWidth - WidgetLayoutTokens.HEADER_GAP) / 2);
            this.grid.clearChildren();
            for (int index = 0; index < entries.size(); index++) {
                this.summaries.get(index).update(entries.get(index), columnWidth);
            }
            for (int index = 0; index < entries.size(); index += 2) {
                var row = this.rows.get(index / 2);
                row.clearChildren();
                row.child(this.summaries.get(index).root);
                row.child(this.rowSpacers.get(index / 2));
                if (index + 1 < entries.size()) row.child(this.summaries.get(index + 1).root);
                this.grid.child(row);
            }
            this.root.clearChildren();
            this.root.child(this.header);
            this.root.child(entries.isEmpty() ? this.empty : this.grid);
        }
    }

    private static final class StatusSummary {
        private final RetainedFlowLayout root = RetainedFlowLayout.horizontal(Sizing.fixed(1), Sizing.fixed(9));
        private final LabelComponent label = boldLabel("", BazaarStyles.PRIMARY_TEXT);
        private final LabelComponent count = boldLabel("", BazaarStyles.PRIMARY_TEXT);

        private StatusSummary() {
            this.root.allowOverflow(true);
            this.root.verticalAlignment(VerticalAlignment.CENTER);
            this.root.child(this.label);
            this.root.child(spacer());
            this.root.child(this.count);
        }

        private void update(BazaarHudWidget.StatusEntry entry, int width) {
            this.root.horizontalSizing(Sizing.fixed(width));
            this.label.text(Component.literal(entry.label()).withStyle(net.minecraft.ChatFormatting.BOLD));
            this.count.text(Component.literal(entry.count() + "x").withStyle(net.minecraft.ChatFormatting.BOLD));
            this.label.color(BazaarStyles.color(entry.color()));
            this.count.color(BazaarStyles.color(entry.color()));
        }
    }

    private static final class Overflow {
        private final RetainedFlowLayout root = RetainedFlowLayout.horizontal(Sizing.fill(100), Sizing.content());
        private final LabelComponent more = label("", BazaarStyles.MUTED_TEXT);
        private final List<StatusCount> statuses = List.of(
            new StatusCount("outdated"), new StatusCount("matched"),
            new StatusCount("best_order"), new StatusCount("unknown")
        );

        private Overflow() {
            this.root.allowOverflow(true);
            this.root.verticalAlignment(VerticalAlignment.CENTER);
            this.root.gap(4);
        }

        private void update(List<BazaarWidgetViewData.Order> orders) {
            long[] counts = {
                count(orders, BazaarWidgetViewData.OrderStatus.Undercut),
                count(orders, BazaarWidgetViewData.OrderStatus.Matched),
                count(orders, BazaarWidgetViewData.OrderStatus.Top),
                count(orders, BazaarWidgetViewData.OrderStatus.Unknown)
            };
            this.more.text(Component.literal("+" + orders.size() + " more ·"));
            this.root.clearChildren();
            this.root.child(this.more);
            for (int index = 0; index < counts.length; index++) {
                if (counts[index] <= 0) continue;
                this.statuses.get(index).update(counts[index]);
                this.root.child(this.statuses.get(index).root);
            }
        }

        private static long count(List<BazaarWidgetViewData.Order> orders, BazaarWidgetViewData.OrderStatus status) {
            return orders.stream().filter(order -> order.status() == status).count();
        }
    }

    private static final class StatusCount {
        private final RetainedFlowLayout root = RetainedFlowLayout.horizontal(Sizing.content(), Sizing.fixed(9));
        private final LabelComponent count = label("", BazaarStyles.SECONDARY_TEXT);

        private StatusCount(String iconName) {
            this.root.allowOverflow(true);
            this.root.verticalAlignment(VerticalAlignment.CENTER);
            this.root.gap(2);
            this.root.child(this.count);
            var icon = UIComponents.texture(
                Identifier.fromNamespaceAndPath("btrbz", "textures/gui/status/" + iconName + ".png"),
                0, 0, 9, 9, 9, 9
            );
            icon.sizing(Sizing.fixed(9), Sizing.fixed(9));
            this.root.child(icon);
        }

        private void update(long value) {
            this.count.text(Component.literal(Long.toString(value)));
        }
    }
}
