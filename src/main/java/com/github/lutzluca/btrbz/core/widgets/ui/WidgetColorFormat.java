package com.github.lutzluca.btrbz.core.widgets.ui;

import java.util.OptionalInt;

public final class WidgetColorFormat {
    private WidgetColorFormat() {
    }

    public static String formatArgb(int argb) {
        return String.format("#%08X", argb);
    }

    public static OptionalInt parse(String value, int currentArgb) {
        if (value == null) {
            return OptionalInt.empty();
        }

        String hex = value.strip();

        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }

        if (hex.length() != 6 && hex.length() != 8) {
            return OptionalInt.empty();
        }

        if (!hex.matches("[0-9a-fA-F]+")) {
            return OptionalInt.empty();
        }

        try {
            int parsed = (int) Long.parseUnsignedLong(hex, 16);

            if (hex.length() == 6) {
                parsed |= currentArgb & 0xFF000000;
            }

            return OptionalInt.of(parsed);
        } catch (NumberFormatException _) {
            return OptionalInt.empty();
        }
    }
}
