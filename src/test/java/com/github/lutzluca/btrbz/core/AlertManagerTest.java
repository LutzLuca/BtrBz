package com.github.lutzluca.btrbz.core;

import com.github.lutzluca.btrbz.core.AlertManager.AlertConfig;
import com.github.lutzluca.btrbz.core.alert.AlertDefinition;
import com.github.lutzluca.btrbz.core.alert.AlertType;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.IndexedProduct;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class AlertManagerTest {

    private static final IndexedProduct DIAMOND = new IndexedProduct("ENCHANTED_DIAMOND", "Enchanted Diamond");
    private static final IndexedProduct GOLD = new IndexedProduct("ENCHANTED_GOLD", "Enchanted Gold");

    @Test
    void createsEditsAndRemovesWithStableIdentityAndOrder() {
        var config = new AlertConfig();
        var saves = new int[1];
        var manager = new AlertManager(new BazaarData(), () -> config, () -> saves[0]++);

        var diamond = manager.saveAlert(null, definition(1_000L, DIAMOND, 100.0)).get();
        var gold = manager.saveAlert(null, definition(2_000L, GOLD, 200.0)).get();
        diamond.remindedAfter = 500L;
        var revisionBeforeEdit = manager.changes().revision();

        var edited = manager.saveAlert(diamond.id, definition(3_000L, DIAMOND, 100.0)).get();

        Assertions.assertEquals(diamond.id, edited.id);
        Assertions.assertEquals(3_000L, edited.createdAt);
        Assertions.assertEquals(-1L, edited.remindedAfter);
        Assertions.assertEquals(diamond.id, manager.alerts().get(0).id);
        Assertions.assertEquals(gold.id, manager.alerts().get(1).id);
        Assertions.assertTrue(manager.changes().revision() > revisionBeforeEdit);
        Assertions.assertTrue(manager.removeAlert(diamond.id));
        Assertions.assertFalse(manager.removeAlert(diamond.id));
        Assertions.assertEquals(4, saves[0]);
        Assertions.assertEquals(gold.id, manager.alerts().getFirst().id);
        Assertions.assertThrows(UnsupportedOperationException.class, () -> manager.alerts().clear());
    }

    @Test
    void rejectsDuplicatesAndStaleEditsWithoutMutatingOrSaving() {
        var config = new AlertConfig();
        var saves = new int[1];
        var manager = new AlertManager(new BazaarData(), () -> config, () -> saves[0]++);
        var first = manager.saveAlert(null, definition(1_000L, DIAMOND, 100.0)).get();
        var revision = manager.changes().revision();

        Assertions.assertTrue(manager.saveAlert(null, definition(2_000L, DIAMOND, 100.0)).isFailure());
        Assertions.assertTrue(manager.saveAlert(UUID.randomUUID(), definition(2_000L, GOLD, 200.0)).isFailure());

        Assertions.assertEquals(1, saves[0]);
        Assertions.assertEquals(revision, manager.changes().revision());
        Assertions.assertEquals(1, manager.alerts().size());
        Assertions.assertEquals(first.id, manager.alerts().getFirst().id);
    }

    @Test
    void validatesEverySavedPriceBeforeMutation() {
        var config = new AlertConfig();
        var manager = new AlertManager(new BazaarData(), () -> config, () -> {});

        Assertions.assertTrue(manager.saveAlert(null, definition(1_000L, DIAMOND, Double.NaN)).isFailure());
        Assertions.assertTrue(
            manager.saveAlert(null, definition(1_000L, DIAMOND, Double.POSITIVE_INFINITY)).isFailure());
        Assertions.assertTrue(manager.saveAlert(null, definition(1_000L, DIAMOND, 0.0)).isFailure());
        Assertions.assertTrue(manager.alerts().isEmpty());
    }

    @Test
    void failedPersistenceRollsBackTheInMemoryMutation() {
        var config = new AlertConfig();
        var manager = new AlertManager(new BazaarData(), () -> config, () -> {
            throw new IllegalStateException("disk unavailable");
        });

        var result = manager.saveAlert(null, definition(1_000L, DIAMOND, 100.0));

        Assertions.assertTrue(result.isFailure());
        Assertions.assertEquals("disk unavailable", result.getCause().getMessage());
        Assertions.assertTrue(manager.alerts().isEmpty());
        Assertions.assertEquals(0, manager.changes().revision());
    }

    @Test
    void generatedIdsAreIndependent() {
        var config = new AlertConfig();
        var manager = new AlertManager(new BazaarData(), () -> config, () -> {});

        var first = manager.saveAlert(null, definition(1_000L, DIAMOND, 100.0)).get();
        var second = manager.saveAlert(null, definition(2_000L, DIAMOND, 101.0)).get();

        Assertions.assertNotEquals(first.id, second.id);
    }

    private static AlertDefinition definition(long timestamp, IndexedProduct product, double price) {
        return new AlertDefinition(timestamp, product, AlertType.InstaBuy, price);
    }
}
