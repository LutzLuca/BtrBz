package com.github.lutzluca.btrbz.core.widgets;

import com.github.lutzluca.btrbz.core.ProductInfoProvider;
import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.IndexedProduct;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.ScreenInfo;
import com.github.lutzluca.btrbz.utils.Utils;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Buy-volume workflow facts and semantic preset application. */
@Slf4j
public final class OrderPresetsComponent {
    private static final int CUSTOM_AMOUNT_SLOT = 16;
    private final BazaarData bazaarData;
    private final ProductInfoProvider productInfoProvider;
    private int maximumVolume = GameUtils.GLOBAL_MAX_ORDER_VOLUME;
    private int pendingVolume = -1;
    private boolean pendingPreset;
    private boolean inTransaction;

    public OrderPresetsComponent(BazaarData bazaarData, ProductInfoProvider productInfoProvider) {
        this.bazaarData = bazaarData;
        this.productInfoProvider = productInfoProvider;
        var configured = ConfigManager.get().widgets.orderPresets.volumes;
        var normalized = normalizeConfiguredVolumes(configured);
        if (!configured.equals(normalized)) {
            configured.clear();
            configured.addAll(normalized);
            ConfigManager.save();
        }
        ScreenInfoHelper.registerOnSwitch(this::onScreenSwitch);
        ScreenInfoHelper.registerOnLoaded(
            info -> info.inMenu(BazaarMenuType.BuyOrderSetupVolume),
            (info, inventory) -> {
                if (ScreenInfoHelper.get().getPrevInfo().inMenu(BazaarMenuType.BuyOrderSetupPrice)) return;
                inventory.getItem(CUSTOM_AMOUNT_SLOT).flatMap(this::readMaximumVolume)
                    .ifPresent(value -> this.maximumVolume = value);
            }
        );
    }

    public boolean eligible(ScreenInfo current, ScreenInfo previous) {
        return current.inMenu(BazaarMenuType.BuyOrderSetupVolume)
            || this.inTransaction
                && current.getScreen() instanceof SignEditScreen
                && previous.inMenu(BazaarMenuType.BuyOrderSetupVolume);
    }

    public boolean inTransaction() {
        return this.inTransaction;
    }

    public List<PresetState> currentPresets() {
        var purse = GameUtils.getPurse();
        var price = Optional.ofNullable(this.currentProduct())
            .map(ProductIdentity::fromIndex)
            .flatMap(this.bazaarData::highestBuyOrderPrice)
            .map(value -> value + 0.1);
        OptionalInt clipboard = OptionalInt.empty();
        String rawClipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
        if (!rawClipboard.isBlank()) {
            clipboard = Utils.parseUsFormattedNumber(rawClipboard)
                .map(Number::intValue)
                .filter(value -> value > 0 && value <= this.maximumVolume)
                .map(OptionalInt::of)
                .getOrElse(OptionalInt.empty());
        }
        return resolvePresets(
            ConfigManager.get().widgets.orderPresets.volumes,
            this.maximumVolume,
            clipboard,
            price,
            purse
        );
    }

    public static List<PresetState> resolvePresets(
        List<Integer> configuredVolumes,
        int maximumVolume,
        OptionalInt clipboardVolume,
        Optional<Double> pricePerUnit,
        Optional<Double> purse
    ) {
        List<OrderPreset> presets = normalizeConfiguredVolumes(configuredVolumes).stream()
            .filter(value -> value <= maximumVolume)
            .map(value -> (OrderPreset) new OrderPreset.Fixed(value))
            .collect(Collectors.toCollection(ArrayList::new));
        presets.addFirst(new OrderPreset.Maximum());
        if (clipboardVolume.isPresent()) {
            presets.add(1, new OrderPreset.Clipboard(clipboardVolume.getAsInt()));
        }
        return presets.stream().map(preset -> switch (preset) {
            case OrderPreset.Maximum _ -> resolveMaximum(
                preset, maximumVolume, pricePerUnit, purse
            );
            case OrderPreset.Clipboard clipboard -> resolveAmount(
                preset, clipboard.amount(), pricePerUnit, purse
            );
            case OrderPreset.Fixed fixed -> resolveAmount(
                preset, fixed.amount(), pricePerUnit, purse
            );
        }).toList();
    }

    static List<Integer> normalizeConfiguredVolumes(List<Integer> configuredVolumes) {
        return configuredVolumes.stream()
            .filter(value -> value != null && value > 0)
            .distinct()
            .sorted()
            .toList();
    }

    public boolean apply(OrderPreset preset) {
        int volume = switch (preset) {
            case OrderPreset.Maximum _ -> {
                var product = this.currentProduct();
                if (product == null) yield 0;
                var price = this.bazaarData.highestBuyOrderPrice(ProductIdentity.fromIndex(product))
                    .map(value -> value + 0.1);
                if (price.isEmpty()) yield 0;
                yield GameUtils.getPurse()
                    .map(purse -> calculateMaximum(purse, price.get(), this.maximumVolume))
                    .orElse(0);
            }
            case OrderPreset.Clipboard clipboard -> clipboard.amount();
            case OrderPreset.Fixed fixed -> fixed.amount();
        };
        if (volume <= 0) return false;

        var client = Minecraft.getInstance();
        var current = ScreenInfoHelper.get().getCurrInfo();
        var container = current.getGenericContainerScreen();
        if (!(current.getScreen() instanceof SignEditScreen) && container.isEmpty()) return false;
        if (client.player == null || client.gameMode == null) return false;

        this.pendingPreset = true;
        this.pendingVolume = volume;
        if (current.getScreen() instanceof SignEditScreen sign) {
            GameUtils.submitSignValue(sign, String.valueOf(volume));
            this.pendingPreset = false;
            this.pendingVolume = -1;
            return true;
        }
        client.gameMode.handleContainerInput(
            container.get().getMenu().containerId,
            CUSTOM_AMOUNT_SLOT,
            1,
            ContainerInput.PICKUP,
            client.player
        );
        return true;
    }

    private void onScreenSwitch(ScreenInfo current) {
        var previous = ScreenInfoHelper.get().getPrevInfo();
        if (current.inMenu(BazaarMenuType.BuyOrderSetupVolume)
            && previous.inMenu(BazaarMenuType.Item)) {
            this.maximumVolume = current.getItemStack(CUSTOM_AMOUNT_SLOT)
                .flatMap(this::readMaximumVolume)
                .orElse(GameUtils.GLOBAL_MAX_ORDER_VOLUME);
            this.inTransaction = true;
            return;
        }
        if (previous.inMenu(BazaarMenuType.BuyOrderSetupVolume)
            && current.getScreen() instanceof SignEditScreen sign
            && this.pendingPreset
            && this.pendingVolume > 0) {
            GameUtils.submitSignValue(sign, String.valueOf(this.pendingVolume));
            this.pendingPreset = false;
            this.pendingVolume = -1;
            return;
        }
        if (this.inTransaction && previous.getScreen() instanceof SignEditScreen
            && current.getScreen() == null) return;
        boolean orderFlow = current.inMenu(
            BazaarMenuType.BuyOrderSetupVolume,
            BazaarMenuType.BuyOrderSetupPrice,
            BazaarMenuType.BuyOrderConfirmation
        ) || current.getScreen() instanceof SignEditScreen && previous.inMenu(
            BazaarMenuType.BuyOrderSetupVolume,
            BazaarMenuType.BuyOrderSetupPrice
        );
        if (this.inTransaction && !orderFlow) this.cancel();
    }

    private void cancel() {
        this.inTransaction = false;
        this.pendingPreset = false;
        this.pendingVolume = -1;
        this.maximumVolume = GameUtils.GLOBAL_MAX_ORDER_VOLUME;
    }

    private @Nullable IndexedProduct currentProduct() {
        return this.productInfoProvider.getOpenedProduct();
    }

    private Optional<Integer> readMaximumVolume(ItemStack item) {
        return GameUtils.getLore(item).stream()
            .filter(line -> line.startsWith("Buy up to"))
            .findFirst()
            .map(line -> line.replaceFirst("Buy up to", "").replaceAll("x+", ""))
            .flatMap(value -> Utils.parseUsFormattedNumber(value).toJavaOptional().map(Number::intValue));
    }

    private static PresetState resolveAmount(
        OrderPreset preset,
        int amount,
        Optional<Double> price,
        Optional<Double> purse
    ) {
        if (price.isEmpty()) return new PresetState.Available(preset, amount);
        if (purse.isEmpty()) return new PresetState.PurseUnavailable(preset);
        if (amount * price.get() > purse.get()) return new PresetState.InsufficientCoins(preset);
        return new PresetState.Available(preset, amount);
    }

    private static PresetState resolveMaximum(
        OrderPreset preset,
        int maximumVolume,
        Optional<Double> price,
        Optional<Double> purse
    ) {
        if (price.isEmpty()) return new PresetState.PriceUnavailable(preset);
        if (purse.isEmpty()) return new PresetState.PurseUnavailable(preset);
        int volume = calculateMaximum(purse.get(), price.get(), maximumVolume);
        if (volume <= 0) {
            return new PresetState.CannotAffordSingleItem(preset, price.get() - purse.get());
        }
        return new PresetState.Available(preset, volume);
    }

    static int calculateMaximum(double purse, double price, int maximumVolume) {
        return Math.min((int) (purse / price), maximumVolume);
    }

    public sealed interface PresetState permits PresetState.Available, PresetState.Unavailable {
        OrderPreset preset();
        record Available(OrderPreset preset, int resolvedVolume) implements PresetState {}
        sealed interface Unavailable extends PresetState permits PriceUnavailable,
            PurseUnavailable, InsufficientCoins, CannotAffordSingleItem {}
        record PriceUnavailable(OrderPreset preset) implements Unavailable {}
        record PurseUnavailable(OrderPreset preset) implements Unavailable {}
        record InsufficientCoins(OrderPreset preset) implements Unavailable {}
        record CannotAffordSingleItem(OrderPreset preset, double missingCoins) implements Unavailable {}
    }
}
