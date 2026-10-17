package com.github.lutzluca.btrbz.core.orderdisplay;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.core.config.ConfigImages;
import com.github.lutzluca.btrbz.core.config.ConfigUi;
import com.github.lutzluca.btrbz.core.config.ConfigStore;
import com.github.lutzluca.btrbz.core.trackedorders.TrackedOrderManager;
import com.github.lutzluca.btrbz.screen.ScreenTracker.BazaarMenuType;
import com.github.lutzluca.btrbz.screen.slot.SlotHook;
import com.github.lutzluca.btrbz.screen.slot.SlotHookRegistry;
import com.github.lutzluca.btrbz.screen.slot.SlotRenderContext;
import com.github.lutzluca.btrbz.screen.slot.SlotView;
import com.github.lutzluca.btrbz.utils.GameUtils;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionGroup;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Marks Manage Orders when its lore reports a claim or a tracked order has filled. */
public final class ClaimableOrderIndicator implements SlotHook {
    private static final int MANAGE_ORDERS_SLOT = 50;
    private final TrackedOrderManager trackedOrders;

    public ClaimableOrderIndicator(TrackedOrderManager trackedOrders) {
        this.trackedOrders = trackedOrders;
        SlotHookRegistry.register(this);
    }

    @Override
    public boolean requiresMarketData() {
        return false;
    }

    @Override
    public boolean matches(SlotView view) {
        // slotIdx() is local to the backing inventory, so player inventory slots cannot reach 50.
        return ConfigStore.get().config().claimableOrderIndicator.enabled
            && view.slotIdx() == MANAGE_ORDERS_SLOT
            && view.getCurrInfo().inMenu(BazaarMenuType.Main)
            && "Manage Orders".equals(view.getRawStack().getHoverName().getString());
    }

    @Override
    public @Nullable ItemStack createDisplayStack(SlotRenderContext context) {
        var raw = context.view().getRawStack();
        boolean claimable = this.trackedOrders.filledOrderCount() > 0;

        // You have <amount> items to claim!
        // You have <coins> coins to claim!
        if (!claimable) {
            for (var line : GameUtils.getLoreComponents(raw)) {
                var text = line.getString();
                if (text.startsWith("You have ")
                    && (text.endsWith(" items to claim!") || text.endsWith(" coins to claim!"))) {
                    claimable = true;
                    break;
                }
            }
        }

        if (!claimable) {
            return null;
        }

        var display = raw.copy();
        display.set(BtrBz.CLAIMABLE, true);
        return display;
    }

    public static final class ClaimableOrderIndicatorConfig {
        public boolean enabled = true;

        public OptionGroup createGroup() {
            return OptionGroup.createBuilder()
                .name(Component.literal("Claimable Orders"))
                .option(Option.<Boolean>createBuilder()
                    .name(Component.literal("Show Claimable Indicator"))
                    .description(ConfigUi.createDescription(
                        "Show an icon on Manage Orders when its lore reports items or coins to claim, "
                            + "or a tracked order has filled.",
                        ConfigImages.ClaimableOrders))
                    .binding(true, () -> this.enabled, value -> this.enabled = value)
                    .controller(ConfigUi::createBooleanController)
                    .build())
                .build();
        }
    }
}
