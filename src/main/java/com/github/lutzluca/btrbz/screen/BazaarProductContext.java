package com.github.lutzluca.btrbz.screen;

import com.github.lutzluca.btrbz.cache.CacheToken;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.IndexedProduct;
import com.github.lutzluca.btrbz.screen.ScreenTracker.BazaarMenuType;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import org.jetbrains.annotations.Nullable;

/** Tracks the Bazaar product selected by the current screen workflow. */
@Slf4j
public final class BazaarProductContext {
    private static final int PRODUCT_SLOT = 13;
    private static final BazaarMenuType[] PRODUCT_FLOW_MENUS = {
        BazaarMenuType.Item,
        BazaarMenuType.BuyOrderSetupVolume,
        BazaarMenuType.BuyOrderSetupPrice,
        BazaarMenuType.BuyOrderConfirmation,
        BazaarMenuType.SellOfferSetup,
        BazaarMenuType.SellOfferConfirmation
    };

    private final BazaarData bazaarData;
    private final CacheToken changes = CacheToken.named("product-context.opened-product");

    private @Nullable IndexedProduct openedProduct;

    public BazaarProductContext(BazaarData bazaarData) {
        this.bazaarData = bazaarData;
        ScreenTracker.registerOnLoaded(
            info -> info.inMenu(BazaarMenuType.Item),
            (info, inventory) -> this.loadProduct(inventory.getItem(PRODUCT_SLOT)
                .map(this.bazaarData::resolveProduct)
                .flatMap(this.bazaarData::resolveIndexedProduct)
                .orElse(null)));
        ScreenTracker.registerOnSwitch(this::onScreenSwitch);
    }

    public @Nullable IndexedProduct openedProduct() {
        return this.openedProduct;
    }

    public CacheToken changes() {
        return this.changes;
    }

    public void clear() {
        this.setOpenedProduct(null, "Bazaar interaction cancelled");
    }

    private void loadProduct(@Nullable IndexedProduct product) {
        if (product != null) {
            this.setOpenedProduct(product, "Bazaar product opened");
            log.debug("Opened product: {}", product);
            return;
        }

        this.setOpenedProduct(null, "Bazaar product resolution cleared");
        log.warn("No product resolved for Bazaar item screen");
    }

    private void onScreenSwitch(ScreenTracker.ScreenInfo current) {
        if (this.openedProduct == null) {
            return;
        }

        var previous = ScreenTracker.get().getPrevInfo();
        boolean closed = current.getScreen() == null;
        boolean transientFlowClose = closed && previous.getScreen() instanceof SignEditScreen;
        boolean leftToNonFlowBazaar = current.inBazaar()
            && !current.inMenu(PRODUCT_FLOW_MENUS);

        if (transientFlowClose) {
            log.debug(
                "Preserving product context on transient flow close: {}",
                this.openedProduct);
            return;
        }

        if (closed || leftToNonFlowBazaar) {
            log.debug("Leaving product flow, clearing product: {}", this.openedProduct);
            this.setOpenedProduct(null, "left Bazaar product workflow");
        }
    }

    private void setOpenedProduct(@Nullable IndexedProduct product, String reason) {
        if (Objects.equals(this.openedProduct, product)) {
            return;
        }

        this.openedProduct = product;
        this.changes.invalidate(reason);
    }
}
