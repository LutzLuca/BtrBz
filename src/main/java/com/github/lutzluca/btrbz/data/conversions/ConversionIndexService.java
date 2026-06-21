package com.github.lutzluca.btrbz.data.conversions;

import com.github.lutzluca.btrbz.data.ProductIdentity;
import com.github.lutzluca.btrbz.data.ProductRef;
import com.github.lutzluca.btrbz.utils.MessageQueue;
import com.github.lutzluca.btrbz.utils.MessageQueue.Level;
import io.vavr.control.Try;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

@Slf4j
public final class ConversionIndexService {

    private final ProductResolver resolver;
    private final List<Runnable> indexChangeListeners = new ArrayList<>();
    private final AtomicBoolean refreshInFlight = new AtomicBoolean(false);

    private volatile ConversionIndex currentIndex;
    private volatile IndexLoadSource activeLoadSource;
    private volatile @Nullable String lastSuccessfulRefreshAt;
    private volatile Optional<ConversionRefreshException> lastFailure = Optional.empty();
    private boolean automaticFailureNotified;

    public ConversionIndexService() {
        this(ConversionIndex.empty(), IndexLoadSource.Unavailable);
    }

    public ConversionIndexService(ConversionIndex initialIndex) {
        this(initialIndex, IndexLoadSource.Unavailable);
    }

    private ConversionIndexService(ConversionIndex initialIndex, IndexLoadSource source) {
        this.currentIndex = ConversionIndexNormalizer.normalizeDerivedEntries(initialIndex);
        this.activeLoadSource = source;
        this.resolver = new ProductResolver(this, new ResolutionDiagnostics());
    }

    public void loadConversionIndex() {
        var result = ConversionLoader.loadSync();
        if (result.isSuccess()) {
            var loadResult = result.get();
            this.applyIndex(loadResult.index(), loadResult.source());
            this.lastFailure = Optional.empty();
            return;
        }

        var failure = new ConversionRefreshException(
            ConversionFailurePhase.LoadBundledSeed,
            result.getCause().getMessage(),
            result.getCause()
        );
        this.currentIndex = ConversionIndex.empty();
        this.activeLoadSource = IndexLoadSource.Unavailable;
        this.lastFailure = Optional.of(failure);
        log.error("Failed to load any Bazaar conversion index", result.getCause());
        MessageQueue.sendOrQueue(
            "Failed to load Bazaar conversions; some features may not work as expected. Try /btrbz conversions refresh.",
            Level.Error
        );
        this.notifyIndexChanged();
    }

    public boolean refreshConversionIndex(boolean manual) {
        if (!this.refreshInFlight.compareAndSet(false, true)) {
            if (manual) {
                MessageQueue.sendOrQueue("Bazaar conversion refresh is already running", Level.Info);
            }
            return false;
        }

        CompletableFuture
            .supplyAsync(() -> Try.of(() -> RemoteConversionIndexBuilder.build(this.currentIndex)))
            .thenAccept(result -> {
                this.refreshInFlight.set(false);
                result
                    .onSuccess(index -> this.applyRemoteRefresh(index, manual))
                    .onFailure(err -> this.handleRefreshFailure(toRefreshException(err), manual));
            });
        return true;
    }

    public ConversionStatus status() {
        return ConversionStatus.from(
            this.activeLoadSource,
            this.currentIndex,
            this.lastSuccessfulRefreshAt,
            this.lastFailure,
            this.refreshInFlight.get()
        );
    }

    public ConversionIndex currentIndex() {
        return this.currentIndex;
    }

    public Optional<ProductRef> productById(String productId) {
        if (productId == null || productId.isBlank()) {
            return Optional.empty();
        }
        return this.currentIndex.product(productId);
    }

    public ProductIdentity resolveProduct(ItemStack stack) {
        return this.resolver.resolveProduct(stack);
    }

    public ProductIdentity resolveProduct(@Nullable String rawProductId, String displayName) {
        return this.resolver.resolveProduct(rawProductId, displayName);
    }

    public ProductIdentity resolveProductName(String displayName) {
        return this.resolver.resolveProductName(displayName);
    }

    public void addIndexChangeListener(Runnable listener) {
        this.indexChangeListeners.add(listener);
    }

    public void removeIndexChangeListener(Runnable listener) {
        this.indexChangeListeners.remove(listener);
    }

    private void applyRemoteRefresh(ConversionIndex index, boolean manual) {
        this.applyIndex(index, IndexLoadSource.RemoteRefresh);
        this.lastSuccessfulRefreshAt = Instant.now().toString();
        this.lastFailure = Optional.empty();

        ConversionLoader.persistIndex(index)
            .onFailure(err -> {
                var failure = new ConversionRefreshException(
                    ConversionFailurePhase.Persist,
                    err.getMessage(),
                    err
                );
                this.lastFailure = Optional.of(failure);
                log.warn("Refreshed Bazaar conversion index but failed to persist local cache", err);
                if (manual) {
                    MessageQueue.sendOrQueue("Updated Bazaar conversions, but failed to cache them locally", Level.Warn);
                }
            });

        if (manual && this.lastFailure.isEmpty()) {
            MessageQueue.sendOrQueue("Updated Bazaar conversion index", Level.Info);
        }
    }

    private void applyIndex(ConversionIndex index, IndexLoadSource source) {
        this.currentIndex = ConversionIndexNormalizer.normalizeDerivedEntries(index);
        this.activeLoadSource = source;
        this.logIndexSummary(source, this.currentIndex);
        this.notifyIndexChanged();
    }

    private void logIndexSummary(IndexLoadSource source, ConversionIndex index) {
        var counts = index.sourceCounts();
        log.debug(
            "Applied conversion index from {} ({} products, source counts: hypixelItem={}, neu={}, derived={})",
            source,
            index.size(),
            counts.hypixelItem(),
            counts.neu(),
            counts.derived()
        );
    }

    private void handleRefreshFailure(ConversionRefreshException failure, boolean manual) {
        this.lastFailure = Optional.of(failure);
        log.error("Failed to refresh Bazaar conversion index; active index remains unchanged", failure);
        if (manual) {
            MessageQueue.sendOrQueue("Failed to refresh Bazaar conversions: " + failure.shortMessage(), Level.Warn);
            return;
        }

        if (!this.automaticFailureNotified) {
            this.automaticFailureNotified = true;
            MessageQueue.sendOrQueue(
                "BtrBz could not refresh Bazaar conversions; using bundled/cache data. Run /btrbz conversions status for details.",
                Level.Warn
            );
        }
    }

    private void notifyIndexChanged() {
        List.copyOf(this.indexChangeListeners).forEach(listener ->
            Try.run(listener::run)
                .onFailure(err -> log.error("Conversion index listener failed", err))
        );
    }

    private static ConversionRefreshException toRefreshException(Throwable err) {
        if (err instanceof ConversionRefreshException refreshException) {
            return refreshException;
        }
        return new ConversionRefreshException(ConversionFailurePhase.Parse, err.getMessage(), err);
    }
}
