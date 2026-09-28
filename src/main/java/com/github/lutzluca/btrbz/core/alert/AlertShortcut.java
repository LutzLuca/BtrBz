package com.github.lutzluca.btrbz.core.alert;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.core.config.ConfigStore;
import com.github.lutzluca.btrbz.data.IndexedProduct;
import com.github.lutzluca.btrbz.screen.BazaarProductContext;
import com.github.lutzluca.btrbz.screen.ScreenTracker.BazaarMenuType;
import com.github.lutzluca.btrbz.screen.slot.SlotClickContext;
import com.github.lutzluca.btrbz.screen.slot.SlotClickResult;
import com.github.lutzluca.btrbz.screen.slot.SlotHook;
import com.github.lutzluca.btrbz.screen.slot.SlotHookRegistry;
import com.github.lutzluca.btrbz.screen.slot.SlotRenderContext;
import com.github.lutzluca.btrbz.screen.slot.SlotView;
import com.github.lutzluca.btrbz.utils.GameUtils;
import java.util.Objects;
import java.util.function.BiFunction;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/** Product-page entry to the shared Alerts editor. */
public final class AlertShortcut implements SlotHook {
    private static final int ALERT_SLOT = 0;
    private static final Identifier BELL_MODEL = Identifier.fromNamespaceAndPath(BtrBz.MOD_ID, "alert_bell");

    private final BazaarProductContext productContext;
    private final BiFunction<Screen, IndexedProduct, AlertScreen> createScreen;
    private @Nullable ItemStack displayStack;

    public AlertShortcut(
        BazaarProductContext productContext,
        BiFunction<Screen, IndexedProduct, AlertScreen> createScreen
    ) {
        this.productContext = Objects.requireNonNull(productContext);
        this.createScreen = Objects.requireNonNull(createScreen);
        SlotHookRegistry.register(this);
    }

    @Override
    public boolean matches(SlotView view) {
        return view.slotIdx() == ALERT_SLOT
            && !view.playerInventorySlot()
            && ConfigStore.get().config().alert.enabled
            && view.getCurrInfo().inMenu(BazaarMenuType.Item)
            && this.productContext.openedProduct() != null;
    }

    @Override
    public ItemStack createDisplayStack(SlotRenderContext context) {
        if (this.displayStack == null) {
            this.displayStack = new ItemStack(Items.PAPER);
            this.displayStack.set(DataComponents.ITEM_MODEL, BELL_MODEL);
            this.displayStack.set(DataComponents.CUSTOM_NAME,
                Component.literal("Open Alerts").withStyle(style -> style.withItalic(false)));
        }
        return this.displayStack.copy();
    }

    @Override
    public SlotClickResult onClick(SlotClickContext context) {
        var product = this.productContext.openedProduct();
        if (product == null) {
            return SlotClickResult.Pass;
        }

        GameUtils.setScreen(this.createScreen.apply(context.view().getCurrInfo().getScreen(), product));
        return SlotClickResult.Consume;
    }
}
