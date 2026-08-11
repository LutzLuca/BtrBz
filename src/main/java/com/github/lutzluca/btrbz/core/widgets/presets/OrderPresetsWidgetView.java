package com.github.lutzluca.btrbz.core.widgets.presets;

import com.github.lutzluca.btrbz.core.widgets.WidgetView;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarOrderListComponent;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarOrderRowComponent;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarStyles;
import com.github.lutzluca.btrbz.core.widgets.ui.RetainedFlowLayout;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetLayoutTokens;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import io.wispforest.owo.ui.core.VerticalAlignment;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;

import static com.github.lutzluca.btrbz.core.widgets.ui.BazaarUi.panel;
import static com.github.lutzluca.btrbz.core.widgets.ui.BazaarUi.text;

final class OrderPresetsWidgetView implements WidgetView<
    OrderPresetsWidgetData.Snapshot, OrderPresetsWidgetConfig, OrderPresetsAction
> {
    private static final int ROW_HEIGHT = 15;
    private static final int HEADER_HEIGHT = 15;

    private final FlowLayout root = panel(1);
    private final RetainedFlowLayout header = RetainedFlowLayout.horizontal(
        Sizing.fill(100), Sizing.fixed(HEADER_HEIGHT)
    );

    private final BazaarOrderListComponent list = new BazaarOrderListComponent(
        true,
        ROW_HEIGHT,
        ROW_HEIGHT
    );

    OrderPresetsWidgetView() {
        this.root.gap(WidgetLayoutTokens.SECTION_GAP);

        this.header.verticalAlignment(VerticalAlignment.CENTER);
        this.header.child(text("Presets", BazaarStyles.PRIMARY_TEXT));

        this.root.child(this.header);
        this.root.child(this.list);
    }

    @Override
    public UIComponent root() {
        return this.root;
    }

    @Override
    public void update(
        OrderPresetsWidgetData.Snapshot data,
        OrderPresetsWidgetConfig config,
        WidgetSession session,
        Consumer<OrderPresetsAction> actions
    ) {
        this.root.horizontalSizing(Sizing.fixed(config.contentWidth));

        var rows = new ArrayList<BazaarOrderRowComponent.BazaarRow>();

        for (var preset : data.presets()) {
            if (preset.preset() instanceof OrderPreset.Clipboard && !config.clipboard) {
                continue;
            }

            if (!preset.available() && !config.showDisabled) {
                continue;
            }

            List<Component> tooltip = !preset.tooltip().isBlank()
                ? List.of(Component.literal(preset.tooltip()))
                : List.of();

            Consumer<Boolean> click = preset.available()
                ? _ -> actions.accept(new OrderPresetsAction.Apply(preset.preset()))
                : null;

            int background = switch (preset.preset()) {
                case OrderPreset.Maximum _ -> 0x80404020;
                case OrderPreset.Clipboard _ -> 0x80204080;
                case OrderPreset.Fixed _ -> 0x00000000;
            };

            rows.add(new BazaarOrderRowComponent.BazaarRow(
                rowId(preset),
                preset.label(),
                preset.available() ? BazaarStyles.PRIMARY_TEXT : BazaarStyles.MUTED_TEXT,
                "",
                "",
                BazaarStyles.MUTED_TEXT,
                0,
                tooltip,
                click,
                false,
                background
            ));
        }

        int height = WidgetLayoutTokens.listViewportHeight(
            ROW_HEIGHT, Math.min(config.visibleRows, Math.max(1, rows.size()))
        );

        this.list.update(rows, true, ROW_HEIGHT, height);
    }

    private static String rowId(OrderPresetsWidgetData.Preset preset) {
        return switch (preset.preset()) {
            case OrderPreset.Maximum _ -> "maximum";
            case OrderPreset.Clipboard _ -> "clipboard";
            case OrderPreset.Fixed fixed -> "fixed-" + fixed.amount();
        };
    }
}
