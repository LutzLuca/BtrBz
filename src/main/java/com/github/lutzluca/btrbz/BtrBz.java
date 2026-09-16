package com.github.lutzluca.btrbz;

import com.github.lutzluca.btrbz.utils.Utils;

import com.github.lutzluca.btrbz.core.AlertManager;
import com.github.lutzluca.btrbz.core.Activation;
import com.github.lutzluca.btrbz.core.SkyBlockDetector;
import com.github.lutzluca.btrbz.core.BazaarOrderActions;
import com.github.lutzluca.btrbz.core.ChatFilterManager;
import com.github.lutzluca.btrbz.core.OrderHighlightManager;
import com.github.lutzluca.btrbz.core.OrderTooltipProvider;
import com.github.lutzluca.btrbz.core.OrderProtectionManager;
import com.github.lutzluca.btrbz.core.productinfo.ProductInformation;
import com.github.lutzluca.btrbz.screen.BazaarProductContext;
import com.github.lutzluca.btrbz.core.commands.Commands;
import com.github.lutzluca.btrbz.core.config.ConfigStore;
import com.github.lutzluca.btrbz.core.config.ConfigScreen;
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
import com.github.lutzluca.btrbz.screen.ScreenTracker;
import com.github.lutzluca.btrbz.screen.ScreenTracker.BazaarMenuType;
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
    private BazaarProductContext bazaarProductContext;
    private ConfigScreen configScreen;
    private boolean automaticConversionRefreshStarted;

    public static boolean isActive() {
        return instance != null && instance.activation != null && instance.activation.isActive();
    }

    public static long activationGeneration() {
        return instance.activation.generation();
    }

    private String setEnabled(boolean enabled) {
        ConfigStore.get().updateIfChanged(config -> {
            if (config.enabled == enabled) {
                return false;
            }
            config.enabled = enabled;
            return true;
        });
        this.activation.refresh();
        return this.activation.description();
    }

    public static ConfigScreen configScreen() {
        return instance.configScreen;
    }

    public static OrderProtectionManager orderProtectionManager() {
        return instance.orderProtectionManager;
    }

    public static OrderHighlightManager highlightManager() {
        return instance.highlightManager;
    }

    public static WidgetRuntime widgetRuntime() {
        return instance.widgetRuntime;
    }

    @Override
    public void onInitializeClient() {
        BOOKMARKED = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE,
            Identifier.fromNamespaceAndPath(BtrBz.MOD_ID, "bookmarked"),
            DataComponentType.<Boolean>builder().persistent(Codec.BOOL).build());

        instance = this;
        var configStore = ConfigStore.get();
        configStore.load();
        this.activation = new Activation(
            () -> configStore.config().enabled,
            () -> configStore.config().alwaysActive,
            this::onActivationChanged);
        this.bazaarData = new BazaarData();
        var messageDispatcher = new BazaarMessageDispatcher();
        this.utcDayTracker = new UtcDayTracker();
        this.clipboardTracker = new ClipboardTracker(
            () -> Minecraft.getInstance().keyboardHandler.getClipboard());
        this.purseTracker = new PurseTracker(GameUtils::getPurse);
        this.bazaarPoller = new BazaarPoller(this.bazaarData::onUpdate);
        var flipProductContext = new FlipProductContext();
        this.flipSubmissionTracker = new FlipSubmissionTracker();

        this.highlightManager = new OrderHighlightManager();
        this.tooltipProvider = new OrderTooltipProvider(this.bazaarData, this.highlightManager);
        this.orderManager = new TrackedOrderManager(this.bazaarData);
        this.orderManager.addOnOrderUpdatedListener(order -> this.tooltipProvider.clearCache());
        this.alertManager = new AlertManager(this.bazaarData);
        new ChatFilterManager();
        this.orderProtectionManager = new OrderProtectionManager(this.bazaarData);
        this.bazaarProductContext = new BazaarProductContext(this.bazaarData);
        new ProductInformation(this.bazaarData, this.bazaarProductContext,
            () -> configStore.config().productInfo);
        this.orderActions = new BazaarOrderActions(this.bazaarData);
        var bookmarks = new BookmarkComponent(
            this.bazaarData,
            this.bazaarProductContext,
            this.orderManager,
            () -> configStore.config().widgets.bookmarks,
            configStore::save);
        this.orderValue = new OrderValueComponent();
        var dailyLimit = new DailyLimitComponent(
            () -> configStore.config().widgets.orderLimit, configStore::save, this.utcDayTracker);
        this.orderPresets = new OrderPresetsComponent(
            this.bazaarData, this.bazaarProductContext, this.clipboardTracker, this.purseTracker,
            () -> configStore.config().widgets.orderPresets, configStore::save);
        var orderBookPrice = new OrderBookPriceComponent(
            this.bazaarData,
            this.bazaarProductContext,
            flipProductContext,
            this.flipSubmissionTracker);

        var sessionProvider = new DefaultWidgetSessionProvider(
            this.bazaarData,
            this.bazaarProductContext,
            orderBookPrice);
        var ordersWidgetData = new MemoizedWidgetDataSource<>(new OrdersWidgetData(
            this.bazaarData, this.orderManager, this.tooltipProvider));
        var orderBookWidgetData = new MemoizedWidgetDataSource<>(new OrderBookWidgetData(this.bazaarData));
        var toggleHudKey = BtrBzWidgetKeybinds.registerMapping();
        var bazaarOrdersWidgetDefinition = BazaarOrdersWidgetDefinition.create(
            ordersWidgetData, toggleHudKey::getTranslatedKeyMessage,
            () -> configStore.config().widgets.bazaarOrders);
        var trackedOrdersWidgetDefinition = TrackedOrdersWidgetDefinition.create(
            ordersWidgetData, this.orderManager, () -> configStore.config().widgets.trackedOrders);
        var orderValueWidgetDefinition = OrderValueWidgetDefinition.create(
            this.orderValue, () -> configStore.config().widgets.orderValue);
        var orderBookWidgetDefinition = OrderBookWidgetDefinition.create(
            orderBookWidgetData, orderBookPrice, () -> configStore.config().widgets.orderBookScreen);
        var orderBookPriceWidgetDefinition = OrderBookPriceWidgetDefinition.create(
            orderBookWidgetData, orderBookPrice, () -> configStore.config().widgets.orderBookPrice);
        var bookmarksWidgetDefinition = BookmarksWidgetDefinition.create(
            bookmarks, () -> configStore.config().widgets.bookmarks);
        var orderPresetsWidgetDefinition = OrderPresetsWidgetDefinition.create(
            this.orderPresets, () -> configStore.config().widgets.orderPresets);
        var dailyLimitWidgetDefinition = DailyLimitWidgetDefinition.create(
            dailyLimit, () -> configStore.config().widgets.orderLimit);
        var priceDifferenceWidgetDefinition = PriceDifferenceWidgetDefinition.create(
            this.bazaarData, () -> configStore.config().widgets.priceDiff);
        var widgetRegistry = new WidgetRegistry();
        widgetRegistry.register(
            bazaarOrdersWidgetDefinition,
            trackedOrdersWidgetDefinition,
            orderValueWidgetDefinition,
            orderBookWidgetDefinition,
            orderBookPriceWidgetDefinition,
            bookmarksWidgetDefinition,
            orderPresetsWidgetDefinition,
            dailyLimitWidgetDefinition,
            priceDifferenceWidgetDefinition);
        var widgetStateStore = new WidgetStateStore(() -> configStore.config().widgets, configStore::save);
        this.widgetRuntime = new WidgetRuntime(widgetRegistry, widgetStateStore, sessionProvider, this.activation);

        this.configScreen = new ConfigScreen(this.widgetRuntime, this.activation, this.tooltipProvider);
        var hudHint = new BazaarHudHintController(
            bazaarOrdersWidgetDefinition.getConfigHandle(),
            toggleHudKey::getTranslatedKeyMessage,
            configStore::save);
        HudWidgetBridge.register(
            Identifier.fromNamespaceAndPath(MOD_ID, "widgets_hud"),
            this.widgetRuntime.createHudHost(),
            hudHint::onWidgetRendered);
        new OrderBookScreenController(this.bazaarProductContext, this.widgetRuntime);
        Commands.registerAll(this.bazaarData, this.widgetRuntime, this.alertManager, this.orderManager,
            this.configScreen::open, this::setEnabled);
        BtrBzWidgetKeybinds.registerHandler(
            toggleHudKey, bazaarOrdersWidgetDefinition, widgetStateStore, hudHint::dismiss);

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

        this.orderProtectionManager.onSetOrder((stack, pendingOrderData) -> {
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

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            log.info(
                "BtrBz client shutdown started (active={}, generation={})",
                this.activation.isActive(),
                this.activation.generation());
            this.activation.setSkyBlockConfirmed(false);
            configStore.save();
            this.flipSubmissionTracker.close();
            this.bazaarPoller.close();
        });

        this.flipHelper = new FlipHelper(
            this.bazaarData,
            flipProductContext,
            this.flipSubmissionTracker,
            this.orderManager);

        messageDispatcher.on(BazaarMessage.OrderFlipped.class, this.flipHelper::handleFlipped);
        messageDispatcher.on(BazaarMessage.OrderFilled.class, this.orderManager::handleOrderFilled);
        messageDispatcher.on(BazaarMessage.OrderSetup.class, this.orderManager::confirmOutstanding);

        messageDispatcher.on(
            BazaarMessage.InstaBuy.class,
            info -> dailyLimit.onTransaction(info.total()));
        messageDispatcher.on(
            BazaarMessage.InstaSell.class, info -> dailyLimit
                .onTransaction(info.total() * (1 - configStore.config().tax / 100)));
        messageDispatcher.on(
            BazaarMessage.OrderSetup.class,
            info -> dailyLimit.onTransaction(info.total()));

        this.bazaarData.addConversionEventListener(this::handleConversionEvent);
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> this.bazaarData.loadConversions());

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

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (BtrBz.isActive()) {
                messageDispatcher.handleChatMessage(Utils.stripFormattingCodes(message.getString()));
            }
        });

        ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) -> {
            if (!BtrBz.isActive()) {
                return message;
            }
            var rawMsg = Utils.stripFormattingCodes(message.getString());
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

        ScreenTracker.registerOnLoaded(
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
        this.activation.refresh();
    }

    private void onActivationChanged(boolean active) {
        if (active) {
            this.activate();
        } else {
            this.deactivate();
        }
    }

    private void activate() {
        log.info("BtrBz features activated (generation={})", this.activation.generation());
        this.utcDayTracker.start();
        this.clipboardTracker.initialize();
        this.clipboardTracker.start();
        this.purseTracker.start();
        this.bazaarPoller.start();
        log.debug(
            "Activation lifecycle started: trackers active, Bazaar polling requested, marketDataAvailable={}",
            this.bazaarData.hasMarketData());
        if (!this.automaticConversionRefreshStarted) {
            this.automaticConversionRefreshStarted = true;
            this.bazaarData.refreshConversions(false);
        }
    }

    private void deactivate() {
        log.info("BtrBz features deactivated (generation={})", this.activation.generation());
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
        this.bazaarProductContext.clear();
        this.highlightManager.clear();
        this.orderValue.clear();
        ScreenTracker.get().discard();
        this.widgetRuntime.disposeRuntimeWidgets();
        if (GameUtils.screen() instanceof OrderBookScreen) {
            GameUtils.setScreen(null);
        }
        log.debug(
            "Deactivation cleanup completed: trackers stopped, marketDataAvailable={}, session UI cleared",
            this.bazaarData.hasMarketData());
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
