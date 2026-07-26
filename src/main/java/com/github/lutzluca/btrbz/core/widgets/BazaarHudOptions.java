package com.github.lutzluca.btrbz.core.widgets;

public final class BazaarHudOptions {
    public static final int MINIMUM_CONTENT_WIDTH = 180;
    public static final int DEFAULT_CONTENT_WIDTH = 200;

    private BazaarHudOptions() {}

    static String productName(String name, boolean abbreviateEnchanted) {
        if (!abbreviateEnchanted || !name.startsWith("Enchanted ")) return name;
        return "Ench. " + name.substring("Enchanted ".length());
    }
}
