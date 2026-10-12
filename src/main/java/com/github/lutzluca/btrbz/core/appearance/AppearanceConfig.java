package com.github.lutzluca.btrbz.core.appearance;

import java.util.ArrayList;
import java.util.List;

public final class AppearanceConfig {
    public UiTheme theme = ThemePreset.BtrBzDark.theme();
    public List<UiTheme> savedThemes = new ArrayList<>();
}
