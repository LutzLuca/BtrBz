package com.github.lutzluca.btrbz.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import com.github.lutzluca.btrbz.data.OrderModels.ChatOrderConfirmationInfo;
import com.github.lutzluca.btrbz.data.OrderModels.OutstandingOrderInfo;
import com.github.lutzluca.btrbz.utils.Util;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OutstandingOrderStore {
    private static final long TIME_TO_LIVE_MS = 15_000L;
    private final List<OutstandingOrder> orders = new ArrayList<>();

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "btrbz-outstanding-cleanup");
                t.setDaemon(true);
                return t;
            });

    public OutstandingOrderStore() {
        this.scheduler.scheduleAtFixedRate(this::cleanupExpired, TIME_TO_LIVE_MS, TIME_TO_LIVE_MS,
                TimeUnit.MILLISECONDS);
    }


    public void add(OutstandingOrderInfo info) {
        synchronized (this.orders) {
            this.orders
                    .add(new OutstandingOrder(info, System.currentTimeMillis() + TIME_TO_LIVE_MS));
        }
    }

    public Optional<OutstandingOrderInfo> removeMatching(ChatOrderConfirmationInfo chatInfo) {
        synchronized (this.orders) {
            for (var it = this.orders.iterator(); it.hasNext();) {
                var curr = it.next();
                if (curr.expiresAt < System.currentTimeMillis()) {
                    log.trace("removed expired outstanding order: {}", curr);
                    it.remove();
                    continue;
                }

                if (curr.info.matches(chatInfo)) {
                    it.remove();
                    return Optional.of(curr.info);
                }
            }
        }

        return Optional.empty();
    }


    public List<OutstandingOrderInfo> orders() {
        var now = System.currentTimeMillis();
        synchronized (this.orders) {
            return this.orders.stream().filter(info -> info.expiresAt < now)
                    .map(OutstandingOrder::info).toList();
        }
    }


    private void cleanupExpired() {
        long now = System.currentTimeMillis();
        synchronized (this.orders) {
            var expired = Util.removeIfAndReturn(this.orders, info -> info.expiresAt < now);
            log.trace("removed {} expired outstanding order: {}", expired.size(),
                    expired.toString());
        }
    }

    private record OutstandingOrder(OutstandingOrderInfo info, long expiresAt) {
    }
}


