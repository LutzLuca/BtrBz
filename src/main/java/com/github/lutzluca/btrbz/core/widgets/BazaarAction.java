package com.github.lutzluca.btrbz.core.widgets;

import com.github.lutzluca.btrbz.data.OrderModels.TrackedOrderId;

public sealed interface BazaarAction permits BazaarAction.ReorderTracked,
    BazaarAction.SelectPrice, BazaarAction.ApplyPreset, BazaarAction.OpenBookmark,
    BazaarAction.RemoveBookmark, BazaarAction.ReorderBookmark {

    record ReorderTracked(TrackedOrderId id, int insertionIndex) implements BazaarAction {}
    record SelectPrice(double price, boolean copyOnly) implements BazaarAction {}
    record ApplyPreset(OrderPreset preset) implements BazaarAction {}
    record OpenBookmark(String productId) implements BazaarAction {}
    record RemoveBookmark(String productId) implements BazaarAction {}
    record ReorderBookmark(String productId, int insertionIndex) implements BazaarAction {}
}
