package com.github.lutzluca.btrbz.core.widgets.session;

import com.github.lutzluca.btrbz.data.ProductIdentity;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public record WidgetProductContext(
    ProductIdentity identity,
    Component displayName,
    Optional<ItemStack> itemStack
) {
    public WidgetProductContext {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(displayName, "displayName");
        itemStack = itemStack.map(ItemStack::copy);
    }

    @Override
    public Optional<ItemStack> itemStack() {
        return this.itemStack.map(ItemStack::copy);
    }

    public String productId() {
        return this.identity.bazaarProductId().orElse(this.identity.strippedName());
    }
}
