package com.github.lutzluca.btrbz;

import com.github.lutzluca.btrbz.core.BzOrderManager;
import com.github.lutzluca.btrbz.core.HighlightManager;
import com.github.lutzluca.btrbz.data.BazaarPoller;
import com.github.lutzluca.btrbz.data.ConversionLoader;
import com.github.lutzluca.btrbz.data.OrderInfoParser;
import com.github.lutzluca.btrbz.data.OrderModels.ChatOrderConfirmationInfo;
import com.github.lutzluca.btrbz.data.OrderModels.TrackedOrder;
import com.github.lutzluca.btrbz.utils.InventoryLoadWatcher;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

@Slf4j
public class BtrBz implements ClientModInitializer {

    public static final String MOD_ID = "btrbz";
    private static BtrBz instance;
    private BzOrderManager orderManager;
    private HighlightManager highlightManager;

    public static BzOrderManager orderManager() {
        return instance.orderManager;
    }

    public static HighlightManager highlightManager() {
        return instance.highlightManager;
    }

    @Override
    public void onInitializeClient() {
        instance = this;

        var conversions = ConversionLoader
            .initialize()
            .onSuccess(map -> log.info("Conversion mappings initialized ({} entries)", map.size()))
            .getOrElseThrow(err -> new RuntimeException(
                "Failed to initialize conversion mappings",
                err
            ));

        this.highlightManager = new HighlightManager();
        this.orderManager = new BzOrderManager(conversions, this.highlightManager::updateStatus);

        new BazaarPoller(this.orderManager::onBazaarUpdate);

        // @formatter:off
        new InventoryLoadWatcher(
            (screen) -> screen.getTitle().getString().equals("Your Bazaar Orders"), (slots) -> {
            final var FILTER = Set.of(Items.BLACK_STAINED_GLASS_PANE, Items.ARROW, Items.HOPPER);

            var parsed = slots
                .stream()
                .filter(slot -> !(slot.stack().isEmpty() || FILTER.contains(slot.stack().getItem())))
                .map(slot -> OrderInfoParser.parseOrderInfo(slot.stack(), slot.idx()).toJavaOptional())
                .flatMap(Optional::stream)
                .collect(Collectors.toList());

            this.orderManager.syncFromUi(parsed);
            this.highlightManager.setStatuses(parsed);
        });
        // @formatter:on

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            // TODO: make this better, use something to decide which info to parse prior to
            // parsing.
            var msg = Formatting.strip(message.getString());
            if (!msg.startsWith("[Bazaar]")) {
                return;
            }

            var filledOrderInfos = OrderInfoParser.parseFilledOrderInfo(msg);
            if (filledOrderInfos.isSuccess()) {
                this.orderManager.removeMatching(filledOrderInfos.get());
                return;
            }

            var chatOrderTry = OrderInfoParser.parseSetupChat(msg);
            if (chatOrderTry.isFailure()) {
                log.trace(
                    "Failed to parse out a `ChatOrderConfirmationInfo` from bazaar msg: `{}`",
                    msg
                );
                return;
            }
            ChatOrderConfirmationInfo chatOrder = chatOrderTry.get();
            log.trace("parsed out `ChatOrderConfirmationInfo`: {}", chatOrderTry.get());

            this.orderManager.confirmOutstanding(chatOrder);
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("reset").executes(context -> {
                MinecraftClient.getInstance().execute(() -> {
                    this.orderManager.resetTrackedOrders();
                    var player = MinecraftClient.getInstance().player;
                    if (player != null) {
                        player.sendMessage(
                            Text.literal("Tracked Bazaar orders have been reset."),
                            false
                        );
                    }
                });

                return 1;
            }));

            dispatcher.register(ClientCommandManager.literal("show").executes(context -> {
                MinecraftClient.getInstance().execute(() -> {
                    var player = MinecraftClient.getInstance().player;
                    var orders = this.orderManager.getTrackedOrders();

                    if (player != null) {
                        var trackedOrdersStr = orders
                            .stream()
                            .map(TrackedOrder::toString)
                            .collect(Collectors.joining("\n"));

                        player.sendMessage(
                            Text.literal("Your orders:\n" + trackedOrdersStr),
                            false
                        );
                    }
                });

                return 1;
            }));
        });
    }
}
