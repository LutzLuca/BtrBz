package com.github.lutzluca.btrbz.core.bazaariteminfo;

import java.util.Objects;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;

/** Link confirmation that reports replacement when its normal result callback did not finish. */
final class OwnedConfirmLinkScreen extends ConfirmLinkScreen {
    private final Runnable removedAction;

    OwnedConfirmLinkScreen(
        BooleanConsumer resultCallback,
        String link,
        boolean trusted,
        Runnable removedAction
    ) {
        super(resultCallback, link, trusted);
        this.removedAction = Objects.requireNonNull(removedAction, "removedAction");
    }

    @Override
    public void removed() {
        super.removed();
        this.removedAction.run();
    }
}
