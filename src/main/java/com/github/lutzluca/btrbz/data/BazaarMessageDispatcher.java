package com.github.lutzluca.btrbz.data;

import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class BazaarMessageDispatcher {

    public final Map<Class<? extends BazaarMessage>, List<Consumer<? extends BazaarMessage>>> listeners = new HashMap<>();

    public <T extends BazaarMessage> void on(Class<T> type, Consumer<T> listener) {
        this.listeners.computeIfAbsent(type, key -> new ArrayList<>()).add(listener);
    }

    @SuppressWarnings("unchecked")
    private <T extends BazaarMessage> void dispatch(T msg) {
        log.debug("Dispatching bazaar message: {}", msg);

        Optional.ofNullable(this.listeners.get(msg.getClass())).ifPresent(listeners -> {
            listeners.forEach(listener -> ((Consumer<T>) listener).accept(msg));
        });
    }

    public void handleChatMessage(String msg) {
        if (!msg.contains("[Bazaar]")) {
            return;
        }

        OrderInfoParser.parseBazaarMessage(msg).onSuccess(this::dispatch);
    }

    public sealed interface BazaarMessage permits BazaarMessage.OrderSetup,
        BazaarMessage.OrderFilled,
        BazaarMessage.InstaSell,
        BazaarMessage.InstaBuy,
        BazaarMessage.OrderFlipped
    {

        // `total` is the rounded total price once the price reaches a certain limit
        // suppose the real price was 1,123,456.6 coins, total would be 1,123,457 coins

        record OrderSetup(OrderType type, int volume, String productName, double total)
            implements BazaarMessage { }

        record OrderFilled(OrderType type, int volume, String productName)
            implements BazaarMessage { }

        record InstaSell(int volume, String productName, double total) implements BazaarMessage { }

        record InstaBuy(int volume, String productName, double total) implements BazaarMessage { }

        record OrderFlipped(int volume, String productName, double profit)
            implements BazaarMessage { }
    }
}
