package com.github.lutzluca.btrbz.core;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.utils.ItemOverrideManager;
import com.github.lutzluca.btrbz.utils.Notifier;
import com.github.lutzluca.btrbz.utils.ScreenActionManager;
import com.github.lutzluca.btrbz.utils.ScreenActionManager.ScreenClickRule;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.ScreenInfo;
import io.vavr.control.Try;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;
import lombok.extern.slf4j.Slf4j;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConfirmLinkScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

@Slf4j
public final class ProductInfoProvider {

    private static ProductInfoProvider instance;

    private String openedProductId;

    private ProductInfoProvider() {
        registerScreenHooks();
        registerItemOverrides();
        registerClickRules();
        registerTooltipHandler();
    }

    public static void init() {
        if (instance == null) {
            instance = new ProductInfoProvider();
            log.info("Initialized ProductInfoProvider");
        }
    }

    // === Screen & Slot Tracking ===
    private void registerScreenHooks() {
        ScreenInfoHelper.registerOnLoaded(
            info -> info.inMenu(BazaarMenuType.Item), (info, inv) -> {
                final int productIdx = 13;
                var productName = inv
                    .getItem(productIdx)
                    .map(ItemStack::getName)
                    .map(Text::getString)
                    .orElse("<empty>");

                BtrBz.bazaarData().nameToId(productName).ifPresentOrElse(
                    id -> {
                        this.openedProductId = id;
                        log.debug("Opened product: {}", id);
                    }, () -> log.warn("No product id found for {}", productName)
                );
            }
        );

        ScreenInfoHelper.registerOnClose(info -> true, info -> this.openedProductId = null);
    }

    // === Slot Replacement ===
    private void registerItemOverrides() {
        ItemOverrideManager.register((info, slot, original) -> {
            if (this.openedProductId == null || slot.getIndex() != 22) {
                return Optional.empty();
            }

            var player = MinecraftClient.getInstance().player;
            if (player != null && slot.inventory == player.getInventory()) {
                return Optional.empty();
            }

            var item = new ItemStack(Items.PAPER);
            item.set(
                DataComponentTypes.CUSTOM_NAME,
                Text.literal("Product Info").formatted(Formatting.AQUA, Formatting.BOLD)
            );
            List<Text> loreLines = List.of(
                Text.literal("Click here to open"),
                Text.literal("and view product info"),
                Text.literal("on ").append(Text.literal("skyblock.bz").formatted(Formatting.RED))
            );
            item.set(DataComponentTypes.LORE, new LoreComponent(loreLines));
            return Optional.of(item);
        });
    }

    // === Slot Click Rule (Slot 22) ===
    private void registerClickRules() {
        ScreenActionManager.register(new ScreenActionManager.ScreenClickRule() {
            @Override
            public boolean applies(ScreenInfo info, Slot slot, int button) {
                return openedProductId != null && slot.getIndex() == 22;
            }

            @Override
            public boolean onClick(ScreenInfo info, Slot slot, int button) {
                String link = "https://skyblock.bz/product/" + openedProductId;
                confirmAndOpen(link);
                return true;
            }
        });
    }

    // === Tooltip for Bazaar Items (CTRL+SHIFT hint) ===
    private void registerTooltipHandler() {
        ItemTooltipCallback.EVENT.register((stack, ctx, type, lines) -> {
            if (shouldNotApply(stack)) {
                return;
            }

            BtrBz.bazaarData().nameToId(stack.getName().getString()).ifPresent(id -> {
                lines.add(Text.literal(""));

                var tooltipText = Text
                    .literal("[")
                    .formatted(Formatting.GRAY)
                    .append(Text.literal("CTRL").formatted(Formatting.AQUA, Formatting.BOLD))
                    .append(Text.literal("+").formatted(Formatting.GRAY))
                    .append(Text.literal("SHIFT").formatted(Formatting.AQUA, Formatting.BOLD))
                    .append(Text.literal("] ").formatted(Formatting.GRAY))
                    .append(Text.literal("View product info on ").formatted(Formatting.WHITE))
                    .append(Text.literal("skyblock.bz").formatted(Formatting.RED, Formatting.BOLD));

                lines.add(tooltipText);
            });
        });

        ScreenActionManager.register(new ScreenClickRule() {
            public boolean applies(ScreenInfo info, Slot slot, int button) {
                if(shouldNotApply(slot.getStack())) {
                    return false;
                }

                return Screen.hasControlDown() && Screen.hasShiftDown() && BtrBz
                    .bazaarData()
                    .nameToId(slot.getStack().getName().getString())
                    .isPresent();
            }

            @Override
            public boolean onClick(ScreenInfo info, Slot slot, int button) {
                var productName = slot.getStack().getName().getString();
                var idOpt = BtrBz.bazaarData().nameToId(productName);
                if (idOpt.isEmpty()) {
                    return false;
                }

                String link = "https://skyblock.bz/product/" + idOpt.get();
                confirmAndOpen(link);
                return true;
            }
        });
    }

    private boolean shouldNotApply(ItemStack stack) {
        return ScreenInfoHelper.inMenu(BazaarMenuType.Main, BazaarMenuType.Item) && !Try
            .of(() -> StreamSupport
                .stream(
                    MinecraftClient.getInstance().player.getInventory().spliterator(),
                    false
                )
                .anyMatch(playerStack -> playerStack == stack))
            .getOrElse(false);
    }

    private void confirmAndOpen(String link) {
        var client = MinecraftClient.getInstance();
        client.setScreen(new ConfirmLinkScreen(
            confirmed -> {
                if (confirmed) {
                    Try
                        .run(() -> net.minecraft.util.Util.getOperatingSystem().open(new URI(link)))
                        .onFailure(e -> Notifier.notifyPlayer(Text
                            .literal("Failed to open link: ")
                            .formatted(Formatting.RED)
                            .append(Text
                                .literal(link)
                                .formatted(Formatting.UNDERLINE, Formatting.BLUE))));
                }

                client.setScreen(null);
            }, link, true
        ));
    }
}
