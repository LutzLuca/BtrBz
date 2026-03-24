package com.github.lutzluca.btrbz.core;

import com.github.lutzluca.btrbz.core.config.Config;
import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.modules.BindModule;
import com.github.lutzluca.btrbz.core.modules.Module;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.widgets.core.WidgetManager;
import com.github.lutzluca.btrbz.widgets.base.DraggableWidget;
import com.github.lutzluca.btrbz.utils.ClientTickDispatcher;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.ScreenInfo;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Slf4j
public class ModuleManager {

    private static ModuleManager instance;

    private final Map<Class<? extends Module<?>>, Module<?>> modules = new HashMap<>();
    private final Map<Class<? extends Module<?>>, Field> moduleBindings = new HashMap<>();

    private @Nullable WidgetManager widgetManager;
    private @Nullable ModuleContext context;

    public record ModuleContext(@NotNull BazaarData bazaarData, @NotNull ProductInfoProvider productInfoProvider) { }

    @Setter
    private boolean isDirty = false;

    private ModuleManager() {
        ClientTickDispatcher.register(client -> this.saveOnDirty());
    }

    public static ModuleManager getInstance() {
        if (instance == null) {
            instance = new ModuleManager();
            ScreenInfoHelper.registerOnSwitch(instance::renderModules);

            ScreenInfoHelper.registerOnLoaded(
                info -> true,
                (info, ignored) -> instance.revalidateModules(info)
            );
        }

        return instance;
    }

    public @Nullable WidgetManager getWidgetManager() {
        return this.widgetManager;
    }

    public void initContext(@NotNull ModuleContext context) {
        if (this.context != null) {
            throw new IllegalStateException("ModuleManager context has already been initialized");
        }

        this.context = context;
    }

    private void renderModules(ScreenInfo info) {
        this.modules.values().forEach(module -> module.setDisplayed(false));

        java.util.List<DraggableWidget> widgets = this.modules
            .values()
            .stream()
            .filter(module -> module.shouldDisplay(info))
            .peek(module -> module.setDisplayed(true))
            .flatMap(module -> module.createWidgets(info).stream())
            .toList();

        this.widgetManager = new WidgetManager(widgets);
        this.widgetManager.init();
        log.trace("WidgetManager initialized with {} widgets", widgets.size());
    }

    private void revalidateModules(ScreenInfo info) {
        var newWidgets = this.modules
            .values()
            .stream()
            .filter(module -> !module.isDisplayed() && module.shouldDisplay(info))
            .peek(module -> {
                log.trace(
                    "Module {} now displays after inventory load",
                    module.getClass().getSimpleName()
                );
                module.setDisplayed(true);
            })
            .flatMap(module -> module.createWidgets(info).stream())
            .toList();

        if (!newWidgets.isEmpty() && this.widgetManager != null) {
            log.debug("Adding {} widgets after revalidation", newWidgets.size());
            newWidgets.forEach(this.widgetManager::addWidget);
        }
    }

    public <T, M extends Module<T>> M registerModule(Class<M> moduleClass) {
        try {
            M module = moduleClass.getDeclaredConstructor().newInstance();
            module.initContext(Objects.requireNonNull(this.context, "ModuleManager context not initialized"));
            this.modules.put(moduleClass, module);
            this.applyConfigToModule(module);
            module.onLoad();
            log.info("Registered module: {}", moduleClass.getName());
            return module;
        } catch (Exception err) {
            throw new RuntimeException(
                "Failed to instantiate module: " + moduleClass.getName(),
                err
            );
        }
    }

    private void applyConfigToModule(Module<?> module) {
        var field = this.moduleBindings.get(module.getClass());
        try {
            Object value = field.get(ConfigManager.get());
            if (value == null) {
                throw new IllegalStateException("Config field '" + field.getName() + "' is null. " + "Ensure the field is initialized in the Config class");
            }

            this.castModule(module).applyConfigState(value);
            log.debug(
                "Applied config value '{}' to module: {}",
                value,
                module.getClass().getName()
            );
        } catch (IllegalAccessException err) {
            throw new RuntimeException("Failed to access config field: " + field.getName(), err);
        }
    }

    public void discoverBindings() {
        log.info("Discovering `@BindModule` bindings in the config file");

        for (Field field : Config.class.getDeclaredFields()) {
            BindModule annotation = field.getAnnotation(BindModule.class);
            if (annotation != null) {
                Class<? extends Module<?>> moduleClass = annotation.value();
                log.debug(
                    "Validating `@BindModule` annotation for config field '{}' with type '{}' for module '{}'",
                    field.getName(),
                    field.getType().getSimpleName(),
                    moduleClass.getName()
                );

                this.validateBinding(field, moduleClass);
                this.moduleBindings.put(moduleClass, field);
            }
        }
    }

    private void validateBinding(Field field, Class<? extends Module<?>> moduleClass) {
        Type moduleStateType = this.extractModuleStateType(moduleClass);
        if (moduleStateType == null) {
            throw new IllegalStateException("Cannot determine state type for module: " + moduleClass.getName());
        }

        Class<?> fieldType = field.getType();
        Class<?> stateClass = (Class<?>) moduleStateType;

        if (!fieldType.equals(stateClass)) {
            throw new IllegalStateException(String.format(
                """
                    Type mismatch for @BindModule on field '%s':
                    Expected: %s (from Module<%s>)
                    Found: %s
                    Module: %s""",
                field.getName(),
                stateClass.getSimpleName(),
                stateClass.getSimpleName(),
                fieldType.getSimpleName(),
                moduleClass.getName()
            ));
        }
    }

    private Type extractModuleStateType(Class<? extends Module<?>> moduleClass) {
        Type superclass = moduleClass.getGenericSuperclass();

        if (superclass instanceof ParameterizedType paramType) {
            if (paramType.getRawType().equals(Module.class)) {
                return paramType.getActualTypeArguments()[0];
            }
        }

        return null;
    }

    private void saveOnDirty() {
        if (!this.isDirty) {
            return;
        }

        ConfigManager.save();
        this.isDirty = false;
    }

    @SuppressWarnings("unchecked")
    private <T> Module<T> castModule(Module<?> module) {
        return (Module<T>) module;
    }

    @SuppressWarnings("unchecked")
    public <M extends Module<?>> M getModule(Class<M> moduleClass) {
        Module<?> module = this.modules.get(moduleClass);
        if (module == null) {
            throw new IllegalStateException("Module not registered: " + moduleClass.getName());
        }
        return (M) module;
    }

    public <M extends Module<?>> void withModule(Class<M> moduleClass, Consumer<M> action) {
        M module = this.getModule(moduleClass);
        action.accept(module);
        this.isDirty = true;
    }
}
