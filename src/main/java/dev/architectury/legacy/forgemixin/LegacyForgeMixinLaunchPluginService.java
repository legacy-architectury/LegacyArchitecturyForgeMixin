package dev.architectury.legacy.forgemixin;

import cpw.mods.modlauncher.TransformingClassLoader;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

public class LegacyForgeMixinLaunchPluginService implements ILaunchPluginService {

    private static final List<String> SKIP_PACKAGES = Arrays.asList(
            "org.objectweb.asm.",
            "org.spongepowered.asm.launch.",
            "org.spongepowered.asm.lib.",
            "org.spongepowered.asm.mixin.",
            "org.spongepowered.asm.service.",
            "org.spongepowered.asm.util."
    );

    @Override
    public String name() {
        return LegacyForgeMixinBootstrap.MODID;
    }

    @Override
    public EnumSet<Phase> handlesClass(Type classType, boolean isEmpty) {
        throw new UnsupportedOperationException("Outdated ModLauncher");
    }

    @Override
    public boolean processClass(Phase phase, ClassNode classNode, Type classType) {
        throw new UnsupportedOperationException("Outdated ModLauncher");
    }

    @Override
    public EnumSet<Phase> handlesClass(Type classType, boolean isEmpty, String reason) {
        return EnumSet.noneOf(Phase.class);
    }

    @Override
    public boolean processClass(Phase phase, ClassNode classNode, Type classType, String reason) {
        return false;
    }

    @Override
    public void initializeLaunch(ITransformerLoader transformerLoader, Path[] specialPaths) {
        TransformingClassLoader classLoader = (TransformingClassLoader) Thread.currentThread().getContextClassLoader();
        classLoader.addTargetPackageFilter(name -> SKIP_PACKAGES.stream().noneMatch(name::startsWith));
    }
}
