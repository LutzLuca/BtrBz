package com.github.lutzluca.btrbz.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractSignEditScreen.class)
public interface AbstractSignEditScreenAccessor {

    @Invoker("setMessage")
    void invokeSetMessage(String message);

    @Accessor("line")
    void setLine(int row);
}
