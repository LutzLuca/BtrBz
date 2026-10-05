package com.github.lutzluca.btrbz.core.runtime;

import com.github.lutzluca.btrbz.utils.Utils;
import io.vavr.control.Try;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;

/** Client-thread profile identity and bounded verification driven by accepted SkyBlock locations. */
@Slf4j
public final class ProfileTracker {
    private static final int AUTOMATIC_MESSAGE_TICKS = 200;
    private static final int RESPONSE_TICKS = 100;
    private static final String PROFILE_ID_PREFIX = "Profile ID: ";

    private final BooleanSupplier enabled;
    private final BiConsumer<Integer, Runnable> schedule;
    private final BooleanSupplier requestProfileId;
    private final Runnable resetSession;
    private final Consumer<Notice> notifyPlayer;
    private Optional<String> server = Optional.empty();
    private Optional<UUID> profileId = Optional.empty();
    private Verification verification = Verification.Inactive;
    private long verificationGeneration;

    public ProfileTracker(
        BooleanSupplier enabled,
        BiConsumer<Integer, Runnable> schedule,
        BooleanSupplier requestProfileId,
        Runnable resetSession,
        Consumer<Notice> notifyPlayer
    ) {
        this.enabled = enabled;
        this.schedule = schedule;
        this.requestProfileId = requestProfileId;
        this.resetSession = resetSession;
        this.notifyPlayer = notifyPlayer;
    }

    public void register() {
        // Fabric calls every ALLOW_GAME listener, including when another listener hides the message.
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (!overlay) {
                this.onMessage(Utils.stripFormattingCodes(message.getString()));
            }
            return true;
        });
    }

    public void onLocation(Optional<String> skyBlockServer) {
        boolean changed = !this.server.equals(skyBlockServer);
        this.server = skyBlockServer;
        if (this.server.isEmpty()) {
            this.clearIdentity(Verification.Inactive);
        } else if (!this.enabled.getAsBoolean()) {
            this.clearIdentity(Verification.Disabled);
        } else if (changed || this.verification == Verification.Disabled) {
            this.beginVerification();
        }
    }

    /** Follow the existing global enable setting without tying identity to market availability. */
    public void refreshEnabled() {
        this.onLocation(this.server);
    }

    public void onMessage(String message) {
        if (this.server.isEmpty() || !this.enabled.getAsBoolean()) {
            return;
        }
        if (!message.startsWith(PROFILE_ID_PREFIX)) {
            return;
        }
        String value = message.substring(PROFILE_ID_PREFIX.length());
        var parsed = Try.of(() -> UUID.fromString(value)).toJavaOptional()
            .filter(id -> id.toString().equalsIgnoreCase(value));
        if (parsed.isEmpty()) {
            return;
        }

        var previous = this.profileId;
        var next = parsed.get();
        this.profileId = parsed;
        this.verification = Verification.Verified;
        this.verificationGeneration++;
        log.debug("SkyBlock profile verified: profileId={}, server={}", next, this.server.orElseThrow());
        if (previous.isPresent() && !previous.get().equals(next)) {
            log.info("SkyBlock profile changed: previous={}, current={}", previous.get(), next);
            this.resetSession.run();
            this.notifyPlayer.accept(new Notice("SkyBlock profile changed. Session state reset.", false));
        }
    }

    public void forceReset() {
        boolean recheck = this.server.isPresent() && this.enabled.getAsBoolean();
        this.clearIdentity(recheck
            ? Verification.WaitingForReply
            : this.server.isPresent() ? Verification.Disabled : Verification.Inactive);
        log.info("Forced session reset: server={}", this.server.orElse("unknown"));
        this.resetSession.run();
        this.notifyPlayer.accept(new Notice(recheck
            ? "Session state reset. Rechecking your SkyBlock profile."
            : "Session state reset. Profile verification is currently inactive.", false));
        if (recheck) {
            this.sendRequest(this.verificationGeneration);
        }
    }

    public Status status() {
        return new Status(this.profileId, this.verification);
    }

    private void clearIdentity(Verification next) {
        this.verificationGeneration++;
        this.profileId = Optional.empty();
        this.verification = next;
    }

    private void beginVerification() {
        long generation = ++this.verificationGeneration;
        this.verification = Verification.WaitingForMessage;
        this.schedule.accept(AUTOMATIC_MESSAGE_TICKS, () -> this.sendRequest(generation));
    }

    private boolean isCurrent(long generation) {
        return generation == this.verificationGeneration
            && this.server.isPresent()
            && this.enabled.getAsBoolean();
    }

    private void sendRequest(long generation) {
        if (!this.isCurrent(generation)) {
            return;
        }
        boolean sent = Try.of(this.requestProfileId::getAsBoolean)
            .onFailure(error -> log.warn("Failed to request SkyBlock profile ID", error))
            .getOrElse(false);
        if (!sent) {
            this.verificationFailed(generation);
            return;
        }
        this.verification = Verification.WaitingForReply;
        log.debug("Requested SkyBlock profile ID: server={}", this.server.orElseThrow());
        this.schedule.accept(RESPONSE_TICKS, () -> this.verificationFailed(generation));
    }

    private void verificationFailed(long generation) {
        if (!this.isCurrent(generation)) {
            return;
        }
        this.verification = Verification.Failed;
        log.warn("SkyBlock profile verification failed: server={}, lastKnownId={}",
            this.server.orElseThrow(), this.profileId);
        this.notifyPlayer.accept(new Notice(
            "Could not verify your SkyBlock profile. Existing state was kept.", true));
    }

    public enum Verification {
        Inactive,
        Disabled,
        WaitingForMessage,
        WaitingForReply,
        Verified,
        Failed
    }

    public record Status(Optional<UUID> lastKnownId, Verification verification) {
        public String description() {
            String identity = "Last known profile: " + this.lastKnownId.map(UUID::toString).orElse("unknown") + ". ";
            return identity + switch (this.verification) {
                case Inactive -> "Tracking inactive outside confirmed SkyBlock.";
                case Disabled -> "Profile tracking disabled with the mod.";
                case WaitingForMessage -> "Awaiting automatic profile confirmation.";
                case WaitingForReply -> "Awaiting /profileid reply.";
                case Verified -> "Verified on the current server.";
                case Failed -> "Verification failed. Use /btrbz profile reset if needed.";
            };
        }
    }

    public record Notice(String message, boolean offerReset) {}
}
