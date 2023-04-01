package dev.architectury.legacy.forgemixin;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class LegacyForgeMixinClassLoader extends ClassLoader {
    private final ClassLoader child;
    private final Set<String> invalidClasses;

    public LegacyForgeMixinClassLoader(ClassLoader parent, ClassLoader child) {
        super(parent);
        this.child = child;
        this.invalidClasses = Collections.newSetFromMap(new ConcurrentHashMap<>());
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        if (invalidClasses.contains(name)) {
            throw new ClassNotFoundException(name);
        }

        invalidClasses.add(name);
        Class<?> validClass = child.loadClass(name);
        invalidClasses.remove(name);

        return validClass;
    }
}
