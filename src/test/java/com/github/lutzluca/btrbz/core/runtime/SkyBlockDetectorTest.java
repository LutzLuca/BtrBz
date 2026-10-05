package com.github.lutzluca.btrbz.core.runtime;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import net.hypixel.data.type.GameType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SkyBlockDetectorTest {
    private final Queue<Runnable> clientTasks = new ArrayDeque<>();
    private final List<Boolean> activationChanges = new ArrayList<>();
    private final Activation activation = new Activation(() -> true, () -> false, this.activationChanges::add);
    private final List<Optional<String>> profileLocations = new ArrayList<>();
    private final SkyBlockDetector tracker = new SkyBlockDetector(this.activation,
        this.profileLocations::add, this.clientTasks::add);

    @Nested
    @DisplayName("official location updates")
    class Locations {
        @Test
        void appliesConfirmationOnlyOnTheClientThread() {
            SkyBlockDetectorTest.this.tracker.beginConnection();
            SkyBlockDetectorTest.this.clientTasks.remove().run();
            SkyBlockDetectorTest.this.tracker.onLocation("miniA", Optional.of(GameType.SKYBLOCK));
            Assertions.assertFalse(SkyBlockDetectorTest.this.activation.isActive());
            SkyBlockDetectorTest.this.clientTasks.remove().run();
            Assertions.assertTrue(SkyBlockDetectorTest.this.activation.isActive());
            Assertions.assertEquals(List.of(Optional.empty(), Optional.of("miniA")),
                SkyBlockDetectorTest.this.profileLocations);
        }

        @Test
        void serverChangesReachProfileTrackingWithoutChangingActivation() {
            SkyBlockDetectorTest.this.tracker.beginConnection();
            SkyBlockDetectorTest.this.clientTasks.remove().run();
            SkyBlockDetectorTest.this.tracker.onLocation("miniA", Optional.of(GameType.SKYBLOCK));
            SkyBlockDetectorTest.this.clientTasks.remove().run();
            SkyBlockDetectorTest.this.tracker.onLocation("miniB", Optional.of(GameType.SKYBLOCK));
            SkyBlockDetectorTest.this.clientTasks.remove().run();

            Assertions.assertEquals(List.of(true), SkyBlockDetectorTest.this.activationChanges);
            Assertions.assertEquals(List.of(Optional.empty(), Optional.of("miniA"), Optional.of("miniB")),
                SkyBlockDetectorTest.this.profileLocations);
        }

        @Test
        void missingLocationOrAnotherGameDeactivates() {
            SkyBlockDetectorTest.this.tracker.beginConnection();
            SkyBlockDetectorTest.this.clientTasks.remove().run();
            SkyBlockDetectorTest.this.tracker.onLocation("miniA", Optional.of(GameType.SKYBLOCK));
            SkyBlockDetectorTest.this.clientTasks.remove().run();
            SkyBlockDetectorTest.this.tracker.onLocation("miniA", Optional.empty());
            SkyBlockDetectorTest.this.clientTasks.remove().run();
            Assertions.assertFalse(SkyBlockDetectorTest.this.activation.isActive());
            SkyBlockDetectorTest.this.tracker.onLocation("miniA", Optional.of(GameType.SKYBLOCK));
            SkyBlockDetectorTest.this.clientTasks.remove().run();
            SkyBlockDetectorTest.this.tracker.onLocation("miniA", Optional.of(GameType.BEDWARS));
            SkyBlockDetectorTest.this.clientTasks.remove().run();
            Assertions.assertFalse(SkyBlockDetectorTest.this.activation.isActive());
            Assertions.assertTrue(SkyBlockDetectorTest.this.activation.isEnabled());
        }
    }

    @Nested
    @DisplayName("connection ownership")
    class Connections {
        @Test
        void queuedConfirmationCannotSurviveADisconnect() {
            SkyBlockDetectorTest.this.tracker.beginConnection();
            SkyBlockDetectorTest.this.clientTasks.remove().run();
            SkyBlockDetectorTest.this.tracker.onLocation("miniA", Optional.of(GameType.SKYBLOCK));
            SkyBlockDetectorTest.this.tracker.endConnection();
            while (!SkyBlockDetectorTest.this.clientTasks.isEmpty()) {
                SkyBlockDetectorTest.this.clientTasks.remove().run();
            }
            Assertions.assertFalse(SkyBlockDetectorTest.this.activation.isActive());
            Assertions.assertFalse(SkyBlockDetectorTest.this.profileLocations.contains(Optional.of("miniA")));
        }

        @Test
        void queuedConfirmationCannotAffectTheNextConnection() {
            SkyBlockDetectorTest.this.tracker.beginConnection();
            SkyBlockDetectorTest.this.clientTasks.remove().run();
            SkyBlockDetectorTest.this.tracker.onLocation("miniA", Optional.of(GameType.SKYBLOCK));
            var oldLocation = SkyBlockDetectorTest.this.clientTasks.remove();
            SkyBlockDetectorTest.this.tracker.endConnection();
            SkyBlockDetectorTest.this.clientTasks.remove().run();
            SkyBlockDetectorTest.this.tracker.beginConnection();
            SkyBlockDetectorTest.this.clientTasks.remove().run();
            oldLocation.run();
            Assertions.assertFalse(SkyBlockDetectorTest.this.activation.isActive());
            SkyBlockDetectorTest.this.tracker.onLocation("miniA", Optional.of(GameType.SKYBLOCK));
            SkyBlockDetectorTest.this.clientTasks.remove().run();
            Assertions.assertTrue(SkyBlockDetectorTest.this.activation.isActive());
        }

        @Test
        void oldQueuedUnknownLocationCannotDeactivateANewerConnection() {
            SkyBlockDetectorTest.this.tracker.beginConnection();
            SkyBlockDetectorTest.this.clientTasks.remove().run();
            SkyBlockDetectorTest.this.tracker.onLocation("miniA", Optional.empty());
            var oldLocation = SkyBlockDetectorTest.this.clientTasks.remove();
            SkyBlockDetectorTest.this.tracker.endConnection();
            while (!SkyBlockDetectorTest.this.clientTasks.isEmpty()) {
                SkyBlockDetectorTest.this.clientTasks.remove().run();
            }
            SkyBlockDetectorTest.this.tracker.beginConnection();
            SkyBlockDetectorTest.this.clientTasks.remove().run();
            SkyBlockDetectorTest.this.tracker.onLocation("miniA", Optional.of(GameType.SKYBLOCK));
            SkyBlockDetectorTest.this.clientTasks.remove().run();
            oldLocation.run();
            Assertions.assertTrue(SkyBlockDetectorTest.this.activation.isActive());
            Assertions.assertEquals(Optional.of("miniA"), SkyBlockDetectorTest.this.profileLocations.getLast());
        }

        @Test
        void reconfigurationKeepsConfirmationUntilLocationChangesGameType() {
            SkyBlockDetectorTest.this.tracker.beginConnection();
            SkyBlockDetectorTest.this.clientTasks.remove().run();
            SkyBlockDetectorTest.this.tracker.onLocation("miniA", Optional.of(GameType.SKYBLOCK));
            SkyBlockDetectorTest.this.clientTasks.remove().run();

            SkyBlockDetectorTest.this.tracker.beginConnection();
            SkyBlockDetectorTest.this.clientTasks.remove().run();
            Assertions.assertTrue(SkyBlockDetectorTest.this.activation.isActive());
            SkyBlockDetectorTest.this.tracker.onLocation("miniA", Optional.of(GameType.SKYBLOCK));
            SkyBlockDetectorTest.this.clientTasks.remove().run();
            Assertions.assertTrue(SkyBlockDetectorTest.this.activation.isActive());
            SkyBlockDetectorTest.this.tracker.onLocation("miniA", Optional.of(GameType.BEDWARS));
            SkyBlockDetectorTest.this.clientTasks.remove().run();
            Assertions.assertFalse(SkyBlockDetectorTest.this.activation.isActive());
        }

        @Test
        void rapidReconnectStillClearsConfirmationWhenEarlierTasksBecomeObsolete() {
            SkyBlockDetectorTest.this.tracker.beginConnection();
            SkyBlockDetectorTest.this.clientTasks.remove().run();
            SkyBlockDetectorTest.this.tracker.onLocation("miniA", Optional.of(GameType.SKYBLOCK));
            SkyBlockDetectorTest.this.clientTasks.remove().run();

            SkyBlockDetectorTest.this.tracker.endConnection();
            SkyBlockDetectorTest.this.tracker.beginConnection();
            SkyBlockDetectorTest.this.tracker.beginConnection();
            while (!SkyBlockDetectorTest.this.clientTasks.isEmpty()) {
                SkyBlockDetectorTest.this.clientTasks.remove().run();
            }

            Assertions.assertFalse(SkyBlockDetectorTest.this.activation.isActive());
        }
    }
}
