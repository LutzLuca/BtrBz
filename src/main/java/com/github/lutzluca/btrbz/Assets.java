package com.github.lutzluca.btrbz;

import net.minecraft.resources.Identifier;
import com.github.lutzluca.btrbz.core.ui.UiIcon;

/** Bundled icons used by in-game UI; config previews and their dimensions live in ConfigImages. */
public final class Assets {
    public static final UiIcon MOD_ICON = new UiIcon(Identifier.fromNamespaceAndPath(BtrBz.MOD_ID, "icon.png"), 1024);

    public static final UiIcon BOOKMARK_ICON = new UiIcon(Identifier.fromNamespaceAndPath(
        BtrBz.MOD_ID, "textures/bookmark.png"), 16);
    public static final UiIcon BOOKMARK_STAR = new UiIcon(Identifier.fromNamespaceAndPath(
        BtrBz.MOD_ID, "textures/bookmark-star.png"), 16);
    public static final UiIcon CLAIMABLE = new UiIcon(Identifier.fromNamespaceAndPath(
        BtrBz.MOD_ID, "textures/claimable.png"), 8);
    public static final UiIcon GREEN_CHECK = new UiIcon(Identifier.fromNamespaceAndPath(
        BtrBz.MOD_ID, "textures/green-check.png"), 16);
    public static final UiIcon RED_CROSS = new UiIcon(Identifier.fromNamespaceAndPath(
        BtrBz.MOD_ID, "textures/red-cross.png"), 16);
    public static final UiIcon HELP = new UiIcon(Identifier.fromNamespaceAndPath(
        BtrBz.MOD_ID, "textures/info-icon.png"), 64);
    public static final UiIcon TRASHCAN = new UiIcon(Identifier.fromNamespaceAndPath(
        BtrBz.MOD_ID, "textures/trashcan.png"), 32);
    public static final UiIcon REDO_ICON = new UiIcon(Identifier.fromNamespaceAndPath(
        BtrBz.MOD_ID, "textures/redo.png"), 64);

    public static final UiIcon STATUS_OUTDATED = new UiIcon(Identifier.fromNamespaceAndPath(
        BtrBz.MOD_ID, "textures/gui/status/outdated.png"), 9);
    public static final UiIcon STATUS_MATCHED = new UiIcon(Identifier.fromNamespaceAndPath(
        BtrBz.MOD_ID, "textures/gui/status/matched.png"), 9);
    public static final UiIcon STATUS_BEST_ORDER = new UiIcon(Identifier.fromNamespaceAndPath(
        BtrBz.MOD_ID, "textures/gui/status/best_order.png"), 9);
    public static final UiIcon STATUS_UNKNOWN = new UiIcon(Identifier.fromNamespaceAndPath(
        BtrBz.MOD_ID, "textures/gui/status/unknown.png"), 9);

    public static final UiIcon ALERT_BELL = new UiIcon(Identifier.fromNamespaceAndPath(
        BtrBz.MOD_ID, "textures/gui/alerts/bell.png"), 7);
    public static final UiIcon ALERT_ARCHIVE = new UiIcon(Identifier.fromNamespaceAndPath(
        BtrBz.MOD_ID, "textures/gui/alerts/archive.png"), 7);
    public static final UiIcon ALERT_EXPAND = new UiIcon(Identifier.fromNamespaceAndPath(
        BtrBz.MOD_ID, "textures/gui/alerts/expand.png"), 7);
    public static final UiIcon ALERT_COLLAPSE = new UiIcon(Identifier.fromNamespaceAndPath(
        BtrBz.MOD_ID, "textures/gui/alerts/collapse.png"), 7);

    private Assets() {}
}
