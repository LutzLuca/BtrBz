package com.github.lutzluca.btrbz.core.widgets.hud;

import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetViewData;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarStyles;
import java.util.ArrayList;
import java.util.List;

/** Pure HUD presentation decisions used by the retained view. */
public final class BazaarHudWidget {
    private BazaarHudWidget() {}

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

    private static void addStatusEntry(List<StatusEntry> entries, String label, int count, int color) {
        if (count > 0) {
            entries.add(new StatusEntry(label, count, color));
        }
    }

    public record StatusEntry(String label, int count, int color) {}
}
