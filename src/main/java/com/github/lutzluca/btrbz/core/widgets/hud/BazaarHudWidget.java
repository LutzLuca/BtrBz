package com.github.lutzluca.btrbz.core.widgets.hud;

import com.github.lutzluca.btrbz.core.ui.UiStyles;

import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetViewData;
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
        addStatusEntry(entries, "Undercut", counts.undercut(), UiStyles.palette().error());
        addStatusEntry(entries, "Matched", counts.matched(), UiStyles.palette().matched());
        addStatusEntry(entries, "Best", counts.top(), UiStyles.palette().success());
        addStatusEntry(entries, "Filled", data.filledOrderCount(), UiStyles.palette().filled());
        addStatusEntry(entries, "Unknown", counts.unknown(), UiStyles.palette().unknown());

        return List.copyOf(entries);
    }

    private static void addStatusEntry(List<StatusEntry> entries, String label, int count, int color) {
        if (count > 0) {
            entries.add(new StatusEntry(label, count, color));
        }
    }

    public record StatusEntry(String label, int count, int color) {}
}
