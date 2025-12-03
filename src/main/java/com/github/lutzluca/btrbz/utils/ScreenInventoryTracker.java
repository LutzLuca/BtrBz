package com.github.lutzluca.btrbz.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

@Slf4j
public class ScreenInventoryTracker {

    private static final Map<MenuType<?>, Integer> SLOT_COUNT_MAP = new HashMap<>();

    static {
        SLOT_COUNT_MAP.put(MenuType.ANVIL, 3);
        SLOT_COUNT_MAP.put(MenuType.BEACON, 1);
        SLOT_COUNT_MAP.put(MenuType.BLAST_FURNACE, 3);
        SLOT_COUNT_MAP.put(MenuType.BREWING_STAND, 5);
        SLOT_COUNT_MAP.put(MenuType.CARTOGRAPHY_TABLE, 2);
        SLOT_COUNT_MAP.put(MenuType.CRAFTING, 9);
        SLOT_COUNT_MAP.put(MenuType.ENCHANTMENT, 2);
        SLOT_COUNT_MAP.put(MenuType.FURNACE, 3);
        SLOT_COUNT_MAP.put(MenuType.GENERIC_3x3, 9);
        SLOT_COUNT_MAP.put(MenuType.GENERIC_9x1, 9);
        SLOT_COUNT_MAP.put(MenuType.GENERIC_9x2, 18);
        SLOT_COUNT_MAP.put(MenuType.GENERIC_9x3, 27);
        SLOT_COUNT_MAP.put(MenuType.GENERIC_9x4, 36);
        SLOT_COUNT_MAP.put(MenuType.GENERIC_9x5, 45);
        SLOT_COUNT_MAP.put(MenuType.GENERIC_9x6, 54);
        SLOT_COUNT_MAP.put(MenuType.GRINDSTONE, 3);
        SLOT_COUNT_MAP.put(MenuType.HOPPER, 5);
        SLOT_COUNT_MAP.put(MenuType.LECTERN, 1);
        SLOT_COUNT_MAP.put(MenuType.LOOM, 3);
        SLOT_COUNT_MAP.put(MenuType.MERCHANT, 3);
        SLOT_COUNT_MAP.put(MenuType.SHULKER_BOX, 27);
        SLOT_COUNT_MAP.put(MenuType.SMITHING, 3);
        SLOT_COUNT_MAP.put(MenuType.SMOKER, 3);
        SLOT_COUNT_MAP.put(MenuType.STONECUTTER, 1);
    }

    @Getter
    private @Nullable Inventory currInv = null;
    private boolean acceptItems = false;

    private Consumer<Inventory> onFullyLoadedCallback = null;
    private Consumer<String> onCloseCallback = null;

    public void setOnLoaded(Consumer<Inventory> callback) {
        this.onFullyLoadedCallback = callback;
    }

    public void setOnClose(Consumer<String> callback) {
        this.onCloseCallback = callback;
    }

    public void onCloseScreen() {
        this.close();
    }

    public void close() {
        String title = this.currInv != null ? this.currInv.title : "";
        if (this.onCloseCallback != null) {
            this.onCloseCallback.accept(title);
        }
        this.currInv = null;
    }

    public void onPacketReceived(Object packet) {
        switch (packet) {
            case ClientboundOpenScreenPacket openPacket -> this.handleOpenScreen(openPacket);
            case ClientboundContainerSetSlotPacket slotPacket -> this.handleSlotUpdate(slotPacket);
            case ClientboundContainerClosePacket ignored -> this.close();
            default -> { }
        }
    }

    private void handleOpenScreen(ClientboundOpenScreenPacket packet) {
        var title = packet.getTitle().getString();
        int syncId = packet.getContainerId();
        var handlerType = packet.getType();

        var slotCount = SLOT_COUNT_MAP.get(handlerType);
        if (slotCount == null) {
            log.error(
                "Unknown screen handler type for inventory '{}'. Ignoring this inventory.",
                title
            );
            return;
        }

        this.close();

        this.currInv = new Inventory(syncId, title, slotCount);
        this.acceptItems = true;
    }

    private void handleSlotUpdate(ClientboundContainerSetSlotPacket packet) {
        if (this.currInv == null) {
            return;
        }

        if (this.currInv.syncId != packet.getContainerId()) {
            return;
        }

        int slot = packet.getSlot();
        if (slot >= this.currInv.slotCount) {
            if (this.acceptItems) {
                this.loaded();
            }
            return;
        }

        var itemStack = packet.getItem();
        if (!itemStack.isEmpty()) {
            this.currInv.items.put(slot, itemStack);
        }

        if (this.acceptItems && this.currInv.items.size() == this.currInv.slotCount) {
            this.loaded();
        }
    }

    private void loaded() {
        assert this.currInv != null;

        this.currInv.fullyLoaded = true;

        if (this.onFullyLoadedCallback != null) {
            this.onFullyLoadedCallback.accept(this.currInv);
        }

        this.acceptItems = false;
    }

    @ToString
    public static class Inventory {

        public final Map<Integer, ItemStack> items;
        public final String title;
        private final int syncId;
        private final int slotCount;
        public boolean fullyLoaded;

        public Inventory(int syncId, String title, int slotCount) {
            this.syncId = syncId;
            this.title = title;
            this.slotCount = slotCount;
            this.items = new HashMap<>();
            this.fullyLoaded = false;
        }

        public Optional<ItemStack> getItem(int idx) {
            return Optional.ofNullable(this.items.get(idx));
        }

        public boolean hasItem(int idx) {
            return this.items.containsKey(idx);
        }
    }
}
