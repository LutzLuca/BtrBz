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
import lombok.extern.slf4j.Slf4j;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConfirmLinkScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

// TODO make it singleton
@Slf4j
public class ProductInfoProvider {

    private String openedProductId = null;

    public ProductInfoProvider() {
        ScreenInfoHelper.registerOnLoaded(info -> info.inMenu(BazaarMenuType.Item), (info, inv) -> {
            final int productIdx = 13;

            var productName = inv.getItem(productIdx).map(ItemStack::getName).map(Text::getString)
                    .orElse("<empty>");

            BtrBz.bazaarData().nameToId(productName).ifPresentOrElse(productId -> {
                this.openedProductId = productId;
                log.debug("Set opened product id to {} for item menu with product name {}",
                        productId, productName);
            }, () -> {
                log.warn("Could not find product id for opened bazaar item menu! Item name: {}",
                        productName);
            });
        });

        ScreenInfoHelper.registerOnClose(info -> true, info -> {
            this.openedProductId = null;
        });

        ItemOverrideManager.register((info, slot, original) -> {
            if (this.openedProductId == null || slot.getIndex() != 22) {
                return Optional.empty();
            }

            var player = MinecraftClient.getInstance().player;
            if (player != null && slot.inventory == player.getInventory()) {
                return Optional.empty();
            }

            var item = new ItemStack(Items.PAPER);
            item.set(DataComponentTypes.CUSTOM_NAME,
                    Text.literal("Product Info").formatted(Formatting.AQUA));
            List<Text> loreLines =
                    List.of(Text.literal("Click here to open").formatted(Formatting.WHITE),
                            Text.literal("and view product info").formatted(Formatting.WHITE),
                            Text.literal("on ").formatted(Formatting.WHITE)
                                    .append(Text.literal("skyblock.bz").formatted(Formatting.RED)));
            item.set(DataComponentTypes.LORE, new LoreComponent(loreLines));
            return Optional.of(item);
        });

        ScreenActionManager.register(new ScreenClickRule() {

            @Override
            public boolean applies(ScreenInfo info, Slot slot, int button) {
                return openedProductId != null && slot.getIndex() == 22;
            }

            @Override
            public boolean onClick(ScreenInfo info, Slot slot, int button) {
                confirmAndOpen(
                        "https://www.skyblock.bz/product/" + openedProductId);
                return true;
            }
        });
    }

    private void confirmAndOpen(String link) {
        MinecraftClient.getInstance().setScreen(new ConfirmLinkScreen(confirmed -> {
            if (confirmed) {
                Try.run(() -> net.minecraft.util.Util.getOperatingSystem().open(new URI(link)))
                        .onFailure(err -> Notifier.notifyPlayer(
                                Text.literal("Failed to open link: ").formatted(Formatting.RED)
                                        .append(Text.literal(link).formatted(Formatting.UNDERLINE,
                                                Formatting.BLUE))));
            }
            MinecraftClient.getInstance().setScreen(null);
        }, link, true));
    }

}
