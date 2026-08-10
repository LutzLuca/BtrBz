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
        displayName = Objects.requireNonNull(displayName, "displayName").copy();
        itemStack = itemStack.map(ItemStack::copy);
    }

    @Override
    public Optional<ItemStack> itemStack() {
        return this.itemStack.map(ItemStack::copy);
    }

    @Override
    public Component displayName() {
        return this.displayName.copy();
    }

    public String productId() {
        return this.identity.bazaarProductId().orElse(this.identity.strippedName());
    }

    public WidgetProductContext detachedCopy() {
        return new WidgetProductContext(this.identity, this.displayName.copy(), this.itemStack());
    }

    public boolean samePresentation(WidgetProductContext other) {
        if (other == null || !this.identity.equals(other.identity)
            || !this.displayName.equals(other.displayName)
            || this.itemStack.isPresent() != other.itemStack.isPresent()) {
            return false;
        }

        return this.itemStack.isEmpty() || ItemStack.isSameItemSameComponents(
            this.itemStack.orElseThrow(), other.itemStack.orElseThrow()
        );
    }
}
