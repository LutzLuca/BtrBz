package com.github.lutzluca.btrbz.widgets.framework.ui;

/**
 * Progress and scrollbar-thumb capture state that can outlive a rebuilt widget
 * component tree.
 */
public final class WidgetScrollState {
    private double progress = 0.0;
    private boolean thumbCaptured = false;
    private long visibleUntil = 0L;

    public WidgetScrollState() {}

    double progress() {
        return this.progress;
    }

    void rememberOffset(double offset, int maxScroll) {
        this.progress = maxScroll <= 0 ? 0.0 : clamp01(offset / maxScroll);
    }

    boolean thumbCaptured() {
        return this.thumbCaptured;
    }

    void captureThumb() {
        this.thumbCaptured = true;
        this.keepVisibleFor(1500L);
    }

    void releaseThumb() {
        this.thumbCaptured = false;
        this.keepVisibleFor(1250L);
    }

    long visibleUntil() {
        return this.visibleUntil;
    }

    void rememberVisibleUntil(long visibleUntil) {
        this.visibleUntil = Math.max(this.visibleUntil, visibleUntil);
    }

    private void keepVisibleFor(long durationMillis) {
        this.visibleUntil = Math.max(this.visibleUntil, System.currentTimeMillis() + durationMillis);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
