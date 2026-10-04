package com.github.lutzluca.btrbz.core.runtime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ProfileTrackerTest {
    private static final UUID MANGO = UUID.fromString("57ae3cb0-fa01-4eb2-bc51-34529412d3bb");
    private static final UUID PEAR = UUID.fromString("0f053cff-1f1f-4f6c-ad16-b1a486bb84de");

    private final PriorityQueue<Scheduled> tasks = new PriorityQueue<>(Comparator.comparingLong(Scheduled::tick));
    private final List<ProfileTracker.Notice> notices = new ArrayList<>();
    private final List<Optional<UUID>> identitiesAtReset = new ArrayList<>();
    private boolean enabled = true;
    private boolean canSend = true;
    private int requests;
    private long tick;
    private final ProfileTracker tracker = new ProfileTracker(() -> this.enabled,
        (delay, task) -> this.tasks.add(new Scheduled(this.tick + delay, task)),
        () -> {
            this.requests++;
            return this.canSend;
        }, this::recordReset, this.notices::add);

    @Test
    void uuidChangesResetOnceWhileDuplicatesTransfersAndFreshBaselinesDoNot() {
        this.tracker.onLocation(Optional.of("miniA"));
        this.tracker.onMessage("ProfileId: " + MANGO);
        this.tracker.onMessage("Profile ID: 1-1-1-1-1");
        Assertions.assertTrue(this.tracker.status().lastKnownId().isEmpty());
        this.tracker.onMessage("Profile ID: " + MANGO);
        this.advance(200);
        Assertions.assertEquals(0, this.requests);

        this.tracker.onLocation(Optional.of("miniB"));
        this.tracker.onMessage("Profile ID: " + MANGO);
        this.tracker.onMessage("Profile ID: " + PEAR);
        this.tracker.onMessage("Profile ID: " + PEAR);
        this.advance(300);

        Assertions.assertEquals(List.of(Optional.of(PEAR)), this.identitiesAtReset);
        Assertions.assertEquals(0, this.requests);
        Assertions.assertEquals(ProfileTracker.Verification.Verified, this.tracker.status().verification());

        this.tracker.onLocation(Optional.empty());
        this.tracker.onLocation(Optional.of("miniC"));
        this.tracker.onMessage("Profile ID: " + MANGO);
        Assertions.assertEquals(1, this.identitiesAtReset.size());
    }

    @Test
    void fallbackRunsOnceAndFailureKeepsIdentityUntilAValidMessageArrives() {
        this.tracker.onLocation(Optional.of("miniA"));
        this.tracker.onMessage("Profile ID: " + MANGO);
        this.tracker.onLocation(Optional.of("miniB"));
        this.advance(199);
        Assertions.assertEquals(0, this.requests);
        this.advance(1);
        Assertions.assertEquals(1, this.requests);
        Assertions.assertEquals(ProfileTracker.Verification.WaitingForReply, this.tracker.status().verification());
        this.advance(99);
        Assertions.assertTrue(this.notices.isEmpty());
        this.advance(1);

        Assertions.assertEquals(ProfileTracker.Verification.Failed, this.tracker.status().verification());
        Assertions.assertEquals(Optional.of(MANGO), this.tracker.status().lastKnownId());
        Assertions.assertTrue(this.identitiesAtReset.isEmpty());
        Assertions.assertEquals(1, this.notices.size());
        Assertions.assertTrue(this.notices.getFirst().offerReset());
        this.advance(1000);
        Assertions.assertEquals(1, this.requests);
        Assertions.assertEquals(1, this.notices.size());

        this.tracker.onMessage("Profile ID: " + PEAR);
        Assertions.assertEquals(List.of(Optional.of(PEAR)), this.identitiesAtReset);
        Assertions.assertEquals(ProfileTracker.Verification.Verified, this.tracker.status().verification());
    }

    @Test
    void supersededDisconnectedAndDisabledAttemptsCannotSendOrWarn() {
        this.tracker.onLocation(Optional.of("miniA"));
        this.advance(100);
        this.tracker.onLocation(Optional.of("miniB"));
        this.advance(100);
        Assertions.assertEquals(0, this.requests);
        this.tracker.onMessage("Profile ID: " + MANGO);
        this.advance(300);
        Assertions.assertEquals(0, this.requests);

        this.tracker.onLocation(Optional.of("miniC"));
        this.advance(200);
        this.tracker.onLocation(Optional.empty());
        this.tracker.onMessage("Profile ID: " + PEAR);
        this.advance(100);
        Assertions.assertEquals(1, this.requests);
        Assertions.assertTrue(this.tracker.status().lastKnownId().isEmpty());
        Assertions.assertTrue(this.notices.isEmpty());

        this.tracker.onLocation(Optional.of("miniD"));
        this.advance(200);
        this.enabled = false;
        this.tracker.refreshEnabled();
        this.advance(100);
        Assertions.assertEquals(2, this.requests);
        Assertions.assertTrue(this.notices.isEmpty());
        this.enabled = true;
        this.tracker.refreshEnabled();
        this.advance(200);
        Assertions.assertEquals(3, this.requests);
    }

    @Test
    void forcedResetClearsTheBaselineEvenWhenTheCommandCannotBeSent() {
        this.tracker.onLocation(Optional.of("miniA"));
        this.tracker.onMessage("Profile ID: " + MANGO);
        this.canSend = false;
        this.tracker.forceReset();

        Assertions.assertEquals(List.of(Optional.empty()), this.identitiesAtReset);
        Assertions.assertTrue(this.tracker.status().lastKnownId().isEmpty());
        Assertions.assertEquals(1, this.requests);
        Assertions.assertEquals(2, this.notices.size());
        Assertions.assertFalse(this.notices.getFirst().offerReset());
        Assertions.assertTrue(this.notices.getLast().offerReset());
        this.advance(1000);
        Assertions.assertEquals(2, this.notices.size());

        this.canSend = true;
        this.tracker.forceReset();
        this.tracker.onMessage("Profile ID: " + PEAR);
        this.advance(100);
        Assertions.assertEquals(2, this.identitiesAtReset.size());
        Assertions.assertEquals(Optional.of(PEAR), this.tracker.status().lastKnownId());
        Assertions.assertEquals(ProfileTracker.Verification.Verified, this.tracker.status().verification());
        Assertions.assertEquals(3, this.notices.size());
    }

    private void recordReset() {
        this.identitiesAtReset.add(this.tracker.status().lastKnownId());
    }

    private void advance(int ticks) {
        long target = this.tick + ticks;
        while (!this.tasks.isEmpty() && this.tasks.peek().tick() <= target) {
            var scheduled = this.tasks.remove();
            this.tick = scheduled.tick();
            scheduled.task().run();
        }
        this.tick = target;
    }

    private record Scheduled(long tick, Runnable task) {}
}
