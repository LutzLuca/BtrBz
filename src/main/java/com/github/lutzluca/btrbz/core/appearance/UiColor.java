package com.github.lutzluca.btrbz.core.appearance;

import java.util.function.ToIntFunction;
import lombok.Getter;
import lombok.experimental.Accessors;

@Getter
@Accessors(fluent = true)
public enum UiColor {
    Surface("surface", "Panel", Group.Interface, UiPalette::surface),
    SurfaceRaised("surfaceRaised", "Raised surface", Group.Interface, UiPalette::surfaceRaised),
    Outline("outline", "Outline", Group.Interface, UiPalette::outline),
    TextPrimary("textPrimary", "Primary text", Group.Interface, UiPalette::textPrimary),
    TextSecondary("textSecondary", "Secondary text", Group.Interface, UiPalette::textSecondary),
    TextMuted("textMuted", "Muted text", Group.Interface, UiPalette::textMuted),
    Accent("accent", "Accent", Group.Interface, UiPalette::accent),
    OnAccent("onAccent", "Text on accent", Group.Interface, UiPalette::onAccent),
    Success("success", "Success", Group.Feedback, UiPalette::success),
    Warning("warning", "Warning", Group.Feedback, UiPalette::warning),
    Error("error", "Error", Group.Feedback, UiPalette::error),
    Money("money", "Coins", Group.Bazaar, UiPalette::money),
    Buy("buy", "Buy", Group.Bazaar, UiPalette::buy),
    Sell("sell", "Sell", Group.Bazaar, UiPalette::sell),
    Best("best", "Best", Group.Bazaar, UiPalette::best),
    Matched("matched", "Matched", Group.Bazaar, UiPalette::matched),
    Undercut("undercut", "Undercut", Group.Bazaar, UiPalette::undercut),
    Filled("filled", "Filled", Group.Bazaar, UiPalette::filled),
    Expired("expired", "Expired", Group.Bazaar, UiPalette::expired),
    Unknown("unknown", "Unknown", Group.Bazaar, UiPalette::unknown);

    private final String key;
    private final String label;
    private final Group group;
    private final ToIntFunction<UiPalette> read;

    UiColor(String key, String label, Group group, ToIntFunction<UiPalette> read) {
        this.key = key;
        this.label = label;
        this.group = group;
        this.read = read;
    }

    public int color(UiPalette palette) {
        return this.read.applyAsInt(palette);
    }

    public enum Group {
        Interface, Feedback, Bazaar
    }
}
