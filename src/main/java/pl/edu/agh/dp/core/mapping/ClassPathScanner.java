package pl.edu.agh.dp.core.mapping;

import pl.edu.agh.dp.core.mapping.annotations.Entity;

import java.io.*;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.jar.*;

public class ClassPathScanner {

    public static Set<Class<?>> scanForEntities(ClassLoader cl) {
        return scanForEntities(cl, (String[]) null);
    }

    public static Set<Class<?>> scanForEntities(ClassLoader cl, String... basePackages) {
        Set<Class<?>> entities = new HashSet<>();

        if (basePackages != null && basePackages.length > 0) {
            // Skanuj tylko określone pakiety
            for (String basePackage : basePackages) {
                scanPackage(basePackage, cl, entities);
            }
        } else {
            // Skanuj wszystko - używaj ostrożnie
            scanAllPackages(cl, entities);
        }

        return entities;
    }

    private static void scanPackage(String basePackage, ClassLoader cl, Set<Class<?>> entities) {
        String packagePath = basePackage.replace('.', '/');

        try {
            Enumeration<URL> resources = cl.getResources(packagePath);

            if (!resources.hasMoreElements()) {
                // Spróbuj też bez trailing slash
                resources = cl.getResources(packagePath + "/");
            }

            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                scanResource(resource, basePackage, cl, entities);
            }
        } catch (Exception e) {
            System.err.println("ClassPathScanner: Error scanning package " + basePackage + ": " + e.getMessage());
        }
    }

    private static void scanResource(URL resource, String basePackage, ClassLoader cl, Set<Class<?>> entities) {
        String protocol = resource.getProtocol();

        try {
            if ("file".equals(protocol)) {
                // Klasy w folderze (development mode)
                File directory = new File(resource.toURI());
                if (directory.exists() && directory.isDirectory()) {
                    scanDirectory(directory, basePackage, cl, entities);
                }
            } else if ("jar".equals(protocol)) {
                // Klasy w JARze (włącznie ze Spring Boot nested JARs)
                scanJarUrl(resource, basePackage, cl, entities);
            } else if ("nested".equals(protocol)) {
                // Spring Boot 3.2+ nested JAR protocol
                scanNestedJar(resource, basePackage, cl, entities);
            }
        } catch (Exception e) {
            System.err.println("ClassPathScanner: Error scanning resource " + resource + ": " + e.getMessage());
        }
    }

    private static void scanJarUrl(URL jarUrl, String basePackage, ClassLoader cl, Set<Class<?>> entities) {
        try {
            JarURLConnection connection = (JarURLConnection) jarUrl.openConnection();
            JarFile jarFile = connection.getJarFile();
            String packagePath = basePackage.replace('.', '/');

            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();

                if (entryName.startsWith(packagePath) && entryName.endsWith(".class")) {
                    String className = entryName
                            .replace("/", ".")
                            .replace(".class", "");
                    tryLoad(className, cl, entities);
                }
            }
        } catch (Exception e) {
            // Fallback - spróbuj inaczej
            try {
                String jarPath = jarUrl.getPath();
                if (jarPath.contains("!")) {
                    String filePath = jarPath.substring(0, jarPath.indexOf("!"));
                    if (filePath.startsWith("file:")) {
                        filePath = filePath.substring(5);
                    }
                    filePath = URLDecoder.decode(filePath, StandardCharsets.UTF_8);
                    scanJarFile(new File(filePath), basePackage, cl, entities);
                }
            } catch (Exception ex) {
                System.err.println("ClassPathScanner: Failed to scan JAR: " + jarUrl);
            }
        }
    }

    private static void scanNestedJar(URL nestedUrl, String basePackage, ClassLoader cl, Set<Class<?>> entities) {
        // Dla nested JARs najlepiej użyć ClassLoader do znalezienia klas
        // bo struktura może być skomplikowana
        String packagePath = basePackage.replace('.', '/') + "/";
        
        try {
            // Użyj ClassLoader do listowania zasobów
            Enumeration<URL> classResources = cl.getResources(packagePath);
            Set<String> scannedClasses = new HashSet<>();
            
            while (classResources.hasMoreElements()) {
                URL classUrl = classResources.nextElement();
                // Próbuj załadować klasy z tego zasobu
                scanClassesFromUrl(classUrl, basePackage, cl, entities, scannedClasses);
            }
        } catch (Exception e) {
            System.err.println("ClassPathScanner: Error with nested JAR: " + e.getMessage());
        }
    }

    private static void scanClassesFromUrl(URL url, String basePackage, ClassLoader cl, 
                                           Set<Class<?>> entities, Set<String> scannedClasses) {
        try {
            if ("file".equals(url.getProtocol())) {
                File dir = new File(url.toURI());
                if (dir.isDirectory()) {
                    for (File file : Objects.requireNonNull(dir.listFiles())) {
                        if (file.isDirectory()) {
                            scanClassesFromUrl(file.toURI().toURL(), 
                                    basePackage + "." + file.getName(), cl, entities, scannedClasses);
                        } else if (file.getName().endsWith(".class")) {
                            String className = basePackage + "." + file.getName().replace(".class", "");
                            if (scannedClasses.add(className)) {
                                tryLoad(className, cl, entities);
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private static void scanAllPackages(ClassLoader cl, Set<Class<?>> entities) {
        // Najpierw spróbuj przez ClassLoader
        try {
            Enumeration<URL> roots = cl.getResources("");
            while (roots.hasMoreElements()) {
                URL root = roots.nextElement();
                scanResource(root, "", cl, entities);
            }
        } catch (Exception e) {
            // Fallback na java.class.path
        }
        
        // Dodatkowo skanuj java.class.path (dla zwykłych aplikacji)
        String classpath = System.getProperty("java.class.path");
        if (classpath != null && !classpath.isEmpty()) {
            String separator = System.getProperty("path.separator");
            for (String path : classpath.split(separator)) {
                File file = new File(path);
                if (file.isDirectory()) {
                    scanDirectory(file, "", cl, entities);
                } else if (file.getName().endsWith(".jar")) {
                    scanJarFile(file, null, cl, entities);
                }
            }
        }
    }

    private static void scanDirectory(File root, String packageName,
                                      ClassLoader cl, Set<Class<?>> entities) {
        File[] files = root.listFiles();
        if (files == null) return;
        
        for (File file : files) {
            if (file.isDirectory()) {
                String subPackage = packageName.isEmpty() 
                    ? file.getName() 
                    : packageName + "." + file.getName();
                scanDirectory(file, subPackage, cl, entities);
            } else if (file.getName().endsWith(".class")) {
                String className = packageName.isEmpty()
                    ? file.getName().replace(".class", "")
                    : packageName + "." + file.getName().replace(".class", "");
                tryLoad(className, cl, entities);
            }
        }
    }

    private static void scanJarFile(File jarFile, String packageFilter, ClassLoader cl, Set<Class<?>> entities) {
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> e = jar.entries();
            String packagePath = packageFilter != null ? packageFilter.replace('.', '/') : null;
            
            while (e.hasMoreElements()) {
                JarEntry entry = e.nextElement();
                String entryName = entry.getName();

                if (entryName.endsWith(".class")) {
                    if (packagePath != null && !entryName.startsWith(packagePath)) {
                        continue;
                    }
                    
                    String className = entryName
                            .replace("/", ".")
                            .replace(".class", "");
                    tryLoad(className, cl, entities);
                }
            }
        } catch (Exception ignored) {}
    }

    private static void tryLoad(String className, ClassLoader cl, Set<Class<?>> entities) {
        // Ignoruj klasy wewnętrzne, anonimowe i Spring/Java framework
        if (className.contains("$") || 
            className.startsWith("java.") || 
            className.startsWith("javax.") ||
            className.startsWith("sun.") ||
            className.startsWith("jdk.") ||
            className.startsWith("org.springframework.") ||
            className.startsWith("org.apache.") ||
            className.startsWith("com.fasterxml.") ||
            className.startsWith("org.hibernate.")) {
            return;
        }
        
        try {
            Class<?> clazz = Class.forName(className, false, cl);
            if (clazz.isAnnotationPresent(Entity.class)) {
                entities.add(clazz);
            }
        } catch (Throwable ignored) {
            // Normalne - wiele klas nie można załadować
        }
    }
}
