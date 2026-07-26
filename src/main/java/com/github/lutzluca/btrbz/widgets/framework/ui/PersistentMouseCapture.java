package com.github.lutzluca.btrbz.widgets.framework.ui;

/**
 * Implemented by rebuilt widget descendants whose mouse capture is backed by
 * state outside the component instance.
 */
public interface PersistentMouseCapture {
    boolean hasPersistentMouseCapture();
}
