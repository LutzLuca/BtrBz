package com.github.lutzluca.btrbz;

import com.github.lutzluca.btrbz.core.AlertManager;
import com.github.lutzluca.btrbz.core.BazaarOrderActions;
import com.github.lutzluca.btrbz.core.ChatFilterManager;
import com.github.lutzluca.btrbz.core.OrderHighlightManager;
import com.github.lutzluca.btrbz.core.OrderTooltipProvider;
import com.github.lutzluca.btrbz.core.OrderProtectionManager;
import com.github.lutzluca.btrbz.core.ProductInfoProvider;
import com.github.lutzluca.btrbz.core.commands.Commands;
import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.fliphelper.FlipHelper;
import com.github.lutzluca.btrbz.core.fliphelper.FlipProductContext;
import com.github.lutzluca.btrbz.core.fliphelper.FlipSubmissionTracker;
import com.github.lutzluca.btrbz.core.orderbook.OrderBookScreenController;
import com.github.lutzluca.btrbz.core.trackedorders.TrackedOrderManager;
import com.github.lutzluca.btrbz.core.widgets.bookmarks.BookmarksWidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.dailylimit.DailyLimitWidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.orderbook.OrderBookWidgetData;
import com.github.lutzluca.btrbz.core.widgets.orderbook.OrderBookPriceWidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.orderbook.OrderBookWidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.ordervalue.OrderValueWidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.presets.OrderPresetsWidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.pricedifference.PriceDifferenceWidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.trackedorders.TrackedOrdersWidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.data.OrdersWidgetData;
import com.github.lutzluca.btrbz.core.widgets.hud.BazaarOrdersWidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.hud.BtrBzWidgetKeybinds;
import com.github.lutzluca.btrbz.core.widgets.bookmarks.BookmarkComponent;
import com.github.lutzluca.btrbz.core.widgets.dailylimit.DailyLimitComponent;
import com.github.lutzluca.btrbz.core.widgets.orderbook.OrderBookPriceComponent;
import com.github.lutzluca.btrbz.core.widgets.ordervalue.OrderValueComponent;
import com.github.lutzluca.btrbz.core.widgets.presets.OrderPresetsComponent;
import com.github.lutzluca.btrbz.core.widgets.session.DefaultWidgetSessionProvider;
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
import com.github.lutzluca.btrbz.core.widgets.WidgetRegistry;
import com.github.lutzluca.btrbz.core.widgets.WidgetRuntime;
import com.github.lutzluca.btrbz.core.widgets.config.WidgetStateStore;
import com.github.lutzluca.btrbz.core.widgets.hud.HudWidgetBridge;

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
    private WidgetRuntime widgetRuntime;
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

    public static WidgetRuntime widgetRuntime() {
        return instance.widgetRuntime;
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
        var flipProductContext = new FlipProductContext();
        var flipSubmissionTracker = new FlipSubmissionTracker();

        var bookmarks = new BookmarkComponent(
            BAZAAR_DATA,
            productInfoProvider,
            this.orderManager
        );
        var orderValue = new OrderValueComponent();
        var dailyLimit = new DailyLimitComponent();
        var orderPresets = new OrderPresetsComponent(BAZAAR_DATA, productInfoProvider);
        var orderBookPrice = new OrderBookPriceComponent(
            BAZAAR_DATA,
            productInfoProvider,
            flipProductContext,
            flipSubmissionTracker
        );

        var sessionProvider = new DefaultWidgetSessionProvider(
            BAZAAR_DATA,
            productInfoProvider,
            orderBookPrice,
            this.orderManager
        );
        var ordersWidgetData = new OrdersWidgetData(
            BAZAAR_DATA, this.orderManager, this.tooltipProvider, orderValue
        );
        var orderBookWidgetData = new OrderBookWidgetData(BAZAAR_DATA);
        var widgetRegistry = new WidgetRegistry();
        widgetRegistry.register(BazaarOrdersWidgetDefinition.create(ordersWidgetData));
        widgetRegistry.register(TrackedOrdersWidgetDefinition.create(ordersWidgetData, this.orderManager));
        widgetRegistry.register(OrderValueWidgetDefinition.create(orderValue));
        widgetRegistry.register(OrderBookWidgetDefinition.create(orderBookWidgetData, orderBookPrice));
        widgetRegistry.register(OrderBookPriceWidgetDefinition.create(orderBookWidgetData, orderBookPrice));
        widgetRegistry.register(BookmarksWidgetDefinition.create(bookmarks));
        widgetRegistry.register(OrderPresetsWidgetDefinition.create(orderPresets));
        widgetRegistry.register(DailyLimitWidgetDefinition.create(dailyLimit));
        widgetRegistry.register(PriceDifferenceWidgetDefinition.create(BAZAAR_DATA));
        this.widgetRuntime = new WidgetRuntime(widgetRegistry, new WidgetStateStore(), sessionProvider);
        HudWidgetBridge.register(
            Identifier.fromNamespaceAndPath(MOD_ID, "widgets_hud"),
            this.widgetRuntime.createHudHost()
        );
        new OrderBookScreenController(productInfoProvider, this.widgetRuntime);
        Commands.registerAll(BAZAAR_DATA, this.widgetRuntime);
        BtrBzWidgetKeybinds.register();

        this.orderManager.afterOrderSync((unfilledOrders, filledOrder) -> {
            var trackedOrders = this.orderManager.getTrackedOrders();
            this.highlightManager.sync(trackedOrders, filledOrder);
            orderValue.sync(unfilledOrders, filledOrder);
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
        var flipHelper = new FlipHelper(
            BAZAAR_DATA,
            flipProductContext,
            flipSubmissionTracker
        );

        MESSAGE_DISPATCHER.on(BazaarMessage.OrderFlipped.class, flipHelper::handleFlipped);
        MESSAGE_DISPATCHER.on(BazaarMessage.OrderFilled.class, orderManager::removeMatching);
        MESSAGE_DISPATCHER.on(BazaarMessage.OrderSetup.class, orderManager::confirmOutstanding);

        MESSAGE_DISPATCHER.on(
            BazaarMessage.InstaBuy.class,
            info -> dailyLimit.onTransaction(info.total())
        );
        MESSAGE_DISPATCHER.on(
            BazaarMessage.InstaSell.class, info -> dailyLimit
                .onTransaction(info.total() * (1 - ConfigManager.get().tax / 100))
        );
        MESSAGE_DISPATCHER.on(
            BazaarMessage.OrderSetup.class,
            info -> dailyLimit.onTransaction(info.total())
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
                    if (event.message().isBlank()) {
                        MessageQueue.sendOrQueue("Updated Bazaar conversion index", Level.Info);
                    } else {
                        MessageQueue.sendOrQueue(event.message(), Level.Warn);
                    }
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
