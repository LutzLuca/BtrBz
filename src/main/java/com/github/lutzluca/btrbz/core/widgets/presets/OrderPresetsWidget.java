package com.github.lutzluca.btrbz.core.widgets.presets;

import com.github.lutzluca.btrbz.core.widgets.action.BazaarAction;
import com.github.lutzluca.btrbz.core.widgets.config.BazaarWidgetOptions;
import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetViewData;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarOrderListComponent;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarOrderRowComponent;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarStyles;
import com.github.lutzluca.btrbz.widgets.framework.ui.WidgetLayoutTokens;
import com.github.lutzluca.btrbz.widgets.framework.ui.WidgetScrollState;
import io.wispforest.owo.ui.core.UIComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import static com.github.lutzluca.btrbz.core.widgets.ui.BazaarUi.*;

public final class OrderPresetsWidget {
    private OrderPresetsWidget() {}

    public static UIComponent render(
        List<BazaarWidgetViewData.Preset> presets,
        BazaarWidgetOptions.Presets options,
        boolean interactive,
        WidgetScrollState scrollState,
        Consumer<BazaarAction> actions
    ) {
        var root = panel(options.contentWidth());
        root.child(text("Order Presets", BazaarStyles.PRIMARY_TEXT));
        var rows = new ArrayList<BazaarOrderRowComponent.BazaarRow>();
        int index = 0;
        for (var preset : presets) {
            if (preset.label().equals("Maximum") && !options.maximum()) continue;
            if (preset.label().equals("Clipboard") && !options.clipboard()) continue;
            if (!preset.available() && !options.showDisabled()) continue;
            List<Component> tooltip = options.showTooltips() && interactive
                ? List.of(Component.literal(preset.tooltip()))
                : List.of();
            Consumer<Boolean> click = preset.available() && interactive
                ? _ -> actions.accept(new BazaarAction.ApplyPreset(preset.preset()))
                : null;
            int background = switch (preset.label()) {
                case "Maximum" -> 0x80404020;
                case "Clipboard" -> 0x80204080;
                default -> 0x00000000;
            };
            rows.add(new BazaarOrderRowComponent.BazaarRow(
                "preset-" + index++,
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
        int rowHeight = WidgetLayoutTokens.singleLineRowHeight(Minecraft.getInstance().font.lineHeight);
        root.child(new BazaarOrderListComponent(
            rows,
            interactive,
            rowHeight,
            WidgetLayoutTokens.listViewportHeight(rowHeight, Math.min(5, Math.max(1, rows.size()))),
            scrollState
        ));
        return root;
    }
}
