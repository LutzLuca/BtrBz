package com.github.lutzluca.btrbz;

import com.github.lutzluca.btrbz.InventoryLoadWatcher.SlotSnapshot;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

public class BtrBz implements ClientModInitializer {

    public static final String MOD_ID = "btrbz";

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

        var manager = new BzOrderManager(conversions);
        new BzPoller(manager::onBazaarUpdate);

        // @formatter:off
        new InventoryLoadWatcher(
            (screen) -> screen.getTitle().getString().equals("Your Bazaar Orders"), (items) -> {
            final var FILTER = Set.of(Items.BLACK_STAINED_GLASS_PANE, Items.ARROW, Items.HOPPER);

            var orders = items
                .stream()
                .map(SlotSnapshot::stack)
                .filter(itemStack -> !(itemStack.isEmpty() || FILTER.contains(itemStack.getItem())))
                .map(OrderParser::parseOrder)
                .flatMap(Optional::stream)
                .toList();

            manager.syncFromUi(orders);
        });
        // @formatter:on

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
