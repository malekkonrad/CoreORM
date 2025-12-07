package pl.edu.agh.dp.core.mapping.scanner;



import pl.edu.agh.dp.api.annotations.Entity;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.jar.*;

public class ClassPathScanner {

    public static Set<Class<?>> scanForEntities(ClassLoader cl) {
        Set<Class<?>> entities = new HashSet<>();
        String classpath = System.getProperty("java.class.path");
        String separator = System.getProperty("path.separator");

        for (String path : classpath.split(separator)) {
            File file = new File(path);
            if (file.isDirectory()) {
                scanDirectory(file, "", cl, entities);
            } else if (file.getName().endsWith(".jar")) {
                scanJar(file, cl, entities);
            }
        }
        return entities;
    }

    private static void scanDirectory(File root, String packageName,
                                      ClassLoader cl, Set<Class<?>> entities) {
        for (File file : Objects.requireNonNull(root.listFiles())) {

            if (file.isDirectory()) {
                scanDirectory(file,
                        packageName + file.getName() + ".",
                        cl, entities);
            } else if (file.getName().endsWith(".class")) {
                String className = packageName +
                        file.getName().replace(".class", "");
                tryLoad(className, cl, entities);
            }
        }
    }

    private static void scanJar(File jarFile, ClassLoader cl, Set<Class<?>> entities) {
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> e = jar.entries();
            while (e.hasMoreElements()) {
                JarEntry entry = e.nextElement();

                if (entry.getName().endsWith(".class")) {
                    String className =
                            entry.getName()
                                    .replace("/", ".")
                                    .replace(".class", "");
                    tryLoad(className, cl, entities);
                }
            }
        } catch (Exception ignored) {}
    }

    private static void tryLoad(String className,
                                ClassLoader cl,
                                Set<Class<?>> entities) {
        try {
            Class<?> clazz = Class.forName(className, false, cl);
            if (clazz.isAnnotationPresent(Entity.class)) {
                entities.add(clazz);
            }
        } catch (Throwable ignored) {}
    }
}
