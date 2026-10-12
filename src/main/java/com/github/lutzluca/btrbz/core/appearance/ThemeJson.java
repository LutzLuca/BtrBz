package com.github.lutzluca.btrbz.core.appearance;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.vavr.control.Try;
import java.util.Locale;

/** The sharing format contains appearance only, with a complete set of RGB roles. */
public final class ThemeJson {
    private ThemeJson() {}

    public static String exportTheme(UiTheme theme) {
        var document = new JsonObject();
        document.addProperty("type", "btrbz-theme");
        document.addProperty("schemaVersion", 2);
        document.addProperty("name", theme.name());
        document.addProperty("panelOpacity", theme.palette().panelOpacity());
        document.addProperty("orderHighlightOpacity", theme.orderHighlightOpacity());
        document.addProperty("formattedProductNames", theme.formattedProductNames());
        document.addProperty("productColorStrength", theme.productColorStrength());
        var colors = new JsonObject();
        for (var role : UiColor.values()) {
            colors.addProperty(role.key(), formatColor(role.color(theme.palette())));
        }
        document.add("colors", colors);
        return new GsonBuilder().setPrettyPrinting().create().toJson(document);
    }

    public static Try<UiTheme> importTheme(String json) {
        return Try.of(() -> {
            if (json.length() > 16_384) {
                throw new IllegalArgumentException("Theme JSON is too large");
            }
            var document = JsonParser.parseString(json).getAsJsonObject();
            if (!"btrbz-theme".equals(document.get("type").getAsString())) {
                throw new IllegalArgumentException("This is not a BtrBz theme");
            }
            if (!document.get("schemaVersion").getAsJsonPrimitive().isNumber()
                || document.get("schemaVersion").getAsDouble() != 2) {
                throw new IllegalArgumentException("Unsupported theme version");
            }
            var colors = document.getAsJsonObject("colors");
            int[] values = new int[UiColor.values().length];
            for (var role : UiColor.values()) {
                var value = colors.get(role.key());
                if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                    throw new IllegalArgumentException("Missing color: " + role.label());
                }
                values[role.ordinal()] = parseColor(value.getAsString()).get();
            }
            if (!document.get("panelOpacity").getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException("Panel opacity must be a number");
            }
            var name = document.get("name");
            if (name == null || !name.isJsonPrimitive() || !name.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("Theme name must be text");
            }
            var formatted = document.get("formattedProductNames");
            if (formatted == null || !formatted.isJsonPrimitive() || !formatted.getAsJsonPrimitive().isBoolean()) {
                throw new IllegalArgumentException("Product name formatting must be a boolean");
            }
            return new UiTheme(name.getAsString(),
                UiPalette.fromColors(values, document.get("panelOpacity").getAsDouble()),
                formatted.getAsBoolean(), percentage(document, "productColorStrength"),
                percentage(document, "orderHighlightOpacity"));
        });
    }

    private static int percentage(JsonObject document, String key) {
        var value = document.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(key + " must be a percentage");
        }
        return value.getAsBigDecimal().intValueExact();
    }

    public static String formatColor(int color) {
        return String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF);
    }

    public static Try<Integer> parseColor(String text) {
        return Try.of(() -> {
            if (!text.matches("#[0-9a-fA-F]{6}")) {
                throw new IllegalArgumentException("Colors must use #RRGGBB");
            }
            return Integer.parseInt(text.substring(1), 16) | 0xFF000000;
        });
    }
}
