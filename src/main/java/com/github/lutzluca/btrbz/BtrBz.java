package com.github.lutzluca.btrbz;

import com.github.lutzluca.btrbz.core.BzOrderManager;
import com.github.lutzluca.btrbz.core.FlipHelper;
import com.github.lutzluca.btrbz.core.HighlightManager;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.BazaarPoller;
import com.github.lutzluca.btrbz.data.ConversionLoader;
import com.github.lutzluca.btrbz.data.OrderInfoParser;
import com.github.lutzluca.btrbz.data.OrderModels.ChatOrderConfirmationInfo;
import com.github.lutzluca.btrbz.data.OrderModels.TrackedOrder;
import com.github.lutzluca.btrbz.utils.ScreenActionManager;
import com.github.lutzluca.btrbz.utils.ScreenActionManager.ScreenClickRule;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.ScreenInfo;
import com.github.lutzluca.btrbz.utils.Util;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.screen.slot.Slot;
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

        BazaarData bazaarData = new BazaarData(conversions);
        this.highlightManager = new HighlightManager();
        this.orderManager = new BzOrderManager(bazaarData, this.highlightManager::updateStatus);
        bazaarData.addListener(this.orderManager::onBazaarUpdate);

        new BazaarPoller(bazaarData::onUpdate);

        var flipHelper = new FlipHelper(bazaarData);

        // @formatter:off
        ScreenInfoHelper.registerOnLoaded(
            info -> info.inMenu(BazaarMenuType.Orders),
            (info, slots) -> {

                var parsed = slots.stream()
                    .filter(slot -> {
                        var stack = slot.stack();
                        return !stack.isEmpty() && !Util.orderScreenNonOrderItem.contains(stack.getItem());
                    })
                    .map(slot ->
                        OrderInfoParser
                            .parseOrderInfo(slot.stack(), slot.idx())
                            .toJavaOptional()
                    )
                    .flatMap(Optional::stream)
                    .collect(Collectors.toList());

                this.orderManager.syncFromUi(parsed);
                this.highlightManager.setStatuses(parsed);
            }
        );
        // @formatter:on

        ScreenActionManager.register(new ScreenClickRule() {

            @Override
            public boolean applies(ScreenInfo info, Slot slot, int button) {
                final int orderItemIdx = 13;

                return info.inMenu(
                    BazaarMenuType.BuyOrderConfirmation,
                    BazaarMenuType.SellOfferConfirmation
                ) && slot != null && slot.getIndex() == orderItemIdx;
            }

            @Override
            public boolean onClick(ScreenInfo info, Slot slot, int button) {
                OrderInfoParser.parseSetOrderItem(slot.getStack()).onSuccess((setOrderInfo) -> {
                    BtrBz.orderManager().addOutstandingOrder(setOrderInfo);

                    log.trace(
                        "Stored outstanding order for {}x {}",
                        setOrderInfo.volume(),
                        setOrderInfo.productName()
                    );
                }).onFailure((err) -> log.warn("Failed to parse confirm item", err));

                return false;
            }
        });

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            // TODO: make this better, use something to decide which info to parse prior to
            // parsing.
            var msg = Formatting.strip(message.getString());
            if (!msg.startsWith("[Bazaar]")) {
                return;
            }
            var flippedTry = OrderInfoParser.parseFlippedOrderInfo(msg);
            if (flippedTry.isSuccess()) {
                var flipped = flippedTry.get();
                log.info("Parsed flipped order: {}x {}", flipped.volume(), flipped.productName());
                flipHelper.handleFlipped(flipped);
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
