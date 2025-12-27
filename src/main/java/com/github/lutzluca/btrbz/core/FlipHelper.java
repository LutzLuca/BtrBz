package com.github.lutzluca.btrbz.core;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import com.github.lutzluca.btrbz.core.config.ConfigScreen.OptionGrouping;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.BazaarData.TrackedProduct;
import com.github.lutzluca.btrbz.data.BazaarMessageDispatcher.BazaarMessage;
import com.github.lutzluca.btrbz.data.OrderModels.OrderInfo;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.data.OrderModels.TrackedOrder;
import com.github.lutzluca.btrbz.data.TimedStore;
import com.github.lutzluca.btrbz.mixin.AbstractSignEditScreenAccessor;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.github.lutzluca.btrbz.utils.ItemOverrideManager;
import com.github.lutzluca.btrbz.utils.Notifier;
import com.github.lutzluca.btrbz.utils.ScreenActionManager;
import com.github.lutzluca.btrbz.utils.ScreenActionManager.ScreenClickRule;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.ScreenInfo;
import com.github.lutzluca.btrbz.utils.Utils;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;


@Slf4j
public class FlipHelper {

    private static final int FLIP_ORDER_ITEM_SLOT_IDX = 15;
    private static final int CUSTOM_HELPER_ITEM_SLOT_IDX = 16;

    private final TimedStore<FlipEntry> pendingFlips = new TimedStore<>(15_000L);
    private final BazaarData bazaarData;

    private TrackedProduct potentialFlipProduct = null;
    private boolean pendingFlip = false;

    public FlipHelper(BazaarData bazaarData) {
        this.bazaarData = bazaarData;
        this.registerFlipHelperItemOverride();
        this.registerFlipExecutionTrigger();
        this.registerFlipPriceScreenHandler();
    }

    public void onOrderClick(OrderInfo info) {
        if (info.type() != OrderType.Buy) {
            this.clearPendingFlipState();
            return;
        }

        if (info instanceof OrderInfo.UnfilledOrderInfo) {
            this.clearPendingFlipState();
            return;
        }

        if (this.potentialFlipProduct != null) {
            this.potentialFlipProduct.destroy();
        }

        this.potentialFlipProduct = new TrackedProduct(this.bazaarData, info.productName());
        log.debug("Set `potentialFlipProduct` for product: '{}'", info.productName());
    }

    private void registerFlipHelperItemOverride() {
        ItemOverrideManager.register((info, slot, original) -> {
            if (!ConfigManager.get().flipHelper.enabled) {
                return Optional.empty();
            }

            if (!info.inMenu(BazaarMenuType.OrderOptions)) {
                return Optional.empty();
            }

            if (slot == null || slot.getContainerSlot() != CUSTOM_HELPER_ITEM_SLOT_IDX || this.potentialFlipProduct == null) {
                return Optional.empty();
            }

            if (GameUtils.isPlayerInventorySlot(slot)) {
                return Optional.empty();
            }

            return this.potentialFlipProduct.getSellOfferPrice().map(price -> {
                var formatted = Utils.formatDecimal(Math.max(price - 0.1, .1), 1, true);

                var customHelperItem = new ItemStack(Items.NETHER_STAR);
                customHelperItem.set(
                    DataComponents.CUSTOM_NAME,
                    Component
                        .literal("Flip for ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(formatted).withStyle(ChatFormatting.GOLD))
                        .append(Component.literal(" coins each").withStyle(ChatFormatting.GRAY))
                        .withStyle(style -> style.withItalic(false))
                );

                return customHelperItem;
            });
        });
    }

    private void registerFlipExecutionTrigger() {
        ScreenActionManager.register(new ScreenClickRule() {
            @Override
            public boolean applies(ScreenInfo info, Slot slot, int button) {
                if (!ConfigManager.get().flipHelper.enabled) {
                    return false;
                }

                return slot != null && slot.getContainerSlot() == CUSTOM_HELPER_ITEM_SLOT_IDX && info.inMenu(
                    BazaarMenuType.OrderOptions);
            }

            @Override
            public boolean onClick(ScreenInfo info, Slot slot, int button) {
                var client = Minecraft.getInstance();
                if (client == null) {
                    return false;
                }

                var gcsOpt = info.getGenericContainerScreen();
                if (gcsOpt.isEmpty()) {
                    return false;
                }

                var handler = gcsOpt.get().getMenu();
                var player = client.player;
                var interactionManager = client.gameMode;

                if (player == null || interactionManager == null) {
                    return false;
                }

                if (potentialFlipProduct == null || potentialFlipProduct
                    .getSellOfferPrice()
                    .isEmpty()) {

                    log.debug(
                        "Ignoring flip execution click because it's price could not be resolved: '{}'",
                        potentialFlipProduct == null ? "no product selected" : "price not available"
                    );
                    return false;
                }

                interactionManager.handleInventoryMouseClick(
                    handler.containerId,
                    FLIP_ORDER_ITEM_SLOT_IDX,
                    button,
                    ClickType.PICKUP,
                    player
                );
                pendingFlip = true;

                return false;
            }
        });
    }

    private void registerFlipPriceScreenHandler() {
        ScreenInfoHelper.registerOnSwitch(curr -> {
            if (!ConfigManager.get().flipHelper.enabled) {
                return;
            }
            var prev = ScreenInfoHelper.get().getPrevInfo();
            if (prev == null || !prev.inMenu(BazaarMenuType.OrderOptions)) {
                pendingFlip = false;
                return;
            }

            if (!this.pendingFlip) {
                log.debug(
                    "Screen transition from OrderOption without a pendingFlip -> resetting flip state");
                this.clearPendingFlipState();
                return;
            }

            if (!(curr.getScreen() instanceof SignEditScreen signEditScreen)) {
                log.warn("""
                        Expected screen transition from OrderOptions to a SignEditScreen while pendingFlip is set,
                        but switched to a non-SignEditScreen; resetting flip state
                    """);
                this.clearPendingFlipState();
                return;
            }

            if (this.potentialFlipProduct == null) {
                log.warn(
                    "Expected `potentialFlipProduct` to be non-null to proceed with entering the flipPrice");
                this.clearPendingFlipState();
                return;
            }

            var flipPrice = this.potentialFlipProduct
                .getSellOfferPrice()
                .map(price -> Math.max(price - .1, 0.1));

            if (flipPrice.isEmpty()) {
                log.warn(
                    "Could not resolve price for product '{}'",
                    this.potentialFlipProduct.getProductName()
                );
                this.clearPendingFlipState();
                return;
            }

            var formatted = Utils.formatDecimal(flipPrice.get(), 1, false);
            var accessor = (AbstractSignEditScreenAccessor) signEditScreen;
            accessor.setLine(0);
            accessor.invokeSetMessage(formatted);

            //NOTE: signEditScreen.onClose() gets broken by Skyblocker so setScreen is used instead.
            Minecraft.getInstance().setScreen(null);
            this.pendingFlips.add(new FlipEntry(
                potentialFlipProduct.getProductName(),
                flipPrice.get()
            ));

            this.clearPendingFlipState();
        });
    }

    public void handleFlipped(BazaarMessage.OrderFlipped flipped) {
        var match = this.pendingFlips.removeFirstMatch(entry -> entry
            .productName()
            .equalsIgnoreCase(flipped.productName()));

        // this may be unnecessary as after entering the price in the sign, it opens the orders
        // menu, might as well leave it atm
        if (match.isEmpty()) {
            log.warn(
                "No matching pending flip for flipped order {}x {}. Orders may be out of sync",
                flipped.volume(),
                flipped.productName()
            );
            Notifier.notifyChatCommand(
                "No matching pending flip found for flipped order. Click to resync tracked orders",
                "managebazaarorders"
            );
            return;
        }

        var entry = match.get();
        double pricePerUnit = entry.pricePerUnit();

        var orderInfo = new OrderInfo.UnfilledOrderInfo(
            flipped.productName(),
            OrderType.Sell,
            flipped.volume(),
            pricePerUnit,
            0,
            0,
            -1
        );
        BtrBz.orderManager().addTrackedOrder(new TrackedOrder(orderInfo));

        log.debug(
            "Added tracked Sell order from flipped chat: {}x {} at {} per unit",
            flipped.volume(),
            flipped.productName(),
            Utils.formatDecimal(pricePerUnit, 1, true)
        );
    }

    private void clearPendingFlipState() {
        if (this.potentialFlipProduct != null) {
            log.debug(
                "Destroying `potentialFlipProduct` '{}'",
                this.potentialFlipProduct.getProductName()
            );
            this.potentialFlipProduct.destroy();
        }
        this.potentialFlipProduct = null;
        this.pendingFlip = false;
    }

    private record FlipEntry(String productName, double pricePerUnit) { }

    public static class FlipHelperConfig {

        public boolean enabled = true;

        public Option.Builder<Boolean> createEnabledOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Flip Helper"))
                .binding(true, () -> this.enabled, enabled -> this.enabled = enabled)
                .description(OptionDescription.of(Component.literal(
                    "Enable or disable the flip helper features (quick flip UI interactions)")))
                .controller(ConfigScreen::createBooleanController);
        }

        public OptionGroup createGroup() {
            var rootGroup = new OptionGrouping(this.createEnabledOption());

            return OptionGroup
                .createBuilder()
                .name(Component.literal("Flip Helper"))
                .description(OptionDescription.of(Component.literal(
                    "Enable or disable the flip helper features (quick flip UI interactions)")))
                .options(rootGroup.build())
                .collapsed(false)
                .build();
        }
    }
}
