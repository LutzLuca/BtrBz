package com.github.lutzluca.btrbz.core.appearance;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import io.vavr.control.Try;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** The sharing format contains appearance only, with a complete set of RGB roles. */
public final class ThemeJson {
    private static final String TYPE = "btrbz-theme";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ThemeJson() {}

    public static String exportTheme(UiTheme theme) {
        return GSON.toJson(Version2.fromTheme(theme));
    }

    public static Try<UiTheme> importTheme(String json) {
        return Try.of(() -> {
            if (json.length() > 16_384) {
                throw new IllegalArgumentException("Theme JSON is too large");
            }
            var document = JsonParser.parseString(json).getAsJsonObject();
            if (!TYPE.equals(string(document, "type"))) {
                throw new IllegalArgumentException("This is not a BtrBz theme");
            }
            return switch (number(document, "schemaVersion").getAsBigDecimal().intValueExact()) {
                case 2 -> Version2.read(document).toTheme();
                default -> throw new IllegalArgumentException("Unsupported theme version");
            };
        });
    }

    private static JsonPrimitive primitive(JsonObject document, String key) {
        var value = document.get(key);
        if (value == null || !value.isJsonPrimitive()) {
            throw new IllegalArgumentException("Missing or invalid field: " + key);
        }
        return value.getAsJsonPrimitive();
    }

    private static String string(JsonObject document, String key) {
        var value = primitive(document, key);
        if (!value.isString()) {
            throw new IllegalArgumentException(key + " must be text");
        }
        return value.getAsString();
    }

    private static JsonPrimitive number(JsonObject document, String key) {
        var value = primitive(document, key);
        if (!value.isNumber()) {
            throw new IllegalArgumentException(key + " must be a number");
        }
        return value;
    }

    private static boolean bool(JsonObject document, String key) {
        var value = primitive(document, key);
        if (!value.isBoolean()) {
            throw new IllegalArgumentException(key + " must be a boolean");
        }
        return value.getAsBoolean();
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

    /** Owns the v2 wire format and its conversion into a complete runtime profile. */
    private record Version2(
        String type, int schemaVersion, String name, double panelOpacity,
        int orderHighlightOpacity, boolean formattedProductNames, int productColorStrength,
        Map<String, String> colors
    ) {
        private Version2 {
            colors = Collections.unmodifiableMap(new LinkedHashMap<>(colors));
        }

        private static Version2 fromTheme(UiTheme theme) {
            var colors = new LinkedHashMap<String, String>();
            for (var role : UiColor.values()) {
                colors.put(role.key(), formatColor(role.color(theme.palette())));
            }
            return new Version2(TYPE, 2, theme.name(), theme.palette().panelOpacity(),
                theme.orderHighlightOpacity(), theme.formattedProductNames(), theme.productColorStrength(), colors);
        }

        private static Version2 read(JsonObject document) {
            var colorDocument = document.getAsJsonObject("colors");
            var colors = new LinkedHashMap<String, String>();
            for (var role : UiColor.values()) {
                colors.put(role.key(), string(colorDocument, role.key()));
            }
            return new Version2(TYPE, 2, string(document, "name"), number(document, "panelOpacity").getAsDouble(),
                number(document, "orderHighlightOpacity").getAsBigDecimal().intValueExact(),
                bool(document, "formattedProductNames"),
                number(document, "productColorStrength").getAsBigDecimal().intValueExact(), colors);
        }

        private UiTheme toTheme() {
            int[] values = new int[UiColor.values().length];
            for (var role : UiColor.values()) {
                values[role.ordinal()] = parseColor(this.colors.get(role.key())).get();
            }
            return new UiTheme(this.name, UiPalette.fromColors(values, this.panelOpacity),
                this.formattedProductNames, this.productColorStrength, this.orderHighlightOpacity);
        }
    }
}
