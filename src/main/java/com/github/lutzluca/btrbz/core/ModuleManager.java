package com.github.lutzluca.btrbz.core;

import com.github.lutzluca.btrbz.core.config.Config;
import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.modules.BindModule;
import com.github.lutzluca.btrbz.core.modules.Module;
import com.github.lutzluca.btrbz.mixin.ScreenAccessor;
import com.github.lutzluca.btrbz.utils.ClientTickDispatcher;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.ScreenInfo;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ModuleManager {

    private static ModuleManager instance;

    private final Map<Class<? extends Module<?>>, Module<?>> modules = new HashMap<>();
    private final Map<Class<? extends Module<?>>, Field> moduleBindings = new HashMap<>();

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


    private void renderModules(ScreenInfo info) {
        var screen = info.getScreen();
        if (!(screen instanceof ScreenAccessor accessor)) {
            return;
        }

        this.modules.values().forEach(module -> module.setDisplayed(false));

        var widgets = this.modules
            .values()
            .stream()
            .filter(module -> module.shouldDisplay(info))
            .peek(module -> module.setDisplayed(true))
            .flatMap(module -> module.createWidgets(info).stream())
            .toList();

        log.trace("Adding {} widgets for initial render", widgets.size());
        widgets.forEach(accessor::invokeAddDrawableChild);
    }

    private void revalidateModules(ScreenInfo info) {
        var screen = info.getScreen();
        if (!(screen instanceof ScreenAccessor accessor)) {
            return;
        }

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

        if (!newWidgets.isEmpty()) {
            log.debug("Adding {} widgets after revalidation", newWidgets.size());
            newWidgets.forEach(accessor::invokeAddDrawableChild);
        }
    }

    public <T, M extends Module<T>> M registerModule(Class<M> moduleClass) {
        try {
            M module = moduleClass.getDeclaredConstructor().newInstance();
            modules.put(moduleClass, module);
            applyConfigToModule(module);
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

            castModule(module).applyConfigState(value);
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
                moduleBindings.put(moduleClass, field);
            }
        }
    }

    private void validateBinding(Field field, Class<? extends Module<?>> moduleClass) {
        Type moduleStateType = extractModuleStateType(moduleClass);
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

        ConfigManager.withConfig(cfg -> {
            this.modules.forEach((moduleClass, module) -> {
                var field = moduleBindings.get(moduleClass);
                if (field == null) {
                    log.warn("No binding found for module: {}", moduleClass.getName());
                    return;
                }

                try {
                    var newState = castModule(module).serializeConfigState();
                    field.set(cfg, newState);
                } catch (Exception err) {
                    log.error(
                        "Failed to update config field '{}' for module: {}",
                        field.getName(),
                        moduleClass.getName(),
                        err
                    );
                }
            });
        });

        this.isDirty = false;
    }

    @SuppressWarnings("unchecked")
    private <T> Module<T> castModule(Module<?> module) {
        return (Module<T>) module;
    }

    @SuppressWarnings("unchecked")
    public <M extends Module<?>> M getModule(Class<M> moduleClass) {
        Module<?> module = modules.get(moduleClass);
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
