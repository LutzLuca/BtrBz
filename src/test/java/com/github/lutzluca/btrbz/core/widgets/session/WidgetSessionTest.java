package com.github.lutzluca.btrbz.core.widgets.session;

import java.util.Optional;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Widget session")
class WidgetSessionTest {
    @Test
    @DisplayName("represents a future price graph without a registry category")
    void representsPriceGraph() {
        var session = new WidgetSession(
            7, false, false, false, true, Optional.empty(), Optional.empty(), true,
            Optional.empty(), Optional.empty(), 0, null
        );
        assertTrue(session.inPriceGraph());
        assertFalse(session.inOrderBook());
        assertFalse(session.inHud());
    }

    @Test
    @DisplayName("does not expose stale menus as container contexts")
    void excludesStaleMenuFromCustomScreen() {
        var session = new WidgetSession(
            8, false, false, true, false, Optional.of(BazaarMenuType.Orders), Optional.empty(), true,
            Optional.empty(), Optional.empty(), 0, null
        );
        assertFalse(session.inBazaarMenu(BazaarMenuType.Orders));
    }
}
