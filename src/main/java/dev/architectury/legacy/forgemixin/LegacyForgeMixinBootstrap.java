package dev.architectury.legacy.forgemixin;

import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.IncompatibleEnvironmentException;
import io.github.lxgaming.classloader.ClassLoaderUtils;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.launch.MixinBootstrap;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.nio.file.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Mod(LegacyForgeMixinBootstrap.MODID)
public class LegacyForgeMixinBootstrap {
    public static final String MODID = "legacyarchforgemixin";
    public static final String MODNAME = "LegacyArchitecturyForgeMixin";
    public static final String MODVERSION = "@version@";
    public static final String MixinVersion = MixinBootstrap.VERSION;
    public static final String ModLauncherImplVersion = IEnvironment.class.getPackage().getImplementationVersion();
    public static final String ModLauncherSpecVersion = IEnvironment.class.getPackage().getSpecificationVersion();
    public static final Logger LOGGER = LogManager.getLogger(MODNAME);

    public LegacyForgeMixinBootstrap() {
        LOGGER.info("{} v{}, Mixin v{}, ModLauncher v{} ({})", MODNAME, MODVERSION, MixinVersion, ModLauncherImplVersion, ModLauncherSpecVersion);
    }

    public static void initialize(IEnvironment environment) {
        ensureTransformerExclusion();
    }

    public static void onLoad(IEnvironment environment, LegacyForgeMixinTransformationService service) throws IncompatibleEnvironmentException {
        if (environment.findLaunchPlugin("mixin").isPresent()) {
            LOGGER.debug("MixinLaunchPlugin detected");
            return;
        }

        if (IEnvironment.class.getPackage().isCompatibleWith("8.0")) {
            setFallbackClassLoader(service.getClass().getClassLoader());

            // Mixin
            // - Plugin Service
            service.registerLaunchPluginService("org.spongepowered.asm.launch.MixinLaunchPlugin", MixinBootstrap.class.getClassLoader());

            // - Transformation Service
            // This cannot be loaded by the ServiceLoader as it will load classes under the wrong classloader
            service.registerTransformationService("org.spongepowered.asm.launch.MixinTransformationService", MixinBootstrap.class.getClassLoader());
            return;
        }

        if (IEnvironment.class.getPackage().isCompatibleWith("4.0")) {
            appendToClassPath();

            // Mixin
            // - Plugin Service
            service.registerLaunchPluginService("org.spongepowered.asm.launch.MixinLaunchPluginLegacy", Launcher.class.getClassLoader());

            // - Transformation Service
            // This cannot be loaded by the ServiceLoader as it will load classes under the wrong classloader
            service.registerTransformationService("org.spongepowered.asm.launch.MixinTransformationServiceLegacy", Thread.currentThread().getContextClassLoader());

            // MixinBootstrap
            // - Plugin Service
            service.registerLaunchPluginService("io.github.lxgaming.mixin.launch.MixinLaunchPluginService", Launcher.class.getClassLoader());
            return;
        }

        LOGGER.error("-------------------------[ ERROR ]-------------------------");
        LOGGER.error("Mixin is not compatible with ModLauncher v{}", ITransformationService.class.getPackage().getImplementationVersion());
        LOGGER.error("Ensure you are running Forge v28.1.45 or later");
        LOGGER.error("-------------------------[ ERROR ]-------------------------");
        throw new IncompatibleEnvironmentException("Incompatibility with ModLauncher");
    }

    private static void appendToClassPath() throws IncompatibleEnvironmentException {
        try {
            Map<String, String> map = new HashMap<>();
            map.put("create", "true");
            FileSystem fileSystem = FileSystems.newFileSystem(URI.create("jar:" + MixinBootstrap.class.getProtectionDomain().getCodeSource().getLocation().toURI()), map);
            Map<Path, String> libraries = Files.list(fileSystem.getPath("META-INF", "libraries"))
                    .filter(path -> !Files.isDirectory(path) && path.getFileName().toString().endsWith(".jar"))
                    .collect(Collectors.toMap(path -> path, path -> {
                        String fileName = path.getFileName().toString();
                        int index = fileName.lastIndexOf('.');
                        return index != -1 ? fileName.substring(0, index) : fileName;
                    }));

            LOGGER.debug("Found {} libraries", libraries.size());

            for (Map.Entry<Path, String> entry : libraries.entrySet()) {
                Path temporaryPath = Files.createTempFile(MODID + "-" + entry.getValue() + "-", ".jar");

                LOGGER.debug("Copying {} -> {}", entry.getKey(), temporaryPath);
                Files.copy(entry.getKey(), temporaryPath, StandardCopyOption.REPLACE_EXISTING);

                URL url = temporaryPath.toUri().toURL();
                LOGGER.debug("Loading {}", url);
                ClassLoaderUtils.appendToClassPath(Thread.currentThread().getContextClassLoader(), url);
            }

            URL url = MixinBootstrap.class.getProtectionDomain().getCodeSource().getLocation().toURI().toURL();
            LOGGER.debug("Loading {}", url);
            ClassLoaderUtils.appendToClassPath(Thread.currentThread().getContextClassLoader(), url);
        } catch (Throwable ex) {
            LOGGER.error("Encountered an error while appending to the class path", ex);
            throw new IncompatibleEnvironmentException("Failed to append to the class path");
        }
    }

    /**
     * Mixin requires it can be loaded in context class loader.
     * Thanks <a href="https://github.com/ZekerZhayard">ZekerZhayard</a>
     */
    private static void setFallbackClassLoader(ClassLoader classLoader) throws IncompatibleEnvironmentException {
        try {
            Class<?> moduleClassLoaderClass = Class.forName("cpw.mods.cl.ModuleClassLoader");
            ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
            if (!moduleClassLoaderClass.isInstance(contextClassLoader) || classLoader.equals(contextClassLoader)) {
                throw new IllegalStateException("Unexpected ClassLoader: " + contextClassLoader.getClass().getName());
            }

            Method getPlatformClassLoaderMethod = ClassLoader.class.getMethod("getPlatformClassLoader");
            getPlatformClassLoaderMethod.setAccessible(true);
            ClassLoader platformClassLoader = (ClassLoader) getPlatformClassLoaderMethod.invoke(null);

            Method setFallbackClassLoaderMethod = moduleClassLoaderClass.getMethod("setFallbackClassLoader", ClassLoader.class);
            setFallbackClassLoaderMethod.setAccessible(true);
            setFallbackClassLoaderMethod.invoke(contextClassLoader, new LegacyForgeMixinClassLoader(platformClassLoader, classLoader));
        } catch (Throwable ex) {
            LOGGER.error("Encountered an error while setting fallback classloader", ex);
            throw new IncompatibleEnvironmentException("Failed to set fallback classloader");
        }
    }

    /**
     * Fixes <a href="https://github.com/MinecraftForge/MinecraftForge/pull/6600">MinecraftForge/MinecraftForge/pull/6600</a>
     */
    @SuppressWarnings("unchecked")
    private static void ensureTransformerExclusion() {
        try {
            Path path = Launcher.INSTANCE.environment().getProperty(IEnvironment.Keys.GAMEDIR.get())
                    .map(parentPath -> parentPath.resolve("mods"))
                    .map(Path::toAbsolutePath)
                    .map(Path::normalize)
                    .map(parentPath -> {
                        // Extract the file name
                        String file = MixinBootstrap.class.getProtectionDomain().getCodeSource().getLocation().getFile();
                        return parentPath.resolve(file.substring(file.lastIndexOf('/') + 1));
                    })
                    .filter(Files::exists)
                    .orElse(null);
            if (path == null) {
                return;
            }

            // Check if the path is behind a symbolic link
            if (path.equals(path.toRealPath())) {
                return;
            }

            Class<?> modDirTransformerDiscovererClass = Class.forName("net.minecraftforge.fml.loading.ModDirTransformerDiscoverer", true, Launcher.class.getClassLoader());

            // net.minecraftforge.fml.loading.ModDirTransformerDiscoverer.transformers
            Field transformersField = modDirTransformerDiscovererClass.getDeclaredField("transformers");
            transformersField.setAccessible(true);
            List<Path> transformers = (List<Path>) transformersField.get(null);

            if (transformers != null && !transformers.contains(path)) {
                transformers.add(path);
            }
        } catch (Throwable ex) {
            LOGGER.error("Encountered an error while ensuring transformer exclusion", ex);
        }
    }
}
