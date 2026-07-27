package com.github.lutzluca.btrbz.core.widgets.session;

import com.github.lutzluca.btrbz.data.ProductIdentity;
import java.util.Objects;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public record WidgetProductContext(
    ProductIdentity identity,
    Component displayName,
    ItemStack icon
) {
    public WidgetProductContext {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(displayName, "displayName");
        icon = Objects.requireNonNull(icon, "icon").copy();
    }

    @Override
    public ItemStack icon() {
        return this.icon.copy();
    }

    public String productId() {
        return this.identity.bazaarProductId().orElse(this.identity.strippedName());
    }
}
