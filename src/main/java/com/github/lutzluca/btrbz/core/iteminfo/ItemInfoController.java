package com.github.lutzluca.btrbz.core.iteminfo;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.core.runtime.FeatureRuntime;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.IndexedProduct;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import com.github.lutzluca.btrbz.mixin.AbstractContainerScreenAccessor;
import com.github.lutzluca.btrbz.screen.slot.VirtualSlotProjection;
import com.github.lutzluca.btrbz.utils.Notifier;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.github.lutzluca.coflnet.CoflnetClient;
import com.mojang.blaze3d.platform.InputConstants;
import java.time.Clock;
import java.util.Locale;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/** Owns Item Info entry points and closes the logical opening when its game session ends. */
public final class ItemInfoController implements AutoCloseable {
    private final BazaarData market;
    private final CoflnetClient client;
    private final FeatureRuntime runtime;
    private final Supplier<ItemInfoConfig> config;
    private final Runnable save;
    private final KeyMapping key;
    private @Nullable ItemInfoSession session;

    public ItemInfoController(
        BazaarData market,
        CoflnetClient client,
        FeatureRuntime runtime,
        Supplier<ItemInfoConfig> config,
        Runnable save
    ) {
        this.market = market;
        this.client = client;
        this.runtime = runtime;
        this.config = config;
        this.save = save;
        var category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(BtrBz.MOD_ID, "item_info"));
        this.key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.btrbz.open_item_info",
            InputConstants.Type.KEYSYM, InputConstants.KEY_I, category));
    }

    public boolean handleKey(AbstractContainerScreen<?> parent, KeyEvent event) {
        if (!this.key.matches(event) || this.unavailableReason() != null || parent.getFocused() instanceof EditBox) {
            return false;
        }
        var slot = ((AbstractContainerScreenAccessor) parent).getHoveredSlot();
        if (slot == null) {
            return false;
        }
        var stack = VirtualSlotProjection.withProjectionSuppressed(slot::getItem);
        if (stack.isEmpty()) {
            return false;
        }
        var product = this.market.resolveProduct(stack);
        boolean known = this.market.resolveIndexedProduct(product).isPresent()
            || this.market.liveProduct(product).isPresent();
        return known && this.open(parent, product, "", false);
    }

    public void openFromCommand(String query) {
        var minecraft = Minecraft.getInstance();
        minecraft.schedule(() -> {
            var reason = this.unavailableReason();
            if (reason != null) {
                Notifier.notifyPlayer(Notifier.prefix().append(Component.literal(reason)));
                return;
            }
            var normalized = query.trim();
            var product = this.market.resolveProductId(normalized.toUpperCase(Locale.ROOT))
                .map(ProductIdentity::fromIndex)
                .orElseGet(() -> normalized.isEmpty() ? null : this.market.resolveProductName(normalized));
            if (product != null && product.bazaarProductId().isEmpty()) {
                product = null;
            }
            this.open(GameUtils.screen(), product, product == null ? normalized : "", false);
        });
    }

    public @Nullable String unavailableReason() {
        if (!this.config.get().enabled) {
            return "Item Info is disabled in settings.";
        }
        return this.runtime.isRunning() ? null : "Item Info is available while BtrBz is running in SkyBlock.";
    }

    public boolean openBook(@Nullable Screen parent, IndexedProduct product) {
        return this.open(parent, ProductIdentity.fromIndex(product), "", true);
    }

    private boolean open(@Nullable Screen parent, @Nullable ProductIdentity product, String search, boolean book) {
        if (this.unavailableReason() != null) {
            return false;
        }
        this.close();
        var minecraft = Minecraft.getInstance();
        long generation = this.runtime.sessionGeneration();
        var level = minecraft.level;
        var connection = minecraft.getConnection();
        var section = this.config.get();
        this.session = new ItemInfoSession(this.market, this.client,
            () -> this.runtime.isRunning() && this.runtime.sessionGeneration() == generation
                && minecraft.level == level
                && minecraft.getConnection() == connection,
            minecraft::execute, Clock.systemUTC(), section.range, section.showMayors);
        this.session.historyVisible(!book);
        if (product != null) {
            this.session.select(product);
        }
        GameUtils.setScreen(new ItemInfoScreen(parent, this.market, this.session, section, this.save, search, book));
        return true;
    }

    @Override
    public void close() {
        if (this.session != null) {
            this.session.close();
            this.session = null;
        }
        if (GameUtils.screen() instanceof ItemInfoScreen) {
            GameUtils.setScreen(null);
        }
    }
}
