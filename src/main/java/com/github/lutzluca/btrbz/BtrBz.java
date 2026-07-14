package com.github.lutzluca.btrbz;

import com.github.lutzluca.btrbz.core.AlertManager;
import com.github.lutzluca.btrbz.core.BazaarOrderActions;
import com.github.lutzluca.btrbz.core.ChatFilterManager;
import com.github.lutzluca.btrbz.core.FlipHelper;
import com.github.lutzluca.btrbz.core.ModuleManager;
import com.github.lutzluca.btrbz.core.ModuleManager.ModuleContext;
import com.github.lutzluca.btrbz.core.OrderHighlightManager;
import com.github.lutzluca.btrbz.core.OrderTooltipProvider;
import com.github.lutzluca.btrbz.core.OrderProtectionManager;
import com.github.lutzluca.btrbz.core.ProductInfoProvider;
import com.github.lutzluca.btrbz.core.commands.Commands;
import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.modules.BookmarkModule;
import com.github.lutzluca.btrbz.core.modules.OrderBookPriceModule;
import com.github.lutzluca.btrbz.core.modules.OrderLimitModule;
import com.github.lutzluca.btrbz.core.modules.orderpreset.OrderPresetsModule;
import com.github.lutzluca.btrbz.core.modules.OrderValueModule;
import com.github.lutzluca.btrbz.core.modules.PriceDiffModule;
import com.github.lutzluca.btrbz.core.modules.TrackedOrdersListModule;
import com.github.lutzluca.btrbz.core.orderbook.OrderBookScreenController;
import com.github.lutzluca.btrbz.core.trackedorders.TrackedOrderManager;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.BazaarMessageDispatcher;
import com.github.lutzluca.btrbz.data.BazaarMessageDispatcher.BazaarMessage;
import com.github.lutzluca.btrbz.data.BazaarPoller;
import com.github.lutzluca.btrbz.data.ConversionEvent;
import com.github.lutzluca.btrbz.data.OrderInfoParser;
import com.github.lutzluca.btrbz.data.OrderModels.OutstandingOrderInfo;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.github.lutzluca.btrbz.utils.MessageQueue;
import com.github.lutzluca.btrbz.utils.MessageQueue.Level;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import com.mojang.serialization.Codec;
import java.util.Optional;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent.RunCommand;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent.ShowText;
import net.minecraft.resources.Identifier;

@Slf4j
public class BtrBz implements ClientModInitializer {

    public static final String MOD_ID = "btrbz";
    public static DataComponentType<Boolean> BOOKMARKED;
    
    public static final BazaarMessageDispatcher MESSAGE_DISPATCHER = new BazaarMessageDispatcher();
    private static final BazaarData BAZAAR_DATA = new BazaarData();
    
    private static BtrBz instance;

    private TrackedOrderManager orderManager;
    private OrderHighlightManager highlightManager;
    private AlertManager alertManager;
    private OrderTooltipProvider tooltipProvider;
    private OrderProtectionManager orderProtectionManager;
    private boolean automaticConversionFailureNotified;

    public static TrackedOrderManager orderManager() {
        return instance.orderManager;
    }

    public static OrderHighlightManager highlightManager() {
        return instance.highlightManager;
    }

    public static AlertManager alertManager() {
        return instance.alertManager;
    }

    public static OrderTooltipProvider tooltipProvider() {
        return instance.tooltipProvider;
    }

    public static OrderProtectionManager orderProtectionManager() {
        return instance.orderProtectionManager;
    }

    @Override
    public void onInitializeClient() {
        instance = this;

        BOOKMARKED = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE,
            Identifier.fromNamespaceAndPath(BtrBz.MOD_ID, "bookmarked"),
            DataComponentType.<Boolean>builder().persistent(Codec.BOOL).build()
        );

        ConfigManager.load();
        Commands.registerAll(BAZAAR_DATA);
        BAZAAR_DATA.addConversionEventListener(this::handleConversionEvent);
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> BAZAAR_DATA.loadConversions());

        this.highlightManager = new OrderHighlightManager();
        this.tooltipProvider = new OrderTooltipProvider(BAZAAR_DATA);

        ScreenInfoHelper.registerOnSwitch(info -> this.highlightManager.clearHighlightOverride());

        this.orderManager = new TrackedOrderManager(BAZAAR_DATA);
        this.orderManager.addOnOrderUpdatedListener(order -> this.tooltipProvider.clearCache());
        this.alertManager = new AlertManager(BAZAAR_DATA);
        new ChatFilterManager();
        this.orderProtectionManager = new OrderProtectionManager(BAZAAR_DATA);

        var productInfoProvider = new ProductInfoProvider(BAZAAR_DATA);
        var orderActions = new BazaarOrderActions(BAZAAR_DATA);
        new OrderBookScreenController(BAZAAR_DATA, productInfoProvider);

        var moduleManager = ModuleManager.getInstance();
        moduleManager.initContext(new ModuleContext(BAZAAR_DATA, productInfoProvider));

        moduleManager.discoverBindings();
        moduleManager.registerModule(BookmarkModule.class);
        moduleManager.registerModule(PriceDiffModule.class);
        moduleManager.registerModule(TrackedOrdersListModule.class);
        moduleManager.registerModule(OrderPresetsModule.class);
        var orderLimitModule = moduleManager.registerModule(OrderLimitModule.class);
        var orderValueModule = moduleManager.registerModule(OrderValueModule.class);
        moduleManager.registerModule(OrderBookPriceModule.class);

        this.orderManager.afterOrderSync((unfilledOrders, filledOrder) -> {
            var trackedOrders = this.orderManager.getTrackedOrders();
            this.highlightManager.sync(trackedOrders, filledOrder);
            orderValueModule.sync(unfilledOrders, filledOrder);
        });

        Consumer<OutstandingOrderInfo> addOutstanding = setOrderInfo -> {
            this.orderManager.addOutstandingOrder(setOrderInfo);
            log.trace(
                "Stored outstanding order for {}x {}", setOrderInfo.volume(),
                setOrderInfo.productName()
            );
        };

        orderProtectionManager.onSetOrder((stack, pendingOrderData) -> {
            pendingOrderData.ifPresentOrElse(
                data -> addOutstanding.accept(data.orderInfo()),
                () -> OrderInfoParser
                    .parseSetOrderItem(stack, BAZAAR_DATA)
                    .onSuccess(addOutstanding)
                    .onFailure(err -> log.warn("Failed to parse confirm item", err))
            );
            orderActions.setReopenBazaar();
        });

        BAZAAR_DATA.addListener(this.alertManager::onBazaarUpdate);
        BAZAAR_DATA.addListener(this.orderManager::onBazaarUpdate);

        new BazaarPoller(BAZAAR_DATA::onUpdate);
        var flipHelper = new FlipHelper(BAZAAR_DATA, productInfoProvider);

        MESSAGE_DISPATCHER.on(BazaarMessage.OrderFlipped.class, flipHelper::handleFlipped);
        MESSAGE_DISPATCHER.on(BazaarMessage.OrderFilled.class, orderManager::removeMatching);
        MESSAGE_DISPATCHER.on(BazaarMessage.OrderSetup.class, orderManager::confirmOutstanding);

        MESSAGE_DISPATCHER.on(
            BazaarMessage.InstaBuy.class,
            info -> orderLimitModule.onTransaction(info.total())
        );
        MESSAGE_DISPATCHER.on(
            BazaarMessage.InstaSell.class, info -> orderLimitModule
                .onTransaction(info.total() * (1 - ConfigManager.get().tax / 100))
        );
        MESSAGE_DISPATCHER.on(
            BazaarMessage.OrderSetup.class,
            info -> orderLimitModule.onTransaction(info.total())
        );

        ClientReceiveMessageEvents.GAME.register((message, overlay) ->
            MESSAGE_DISPATCHER.handleChatMessage(GameUtils.stripFormattingCodes(message.getString()))
        );

        ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) -> {
            var rawMsg = GameUtils.stripFormattingCodes(message.getString());
            if (overlay || !rawMsg.startsWith("[Bazaar]") || !rawMsg.endsWith("was filled!")) {
                return message;
            }

            // TODO: make this optional (config flag)
            return message.copy()
                .withStyle(style -> style
                    .withClickEvent(new RunCommand("/managebazaarorders"))
                    .withHoverEvent(new ShowText(Component.literal("Opens the Bazaar order screen"))))
                .append(Component.literal(" [Go To Orders]")
                    .withStyle(ChatFormatting.DARK_AQUA));
        });

        ScreenInfoHelper.registerOnLoaded(
            info -> info.inMenu(BazaarMenuType.Orders),
            (info, inv) -> {
                var parsed = inv.items
                    .entrySet()
                    .stream()
                    .filter(entry -> GameUtils.orderScreenNonOrderItemsFilter(entry.getValue()))
                    .map(entry -> OrderInfoParser
                        .parseOrderInfo(entry.getValue(), entry.getKey(), BAZAAR_DATA)
                        .toJavaOptional()
                    )
                    .flatMap(Optional::stream)
                    .toList();

                this.orderManager.syncOrders(parsed);
            }
        );
    }

    private void handleConversionEvent(ConversionEvent event) {
        switch (event.kind()) {
            case LoadFailure -> MessageQueue.sendOrQueue(
                "Failed to load Bazaar conversions; some features may not work as expected. Try /btrbz conversions refresh.",
                Level.Error
            );
            case RefreshAlreadyRunning -> {
                if (event.manual()) {
                    MessageQueue.sendOrQueue("Bazaar conversion refresh is already running", Level.Info);
                }
            }
            case RefreshSuccess -> {
                this.automaticConversionFailureNotified = false;
                if (event.manual()) {
                    MessageQueue.sendOrQueue("Updated Bazaar conversion index", Level.Info);
                }
            }
            case PersistFailure -> {
                if (event.manual()) {
                    MessageQueue.sendOrQueue("Updated Bazaar conversions, but failed to cache them locally", Level.Warn);
                }
            }
            case RefreshFailure -> {
                if (event.manual()) {
                    MessageQueue.sendOrQueue(
                        "Failed to refresh Bazaar conversions: " + event.message(),
                        Level.Warn
                    );
                    return;
                }

                if (!this.automaticConversionFailureNotified) {
                    this.automaticConversionFailureNotified = true;
                    MessageQueue.sendOrQueue(
                        "BtrBz could not refresh Bazaar conversions; using bundled/cache data. Run /btrbz conversions status for details.",
                        Level.Warn
                    );
                }
            }
        }
    }
}
