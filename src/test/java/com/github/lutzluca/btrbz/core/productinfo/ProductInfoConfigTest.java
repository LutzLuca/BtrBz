package com.github.lutzluca.btrbz.core.productinfo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.lutzluca.btrbz.core.config.Config;
import com.github.lutzluca.btrbz.core.AlertManager.Alert;
import com.github.lutzluca.btrbz.core.productinfo.ProductInfoConfig.Site;
import com.github.lutzluca.btrbz.core.widgets.bookmarks.BookmarksWidgetConfig.BookmarkedItem;
import com.github.lutzluca.btrbz.data.IndexedProduct;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;

class ProductInfoConfigTest {
    private final Gson gson = new GsonBuilder()
        .registerTypeAdapter(Alert.class, new Alert.GsonAdapter())
        .registerTypeAdapter(BookmarkedItem.class, new BookmarkedItem.GsonAdapter())
        .registerTypeAdapter(IndexedProduct.class, new IndexedProduct.GsonAdapter())
        .create();

    @Test
    void keepsExistingDefaults() {
        var config = new ProductInfoConfig();

        assertTrue(config.enabled);
        assertTrue(config.itemClickEnabled);
        assertTrue(config.ctrlShiftEnabled);
        assertTrue(config.ctrlShiftOnBazaarItems);
        assertFalse(config.showOutsideBazaar);
        assertTrue(config.priceTooltipEnabled);
        assertEquals(Site.SkyblockBz, config.site);
    }

    @Test
    void preservesSerializedSiteNames() {
        for (var site : Site.values()) {
            var config = new Config();
            config.productInfo.site = site;

            var json = this.gson.toJson(config);
            var restored = this.gson.fromJson(json, Config.class);

            assertTrue(json.contains("\"productInfo\":"));
            assertTrue(json.contains("\"site\":\"" + site.name() + "\""));
            assertEquals(site, restored.productInfo.site);
        }
    }
}
