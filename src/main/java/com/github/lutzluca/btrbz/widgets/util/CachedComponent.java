package com.github.lutzluca.btrbz.widgets.util;

import org.jetbrains.annotations.Nullable;

import lombok.Getter;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Utility class for caching Component creation to reduce GC pressure.
 * 
 * This class caches the created Component and only rebuilds it when
 * the underlying text or formatting changes. Ideal for text that is
 * rendered every frame but changes infrequently.
 * 
 * Usage:
 * <pre>
 * private final CachedComponent displayText = new CachedComponent("");
 * 
 * public void updateText(String text) {
 *     this.displayText.update(text, ChatFormatting.WHITE);
 * }
 * 
 * public void render() {
 *     graphics.drawText(font, this.displayText.get(), x, y, color, shadow);
 * }
 * </pre>
 */
public class CachedComponent {
    @Getter
    private String text;
    private ChatFormatting formatting;
    private MutableComponent cached;
    @Getter
    private boolean dirty;

    /**
     * Create a new CachedComponent with empty text.
     */
    public CachedComponent() {
        this("");
    }

    /**
     * Create a new CachedComponent with initial text.
     * 
     * @param text Initial text content
     */
    public CachedComponent(String text) {
        this.text = text;
        this.formatting = null;
        this.dirty = true;
    }

    /**
     * Update the text and formatting. Marks cache as dirty if changed.
     * 
     * @param text New text content
     * @param formatting New formatting (can be null)
     */
    public void update(String text, @Nullable ChatFormatting formatting) {
        if (!text.equals(this.text) || formatting != this.formatting) {
            this.text = text;
            this.formatting = formatting;
            this.dirty = true;
        }
    }

    /**
     * Update just the text. Marks cache as dirty if changed.
     * 
     * @param text New text content
     */
    public void update(String text) {
        this.update(text, this.formatting);
    }

    /**
     * Get the cached Component, rebuilding if necessary.
     * 
     * @return The cached Component
     */
    public MutableComponent get() {
        if (this.dirty || this.cached == null) {
            MutableComponent component = Component.literal(this.text);
            if (this.formatting != null) {
                component = component.withStyle(this.formatting);
            }

            this.cached = component;
            this.dirty = false;
        }

        return this.cached;
    }

    /**
     * Force invalidation of the cache. Next get() will rebuild.
     */
    public void invalidate() {
        this.dirty = true;
    }
}
