package com.github.lutzluca.btrbz.core.bazaariteminfo;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.mixin.AbstractContainerScreenAccessor;
import com.github.lutzluca.btrbz.utils.slot.VirtualSlotProjection;
import com.github.lutzluca.coflnet.CoflnetBazaarClient;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.Objects;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Owns hotkey activation and the shared Coflnet SDK client used by item-info screens. */
public final class BazaarItemInfoController implements AutoCloseable {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
        Identifier.fromNamespaceAndPath(BtrBz.MOD_ID, "bazaar_item_info"));

    private final BazaarData bazaarData;
    private final CoflnetBazaarClient coflnet;
    private final KeyMapping openScreen;

    public BazaarItemInfoController(BazaarData bazaarData, CoflnetBazaarClient coflnet) {
        this.bazaarData = Objects.requireNonNull(bazaarData, "bazaarData");
        this.coflnet = Objects.requireNonNull(coflnet, "coflnet");
        this.openScreen = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.btrbz.open_bazaar_item_info",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_I,
            CATEGORY));
    }

    /** Called by the container-screen mixin before vanilla handles the key. */
    public boolean handleContainerKey(AbstractContainerScreen<?> screen, KeyEvent event) {
        var slot = screen instanceof AbstractContainerScreenAccessor accessor
            ? accessor.getHoveredSlot()
            : null;
        return this.openHoveredSlot(
            screen,
            slot,
            this.openScreen.matches(event),
            screen.getFocused() instanceof EditBox);
    }

    boolean openHoveredSlot(
        AbstractContainerScreen<?> parent,
        @Nullable Slot slot,
        boolean keyMatches,
        boolean textInputFocused
    ) {
        if (!keyMatches || textInputFocused || slot == null) {
            return false;
        }

        ItemStack stack = VirtualSlotProjection.withProjectionSuppressed(slot::getItem);
        if (stack.isEmpty()) {
            return false;
        }

        var identity = this.bazaarData.resolveProduct(stack);
        var productTag = identity.bazaarProductId();
        if (!canOpen(
            keyMatches,
            textInputFocused,
            slot != null,
            !stack.isEmpty(),
            productTag.isPresent())) {
            return false;
        }

        Minecraft.getInstance().setScreen(new BazaarItemInfoScreen(
            parent,
            identity,
            productTag.orElseThrow(),
            stack.copy(),
            this.coflnet));
        return true;
    }

    static boolean canOpen(
        boolean keyMatches,
        boolean textInputFocused,
        boolean slotPresent,
        boolean stackPresent,
        boolean tagPresent
    ) {
        return keyMatches && !textInputFocused && slotPresent && stackPresent && tagPresent;
    }

    @Override
    public void close() {
        this.coflnet.close();
    }
}
