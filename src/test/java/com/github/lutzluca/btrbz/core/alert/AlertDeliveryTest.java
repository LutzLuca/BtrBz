package com.github.lutzluca.btrbz.core.alert;

import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.IndexedProduct;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class AlertDeliveryTest {
    private final AlertConfig config = new AlertConfig();
    private final long[] now = {1_000};
    private final ArrayList<Integer> summaries = new ArrayList<>();
    private final ArrayList<ReachedAlert> individual = new ArrayList<>();
    private final AlertManager manager = new AlertManager(new BazaarData(), () -> this.config, () -> {}, _ -> {},
        () -> this.now[0]);

    @Test
    void groupsEventsOnReturnAndKeepsThemPendingUntilExplicitReview() {
        var delivery = this.delivery();
        delivery.tick(true, false);
        var first = this.event(100);
        delivery.reached(first);
        delivery.reached(this.event(101));
        this.now[0] = 3_999;
        delivery.tick(true, false);
        Assertions.assertTrue(this.summaries.isEmpty());
        this.now[0] = 4_000;
        delivery.tick(true, false);
        Assertions.assertEquals(List.of(2), this.summaries);
        Assertions.assertTrue(this.individual.isEmpty());
        Assertions.assertEquals(2, this.manager.pendingCount());

        delivery.disconnect();
        this.now[0] += 1_000;
        delivery.tick(true, false);
        this.now[0] += 3_000;
        delivery.tick(true, false);
        Assertions.assertEquals(List.of(2, 2), this.summaries);

        this.manager.dismiss(first.id());
        this.manager.dismissAll();
        delivery.disconnect();
        delivery.tick(true, false);
        this.now[0] += 3_000;
        delivery.tick(true, false);
        Assertions.assertEquals(List.of(2, 2), this.summaries);
        Assertions.assertEquals(2, this.manager.history().size());
    }

    @Test
    void retriesFailedSummaryDelivery() {
        this.event(100);
        int[] attempts = {0};
        var delivery = new AlertDelivery(this.manager, () -> this.config.remindPending, this.individual::add, count -> {
            attempts[0]++;
            if (attempts[0] == 1) {
                return false;
            }
            this.summaries.add(count);
            return true;
        }, () -> this.now[0]);
        delivery.tick(true, false);
        this.now[0] += 3_000;
        delivery.tick(true, false);
        this.now[0] += 4_999;
        delivery.tick(true, false);
        Assertions.assertEquals(1, attempts[0]);
        this.now[0]++;
        delivery.tick(true, false);
        Assertions.assertEquals(List.of(1), this.summaries);

    }

    @Test
    void viewingInboxSuppressesSummariesWhileNewTriggersStillNotify() {
        var delivery = this.delivery();
        delivery.tick(true, false);
        this.now[0] += 3_000;
        delivery.tick(true, false);
        delivery.reached(this.event(100, AlertLifecycle.Once));
        Assertions.assertEquals(1, this.individual.size());
        this.now[0] += AlertDelivery.REMINDER_INTERVAL_MS;
        delivery.tick(true, true);
        Assertions.assertTrue(this.summaries.isEmpty());
        this.now[0] += AlertDelivery.REMINDER_INTERVAL_MS;
        delivery.tick(true, false);
        Assertions.assertEquals(List.of(1), this.summaries);

        delivery.tick(true, false);
        delivery.reached(this.event(101));
        Assertions.assertEquals(2, this.individual.size());
        this.now[0] += AlertDelivery.REMINDER_INTERVAL_MS;
        delivery.tick(true, false);
        Assertions.assertEquals(List.of(1, 2), this.summaries);
        this.now[0] += AlertDelivery.REMINDER_INTERVAL_MS;
        delivery.tick(true, false);
        Assertions.assertEquals(List.of(1, 2, 2), this.summaries,
            "recurring summaries include every pending event");
        this.manager.dismissAll();
        this.now[0] += AlertDelivery.REMINDER_INTERVAL_MS;
        delivery.tick(true, false);
        Assertions.assertEquals(List.of(1, 2, 2), this.summaries);
    }

    @Test
    void retryingARecurringReminderIncludesEveryPendingEvent() {
        this.event(100);
        this.event(101, AlertLifecycle.Once);
        var attempts = new ArrayList<Integer>();
        var delivery = new AlertDelivery(this.manager, () -> this.config.remindPending, this.individual::add, count -> {
            attempts.add(count);
            return attempts.size() != 2;
        }, () -> this.now[0]);
        delivery.tick(true, false);
        this.now[0] += 3_000;
        delivery.tick(true, false);
        this.now[0] += AlertDelivery.REMINDER_INTERVAL_MS;
        delivery.tick(true, false);
        this.now[0] += 5_000;
        delivery.tick(true, false);
        Assertions.assertEquals(List.of(2, 2, 2), attempts);
        Assertions.assertEquals(2, this.manager.pendingCount());
    }

    private AlertDelivery delivery() {
        return new AlertDelivery(this.manager, () -> this.config.remindPending, this.individual::add, count -> {
            this.summaries.add(count);
            return true;
        }, () -> this.now[0]);
    }

    @Test
    void disablingRemindersCancelsRetriesWithoutConsumingEventsOrMutingTriggers() {
        this.event(100);
        int[] attempts = {0};
        var delivery = new AlertDelivery(this.manager, () -> this.config.remindPending, this.individual::add, count -> {
            if (++attempts[0] == 1) {
                return false;
            }
            this.summaries.add(count);
            return true;
        }, () -> this.now[0]);
        delivery.tick(true, false);
        this.now[0] += 3_000;
        delivery.tick(true, false);
        Assertions.assertEquals(1, attempts[0]);

        this.config.remindPending = false;
        delivery.tick(true, false);
        this.now[0] += AlertDelivery.REMINDER_INTERVAL_MS;
        delivery.tick(true, false);
        Assertions.assertEquals(1, attempts[0]);
        Assertions.assertEquals(1, this.manager.pendingCount());

        delivery.disconnect();
        var newEvent = this.event(101);
        delivery.reached(newEvent);
        delivery.tick(true, false);
        this.now[0] += 3_000;
        delivery.tick(true, false);
        Assertions.assertEquals(List.of(newEvent), this.individual);
        Assertions.assertEquals(1, attempts[0]);
        Assertions.assertEquals(2, this.manager.pendingCount());

        this.config.remindPending = true;
        delivery.tick(true, false);
        this.now[0] += AlertDelivery.REMINDER_INTERVAL_MS - 1;
        delivery.tick(true, false);
        Assertions.assertEquals(1, attempts[0]);
        this.now[0]++;
        delivery.tick(true, false);
        Assertions.assertEquals(List.of(2), this.summaries);
        Assertions.assertEquals(2, this.manager.pendingCount());
    }

    @Test
    void newTriggersDoNotPostponeThePendingReminder() {
        var delivery = this.delivery();
        delivery.tick(true, false);
        this.now[0] += 3_000;
        delivery.tick(true, false);
        delivery.reached(this.event(100));
        long due = this.now[0] + AlertDelivery.REMINDER_INTERVAL_MS;
        this.now[0] = due - 1;
        delivery.reached(this.event(101));
        delivery.tick(true, false);
        Assertions.assertTrue(this.summaries.isEmpty());
        this.now[0] = due;
        delivery.tick(true, false);
        Assertions.assertEquals(List.of(2), this.summaries);
    }

    @Test
    void dismissingOneEventKeepsRemindingAboutTheOthers() {
        this.event(100);
        var reminded = this.event(101, AlertLifecycle.Once);
        var delivery = this.delivery();
        delivery.tick(true, false);
        this.now[0] += 3_000;
        delivery.tick(true, false);
        this.manager.dismiss(reminded.id());
        this.now[0] += AlertDelivery.REMINDER_INTERVAL_MS;
        delivery.tick(true, false);
        Assertions.assertEquals(List.of(2, 1), this.summaries);
        Assertions.assertEquals(1, this.manager.pendingCount());
    }

    private ReachedAlert event(double threshold) {
        return this.event(threshold, AlertLifecycle.Once);
    }

    private ReachedAlert event(double threshold, AlertLifecycle lifecycle) {
        var alert = this.manager.saveAlert(null, new AlertDefinition(this.now[0],
            new IndexedProduct("DIAMOND", "Diamond"), new AlertCondition.Price(
                new AlertType(AlertType.PriceSource.Buy, AlertType.Direction.Below), threshold)),
            lifecycle)
            .get();
        var entry = new ReachedAlert(alert, this.now[0], new AlertCondition.Observation.Price(90));
        this.config.reachedAlerts.addFirst(entry);
        return entry;
    }
}
