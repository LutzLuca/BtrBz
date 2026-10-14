package com.github.lutzluca.btrbz.core.ui;

import com.github.lutzluca.btrbz.core.appearance.UiPalette;
import com.github.lutzluca.btrbz.core.appearance.UiTheme;
import com.github.lutzluca.btrbz.core.appearance.ThemePreset;
import com.github.lutzluca.btrbz.core.appearance.UiColor;
import com.github.lutzluca.btrbz.utils.Utils;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.github.lutzluca.btrbz.cache.CacheToken;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/** Shared text roles and formatting, separate from the palette's colors. */
public final class UiStyles {
    private static ResolvedAppearance current = ResolvedAppearance.from(ThemePreset.BtrBzDark.theme());
    private static final CacheToken APPEARANCE_CHANGES = CacheToken.named("appearance");

    private UiStyles() {}

    public static UiPalette palette() {
        return current.palette();
    }

    public static UiPalette.ButtonColors buttons() {
        return current.buttons();
    }

    public static long appearanceRevision() {
        return APPEARANCE_CHANGES.revision();
    }

    public static CacheToken appearanceChanges() {
        return APPEARANCE_CHANGES;
    }

    public static void configure(UiTheme theme) {
        var resolved = ResolvedAppearance.from(theme);
        if (current.equals(resolved)) {
            return;
        }
        current = resolved;
        APPEARANCE_CHANGES.invalidate("appearance changed");
        TextRenderRevision.invalidate();
    }

    /** Client-thread scope for the draft editor, without saving or invalidating applied appearance caches. */
    public static void preview(UiTheme draft, Runnable action) {
        var applied = current;
        current = ResolvedAppearance.from(draft);
        try {
            action.run();
        } finally {
            current = applied;
        }
    }

    public static Component productName(String formattedName) {
        return productName(GameUtils.legacyFormattedComponent(formattedName), false);
    }

    public static Component productName(Component name, boolean abbreviateEnchanted) {
        var appearance = current;
        var formatted = productName(name, abbreviateEnchanted,
            appearance.formattedProductNames(), appearance.palette().textPrimary());
        if (!appearance.formattedProductNames() || appearance.productColorStrength() >= 100) {
            return formatted;
        }
        var result = Component.empty().withStyle(formatted.getStyle());
        formatted.visit((style, text) -> {
            if (text.isEmpty()) {
                return Optional.empty();
            }
            var color = style.getColor();
            var softened = color == null
                ? style : style.withColor(UiPalette.blend(appearance.palette().textSecondary(),
                    color.getValue(), appearance.productColorStrength() / 100.0) & 0xFFFFFF);
            result.append(Component.literal(text).withStyle(softened));
            return Optional.empty();
        }, Style.EMPTY);
        return result;
    }

    private static Component productName(Component name, boolean abbreviate, boolean formatted, int plainColor) {
        boolean shorten = abbreviate && name.getString().startsWith("Enchanted ");
        int prefixLength = "Enchanted ".length();
        if (!formatted) {
            String text = name.getString();
            return Component.literal(shorten ? "Ench. " + text.substring(prefixLength) : text)
                .withStyle(color(plainColor));
        }
        if (!shorten) {
            return name.copy()
                .withStyle(style -> style.getColor() == null ? style.withColor(plainColor & 0xFFFFFF) : style);
        }
        var result = Component.empty().withStyle(color(plainColor));
        int[] skipped = {0};
        name.visit((style, text) -> {
            if (skipped[0] == 0 && !text.isEmpty()) {
                result.append(Component.literal("Ench. ").withStyle(style));
            }
            int remove = Math.min(text.length(), Math.max(0, prefixLength - skipped[0]));
            skipped[0] += remove;
            if (remove < text.length()) {
                result.append(Component.literal(text.substring(remove)).withStyle(style));
            }
            return Optional.empty();
        }, Style.EMPTY);
        return result;
    }

    public static Style color(int argb) {
        return Style.EMPTY.withColor(argb & 0xFFFFFF).withBold(false).withUnderlined(false);
    }

    public static int argb(UiColor role) {
        return role.color(palette());
    }

    public static Style color(UiColor role) {
        return color(argb(role));
    }

    public static Style primary() {
        return color(palette().textPrimary());
    }

    public static Style label() {
        return color(palette().textSecondary());
    }

    public static Style muted() {
        return color(palette().textMuted());
    }

    public static Style quantity() {
        return primary();
    }

    public static Style heading() {
        return primary().withBold(true);
    }

    public static Style key() {
        return primary().withBold(true);
    }

    public static Style action() {
        return color(palette().accent());
    }

    public static Style modLabel() {
        return color(palette().accent());
    }

    public static Style money() {
        return color(palette().money());
    }

    public static MutableComponent coins(double value) {
        return Component.literal(Utils.formatDecimal(value, 1, true) + " coins").withStyle(money());
    }

    private record ResolvedAppearance(
        UiPalette palette,
        UiPalette.ButtonColors buttons,
        boolean formattedProductNames,
        int productColorStrength
    ) {
        private static ResolvedAppearance from(UiTheme theme) {
            return new ResolvedAppearance(theme.palette(), theme.palette().buttons(),
                theme.formattedProductNames(), theme.productColorStrength());
        }
    }

}
