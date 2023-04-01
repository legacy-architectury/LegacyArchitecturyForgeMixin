package dev.architectury.legacy.forgemixin;

import cpw.mods.modlauncher.LaunchPluginHandler;
import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.IncompatibleEnvironmentException;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import joptsimple.OptionSpecBuilder;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.net.URL;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public class LegacyForgeMixinTransformationService implements ITransformationService {

    private final Map<String, ILaunchPluginService> launchPluginServices;
    private final Set<ITransformationService> transformationServices;

    public LegacyForgeMixinTransformationService() {
        if (Launcher.INSTANCE == null) {
            throw new IllegalStateException("Launcher has not been initialized!");
        }

        this.launchPluginServices = getLaunchPluginServices();
        this.transformationServices = new HashSet<>();
    }

    @Override
    public @NotNull String name() {
        return LegacyForgeMixinBootstrap.MODID;
    }

    @Override
    public void initialize(IEnvironment environment) {
        LegacyForgeMixinBootstrap.initialize(environment);

        for (ITransformationService transformationService : this.transformationServices) {
            transformationService.initialize(environment);
        }
    }

    @Override
    public void beginScanning(IEnvironment environment) {
        for (ITransformationService transformationService : this.transformationServices) {
            transformationService.beginScanning(environment);
        }
    }

    @Override
    public void onLoad(IEnvironment env, Set<String> otherServices) throws IncompatibleEnvironmentException {
        LegacyForgeMixinBootstrap.onLoad(env, this);

        for (ITransformationService transformationService : this.transformationServices) {
            transformationService.onLoad(env, otherServices);
        }
    }

    @Override
    @SuppressWarnings("rawtypes")
    public List<ITransformer> transformers() {
        List<ITransformer> list = new ArrayList<>();
        for (ITransformationService transformationService : this.transformationServices) {
            list.addAll(transformationService.transformers());
        }

        return list;
    }

    @Override
    public void arguments(BiFunction<String, String, OptionSpecBuilder> argumentBuilder) {
        for (ITransformationService transformationService : this.transformationServices) {
            transformationService.arguments(argumentBuilder);
        }
    }

    @Override
    public void argumentValues(OptionResult option) {
        for (ITransformationService transformationService : this.transformationServices) {
            transformationService.argumentValues(option);
        }
    }

    @Override
    public List<Map.Entry<String, Path>> runScan(IEnvironment environment) {
        List<Map.Entry<String, Path>> list = new ArrayList<>();
        for (ITransformationService transformationService : this.transformationServices) {
            list.addAll(transformationService.runScan(environment));
        }

        return list;
    }

    @Override
    public Map.Entry<Set<String>, Supplier<Function<String, Optional<URL>>>> additionalClassesLocator() {
        return null;
    }

    @Override
    public Map.Entry<Set<String>, Supplier<Function<String, Optional<URL>>>> additionalResourcesLocator() {
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, ILaunchPluginService> getLaunchPluginServices() {
        try {
            // cpw.mods.modlauncher.Launcher.launchPlugins
            Field launchPluginsField = Launcher.class.getDeclaredField("launchPlugins");
            launchPluginsField.setAccessible(true);
            LaunchPluginHandler launchPluginHandler = (LaunchPluginHandler) launchPluginsField.get(Launcher.INSTANCE);

            // cpw.mods.modlauncher.LaunchPluginHandler.plugins
            Field pluginsField = LaunchPluginHandler.class.getDeclaredField("plugins");
            pluginsField.setAccessible(true);
            return (Map<String, ILaunchPluginService>) pluginsField.get(launchPluginHandler);
        } catch (Exception ex) {
            LegacyForgeMixinBootstrap.LOGGER.error("Encountered an error while getting LaunchPluginServices", ex);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public void registerLaunchPluginService(String className, ClassLoader classLoader) throws IncompatibleEnvironmentException {
        try {
            Class<? extends ILaunchPluginService> launchPluginServiceClass = (Class<? extends ILaunchPluginService>) Class.forName(className, true, classLoader);
            if (isLaunchPluginServicePresent(launchPluginServiceClass)) {
                LegacyForgeMixinBootstrap.LOGGER.warn("{} is already registered", launchPluginServiceClass.getSimpleName());
                return;
            }

            ILaunchPluginService launchPluginService = launchPluginServiceClass.newInstance();
            String pluginName = launchPluginService.name();
            this.launchPluginServices.put(pluginName, launchPluginService);

            List<Map<String, String>> mods = Launcher.INSTANCE.environment().getProperty(IEnvironment.Keys.MODLIST.get()).orElse(null);
            if (mods != null) {
                Map<String, String> mod = new HashMap<>();
                mod.put("name", pluginName);
                mod.put("type", "PLUGINSERVICE");
                String fileName = launchPluginServiceClass.getProtectionDomain().getCodeSource().getLocation().getFile();
                mod.put("file", fileName.substring(fileName.lastIndexOf('/')));
                mods.add(mod);
            }

            LegacyForgeMixinBootstrap.LOGGER.debug("Registered {} ({})", launchPluginServiceClass.getSimpleName(), pluginName);
        } catch (Throwable ex) {
            LegacyForgeMixinBootstrap.LOGGER.error("Encountered an error while registering {}", className, ex);
            throw new IncompatibleEnvironmentException(String.format("Failed to register %s", className));
        }
    }

    @SuppressWarnings("unchecked")
    public void registerTransformationService(String className, ClassLoader classLoader) throws IncompatibleEnvironmentException {
        try {
            Class<? extends ITransformationService> transformationServiceClass = (Class<? extends ITransformationService>) Class.forName(className, true, classLoader);
            if (isTransformationServicePresent(transformationServiceClass)) {
                LegacyForgeMixinBootstrap.LOGGER.warn("{} is already registered", transformationServiceClass.getSimpleName());
                return;
            }

            ITransformationService transformationService = transformationServiceClass.newInstance();
            String name = transformationService.name();
            this.transformationServices.add(transformationService);
            LegacyForgeMixinBootstrap.LOGGER.debug("Registered {} ({})", transformationServiceClass.getSimpleName(), name);
        } catch (Exception ex) {
            LegacyForgeMixinBootstrap.LOGGER.error("Encountered an error while registering {}", className, ex);
            throw new IncompatibleEnvironmentException(String.format("Failed to register %s", className));
        }
    }

    private boolean isLaunchPluginServicePresent(Class<? extends ILaunchPluginService> launchPluginServiceClass) {
        for (ILaunchPluginService launchPluginService : this.launchPluginServices.values()) {
            if (launchPluginServiceClass.isInstance(launchPluginService)) {
                return true;
            }
        }

        return false;
    }

    private boolean isTransformationServicePresent(Class<? extends ITransformationService> transformationServiceClass) {
        for (ITransformationService transformationService : this.transformationServices) {
            if (transformationServiceClass.isInstance(transformationService)) {
                return true;
            }
        }

        return false;
    }
}
