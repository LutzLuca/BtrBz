package com.github.lutzluca.btrbz.data.conversions;

import com.github.lutzluca.btrbz.data.ConversionEvent;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import com.github.lutzluca.btrbz.data.IndexedProduct;
import com.github.lutzluca.btrbz.utils.Utils;
import io.vavr.control.Try;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

@Slf4j
public final class ConversionIndexService {

    private final ProductResolver resolver;
    private final List<Runnable> indexChangeListeners = new ArrayList<>();
    private final List<Consumer<ConversionEvent>> conversionEventListeners = new ArrayList<>();
    private final AtomicBoolean refreshInFlight = new AtomicBoolean(false);
    private final Map<ItemStack, Map<String, ProductIdentity>> resolvedStackCache = new WeakHashMap<>();

    private volatile ConversionIndex currentIndex;
    private volatile ConversionStatus.IndexLoadSource activeLoadSource;
    private volatile long indexRevision;
    private volatile Optional<String> lastSuccessfulRefreshAt = Optional.empty();
    private volatile Optional<ConversionRefreshException> lastFailure = Optional.empty();

    private record RemoteRefreshResult(
        ConversionIndex index,
        boolean changed,
        Optional<ConversionRefreshException> persistFailure
    ) { }

    public ConversionIndexService() {
        this(ConversionIndex.empty(), ConversionStatus.IndexLoadSource.Unavailable);
    }

    public ConversionIndexService(ConversionIndex initialIndex) {
        this(initialIndex, ConversionStatus.IndexLoadSource.Unavailable);
    }

    private ConversionIndexService(ConversionIndex initialIndex, ConversionStatus.IndexLoadSource source) {
        this.currentIndex = ConversionIndex.empty();
        this.activeLoadSource = ConversionStatus.IndexLoadSource.Unavailable;
        this.resolver = new ProductResolver(this);
        this.applyIndex(initialIndex, source);
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
                ConversionRefreshException.Phase.LoadBundledSeed,
                result.getCause().getMessage(),
                result.getCause());
        this.lastFailure = Optional.of(failure);
        this.applyIndex(ConversionIndex.empty(), ConversionStatus.IndexLoadSource.Unavailable);
        log.error("Failed to load any Bazaar conversion index", result.getCause());
        this.emitConversionEvent(new ConversionEvent(
                ConversionEvent.Kind.LoadFailure,
                false,
                failure.shortMessage()));
    }

    public boolean refreshConversionIndex(boolean manual) {
        if (!this.refreshInFlight.compareAndSet(false, true)) {
            this.emitConversionEvent(new ConversionEvent(
                    ConversionEvent.Kind.RefreshAlreadyRunning,
                    manual,
                    ""));
            return false;
        }

        CompletableFuture
                .supplyAsync(() -> Try.of(this::prepareRemoteRefresh))
                .thenAccept(result -> Minecraft.getInstance().execute(() -> {
                    try {
                        result
                            .onSuccess(refresh -> this.applyRemoteRefresh(refresh, manual))
                            .onFailure(err -> this.handleRefreshFailure(toRefreshException(err), manual));
                    } finally {
                        this.refreshInFlight.set(false);
                        log.info(
                            "Finished Bazaar conversion refresh (manual={}, result={})",
                            manual,
                            result.isSuccess() ? "success" : "failure");
                    }
                }));
        return true;
    }

    public ConversionStatus status() {
        return ConversionStatus.from(
                this.activeLoadSource,
                this.currentIndex,
                this.lastSuccessfulRefreshAt,
                this.lastFailure,
                this.refreshInFlight.get());
    }

    public ConversionIndex currentIndex() {
        return this.currentIndex;
    }

    public Optional<IndexedProduct> productById(String productId) {
        if (productId == null || productId.isBlank()) {
            return Optional.empty();
        }
        return this.currentIndex.product(productId);
    }

    public List<IndexedProduct> allProducts() {
        return this.currentIndex.allProducts();
    }

    public ProductIdentity resolveProduct(ItemStack stack) {
        return this.resolveProduct(stack, stack.getHoverName().getString());
    }

    public ProductIdentity resolveProduct(ItemStack stack, String displayNameEvidence) {
        return this.resolveProduct(
            stack,
            displayNameEvidence,
            Utils.matchingCustomNameLegacy(stack, displayNameEvidence).orElse(null)
        );
    }

    public ProductIdentity resolveProduct(
        ItemStack stack,
        String displayNameEvidence,
        @Nullable String formattedNameEvidence
    ) {
        var revision = this.indexRevision;
        var evidenceKey = Utils.cleanDisplayName(displayNameEvidence)
            + "|"
            + Optional.ofNullable(formattedNameEvidence).orElse("");
        // ItemStack identity is stable for current call sites. If reused stacks start mutating
        // custom data or lore in place, key this cache by an identity fingerprint instead.
        synchronized (this.resolvedStackCache) {
            var cached = this.resolvedStackCache
                    .getOrDefault(stack, Map.of())
                    .get(evidenceKey);
            if (cached != null && revision == this.indexRevision) {
                return cached;
            }
        }

        var resolved = this.resolver.resolveProduct(
            stack,
            Utils.cleanDisplayName(displayNameEvidence),
            formattedNameEvidence
        );
        synchronized (this.resolvedStackCache) {
            if (revision == this.indexRevision) {
                this.resolvedStackCache
                        .computeIfAbsent(stack, ignored -> new HashMap<>())
                        .put(evidenceKey, resolved);
            }
        }
        return resolved;
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

    public void addConversionEventListener(Consumer<ConversionEvent> listener) {
        this.conversionEventListeners.add(listener);
    }

    private RemoteRefreshResult prepareRemoteRefresh() throws ConversionRefreshException {
        var build = RemoteNeuConversionIndexBuilder.build(this.currentIndex);
        if (!build.changed()) {
            return new RemoteRefreshResult(build.index(), false, Optional.empty());
        }

        var persistResult = ConversionLoader.persistIndex(build.index());
        var persistFailure = persistResult
            .failed()
            .map(err -> new ConversionRefreshException(
                ConversionRefreshException.Phase.Persist,
                err.getMessage(),
                err
            ))
            .toJavaOptional();

        return new RemoteRefreshResult(build.index(), true, persistFailure);
    }

    private void applyRemoteRefresh(RemoteRefreshResult result, boolean manual) {
        this.lastSuccessfulRefreshAt = Optional.of(Instant.now().toString());
        this.lastFailure = result.persistFailure();
        if (result.changed()) {
            this.applyIndex(result.index(), ConversionStatus.IndexLoadSource.RemoteRefresh);
        } else {
            log.debug("Remote conversion refresh unchanged; keeping active conversion index");
        }

        if (result.persistFailure().isPresent()) {
            var failure = result.persistFailure().get();
            log.warn("Refreshed Bazaar conversion index but failed to persist local cache", failure);
            this.emitConversionEvent(new ConversionEvent(
                    ConversionEvent.Kind.PersistFailure,
                    manual,
                    failure.shortMessage()));
        } else {
            this.emitConversionEvent(new ConversionEvent(
                    ConversionEvent.Kind.RefreshSuccess,
                    manual,
                    ""));
        }
    }

    private void applyIndex(ConversionIndex index, ConversionStatus.IndexLoadSource source) {
        this.currentIndex = index;
        this.activeLoadSource = source;
        this.clearResolvedStackCache();
        this.logIndexSummary(source, index);
        this.notifyIndexChanged();
    }

    private void clearResolvedStackCache() {
        synchronized (this.resolvedStackCache) {
            var size = this.resolvedStackCache.size();
            this.resolvedStackCache.clear();
            this.indexRevision++;
            log.trace("Cleared product identity cache with {} mappings", size);
        }
    }

    private void logIndexSummary(ConversionStatus.IndexLoadSource source, ConversionIndex index) {
        var counts = index.sourceCounts();
        log.debug(
                "Applied conversion index from {} ({} products, source counts: neu={}, derived={})",
                source,
                index.size(),
                counts.neu(),
                counts.derived());
        this.logDerivedMappings(index);
    }

    private void logDerivedMappings(ConversionIndex index) {
        if (!log.isDebugEnabled()) {
            return;
        }

        log.debug("Derived conversion mappings ({} entries):", index.sourceCounts().derived());
        index
                .products()
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue().source() instanceof ProductNameSource.Derived)
                .sorted((first, second) -> first.getKey().compareTo(second.getKey()))
                .forEach(entry -> log.debug(
                        "Derived conversion mapping: {} -> {}",
                        entry.getKey(),
                        entry.getValue().strippedName()));
    }

    private void handleRefreshFailure(ConversionRefreshException failure, boolean manual) {
        this.lastFailure = Optional.of(failure);
        log.error("Failed to refresh Bazaar conversion index; active index remains unchanged", failure);
        this.emitConversionEvent(new ConversionEvent(
                ConversionEvent.Kind.RefreshFailure,
                manual,
                failure.shortMessage()));
    }

    private void notifyIndexChanged() {
        List.copyOf(this.indexChangeListeners).forEach(listener -> Try.run(listener::run)
                .onFailure(err -> log.error("Conversion index listener failed", err)));
    }

    private void emitConversionEvent(ConversionEvent event) {
        List.copyOf(this.conversionEventListeners).forEach(listener -> Try.run(() -> listener.accept(event))
                .onFailure(err -> log.error("Conversion event listener failed", err)));
    }

    private static ConversionRefreshException toRefreshException(Throwable err) {
        if (err instanceof ConversionRefreshException exc) {
            return exc;
        }
        return new ConversionRefreshException(ConversionRefreshException.Phase.Parse, err.getMessage(), err);
    }
}
