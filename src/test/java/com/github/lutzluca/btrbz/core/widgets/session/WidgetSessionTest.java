package com.github.lutzluca.btrbz.core.widgets.session;

import java.util.Optional;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;

@DisplayName("Widget session")
class WidgetSessionTest {
    @Test
    @DisplayName("does not expose stale menus as container contexts")
    void excludesStaleMenuFromCustomScreen() {
        var session = new WidgetSession(
            8, false, false, false, Optional.of(BazaarMenuType.Orders), Optional.empty(),
            Optional.empty(), Optional.empty(), 0);
        assertFalse(session.inBazaarMenu(BazaarMenuType.Orders));
    }
}
