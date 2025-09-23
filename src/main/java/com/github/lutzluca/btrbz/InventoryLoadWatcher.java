package com.github.lutzluca.btrbz;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.ItemStack;

public class InventoryLoadWatcher {

    private final Predicate<GenericContainerScreen> screenMatcher;
    private final Predicate<ItemStack> loadingPredicate;
    private final Consumer<List<SlotSnapshot>> onLoaded;

    private final int requiredStableTicks;
    private final int maxWaitTicks;

    private int stableTicks;
    private int waitedTicks;
    private boolean loaded;
    private boolean gaveUp;

    private GenericContainerScreen lastScreen;
    private List<SlotSnapshot> lastSnapshot = null;

    public InventoryLoadWatcher(
        Predicate<GenericContainerScreen> screenMatcher,
        Consumer<List<SlotSnapshot>> onLoaded,
        Predicate<ItemStack> loadingPredicate,
        int requiredStableTicks,
        int maxWaitTicks
    ) {
        this.screenMatcher = screenMatcher;
        this.onLoaded = onLoaded;
        this.loadingPredicate = loadingPredicate;

        this.requiredStableTicks = requiredStableTicks;
        this.maxWaitTicks = maxWaitTicks;

        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    public InventoryLoadWatcher(
        Predicate<GenericContainerScreen> screenMatcher,
        Consumer<List<SlotSnapshot>> onLoaded
    ) {
        this(screenMatcher, onLoaded, stack -> {
                if (stack == null || stack.isEmpty()) {
                    return false;
                }
                var name = stack.getName().getString().toLowerCase();
                return name.contains("loading");
            }, 3, 50
        );
    }

    private void onTick(MinecraftClient client) {
        if (!(client.currentScreen instanceof GenericContainerScreen screen) || !screenMatcher.test(
            screen)) {
            this.reset();
            return;
        }

        if (screen != this.lastScreen) {
            this.reset();
            this.lastScreen = screen;
        }

        if (this.loaded || this.gaveUp) {
            return;
        }

        waitedTicks++;
        if (waitedTicks > this.maxWaitTicks) {
            this.gaveUp = true;
            return;
        }

        var inv = screen.getScreenHandler().getInventory();
        if (StreamSupport.stream(inv.spliterator(), false).anyMatch(loadingPredicate)) {
            return;
        }

        var currSnapshot = IntStream
            .range(0, inv.size())
            .mapToObj(idx -> new SlotSnapshot(idx, inv.getStack(idx)))
            .collect(Collectors.toList());

        if (!currSnapshot.equals(this.lastSnapshot)) {
            this.lastSnapshot = currSnapshot;
            this.stableTicks = 0;
            return;
        }

        if (++this.stableTicks >= this.requiredStableTicks) {
            this.loaded = true;
            this.onLoaded.accept(currSnapshot);
        }
    }

    private void reset() {
        this.stableTicks = 0;
        this.waitedTicks = 0;
        this.loaded = false;
        this.gaveUp = false;
        this.lastSnapshot = null;
    }

    public record SlotSnapshot(int idx, ItemStack stack) {

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof SlotSnapshot(int otherIdx, ItemStack otherStack))) {
                return false;
            }
            return idx == otherIdx && ItemStack.areEqual(stack, otherStack);
        }
    }
}
