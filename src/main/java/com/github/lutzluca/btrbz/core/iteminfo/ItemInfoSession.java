package com.github.lutzluca.btrbz.core.iteminfo;

import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.BazaarData.MarketSnapshot;
import com.github.lutzluca.btrbz.data.LiveProductSnapshot;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import com.github.lutzluca.coflnet.CoflnetClient;
import com.github.lutzluca.coflnet.CoflnetRequest;
import com.github.lutzluca.coflnet.HistoryQuery;
import com.github.lutzluca.coflnet.HistoryResponse;
import com.github.lutzluca.coflnet.MayorResponse;
import io.vavr.control.Try;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;

/** One logical opening. UI reinitialization never replaces this request owner. */
public final class ItemInfoSession implements AutoCloseable {
    private final BazaarData market;
    private final CoflnetClient client;
    private final BooleanSupplier current;
    private final Consumer<Runnable> dispatch;
    private final Clock clock;
    private final Consumer<MarketSnapshot> marketListener = this::marketChanged;
    private final Slot<HistoryResponse> history = new Slot<>();
    private final Slot<HistoryResponse> reference = new Slot<>();
    private final Slot<MayorResponse> mayors = new Slot<>();
    private @Nullable ProductIdentity product;
    private Optional<LiveProductSnapshot> live = Optional.empty();
    private ItemInfoRange range;
    private HistoryQuery query;
    private Instant referenceEnd;
    private boolean historyVisible = true;
    private boolean mayorVisible;
    private boolean closed;
    private long revision;
    private Runnable changed = () -> {};

    public ItemInfoSession(
        BazaarData market,
        CoflnetClient client,
        BooleanSupplier current,
        Consumer<Runnable> dispatch,
        Clock clock,
        ItemInfoRange initialRange,
        boolean showMayors
    ) {
        this.market = market;
        this.client = client;
        this.current = current;
        this.dispatch = dispatch;
        this.clock = clock;
        this.range = initialRange == null || initialRange == ItemInfoRange.Custom ? ItemInfoRange.Week : initialRange;
        this.referenceEnd = this.clock.instant();
        this.query = this.presetQuery(this.range, this.referenceEnd);
        this.mayorVisible = showMayors;
        this.market.addListener(this.marketListener);
    }

    public void onChanged(Runnable changed) {
        this.changed = changed;
    }

    public boolean isCurrent() {
        return !this.closed && this.current.getAsBoolean();
    }

    public Data data() {
        return new Data(this.product, this.live, this.range, this.query,
            this.history.result(), this.reference.result(), this.mayors.result(), this.revision);
    }

    public void select(ProductIdentity selected) {
        if (!this.isCurrent()) {
            return;
        }
        boolean sameProduct = this.product != null
            && this.product.bazaarProductId().equals(selected.bazaarProductId());
        this.product = selected;
        if (!sameProduct) {
            this.history.reset();
            this.reference.reset();
            this.mayors.reset();
            this.referenceEnd = this.clock.instant();
            if (this.range != ItemInfoRange.Custom) {
                this.query = this.presetQuery(this.range, this.referenceEnd);
            }
        }
        this.live = this.market.liveProduct(selected);
        this.requestNeeded(false);
        this.emit();
    }

    public void range(ItemInfoRange range) {
        if (range == ItemInfoRange.Custom || !this.isCurrent()) {
            return;
        }
        this.setQuery(range, this.presetQuery(range, this.clock.instant()));
    }

    private HistoryQuery presetQuery(ItemInfoRange range, Instant end) {
        return new HistoryQuery(end.minus(range.duration()), end, range.source());
    }

    public void customRange(Instant start, Instant end) {
        this.setQuery(ItemInfoRange.Custom, new HistoryQuery(start, end));
    }

    private void setQuery(ItemInfoRange range, HistoryQuery query) {
        if (!this.isCurrent()) {
            return;
        }
        if (this.query.equals(query)) {
            if (this.range != range) {
                this.range = range;
                this.emit();
            }
            return;
        }
        this.range = range;
        this.query = query;
        this.history.reset();
        this.mayors.reset();
        this.requestNeeded(false);
        this.emit();
    }

    public void historyVisible(boolean visible) {
        this.historyVisible = visible;
        if (visible) {
            this.requestNeeded(false);
        }
    }

    public void mayorVisible(boolean visible) {
        this.mayorVisible = visible;
        if (!visible) {
            this.mayors.cancel();
        } else {
            this.requestNeeded(false);
        }
        this.emit();
    }

    public void refresh() {
        if (!this.isCurrent()) {
            return;
        }
        this.referenceEnd = this.clock.instant();
        if (this.range != ItemInfoRange.Custom) {
            this.query = this.presetQuery(this.range, this.referenceEnd);
        }
        this.requestNeeded(true);
    }

    private void requestNeeded(boolean refresh) {
        if (!this.isCurrent() || this.product == null || this.product.bazaarProductId().isEmpty()) {
            return;
        }
        var id = this.product.bazaarProductId().orElseThrow();
        var week = this.presetQuery(ItemInfoRange.Week, this.referenceEnd);
        if (this.reference.needs(week) || refresh) {
            this.request(this.reference, week, () -> this.client.history(id, week, refresh));
        }
        var selected = this.query;
        if (this.historyVisible && (this.history.needs(selected) || refresh)) {
            this.request(this.history, selected, () -> this.client.history(id, selected, refresh));
        }
        if (this.historyVisible && this.mayorVisible && (this.mayors.needs(selected) || refresh)) {
            this.request(this.mayors, selected,
                () -> this.client.mayors(selected.start(), selected.end(), refresh));
        }
    }

    private <T> void request(Slot<T> slot, HistoryQuery query, Supplier<CoflnetRequest<T>> start) {
        if (slot.updating) {
            if (query.equals(slot.requestQuery)) {
                return;
            }
            slot.cancel();
        }
        long ticket = ++slot.ticket;
        slot.requestQuery = query;
        slot.updating = true;
        slot.failure = null;
        var attempted = Try.of(start::get);
        if (attempted.isFailure()) {
            slot.updating = false;
            slot.failure = attempted.getCause();
            this.emit();
            return;
        }
        var request = attempted.get();
        slot.request = request;
        request.completion().whenComplete((value, failure) -> this.dispatch.accept(() -> {
            request.close();
            if (!this.isCurrent() || slot.ticket != ticket || slot.request != request) {
                return;
            }
            slot.request = null;
            slot.updating = false;
            if (failure == null) {
                slot.value = value;
                slot.valueQuery = query;
                slot.failure = null;
            } else {
                slot.failure = failure instanceof CompletionException && failure.getCause() != null
                    ? failure.getCause() : failure;
            }
            this.emit();
        }));
        this.emit();
    }

    private void marketChanged(MarketSnapshot snapshot) {
        if (this.isCurrent()) {
            this.live = this.product == null ? Optional.empty() : snapshot.liveProduct(this.product);
            this.emit();
        }
    }

    private void emit() {
        if (this.isCurrent()) {
            this.revision++;
            this.changed.run();
        }
    }

    @Override
    public void close() {
        if (this.closed) {
            return;
        }
        this.closed = true;
        this.market.removeListener(this.marketListener);
        this.history.reset();
        this.reference.reset();
        this.mayors.reset();
        this.live = Optional.empty();
        this.changed = () -> {};
    }

    public record Result<T>(
        @Nullable T value, @Nullable HistoryQuery query, boolean updating, @Nullable Throwable failure
    ) {}

    public record Data(
        @Nullable ProductIdentity product,
        Optional<LiveProductSnapshot> live,
        ItemInfoRange range,
        HistoryQuery query,
        Result<HistoryResponse> history,
        Result<HistoryResponse> reference,
        Result<MayorResponse> mayors,
        long revision
    ) {}

    private static final class Slot<T> {
        private @Nullable T value;
        private @Nullable HistoryQuery valueQuery;
        private boolean updating;
        private @Nullable Throwable failure;
        private @Nullable CoflnetRequest<T> request;
        private @Nullable HistoryQuery requestQuery;
        private long ticket;

        private boolean needs(HistoryQuery query) {
            return this.value == null || !query.equals(this.valueQuery);
        }

        private Result<T> result() {
            return new Result<>(this.value, this.valueQuery, this.updating, this.failure);
        }

        private void cancel() {
            this.ticket++;
            if (this.request != null) {
                this.request.close();
                this.request = null;
            }
            this.updating = false;
            this.requestQuery = null;
        }

        private void reset() {
            this.cancel();
            this.value = null;
            this.valueQuery = null;
            this.failure = null;
        }
    }
}
