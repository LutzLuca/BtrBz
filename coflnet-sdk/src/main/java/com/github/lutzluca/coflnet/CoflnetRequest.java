package com.github.lutzluca.coflnet;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CoflnetRequest<T> implements AutoCloseable {
    private final CompletableFuture<T> future = new CompletableFuture<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Runnable release;

    CoflnetRequest(Runnable release) {
        this.release = release;
    }

    public CompletionStage<T> completion() {
        return this.future.minimalCompletionStage();
    }

    void succeed(T value) {
        this.future.complete(value);
    }

    void fail(Throwable failure) {
        this.future.completeExceptionally(failure);
    }

    @Override
    public void close() {
        if (this.closed.compareAndSet(false, true)) {
            this.future.cancel(false);
            this.release.run();
        }
    }
}
