package com.github.lutzluca.btrbz;

import com.github.lutzluca.btrbz.utils.Utils;
import com.github.lutzluca.btrbz.core.iteminfo.ItemInfoController;
import com.github.lutzluca.coflnet.CoflnetClient;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;

import com.github.lutzluca.btrbz.core.alert.AlertManager;
import com.github.lutzluca.btrbz.core.alert.AlertScreen;
import com.github.lutzluca.btrbz.core.alert.AlertNotifications;
import com.github.lutzluca.btrbz.core.alert.AlertShortcut;
import com.github.lutzluca.btrbz.core.runtime.Activation;
import com.github.lutzluca.btrbz.core.runtime.FeatureRuntime;
import com.github.lutzluca.btrbz.core.runtime.SkyBlockDetector;
import com.github.lutzluca.btrbz.core.runtime.ProfileTracker;
import com.github.lutzluca.btrbz.core.BazaarOrderActions;
import com.github.lutzluca.btrbz.core.BazaarChatManager;
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
import com.github.lutzluca.btrbz.core.trackedorders.TrackedOrderManager;
import com.github.lutzluca.btrbz.core.widgets.bookmarks.BookmarksWidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.dailylimit.DailyLimitWidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.orderbook.OrderBookWidgetData;
import com.github.lutzluca.btrbz.core.widgets.orderbook.OrderBookPriceWidgetDefinition;
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
import com.github.lutzluca.btrbz.utils.ClientTickDispatcher;
import com.github.lutzluca.btrbz.utils.MessageQueue;
import com.github.lutzluca.btrbz.utils.Notifier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent.RunCommand;
import net.minecraft.network.chat.HoverEvent.ShowText;
import com.github.lutzluca.btrbz.utils.SoundUtil;
import com.github.lutzluca.btrbz.utils.ToastNotifications;
import com.github.lutzluca.btrbz.utils.MessageQueue.Level;
import com.github.lutzluca.btrbz.screen.ScreenTracker;
import com.github.lutzluca.btrbz.screen.ScreenTracker.BazaarMenuType;
import com.github.lutzluca.btrbz.screen.slot.SlotClickContext;
import com.mojang.serialization.Codec;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.reloader.ResourceReloaderKeys;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
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
    private FeatureRuntime runtime;
    private ProfileTracker profileTracker;
    private BazaarData bazaarData;
    private TrackedOrderManager orderManager;
    private OrderHighlightManager highlightManager;
    private AlertManager alertManager;
    private ToastNotifications toastNotifications;
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
    private CoflnetClient coflnetClient;
    private ItemInfoController itemInfo;
    private boolean automaticConversionRefreshStarted;
    private boolean marketHibernationAnnounced;

    public static boolean isActive() {
        return instance != null && instance.runtime != null && instance.runtime.isActive();
    }

    public static boolean isRunning() {
        return instance != null && instance.runtime != null && instance.runtime.isRunning();
    }

    public static long sessionGeneration() {
        return instance.runtime.sessionGeneration();
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

    public static boolean handleItemInfoKey(AbstractContainerScreen<?> screen, KeyEvent event) {
        return instance != null && instance.itemInfo != null && instance.itemInfo.handleKey(screen, event);
    }

    public static OrderProtectionManager orderProtectionManager() {
        return instance.orderProtectionManager;
    }

    public static void observeAcceptedClick(SlotClickContext context) {
        instance.orderProtectionManager.observeAcceptedConfirmation(context);
        instance.flipHelper.observeAcceptedOrderClick(context);
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
        this.bazaarPoller = new BazaarPoller(reply -> {
            this.runtime.onMarketReply(reply);
            if (this.marketHibernationAnnounced && this.runtime.isActive()) {
                this.marketHibernationAnnounced = false;
                Notifier.notifyPlayer(Notifier.prefix().append(Component.literal(
                    "Bazaar market data is usable again, and market features have resumed.")
                    .withStyle(ChatFormatting.GREEN)));
            }
        },
            this::hibernateMarket,
            () -> this
                .warnBazaarUnavailable(
                    "Bazaar API requests are still failing after five minutes, retrying automatically."),
            () -> this
                .warnBazaarUnavailable(
                    "Bazaar API data still has not advanced after five minutes, retrying automatically."));
        var flipProductContext = new FlipProductContext();
        this.flipSubmissionTracker = new FlipSubmissionTracker();

        this.highlightManager = new OrderHighlightManager();
        this.tooltipProvider = new OrderTooltipProvider(this.bazaarData, this.highlightManager);
        this.orderManager = new TrackedOrderManager(this.bazaarData);
        this.runtime = new FeatureRuntime(this.activation, this.bazaarData,
            this::startSession, this::suspendMarketFeatures, this::endSession, this::resetSessionState);
        this.profileTracker = new ProfileTracker(this.activation::isEnabled,
            (ticks, task) -> ClientTickDispatcher.scheduleAfter(_ -> task.run(), ticks),
            () -> GameUtils.runCommand("profileid"), this.runtime::resetSession, this::notifyProfileNotice);
        this.orderManager.addOnOrdersResetListener(() -> this.tooltipProvider.clearCache());
        this.orderManager.addOnOrderUpdatedListener(order -> this.tooltipProvider.clearCache());
        this.toastNotifications = new ToastNotifications(this.runtime::isActive);
        this.alertManager = new AlertManager(this.bazaarData,
            reached -> AlertNotifications.notifyReached(reached, this.bazaarData, this.toastNotifications));
        new BazaarChatManager();
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
            orderBookPriceWidgetDefinition,
            bookmarksWidgetDefinition,
            orderPresetsWidgetDefinition,
            dailyLimitWidgetDefinition,
            priceDifferenceWidgetDefinition);
        var widgetStateStore = new WidgetStateStore(() -> configStore.config().widgets, configStore::save);
        this.widgetRuntime = new WidgetRuntime(widgetRegistry, widgetStateStore, sessionProvider, this.runtime);

        this.coflnetClient = new CoflnetClient();
        this.itemInfo = new ItemInfoController(this.bazaarData, this.coflnetClient, this.runtime,
            () -> configStore.config().itemInfo, configStore::save);
        new AlertShortcut(this.bazaarProductContext, (parent, product) -> {
            var screen = new AlertScreen(parent, this.bazaarData, this.alertManager, this.runtime,
                this.itemInfo);
            screen.preselectProduct(product);
            return screen;
        });

        this.configScreen = new ConfigScreen(this.widgetRuntime, this.activation, this.tooltipProvider,
            parent -> new AlertScreen(parent, this.bazaarData, this.alertManager, this.runtime,
                this.itemInfo));
        var hudHint = new BazaarHudHintController(
            bazaarOrdersWidgetDefinition.getConfigHandle(),
            toggleHudKey::getTranslatedKeyMessage,
            configStore::save);
        HudWidgetBridge.register(
            Identifier.fromNamespaceAndPath(MOD_ID, "widgets_hud"),
            this.widgetRuntime.createHudHost(),
            hudHint::onWidgetRendered);
        Commands.registerAll(this.bazaarData, this.widgetRuntime, this.orderManager, this.profileTracker,
            () -> Minecraft.getInstance().schedule(() -> GameUtils.setScreen(
                new AlertScreen(GameUtils.screen(), this.bazaarData, this.alertManager, this.runtime,
                    this.itemInfo))),
            this.configScreen::open, this.itemInfo::openFromCommand, this::setEnabled);
        BtrBzWidgetKeybinds.registerHandler(
            toggleHudKey, bazaarOrdersWidgetDefinition, widgetStateStore, hudHint::dismiss);

        this.orderManager.afterOrderSync(snapshot -> {
            var trackedOrders = this.orderManager.getTrackedOrders();
            this.highlightManager.sync(trackedOrders, snapshot);
            this.orderValue.sync(snapshot);
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
            if (this.runtime.isRunning()) {
                this.orderActions.setReopenBazaar();
            }
        });

        this.bazaarData.addListener(this.alertManager::onBazaarUpdate);
        this.bazaarData.addListener(this.orderManager::onBazaarUpdate);

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            log.info(
                "BtrBz client shutdown started (active={}, generation={})",
                this.activation.isActive(),
                this.runtime.sessionGeneration());
            this.profileTracker.onLocation(Optional.empty());
            this.runtime.deactivate();
            configStore.save();
            this.flipSubmissionTracker.close();
            this.orderManager.close();
            this.bazaarPoller.close();
            this.itemInfo.close();
            this.coflnetClient.close();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            this.toastNotifications.invalidate();
            SoundUtil.invalidatePending();
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
            if (BtrBz.isRunning()) {
                messageDispatcher.handleChatMessage(Utils.stripFormattingCodes(message.getString()));
            }
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

        this.profileTracker.register();
        new SkyBlockDetector(this.activation, server -> {
            this.profileTracker.onLocation(server);
            if (server.isEmpty() && this.runtime.isRunning()) {
                // Normal deactivation already cleared facts. Always-active sessions survive context loss.
                this.runtime.resetSession();
            }
        }).register();
        this.activation.refresh();
    }

    private void onActivationChanged(boolean active) {
        if (active) {
            this.runtime.activate();
        } else {
            this.runtime.deactivate();
        }
        this.profileTracker.refreshEnabled();
    }

    private void startSession() {
        this.marketHibernationAnnounced = false;
        log.info("BtrBz features activated (generation={})", this.runtime.sessionGeneration());
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

    private void warnBazaarUnavailable(String message) {
        Notifier.notifyPlayer(Notifier.prefix().append(Component.literal(message).withStyle(ChatFormatting.YELLOW)));
    }

    private void hibernateMarket() {
        this.runtime.hibernate();
        if (!this.marketHibernationAnnounced) {
            this.marketHibernationAnnounced = true;
            this.warnBazaarUnavailable(
                "Bazaar API data is unavailable or outdated; market features are paused. Retrying automatically.");
        }
    }

    private void suspendMarketFeatures() {
        this.toastNotifications.invalidate();
        SoundUtil.invalidatePending();
        this.orderActions.cancelPendingActions();
        this.orderPresets.cancelPendingPreset();
        this.flipHelper.cancelPendingFlip();
        this.widgetRuntime.disposeRuntimeWidgets();
    }

    private void endSession() {
        this.marketHibernationAnnounced = false;
        log.info("BtrBz features deactivated (generation={})", this.runtime.sessionGeneration());
        this.bazaarPoller.stop();
        this.utcDayTracker.close();
        this.clipboardTracker.close();
        this.resetSessionState();
        log.debug("Deactivation cleanup completed: trackers stopped and session UI cleared");
    }

    private void resetSessionState() {
        this.itemInfo.close();
        this.suspendMarketFeatures();
        this.purseTracker.close();
        this.orderActions.resetSession();
        this.orderManager.cancelOutstandingOrders();
        this.orderManager.resetTrackedOrders();
        this.orderPresets.cancelTransaction();
        this.flipHelper.resetWorkflow();
        this.flipSubmissionTracker.clear();
        this.bazaarProductContext.clear();
        this.highlightManager.clear();
        this.orderValue.clear();
        ScreenTracker.get().discard();
        if (this.runtime.isRunning()) {
            this.purseTracker.start();
        }
        log.debug("Session state reset: generation={}", this.runtime.sessionGeneration());
    }

    private void notifyProfileNotice(ProfileTracker.Notice notice) {
        var message = Notifier.prefix().append(Component.literal(notice.message())
            .withStyle(notice.offerReset() ? ChatFormatting.YELLOW : ChatFormatting.GREEN));
        if (notice.offerReset()) {
            message.append(Component.literal(" [Reset session]").withStyle(style -> style
                .withColor(ChatFormatting.YELLOW)
                .withUnderlined(true)
                .withClickEvent(new RunCommand("/btrbz profile reset"))
                .withHoverEvent(new ShowText(Component.literal("Clear tracked orders and pending actions")))));
        }
        Notifier.notifyPlayer(message);
    }

    private void handleConversionEvent(ConversionEvent event) {
        if (!event.manual() && !BtrBz.isRunning()) {
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
