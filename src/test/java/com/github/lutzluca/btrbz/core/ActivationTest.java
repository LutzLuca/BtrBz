package com.github.lutzluca.btrbz.core;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ActivationTest {
    private final List<Boolean> changes = new ArrayList<>();
    private boolean enabled = true;
    private boolean alwaysActive;
    private final Activation activation = new Activation(
        () -> this.enabled,
        () -> this.alwaysActive,
        this.changes::add);

    private void setEnabled(boolean enabled) {
        this.enabled = enabled;
        this.activation.refresh();
    }

    @Nested
    @DisplayName("manual setting and SkyBlock confirmation")
    class Conditions {
        @Test
        void startsEnabledButInactiveUntilConfirmed() {
            Assertions.assertTrue(ActivationTest.this.activation.isEnabled());
            Assertions.assertFalse(ActivationTest.this.activation.isActive());
            ActivationTest.this.setEnabled(true);
            Assertions.assertFalse(ActivationTest.this.activation.isActive());
            Assertions.assertTrue(ActivationTest.this.changes.isEmpty());
            ActivationTest.this.activation.setSkyBlockConfirmed(true);
            Assertions.assertTrue(ActivationTest.this.activation.isActive());
            Assertions.assertEquals(List.of(true), ActivationTest.this.changes);
        }

        @Test
        void alwaysActiveBypassesSkyBlockConfirmation() {
            ActivationTest.this.alwaysActive = true;
            ActivationTest.this.activation.refresh();
            Assertions.assertTrue(ActivationTest.this.activation.isActive());
            Assertions.assertEquals("Enabled; always active.", ActivationTest.this.activation.description());

            ActivationTest.this.activation.setSkyBlockConfirmed(false);
            Assertions.assertTrue(ActivationTest.this.activation.isActive());

            ActivationTest.this.alwaysActive = false;
            ActivationTest.this.activation.refresh();
            Assertions.assertFalse(ActivationTest.this.activation.isActive());
            Assertions.assertEquals(List.of(true, false), ActivationTest.this.changes);
        }

        @Test
        void manualDisablementOverridesAlwaysActive() {
            ActivationTest.this.alwaysActive = true;
            ActivationTest.this.enabled = false;
            ActivationTest.this.activation.refresh();
            Assertions.assertFalse(ActivationTest.this.activation.isActive());
            Assertions.assertEquals("Disabled manually.", ActivationTest.this.activation.description());
        }

        @Test
        void disablingPreservesLocationAndReenablingStartsFresh() {
            ActivationTest.this.activation.setSkyBlockConfirmed(true);
            long firstRun = ActivationTest.this.activation.generation();
            ActivationTest.this.setEnabled(false);
            Assertions.assertFalse(ActivationTest.this.activation.isActive());
            Assertions.assertEquals("Disabled manually.", ActivationTest.this.activation.description());
            ActivationTest.this.setEnabled(true);
            Assertions.assertTrue(ActivationTest.this.activation.isActive());
            Assertions.assertNotEquals(firstRun, ActivationTest.this.activation.generation());
            Assertions.assertEquals(List.of(true, false, true), ActivationTest.this.changes);
        }

        @Test
        void leavingWhileDisabledCannotReactivateOutsideSkyBlock() {
            ActivationTest.this.activation.setSkyBlockConfirmed(true);
            ActivationTest.this.setEnabled(false);
            ActivationTest.this.activation.setSkyBlockConfirmed(false);
            ActivationTest.this.setEnabled(true);
            Assertions.assertTrue(ActivationTest.this.activation.isEnabled());
            Assertions.assertFalse(ActivationTest.this.activation.isActive());
            Assertions.assertTrue(ActivationTest.this.activation.description().startsWith("Enabled; inactive"));
            Assertions.assertEquals(List.of(true, false), ActivationTest.this.changes);
        }

        @Test
        void locationUpdatesNeverOverrideManualDisablement() {
            var disabled = new Activation(() -> false, () -> false, ActivationTest.this.changes::add);
            disabled.setSkyBlockConfirmed(true);
            disabled.setSkyBlockConfirmed(false);
            Assertions.assertFalse(disabled.isEnabled());
            Assertions.assertFalse(disabled.isActive());
            Assertions.assertTrue(ActivationTest.this.changes.isEmpty());
        }
    }

    @Nested
    @DisplayName("activation transitions")
    class Transitions {
        @Test
        void readsExternalSettingAndRefreshesTheTransitionExactlyOnce() {
            ActivationTest.this.activation.setSkyBlockConfirmed(true);
            long run = ActivationTest.this.activation.generation();

            ActivationTest.this.enabled = false;
            Assertions.assertFalse(ActivationTest.this.activation.isEnabled());
            ActivationTest.this.activation.refresh();
            ActivationTest.this.activation.refresh();

            Assertions.assertFalse(ActivationTest.this.activation.isActive());
            Assertions.assertEquals(run + 1, ActivationTest.this.activation.generation());
            Assertions.assertEquals(List.of(true, false), ActivationTest.this.changes);
        }

        @Test
        void constructionDoesNotReadConfigBeforeItIsLoaded() {
            var activation = new Activation(
                () -> Assertions.fail("Configuration is not loaded yet"),
                () -> Assertions.fail("Configuration is not loaded yet"),
                _ -> Assertions.fail("Construction must not activate features"));
            Assertions.assertFalse(activation.isActive());
            Assertions.assertEquals(0, activation.generation());
        }

        @Test
        void repeatedUpdatesDoNotRestartOrCancelWorkAgain() {
            ActivationTest.this.activation.setSkyBlockConfirmed(true);
            long run = ActivationTest.this.activation.generation();
            ActivationTest.this.activation.setSkyBlockConfirmed(true);
            ActivationTest.this.setEnabled(true);
            Assertions.assertEquals(run, ActivationTest.this.activation.generation());
            ActivationTest.this.activation.setSkyBlockConfirmed(false);
            ActivationTest.this.activation.setSkyBlockConfirmed(false);
            ActivationTest.this.setEnabled(false);
            Assertions.assertEquals(List.of(true, false), ActivationTest.this.changes);
        }

        @Test
        void closesTheGateBeforeRunningCancellation() {
            var holder = new Activation[1];
            holder[0] = new Activation(() -> ActivationTest.this.enabled, () -> false,
                active -> Assertions.assertEquals(active, holder[0].isActive()));
            holder[0].setSkyBlockConfirmed(true);
            ActivationTest.this.enabled = false;
            holder[0].refresh();
            ActivationTest.this.enabled = true;
            holder[0].refresh();
            holder[0].setSkyBlockConfirmed(false);
        }
    }
}
