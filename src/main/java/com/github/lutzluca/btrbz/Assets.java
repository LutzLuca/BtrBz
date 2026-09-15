package com.github.lutzluca.btrbz;

import net.minecraft.resources.Identifier;

/** Bundled icons used by in-game UI; config previews and their dimensions live in ConfigImages. */
public final class Assets {
    public static final Identifier MOD_ICON = Identifier.fromNamespaceAndPath(BtrBz.MOD_ID, "icon.png");

    public static final Identifier BOOKMARK_ICON = Identifier.fromNamespaceAndPath(
        BtrBz.MOD_ID, "textures/bookmark.png");
    public static final Identifier BOOKMARK_STAR = Identifier.fromNamespaceAndPath(
        BtrBz.MOD_ID, "textures/bookmark-star.png");
    public static final Identifier GREEN_CHECK = Identifier.fromNamespaceAndPath(
        BtrBz.MOD_ID, "textures/green-check.png");
    public static final Identifier RED_CROSS = Identifier.fromNamespaceAndPath(
        BtrBz.MOD_ID, "textures/red-cross.png");
    public static final Identifier INFO_ICON = Identifier.fromNamespaceAndPath(
        BtrBz.MOD_ID, "textures/info-icon.png");
    public static final Identifier TRASHCAN = Identifier.fromNamespaceAndPath(
        BtrBz.MOD_ID, "textures/trashcan.png");

    public static final Identifier STATUS_OUTDATED = Identifier.fromNamespaceAndPath(
        BtrBz.MOD_ID, "textures/gui/status/outdated.png");
    public static final Identifier STATUS_MATCHED = Identifier.fromNamespaceAndPath(
        BtrBz.MOD_ID, "textures/gui/status/matched.png");
    public static final Identifier STATUS_BEST_ORDER = Identifier.fromNamespaceAndPath(
        BtrBz.MOD_ID, "textures/gui/status/best_order.png");
    public static final Identifier STATUS_UNKNOWN = Identifier.fromNamespaceAndPath(
        BtrBz.MOD_ID, "textures/gui/status/unknown.png");

    private Assets() {}
}
