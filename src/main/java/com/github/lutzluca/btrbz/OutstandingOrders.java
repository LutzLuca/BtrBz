package com.github.lutzluca.btrbz;

import com.github.lutzluca.btrbz.SetOrderInfo.ChatOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class OutstandingOrders {

    private OutstandingOrders() { }

    private static final List<SetOrderInfo> ORDERS =
        Collections.synchronizedList(new ArrayList<>());
    private static final long EXPIRE_MS = 15_000L;

    private static final ScheduledExecutorService SCHED =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "btrbz-outstanding-cleanup");
            t.setDaemon(true);
            return t;
        });

    static {
        SCHED.scheduleAtFixedRate(OutstandingOrders::cleanupExpired, 5, 5, TimeUnit.SECONDS);
    }

    public static void add(SetOrderInfo info) {
        cleanupExpired();
        ORDERS.add(info);
    }

    public static void cleanupExpired() {
        long now = System.currentTimeMillis();
        synchronized (ORDERS) {
            ORDERS.removeIf(info -> now - info.timestampMillis() > EXPIRE_MS);
        }
    }

    public static Optional<TrackedOrder> matchAndRemove(ChatOrder chatInfo) {
        cleanupExpired();
        synchronized (ORDERS) {
            Iterator<SetOrderInfo> it = ORDERS.iterator();
            while (it.hasNext()) {
                SetOrderInfo order = it.next();
                if (matches(order, chatInfo)) {
                    it.remove();
                    System.out.println(
                        "found match for chat info order: " + chatInfo + " " + order);
                    return Optional.of(new TrackedOrder(order));
                }
            }
        }
        return Optional.empty();
    }

    private static boolean matches(SetOrderInfo setOrder, ChatOrder chatOrder) {
        return setOrder.type() == chatOrder.type()
            && setOrder.productName().equalsIgnoreCase(chatOrder.productName())
            && setOrder.volume() == chatOrder.volume()
            && Double.compare(setOrder.total(), chatOrder.total()) == 0;
    }

    public static List<SetOrderInfo> list() {
        cleanupExpired();
        return List.copyOf(ORDERS);
    }
}
