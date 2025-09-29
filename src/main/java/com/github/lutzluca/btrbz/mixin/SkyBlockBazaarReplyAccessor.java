package com.github.lutzluca.btrbz.mixin;

import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SkyBlockBazaarReply.class)
public interface SkyBlockBazaarReplyAccessor {

    @Accessor(value = "lastUpdated", remap = false)
    long getLastUpdated();
}
