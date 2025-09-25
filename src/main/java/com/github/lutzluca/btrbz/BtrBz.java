package com.github.lutzluca.btrbz;

import com.github.lutzluca.btrbz.SetOrderInfo.ChatOrder;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

public class BtrBz implements ClientModInitializer {

    public static final String MOD_ID = "btrbz";

    private static BzOrderManager manager;

    public static BzOrderManager getManager() {
        return manager;
    }

    @Override
    public void onInitializeClient() {
        Notifier.logInfo("[BtrBz] Initializing Mod...");

        var conversions = ConversionLoader
            .initialize()
            .onFailure(err -> Notifier.logError(
                "Failed to initialize conversion mappings. Mod cannot proceed. {}", err))
            .onSuccess(map -> Notifier.logDebug("Conversion mappings initialized ({} entries)",
                map.size()
            ))
            .get();

        manager = new BzOrderManager(conversions);

        new BzPoller(manager::onBazaarUpdate);

        // @formatter:off
        new InventoryLoadWatcher(
            (screen) -> screen.getTitle().getString().equals("Your Bazaar Orders"), (slots) -> {
            final var FILTER = Set.of(Items.BLACK_STAINED_GLASS_PANE, Items.ARROW, Items.HOPPER);

            var parsed = slots
                .stream()
                .filter(slot -> !(slot.stack().isEmpty() || FILTER.contains(slot.stack().getItem())))
                .map(slot -> OrderParser.parseOrder(slot.stack(), slot.idx()).toJavaOptional()
                )
                .flatMap(Optional::stream)
                .collect(Collectors.toList());

            manager.syncFromUi(parsed);
            HighlightManager.setStatuses(parsed);
        });
        // @formatter:on

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            String msg = message.getString();
            if (!msg.startsWith("[Bazaar]")) {
                return;
            }

            var parsed = SetOrderInfo.parseSetupChat(msg);
            if (parsed.isFailure()) {
                System.out.println(
                    "failed to parse out the setup chat order info: " + parsed.getCause());
                return;
            }
            ChatOrder chatOrder = parsed.get();
            System.out.println("parsed out chat order info: " + parsed.get());

            Optional<TrackedOrder> matched = OutstandingOrders.matchAndRemove(chatOrder);
            if (matched.isPresent()) {
                BtrBz.getManager().addTrackedOrder(matched.get());
                Notifier.logInfo("Matched Bazaar order for {} x{}", matched.get().productName,
                    matched.get().volume
                );
                Notifier.notifyPlayer(Text.literal(
                    "Matched Bazaar order for " + matched.get().productName + " x"
                        + matched.get().volume));
            } else {
                Notifier.notifyChatCommand("No matching confirmation found. Resync orders",
                    "managebazaarorders"
                );
            }
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("reset").executes(context -> {
                MinecraftClient.getInstance().execute(() -> {
                    manager.resetTrackedOrders();
                    var player = MinecraftClient.getInstance().player;
                    if (player != null) {
                        player.sendMessage(Text.literal("Tracked Bazaar orders have been reset."),
                            false
                        );
                    }
                });

                return 1;
            }));

            dispatcher.register(ClientCommandManager.literal("show").executes(context -> {
                MinecraftClient.getInstance().execute(() -> {
                    var player = MinecraftClient.getInstance().player;
                    var orders = manager.getTrackedOrders();

                    if (player != null) {
                        player.sendMessage(Text.literal(orders
                            .stream()
                            .map(TrackedOrder::toString)
                            .collect(Collectors.joining("\n"))), false
                        );
                    }
                });

                return 1;
            }));
        });
    }
}
