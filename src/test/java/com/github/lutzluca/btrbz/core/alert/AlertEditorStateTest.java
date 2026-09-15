package com.github.lutzluca.btrbz.core.alert;

import com.github.lutzluca.btrbz.core.alert.AlertType.PriceSource;
import com.github.lutzluca.btrbz.core.alert.AlertType.Direction;
import com.github.lutzluca.btrbz.data.IndexedProduct;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class AlertEditorStateTest {
    private static final IndexedProduct DIAMOND = new IndexedProduct("DIAMOND", "§bDiamond");
    private static final IndexedProduct GOLD = new IndexedProduct("GOLD", "§6Gold");

    @Test
    void selectionClosesSearchAndCancelChangePreservesTheDraft() {
        var editor = new AlertEditorState();
        editor.query("dimond");
        editor.select(DIAMOND);
        editor.expression("buy * 1.1");
        Assertions.assertFalse(editor.searching());
        Assertions.assertTrue(editor.hasSelection());
        Assertions.assertEquals("", editor.query());

        editor.beginSearch();
        editor.query("gold");
        Assertions.assertFalse(editor.hasSelection());
        editor.cancelSearch();
        Assertions.assertTrue(editor.hasSelection());
        Assertions.assertEquals(DIAMOND, editor.product());
        Assertions.assertEquals("buy * 1.1", editor.expression());
    }

    @Test
    void changingProductsClearsOldThresholdButKeepsChosenTrigger() {
        var editor = new AlertEditorState();
        var id = UUID.randomUUID();
        editor.edit(id, DIAMOND, new AlertType(PriceSource.Sell, Direction.Above), 1_000);
        editor.beginSearch();
        editor.select(GOLD);
        Assertions.assertEquals("", editor.expression());
        Assertions.assertEquals(id, editor.editingId());
        Assertions.assertEquals(PriceSource.Sell, editor.source());
        Assertions.assertEquals(Direction.Above, editor.direction());
    }

    @Test
    void metadataRefreshAndReselectingSameIdentityDoNotReplaceExpression() {
        var editor = new AlertEditorState();
        editor.select(DIAMOND);
        editor.expression("sell - 0.04");
        var refreshed = new IndexedProduct("DIAMOND", "§aDiamond");
        editor.refreshProduct(refreshed);
        Assertions.assertEquals(refreshed, editor.product());
        Assertions.assertEquals("sell - 0.04", editor.expression());
        editor.beginSearch();
        editor.select(refreshed);
        Assertions.assertEquals("sell - 0.04", editor.expression());
    }

    @Test
    void editingUsesOneDecimalAndNewAlertClearsIdentity() {
        var editor = new AlertEditorState();
        editor.edit(UUID.randomUUID(), DIAMOND, new AlertType(PriceSource.Buy, Direction.Below), 8323.0000001);
        Assertions.assertEquals("8,323.0", editor.expression());
        Assertions.assertEquals(8323.0, editor.draft().resolve(null, 1_000L).get().price());
        Assertions.assertEquals(PriceSource.Buy, editor.source());
        Assertions.assertFalse(editor.searching());
        editor.reset();
        Assertions.assertNull(editor.editingId());
        Assertions.assertNull(editor.product());
        Assertions.assertTrue(editor.searching());
        Assertions.assertEquals("", editor.expression());
    }
}
