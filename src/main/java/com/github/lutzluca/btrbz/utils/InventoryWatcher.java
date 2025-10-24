package com.github.lutzluca.btrbz.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.CloseScreenS2CPacket;
import net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.screen.ScreenHandlerType;
import org.jetbrains.annotations.Nullable;

@Slf4j
public class InventoryWatcher {

    private static final Map<ScreenHandlerType<?>, Integer> SLOT_COUNT_MAP = new HashMap<>();

    static {
        SLOT_COUNT_MAP.put(ScreenHandlerType.ANVIL, 3);
        SLOT_COUNT_MAP.put(ScreenHandlerType.BEACON, 1);
        SLOT_COUNT_MAP.put(ScreenHandlerType.BLAST_FURNACE, 3);
        SLOT_COUNT_MAP.put(ScreenHandlerType.BREWING_STAND, 5);
        SLOT_COUNT_MAP.put(ScreenHandlerType.CARTOGRAPHY_TABLE, 2);
        SLOT_COUNT_MAP.put(ScreenHandlerType.CRAFTING, 9);
        SLOT_COUNT_MAP.put(ScreenHandlerType.ENCHANTMENT, 2);
        SLOT_COUNT_MAP.put(ScreenHandlerType.FURNACE, 3);
        SLOT_COUNT_MAP.put(ScreenHandlerType.GENERIC_3X3, 9);
        SLOT_COUNT_MAP.put(ScreenHandlerType.GENERIC_9X1, 9);
        SLOT_COUNT_MAP.put(ScreenHandlerType.GENERIC_9X2, 18);
        SLOT_COUNT_MAP.put(ScreenHandlerType.GENERIC_9X3, 27);
        SLOT_COUNT_MAP.put(ScreenHandlerType.GENERIC_9X4, 36);
        SLOT_COUNT_MAP.put(ScreenHandlerType.GENERIC_9X5, 45);
        SLOT_COUNT_MAP.put(ScreenHandlerType.GENERIC_9X6, 54);
        SLOT_COUNT_MAP.put(ScreenHandlerType.GRINDSTONE, 3);
        SLOT_COUNT_MAP.put(ScreenHandlerType.HOPPER, 5);
        SLOT_COUNT_MAP.put(ScreenHandlerType.LECTERN, 1);
        SLOT_COUNT_MAP.put(ScreenHandlerType.LOOM, 3);
        SLOT_COUNT_MAP.put(ScreenHandlerType.MERCHANT, 3);
        SLOT_COUNT_MAP.put(ScreenHandlerType.SHULKER_BOX, 27);
        SLOT_COUNT_MAP.put(ScreenHandlerType.SMITHING, 3);
        SLOT_COUNT_MAP.put(ScreenHandlerType.SMOKER, 3);
        SLOT_COUNT_MAP.put(ScreenHandlerType.STONECUTTER, 1);
    }

    @Getter
    private @Nullable Inventory currInv = null;
    private boolean acceptItems = false;

    private @Nullable Consumer<Inventory> onFullyLoadedCallback = null;
    private @Nullable BiConsumer<String, Boolean> onCloseCallback = null;

    public void setOnLoaded(@Nullable Consumer<Inventory> callback) {
        this.onFullyLoadedCallback = callback;
    }

    public void setOnClose(@Nullable BiConsumer<String, Boolean> callback) {
        this.onCloseCallback = callback;
    }

    public void onCloseScreen() { this.close(); }

    public void close() {
        this.close(ScreenInfoHelper.get().getCurrInfo().containerName().orElse(""), false);
    }

    public void close(String title, boolean reopenSameName) {
        if (this.onCloseCallback != null) {
            this.onCloseCallback.accept(title, reopenSameName);
        }
        this.currInv = null;
    }

    public void onPacketReceived(Object packet) {
        switch (packet) {
            case OpenScreenS2CPacket openPacket -> this.handleOpenScreen(openPacket);
            case ScreenHandlerSlotUpdateS2CPacket slotPacket -> this.handleSlotUpdate(slotPacket);
            case CloseScreenS2CPacket ignored -> this.close();
            default -> { }
        }
    }

    private void handleOpenScreen(OpenScreenS2CPacket packet) {
        var title = packet.getName().getString();
        int syncId = packet.getSyncId();
        var handlerType = packet.getScreenHandlerType();

        var slotCount = SLOT_COUNT_MAP.get(handlerType);
        if (slotCount == null) {
            log.error(
                "Unknown screen handler type for inventory '{}'. Ignoring this inventory.",
                title
            );
            return;
        }

        boolean reopeningSame = this.currInv != null && title.equals(this.currInv.title);
        this.close(title, reopeningSame);

        this.currInv = new Inventory(syncId, title, slotCount);
        this.acceptItems = true;
    }

    private void handleSlotUpdate(ScreenHandlerSlotUpdateS2CPacket packet) {
        if (this.currInv == null) {
            return;
        }

        if (this.currInv.syncId != packet.getSyncId()) {
            return;
        }

        int slot = packet.getSlot();
        if (slot >= this.currInv.slotCount) {
            if (this.acceptItems) {
                this.markAsFullyLoaded();
            }
            return;
        }

        var itemStack = packet.getStack();
        if (!itemStack.isEmpty()) {
            this.currInv.items.put(slot, itemStack);
        }

        if (this.acceptItems && this.currInv.items.size() == this.currInv.slotCount) {
            this.markAsFullyLoaded();
        }
    }

    private void markAsFullyLoaded() {
        assert this.currInv != null;

        this.currInv.fullyLoaded = true;

        if (this.onFullyLoadedCallback != null) {
            this.onFullyLoadedCallback.accept(this.currInv);
        }

        this.acceptItems = false;
    }

    @ToString
    public static class Inventory {

        public final int syncId;
        public final String title;
        public final int slotCount;
        public final Map<Integer, ItemStack> items;
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
