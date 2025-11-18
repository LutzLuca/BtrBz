package com.github.lutzluca.btrbz;

import com.github.lutzluca.btrbz.core.AlertManager;
import com.github.lutzluca.btrbz.core.BazaarOrderActions;
import com.github.lutzluca.btrbz.core.FlipHelper;
import com.github.lutzluca.btrbz.core.ModuleManager;
import com.github.lutzluca.btrbz.core.OrderHighlightManager;
import com.github.lutzluca.btrbz.core.OrderProtectionManager;
import com.github.lutzluca.btrbz.core.ProductInfoProvider;
import com.github.lutzluca.btrbz.core.TrackedOrderManager;
import com.github.lutzluca.btrbz.core.commands.Commands;
import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.modules.BookmarkModule;
import com.github.lutzluca.btrbz.core.modules.OrderLimitModule;
import com.github.lutzluca.btrbz.core.modules.OrderPresetsModule;
import com.github.lutzluca.btrbz.core.modules.OrderValueModule;
import com.github.lutzluca.btrbz.core.modules.PriceDiffModule;
import com.github.lutzluca.btrbz.core.modules.TrackedOrdersListModule;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.BazaarMessageDispatcher;
import com.github.lutzluca.btrbz.data.BazaarMessageDispatcher.BazaarMessage;
import com.github.lutzluca.btrbz.data.BazaarPoller;
import com.github.lutzluca.btrbz.data.ConversionLoader;
import com.github.lutzluca.btrbz.data.OrderInfoParser;
import com.github.lutzluca.btrbz.data.OrderModels.OutstandingOrderInfo;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.github.lutzluca.btrbz.utils.ScreenActionManager;
import com.github.lutzluca.btrbz.utils.ScreenActionManager.ScreenClickRule;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.ScreenInfo;
import com.google.common.collect.HashBiMap;
import com.mojang.serialization.Codec;
import java.util.Optional;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.component.ComponentType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.ClickEvent.RunCommand;
import net.minecraft.text.HoverEvent.ShowText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

@Slf4j
public class BtrBz implements ClientModInitializer {

    public static final String MOD_ID = "btrbz";
    private static final BazaarData BAZAAR_DATA = new BazaarData(HashBiMap.create());
    public static BazaarMessageDispatcher messageDispatcher = new BazaarMessageDispatcher();
    public static ComponentType<Boolean> BOOKMARKED;
    private static BtrBz instance;

    private TrackedOrderManager orderManager;
    private OrderHighlightManager highlightManager;
    private AlertManager alertManager;

    public static TrackedOrderManager orderManager() {
        return instance.orderManager;
    }

    public static OrderHighlightManager highlightManager() {
        return instance.highlightManager;
    }

    public static BazaarData bazaarData() {
        return BtrBz.BAZAAR_DATA;
    }

    public static AlertManager alertManager() {
        return instance.alertManager;
    }

    @Override
    public void onInitializeClient() {
        instance = this;
        BOOKMARKED = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Identifier.of(BtrBz.MOD_ID, "bookmarked"),
            ComponentType.<Boolean>builder().codec(Codec.BOOL).build()
        );

        ConfigManager.load();
        Commands.registerAll();
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> ConversionLoader.load());

        this.highlightManager = new OrderHighlightManager();
        this.orderManager = new TrackedOrderManager(BAZAAR_DATA);
        this.alertManager = new AlertManager();
        var orderProtectionManager = OrderProtectionManager.getInstance();

        var moduleManager = ModuleManager.getInstance();
        moduleManager.discoverBindings();
        var orderLimitModule = moduleManager.registerModule(OrderLimitModule.class);
        var bookmarkModule = moduleManager.registerModule(BookmarkModule.class);
        var priceDiffModule = moduleManager.registerModule(PriceDiffModule.class);
        var orderValueModule = moduleManager.registerModule(OrderValueModule.class);
        var orderListModule = moduleManager.registerModule(TrackedOrdersListModule.class);
        var orderPresetModule = moduleManager.registerModule(OrderPresetsModule.class);

        this.orderManager.afterOrderSync((unfilledOrders, filledOrder) -> {
            var trackedOrders = this.orderManager.getTrackedOrders();
            this.highlightManager.sync(trackedOrders, filledOrder);
            orderValueModule.sync(unfilledOrders, filledOrder);
        });

        Consumer<OutstandingOrderInfo> addOutstanding = setOrderInfo -> {
            this.orderManager.addOutstandingOrder(setOrderInfo);
            log.trace(
                "Stored outstanding order for {}x {}",
                setOrderInfo.volume(),
                setOrderInfo.productName()
            );
        };

        orderProtectionManager.onSetOrder((stack, pendingOrderData) -> {
            pendingOrderData.ifPresentOrElse(
                data -> addOutstanding.accept(data.orderInfo()),
                () -> OrderInfoParser
                    .parseSetOrderItem(stack)
                    .onSuccess(addOutstanding)
                    .onFailure((err) -> log.warn("Failed to parse confirm item", err))
            );
            BazaarOrderActions.setReopenBazaar();
        });

        BAZAAR_DATA.addListener(this.alertManager::onBazaarUpdate);
        BAZAAR_DATA.addListener(this.orderManager::onBazaarUpdate);

        new BazaarPoller(BAZAAR_DATA::onUpdate);
        var flipHelper = new FlipHelper(BAZAAR_DATA);

        BazaarOrderActions.init();
        ProductInfoProvider.init();

        ScreenActionManager.register(new ScreenClickRule() {
            @Override
            public boolean applies(ScreenInfo info, Slot slot, int button) {
                var cfg = ConfigManager.get();
                if (!cfg.flipHelper.enabled && !cfg.orderActions.enabled) {
                    return false;
                }

                if (slot == null) {
                    return false;
                }

                if (GameUtils.isPlayerInventorySlot(slot)) {
                    return false;
                }

                return info.inMenu(BazaarMenuType.Orders);
            }

            @Override
            public boolean onClick(ScreenInfo info, Slot slot, int button) {
                var orderInfo = OrderInfoParser.parseOrderInfo(slot.getStack(), slot.getIndex());
                if (orderInfo.isSuccess()) {
                    flipHelper.onOrderClick(orderInfo.get());
                    BazaarOrderActions.onOrderClick(orderInfo.get());
                }

                return false;
            }
        });

        messageDispatcher.on(BazaarMessage.OrderFlipped.class, flipHelper::handleFlipped);
        messageDispatcher.on(BazaarMessage.OrderFilled.class, orderManager::removeMatching);
        messageDispatcher.on(BazaarMessage.OrderSetup.class, orderManager::confirmOutstanding);

        messageDispatcher.on(
            BazaarMessage.InstaBuy.class,
            info -> orderLimitModule.onTransaction(info.total())
        );
        messageDispatcher.on(
            BazaarMessage.InstaSell.class,
            info -> orderLimitModule.onTransaction(info.total() * (1 - ConfigManager.get().tax / 100))
        );
        messageDispatcher.on(
            BazaarMessage.OrderSetup.class,
            info -> orderLimitModule.onTransaction(info.total())
        );

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            messageDispatcher.handleChatMessage(Formatting.strip(message.getString()));
        });

        ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) -> {
            var rawMsg = Formatting.strip(message.getString());
            if (overlay || !rawMsg.startsWith("[Bazaar]") || !rawMsg.endsWith("was filled!")) {
                return message;
            }

            return message
                .copy()
                .append(Text
                    .literal(" [Go To Orders]")
                    .styled(style -> style
                        .withClickEvent(new RunCommand("/managebazaarorders"))
                        .withHoverEvent(new ShowText(Text.literal("Opens the Bazaar order screen")))
                        .withColor(Formatting.DARK_AQUA)));
        });

        ScreenInfoHelper.registerOnLoaded(
            info -> info.inMenu(BazaarMenuType.Orders), (info, inv) -> {
                var parsed = inv.items
                    .entrySet()
                    .stream()
                    .filter(entry -> {
                        var stack = entry.getValue();
                        return !stack.isEmpty() && !GameUtils.ORDER_SCREEN_NON_ORDER_ITEMS.contains(
                            stack.getItem());
                    })
                    .map(entry -> OrderInfoParser
                        .parseOrderInfo(entry.getValue(), entry.getKey())
                        .toJavaOptional())
                    .flatMap(Optional::stream)
                    .toList();

                this.orderManager.syncOrders(parsed);
            }
        );
    }
}
