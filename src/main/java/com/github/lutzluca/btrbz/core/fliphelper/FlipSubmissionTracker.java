package com.github.lutzluca.btrbz.core.fliphelper;

import com.github.lutzluca.btrbz.data.ProductIdentity;
import com.github.lutzluca.btrbz.data.TimedStore;
import java.util.Optional;

public final class FlipSubmissionTracker implements AutoCloseable {

    private static final long PENDING_FLIP_TTL_MS = 15_000L;

    private final TimedStore<SubmittedFlip> pendingFlips;

    public FlipSubmissionTracker() {
        this.pendingFlips = new TimedStore<>(PENDING_FLIP_TTL_MS);
    }

    public void recordSubmittedFlip(ProductIdentity product, double pricePerUnit) {
        this.pendingFlips.add(new SubmittedFlip(product, pricePerUnit));
    }

    public Optional<SubmittedFlip> consume(ProductIdentity product) {
        return this.pendingFlips.removeFirstMatch(entry ->
            this.sameProduct(entry.product(), product)
        );
    }

    private boolean sameProduct(ProductIdentity first, ProductIdentity second) {
        if (first.bazaarProductId().isPresent() && second.bazaarProductId().isPresent()) {
            return first.bazaarProductId().get().equals(second.bazaarProductId().get());
        }
        return first.strippedName().equalsIgnoreCase(second.strippedName());
    }

    @Override
    public void close() {
        this.pendingFlips.close();
    }

    public record SubmittedFlip(ProductIdentity product, double pricePerUnit) { }
}
