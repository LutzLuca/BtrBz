package com.github.lutzluca.btrbz.core;

import java.util.ArrayDeque;
import java.util.Optional;
import java.util.Queue;
import net.hypixel.data.type.GameType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SkyBlockDetectorTest {
    private final Queue<Runnable> clientTasks = new ArrayDeque<>();
    private final Activation activation = new Activation(() -> true, () -> false, _ -> {});
    private final SkyBlockDetector tracker = new SkyBlockDetector(this.activation, this.clientTasks::add);

    @Nested
    @DisplayName("official location updates")
    class Locations {
        @Test
        void appliesConfirmationOnlyOnTheClientThread() {
            SkyBlockDetectorTest.this.tracker.beginConnection();
            SkyBlockDetectorTest.this.clientTasks.remove().run();
            SkyBlockDetectorTest.this.tracker.onLocation(Optional.of(GameType.SKYBLOCK));
            Assertions.assertFalse(SkyBlockDetectorTest.this.activation.isActive());
            SkyBlockDetectorTest.this.clientTasks.remove().run();
            Assertions.assertTrue(SkyBlockDetectorTest.this.activation.isActive());
        }

        @Test
        void missingLocationOrAnotherGameDeactivates() {
            SkyBlockDetectorTest.this.tracker.beginConnection();
            SkyBlockDetectorTest.this.clientTasks.remove().run();
            SkyBlockDetectorTest.this.tracker.onLocation(Optional.of(GameType.SKYBLOCK));
            SkyBlockDetectorTest.this.clientTasks.remove().run();
            SkyBlockDetectorTest.this.tracker.onLocation(Optional.empty());
            SkyBlockDetectorTest.this.clientTasks.remove().run();
            Assertions.assertFalse(SkyBlockDetectorTest.this.activation.isActive());
            SkyBlockDetectorTest.this.tracker.onLocation(Optional.of(GameType.SKYBLOCK));
            SkyBlockDetectorTest.this.clientTasks.remove().run();
            SkyBlockDetectorTest.this.tracker.onLocation(Optional.of(GameType.BEDWARS));
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
            SkyBlockDetectorTest.this.tracker.onLocation(Optional.of(GameType.SKYBLOCK));
            SkyBlockDetectorTest.this.tracker.endConnection();
            while (!SkyBlockDetectorTest.this.clientTasks.isEmpty()) {
                SkyBlockDetectorTest.this.clientTasks.remove().run();
            }
            Assertions.assertFalse(SkyBlockDetectorTest.this.activation.isActive());
        }

        @Test
        void queuedConfirmationCannotAffectTheNextConnection() {
            SkyBlockDetectorTest.this.tracker.beginConnection();
            SkyBlockDetectorTest.this.clientTasks.remove().run();
            SkyBlockDetectorTest.this.tracker.onLocation(Optional.of(GameType.SKYBLOCK));
            var oldLocation = SkyBlockDetectorTest.this.clientTasks.remove();
            SkyBlockDetectorTest.this.tracker.endConnection();
            SkyBlockDetectorTest.this.clientTasks.remove().run();
            SkyBlockDetectorTest.this.tracker.beginConnection();
            SkyBlockDetectorTest.this.clientTasks.remove().run();
            oldLocation.run();
            Assertions.assertFalse(SkyBlockDetectorTest.this.activation.isActive());
            SkyBlockDetectorTest.this.tracker.onLocation(Optional.of(GameType.SKYBLOCK));
            SkyBlockDetectorTest.this.clientTasks.remove().run();
            Assertions.assertTrue(SkyBlockDetectorTest.this.activation.isActive());
        }

        @Test
        void oldQueuedErrorCannotDeactivateANewerConnection() {
            SkyBlockDetectorTest.this.tracker.beginConnection();
            SkyBlockDetectorTest.this.clientTasks.remove().run();
            SkyBlockDetectorTest.this.tracker.onLocation(Optional.empty());
            var oldError = SkyBlockDetectorTest.this.clientTasks.remove();
            SkyBlockDetectorTest.this.tracker.endConnection();
            while (!SkyBlockDetectorTest.this.clientTasks.isEmpty()) {
                SkyBlockDetectorTest.this.clientTasks.remove().run();
            }
            SkyBlockDetectorTest.this.tracker.beginConnection();
            SkyBlockDetectorTest.this.clientTasks.remove().run();
            SkyBlockDetectorTest.this.tracker.onLocation(Optional.of(GameType.SKYBLOCK));
            SkyBlockDetectorTest.this.clientTasks.remove().run();
            oldError.run();
            Assertions.assertTrue(SkyBlockDetectorTest.this.activation.isActive());
        }

        @Test
        void reconfigurationKeepsConfirmationUntilLocationChangesGameType() {
            SkyBlockDetectorTest.this.tracker.beginConnection();
            SkyBlockDetectorTest.this.clientTasks.remove().run();
            SkyBlockDetectorTest.this.tracker.onLocation(Optional.of(GameType.SKYBLOCK));
            SkyBlockDetectorTest.this.clientTasks.remove().run();

            SkyBlockDetectorTest.this.tracker.beginConnection();
            SkyBlockDetectorTest.this.clientTasks.remove().run();
            Assertions.assertTrue(SkyBlockDetectorTest.this.activation.isActive());
            SkyBlockDetectorTest.this.tracker.onLocation(Optional.of(GameType.SKYBLOCK));
            SkyBlockDetectorTest.this.clientTasks.remove().run();
            Assertions.assertTrue(SkyBlockDetectorTest.this.activation.isActive());
            SkyBlockDetectorTest.this.tracker.onLocation(Optional.of(GameType.BEDWARS));
            SkyBlockDetectorTest.this.clientTasks.remove().run();
            Assertions.assertFalse(SkyBlockDetectorTest.this.activation.isActive());
        }

        @Test
        void rapidReconnectStillClearsConfirmationWhenEarlierTasksBecomeObsolete() {
            SkyBlockDetectorTest.this.tracker.beginConnection();
            SkyBlockDetectorTest.this.clientTasks.remove().run();
            SkyBlockDetectorTest.this.tracker.onLocation(Optional.of(GameType.SKYBLOCK));
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
