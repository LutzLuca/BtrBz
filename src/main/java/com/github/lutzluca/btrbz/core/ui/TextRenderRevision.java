package com.github.lutzluca.btrbz.core.ui;

public final class TextRenderRevision {
    private static long revision;

    private TextRenderRevision() {}

    public static long current() {
        return revision;
    }

    public static void invalidate() {
        revision++;
    }
}
