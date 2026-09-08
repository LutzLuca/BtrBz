package com.github.lutzluca.btrbz.core.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.github.lutzluca.btrbz.core.AlertManager.Alert;
import com.github.lutzluca.btrbz.core.widgets.bookmarks.BookmarksWidgetConfig.BookmarkedItem;
import com.github.lutzluca.btrbz.data.IndexedProduct;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ActivationConfigTest {
    private final Gson gson = new GsonBuilder()
        .registerTypeAdapter(Alert.class, new Alert.GsonAdapter())
        .registerTypeAdapter(BookmarkedItem.class, new BookmarkedItem.GsonAdapter())
        .registerTypeAdapter(IndexedProduct.class, new IndexedProduct.GsonAdapter())
        .create();

    @Test
    void defaultsToEnabledWhenTheSettingIsAbsent() {
        var config = this.gson.fromJson("{}", Config.class);
        Assertions.assertTrue(config.enabled);
    }

    @Test
    void restoresBothManualSettingsWithoutChangingOtherPreferences() {
        var disabled = this.gson.fromJson("{\"enabled\":false,\"tax\":2.5}", Config.class);
        Assertions.assertFalse(disabled.enabled);
        Assertions.assertEquals(2.5, disabled.tax);

        var restored = this.gson.fromJson(this.gson.toJson(disabled), Config.class);
        Assertions.assertFalse(restored.enabled);
        Assertions.assertEquals(2.5, restored.tax);

        var enabled = this.gson.fromJson("{\"enabled\":true,\"tax\":2.5}", Config.class);
        Assertions.assertTrue(enabled.enabled);
        Assertions.assertEquals(2.5, enabled.tax);
    }
}
