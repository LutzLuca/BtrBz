package com.github.lutzluca.btrbz.core.ui;

import com.github.lutzluca.btrbz.core.appearance.UiPalette;
import com.github.lutzluca.btrbz.core.appearance.UiTheme;
import com.github.lutzluca.btrbz.core.appearance.ThemePreset;
import com.github.lutzluca.btrbz.core.appearance.AppearanceConfig;
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
    private static UiPalette currentPalette = ThemePreset.BtrBzDark.theme().palette();
    private static UiPalette.ButtonColors currentButtons = currentPalette.buttons();
    private static boolean formattedProductNames = true;
    private static int productColorStrength = 100;
    private static final CacheToken APPEARANCE_CHANGES = CacheToken.named("appearance");

    private UiStyles() {}

    public static UiPalette palette() {
        return currentPalette;
    }

    public static UiPalette.ButtonColors buttons() {
        return currentButtons;
    }

    public static long appearanceRevision() {
        return APPEARANCE_CHANGES.revision();
    }

    public static CacheToken appearanceChanges() {
        return APPEARANCE_CHANGES;
    }

    public static void configure(AppearanceConfig config) {
        if (currentPalette.equals(config.theme.palette())
            && formattedProductNames == config.theme.formattedProductNames()
            && productColorStrength == config.theme.productColorStrength()) {
            return;
        }
        currentPalette = config.theme.palette();
        currentButtons = currentPalette.buttons();
        formattedProductNames = config.theme.formattedProductNames();
        productColorStrength = config.theme.productColorStrength();
        APPEARANCE_CHANGES.invalidate("appearance changed");
        TextRenderRevision.invalidate();
    }

    /** Client-thread scope for the draft editor, without saving or invalidating applied appearance caches. */
    public static void preview(UiTheme draft, Runnable action) {
        var palette = currentPalette;
        var buttons = currentButtons;
        boolean formatted = formattedProductNames;
        int strength = productColorStrength;
        currentPalette = draft.palette();
        currentButtons = currentPalette.buttons();
        formattedProductNames = draft.formattedProductNames();
        productColorStrength = draft.productColorStrength();
        try {
            action.run();
        } finally {
            currentPalette = palette;
            currentButtons = buttons;
            formattedProductNames = formatted;
            productColorStrength = strength;
        }
    }

    public static Component productName(String formattedName) {
        return productName(GameUtils.legacyFormattedComponent(formattedName), false);
    }

    public static Component productName(Component name, boolean abbreviateEnchanted) {
        var formatted = productName(name, abbreviateEnchanted, formattedProductNames, palette().textPrimary());
        if (!formattedProductNames || productColorStrength >= 100) {
            return formatted;
        }
        var result = Component.empty().withStyle(formatted.getStyle());
        formatted.visit((style, text) -> {
            if (text.isEmpty()) {
                return Optional.empty();
            }
            var color = style.getColor();
            var softened = color == null
                ? style : style.withColor(UiPalette.blend(palette().textSecondary(),
                    color.getValue(), productColorStrength / 100.0) & 0xFFFFFF);
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

    public static Style color(UiColor role) {
        return color(role.color(palette()));
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
}
