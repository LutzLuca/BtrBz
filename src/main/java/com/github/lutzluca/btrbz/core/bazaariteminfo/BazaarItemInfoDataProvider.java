package com.github.lutzluca.btrbz.core.bazaariteminfo;

import com.github.lutzluca.btrbz.core.bazaariteminfo.BazaarItemInfoViewData.CurrentPrices;
import com.github.lutzluca.btrbz.core.bazaariteminfo.BazaarItemInfoViewData.Empty;
import com.github.lutzluca.btrbz.core.bazaariteminfo.BazaarItemInfoViewData.Failure;
import com.github.lutzluca.btrbz.core.bazaariteminfo.BazaarItemInfoViewData.History;
import com.github.lutzluca.btrbz.core.bazaariteminfo.BazaarItemInfoViewData.LoadState;
import com.github.lutzluca.btrbz.core.bazaariteminfo.BazaarItemInfoViewData.Loading;
import com.github.lutzluca.btrbz.core.bazaariteminfo.BazaarItemInfoViewData.ScreenState;
import com.github.lutzluca.btrbz.core.bazaariteminfo.BazaarItemInfoViewData.Success;
import com.github.lutzluca.coflnet.BazaarHistoryPoint;
import com.github.lutzluca.coflnet.BazaarSnapshot;
import com.github.lutzluca.coflnet.CoflnetBazaarClient;
import com.github.lutzluca.coflnet.HistoryRange;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * Coordinates snapshot and history requests while shielding the screen from stale asynchronous
 * completions. The provider owns no cache; Coflnet caching and pacing remain SDK concerns.
 */
public final class BazaarItemInfoDataProvider {
    private final CoflnetBazaarClient client;
    private final Consumer<ScreenState> stateListener;

    private long requestGeneration;
    private volatile ScreenState state;

    public BazaarItemInfoDataProvider(CoflnetBazaarClient client) {
        this(client, ignored -> {});
    }

    public BazaarItemInfoDataProvider(
        CoflnetBazaarClient client,
        Consumer<ScreenState> stateListener
    ) {
        this.client = Objects.requireNonNull(client, "client");
        this.stateListener = Objects.requireNonNull(stateListener, "stateListener");
    }

    public Optional<ScreenState> state() {
        return Optional.ofNullable(this.state);
    }

    /** Starts an independent snapshot/history refresh for the requested item and range. */
    public void load(String itemTag, HistoryRange range) {
        Objects.requireNonNull(itemTag, "itemTag");
        Objects.requireNonNull(range, "range");

        String normalizedTag = itemTag.trim();
        if (normalizedTag.isEmpty()) {
            throw new IllegalArgumentException("itemTag must not be blank");
        }

        final long generation;
        final ScreenState loading;
        synchronized (this) {
            generation = ++this.requestGeneration;
            var previous = this.state;
            boolean sameItem = previous != null && previous.itemTag().equals(normalizedTag);
            boolean sameHistory = sameItem && previous.range().equals(range);

            loading = new ScreenState(
                normalizedTag,
                range,
                new Loading<>(sameItem ? previous.currentPrices().retainedValue() : Optional.empty()),
                new Loading<>(sameHistory ? previous.history().retainedValue() : Optional.empty())
            );
            this.state = loading;
        }
        this.notifyState(loading);

        CompletionStage<Optional<BazaarSnapshot>> snapshotRequest;
        try {
            snapshotRequest = this.client.snapshot(normalizedTag);
        } catch (RuntimeException exception) {
            this.completeSnapshot(generation, null, exception);
            snapshotRequest = null;
        }

        if (snapshotRequest != null) {
            snapshotRequest.whenComplete((snapshot, error) ->
                this.completeSnapshot(generation, snapshot, error));
        }

        CompletionStage<List<BazaarHistoryPoint>> historyRequest;
        try {
            historyRequest = this.client.history(normalizedTag, range);
        } catch (RuntimeException exception) {
            this.completeHistory(generation, null, exception);
            historyRequest = null;
        }

        if (historyRequest != null) {
            historyRequest.whenComplete((history, error) ->
                this.completeHistory(generation, history, error));
        }
    }

    private void completeSnapshot(
        long generation,
        Optional<BazaarSnapshot> snapshot,
        Throwable error
    ) {
        this.update(generation, current -> {
            LoadState<CurrentPrices> next;
            if (error != null) {
                next = new Failure<>(errorMessage(error), current.currentPrices().retainedValue());
            } else if (snapshot == null || snapshot.isEmpty()) {
                next = new Empty<>();
            } else {
                next = new Success<>(CurrentPrices.from(snapshot.get()));
            }

            return new ScreenState(current.itemTag(), current.range(), next, current.history());
        });
    }

    private void completeHistory(
        long generation,
        List<BazaarHistoryPoint> history,
        Throwable error
    ) {
        this.update(generation, current -> {
            LoadState<History> next;
            if (error != null) {
                next = new Failure<>(errorMessage(error), current.history().retainedValue());
            } else {
                var presentation = new History(history == null ? List.of() : history);
                next = presentation.points().isEmpty() ? new Empty<>() : new Success<>(presentation);
            }

            return new ScreenState(current.itemTag(), current.range(), current.currentPrices(), next);
        });
    }

    private void update(long generation, java.util.function.UnaryOperator<ScreenState> update) {
        ScreenState next;
        synchronized (this) {
            if (generation != this.requestGeneration || this.state == null) {
                return;
            }
            next = update.apply(this.state);
            this.state = next;
        }
        this.notifyState(next);
    }

    private void notifyState(ScreenState next) {
        this.stateListener.accept(next);
    }

    static String errorMessage(Throwable error) {
        Throwable current = Objects.requireNonNull(error, "error");
        while ((current instanceof CompletionException || current instanceof ExecutionException)
            && current.getCause() != null) {
            current = current.getCause();
        }

        String message = current.getMessage();
        return message == null || message.isBlank()
            ? current.getClass().getSimpleName()
            : message;
    }
}
