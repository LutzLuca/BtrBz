package com.github.lutzluca.btrbz;

import com.github.lutzluca.btrbz.core.AlertManager;
import com.github.lutzluca.btrbz.core.Activation;
import com.github.lutzluca.btrbz.core.SkyBlockDetector;
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
import com.github.lutzluca.btrbz.core.orderbook.OrderBookScreen;
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
import com.github.lutzluca.btrbz.core.widgets.hud.BazaarHudHintController;
import com.github.lutzluca.btrbz.core.widgets.hud.BtrBzWidgetKeybinds;
import com.github.lutzluca.btrbz.core.widgets.bookmarks.BookmarkComponent;
import com.github.lutzluca.btrbz.core.widgets.dailylimit.DailyLimitComponent;
import com.github.lutzluca.btrbz.core.widgets.orderbook.OrderBookPriceComponent;
import com.github.lutzluca.btrbz.core.widgets.ordervalue.OrderValueComponent;
import com.github.lutzluca.btrbz.core.widgets.presets.OrderPresetsComponent;
import com.github.lutzluca.btrbz.core.widgets.session.DefaultWidgetSessionProvider;
import com.github.lutzluca.btrbz.core.widgets.cache.ClipboardTracker;
import com.github.lutzluca.btrbz.core.widgets.cache.MemoizedWidgetDataSource;
import com.github.lutzluca.btrbz.core.widgets.cache.PurseTracker;
import com.github.lutzluca.btrbz.core.widgets.cache.UtcDayTracker;
import com.github.lutzluca.btrbz.core.widgets.ui.TextRenderRevision;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.reloader.ResourceReloaderKeys;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
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
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;

@Slf4j
public class BtrBz implements ClientModInitializer {

    public static final String MOD_ID = "btrbz";
    public static DataComponentType<Boolean> BOOKMARKED;

    private static BtrBz instance;

    private Activation activation;
    private BazaarData bazaarData;
    private TrackedOrderManager orderManager;
    private OrderHighlightManager highlightManager;
    private AlertManager alertManager;
    private OrderTooltipProvider tooltipProvider;
    private OrderProtectionManager orderProtectionManager;
    private WidgetRuntime widgetRuntime;
    private BazaarPoller bazaarPoller;
    private BazaarOrderActions orderActions;
    private OrderPresetsComponent orderPresets;
    private OrderValueComponent orderValue;
    private UtcDayTracker utcDayTracker;
    private ClipboardTracker clipboardTracker;
    private PurseTracker purseTracker;
    private FlipHelper flipHelper;
    private FlipSubmissionTracker flipSubmissionTracker;
    private ProductInfoProvider productInfoProvider;
    private boolean automaticConversionRefreshStarted;

    public static boolean isActive() {
        return instance != null && instance.activation != null && instance.activation.isActive();
    }

    public static long activationGeneration() {
        return instance.activation.generation();
    }

    public static String setEnabled(boolean enabled) {
        ConfigManager.updateIfChanged(config -> {
            if (config.enabled == enabled) {
                return false;
            }
            config.enabled = enabled;
            return true;
        });
        instance.activation.refresh();
        return instance.activation.description();
    }

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
        ConfigManager.load();
        this.activation = new Activation(() -> ConfigManager.get().enabled, this::onActivationChanged);
        this.bazaarData = new BazaarData();
        var messageDispatcher = new BazaarMessageDispatcher();

        BOOKMARKED = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE,
            Identifier.fromNamespaceAndPath(BtrBz.MOD_ID, "bookmarked"),
            DataComponentType.<Boolean>builder().persistent(Codec.BOOL).build());

        this.bazaarData.addConversionEventListener(this::handleConversionEvent);
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> this.bazaarData.loadConversions());

        this.highlightManager = new OrderHighlightManager();
        this.tooltipProvider = new OrderTooltipProvider(this.bazaarData);

        ScreenInfoHelper.registerOnSwitch(info -> this.highlightManager.clearHighlightOverride());

        this.orderManager = new TrackedOrderManager(this.bazaarData);
        this.orderManager.addOnOrderUpdatedListener(order -> this.tooltipProvider.clearCache());
        this.alertManager = new AlertManager(this.bazaarData);
        new ChatFilterManager();
        this.orderProtectionManager = new OrderProtectionManager(this.bazaarData);

        this.productInfoProvider = new ProductInfoProvider(this.bazaarData);
        this.orderActions = new BazaarOrderActions(this.bazaarData);
        var flipProductContext = new FlipProductContext();
        this.flipSubmissionTracker = new FlipSubmissionTracker();

        this.utcDayTracker = new UtcDayTracker();
        this.clipboardTracker = new ClipboardTracker(
            () -> Minecraft.getInstance().keyboardHandler.getClipboard());
        this.purseTracker = new PurseTracker(GameUtils::getPurse);

        var textRevisionId = Identifier.fromNamespaceAndPath(MOD_ID, "text_render_revision");
        var clientResources = ResourceLoader.get(PackType.CLIENT_RESOURCES);

        clientResources.registerReloadListener(textRevisionId, new PreparableReloadListener() {
            @Override
            public CompletableFuture<Void> reload(
                SharedState sharedState,
                Executor backgroundExecutor,
                PreparationBarrier barrier,
                Executor gameExecutor
            ) {
                return barrier.wait(null)
                    .thenRunAsync(TextRenderRevision::invalidate, gameExecutor);
            }
        });

        clientResources.addListenerOrdering(
            ResourceReloaderKeys.AFTER_VANILLA, textRevisionId);

        var bookmarks = new BookmarkComponent(
            this.bazaarData,
            this.productInfoProvider,
            this.orderManager);
        this.orderValue = new OrderValueComponent();
        var dailyLimit = new DailyLimitComponent(this.utcDayTracker);
        this.orderPresets = new OrderPresetsComponent(
            this.bazaarData, this.productInfoProvider, this.clipboardTracker, this.purseTracker);
        var orderBookPrice = new OrderBookPriceComponent(
            this.bazaarData,
            this.productInfoProvider,
            flipProductContext,
            this.flipSubmissionTracker);

        var sessionProvider = new DefaultWidgetSessionProvider(
            this.bazaarData,
            this.productInfoProvider,
            orderBookPrice);
        var ordersWidgetData = new MemoizedWidgetDataSource<>(new OrdersWidgetData(
            this.bazaarData, this.orderManager, this.tooltipProvider));
        var orderBookWidgetData = new MemoizedWidgetDataSource<>(new OrderBookWidgetData(this.bazaarData));
        var toggleHudKey = BtrBzWidgetKeybinds.registerMapping();
        var bazaarOrdersWidget = BazaarOrdersWidgetDefinition.create(
            ordersWidgetData, toggleHudKey::getTranslatedKeyMessage);
        var widgetRegistry = new WidgetRegistry();
        widgetRegistry.register(bazaarOrdersWidget);
        widgetRegistry.register(TrackedOrdersWidgetDefinition.create(ordersWidgetData, this.orderManager));
        widgetRegistry.register(OrderValueWidgetDefinition.create(this.orderValue));
        widgetRegistry.register(OrderBookWidgetDefinition.create(orderBookWidgetData, orderBookPrice));
        widgetRegistry.register(OrderBookPriceWidgetDefinition.create(orderBookWidgetData, orderBookPrice));
        widgetRegistry.register(BookmarksWidgetDefinition.create(bookmarks));
        widgetRegistry.register(OrderPresetsWidgetDefinition.create(this.orderPresets));
        widgetRegistry.register(DailyLimitWidgetDefinition.create(dailyLimit));
        widgetRegistry.register(PriceDifferenceWidgetDefinition.create(this.bazaarData));
        var widgetStateStore = new WidgetStateStore();
        this.widgetRuntime = new WidgetRuntime(widgetRegistry, widgetStateStore, sessionProvider);
        var hudHint = new BazaarHudHintController(
            bazaarOrdersWidget.getConfigHandle(),
            toggleHudKey::getTranslatedKeyMessage,
            ConfigManager::save);
        HudWidgetBridge.register(
            Identifier.fromNamespaceAndPath(MOD_ID, "widgets_hud"),
            this.widgetRuntime.createHudHost(),
            hudHint::onWidgetRendered);
        new OrderBookScreenController(this.productInfoProvider, this.widgetRuntime);
        Commands.registerAll(this.bazaarData, this.widgetRuntime);
        BtrBzWidgetKeybinds.registerHandler(
            toggleHudKey, bazaarOrdersWidget, widgetStateStore, hudHint::dismiss);

        this.orderManager.afterOrderSync((unfilledOrders, filledOrder) -> {
            var trackedOrders = this.orderManager.getTrackedOrders();
            this.highlightManager.sync(trackedOrders, filledOrder);
            this.orderValue.sync(unfilledOrders, filledOrder);
        });

        Consumer<OutstandingOrderInfo> addOutstanding = setOrderInfo -> {
            this.orderManager.addOutstandingOrder(setOrderInfo);
            log.trace(
                "Stored outstanding order for {}x {}", setOrderInfo.volume(),
                setOrderInfo.productName());
        };

        orderProtectionManager.onSetOrder((stack, pendingOrderData) -> {
            pendingOrderData.ifPresentOrElse(
                data -> addOutstanding.accept(data.orderInfo()),
                () -> OrderInfoParser
                    .parseSetOrderItem(stack, this.bazaarData)
                    .onSuccess(addOutstanding)
                    .onFailure(err -> log.warn("Failed to parse confirm item", err)));
            this.orderActions.setReopenBazaar();
        });

        this.bazaarData.addListener(this.alertManager::onBazaarUpdate);
        this.bazaarData.addListener(this.orderManager::onBazaarUpdate);

        this.bazaarPoller = new BazaarPoller(this.bazaarData::onUpdate);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            this.activation.setSkyBlockConfirmed(false);
            ConfigManager.save();
            this.flipSubmissionTracker.close();
            this.bazaarPoller.close();
        });
        this.flipHelper = new FlipHelper(
            this.bazaarData,
            flipProductContext,
            this.flipSubmissionTracker);

        messageDispatcher.on(BazaarMessage.OrderFlipped.class, this.flipHelper::handleFlipped);
        messageDispatcher.on(BazaarMessage.OrderFilled.class, orderManager::handleOrderFilled);
        messageDispatcher.on(BazaarMessage.OrderSetup.class, orderManager::confirmOutstanding);

        messageDispatcher.on(
            BazaarMessage.InstaBuy.class,
            info -> dailyLimit.onTransaction(info.total()));
        messageDispatcher.on(
            BazaarMessage.InstaSell.class, info -> dailyLimit
                .onTransaction(info.total() * (1 - ConfigManager.get().tax / 100)));
        messageDispatcher.on(
            BazaarMessage.OrderSetup.class,
            info -> dailyLimit.onTransaction(info.total()));

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (BtrBz.isActive()) {
                messageDispatcher.handleChatMessage(GameUtils.stripFormattingCodes(message.getString()));
            }
        });

        ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) -> {
            if (!BtrBz.isActive()) {
                return message;
            }
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
                        .parseOrderInfo(entry.getValue(), entry.getKey(), this.bazaarData)
                        .toJavaOptional())
                    .flatMap(Optional::stream)
                    .toList();

                this.orderManager.syncOrders(parsed);
            });

        new SkyBlockDetector(this.activation).register();
    }

    private void onActivationChanged(boolean active) {
        if (active) {
            this.activate();
        } else {
            this.deactivate();
        }
    }

    private void activate() {
        log.info("BtrBz features activated");
        this.utcDayTracker.start();
        this.clipboardTracker.initialize();
        this.clipboardTracker.start();
        this.purseTracker.start();
        this.bazaarPoller.start();
        if (!this.automaticConversionRefreshStarted) {
            this.automaticConversionRefreshStarted = true;
            this.bazaarData.refreshConversions(false);
        }
    }

    private void deactivate() {
        log.info("BtrBz features deactivated");
        this.bazaarPoller.stop();
        this.bazaarData.clearMarketData();
        this.utcDayTracker.close();
        this.clipboardTracker.close();
        this.purseTracker.close();
        this.orderActions.cancelPendingActions();
        this.orderPresets.cancelTransaction();
        this.flipHelper.cancelPendingFlip();
        this.flipSubmissionTracker.clear();
        this.orderManager.cancelOutstandingOrders();
        this.productInfoProvider.clearProductContext();
        this.highlightManager.clear();
        this.orderValue.clear();
        ScreenInfoHelper.get().discard();
        this.widgetRuntime.disposeRuntimeWidgets();
        if (GameUtils.screen() instanceof OrderBookScreen) {
            GameUtils.setScreen(null);
        }
    }

    private void handleConversionEvent(ConversionEvent event) {
        if (!event.manual() && !BtrBz.isActive()) {
            return;
        }
        switch (event.kind()) {
            case LoadFailure -> MessageQueue.sendOrQueue(
                "Failed to load Bazaar conversions; some features may not work as expected. "
                    + "Try /btrbz conversions refresh.",
                Level.Error);
            case RefreshAlreadyRunning -> {
                if (event.manual()) {
                    MessageQueue.sendOrQueue("Bazaar conversion refresh is already running", Level.Info);
                }
            }
            case RefreshSuccess -> {
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
                    MessageQueue.sendOrQueue("Updated Bazaar conversions, but failed to cache them locally",
                        Level.Warn);
                }
            }
            case RefreshFailure -> {
                if (event.manual()) {
                    MessageQueue.sendOrQueue(
                        "Failed to refresh Bazaar conversions: " + event.message(),
                        Level.Warn);
                    return;
                }

                MessageQueue.sendOrQueue(
                    "BtrBz could not refresh Bazaar conversions; using bundled/cache data. "
                        + "Run /btrbz conversions status for details.",
                    Level.Warn);
            }
        }
    }
}
