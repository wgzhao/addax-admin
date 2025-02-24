package com.wgzhao.addax.admin.utils;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;

public class DriverLoader {

    public static void loadJarsFromLib(String libPath) throws Exception {
        // 获取 lib 目录
        File libDir = new File(libPath);
        if (!libDir.exists() || !libDir.isDirectory()) {
            throw new IllegalArgumentException("Lib directory not found: " + libPath);
        }

        // 获取所有 JAR 文件
        File[] jars = libDir.listFiles((dir, name) -> name.endsWith(".jar"));
        if (jars == null || jars.length == 0) {
            System.out.println("No JAR files found in lib directory.");
            return;
        }

        // 构建 URL 数组
        URL[] jarUrls = new URL[jars.length];
        for (int i = 0; i < jars.length; i++) {
            jarUrls[i] = jars[i].toURI().toURL();
            System.out.println("Loading JAR: " + jars[i].getName());
        }

        // 创建一个 URLClassLoader
        URLClassLoader jarClassLoader = new URLClassLoader(jarUrls, Thread.currentThread().getContextClassLoader());

        // 设置当前线程的上下文类加载器
        Thread.currentThread().setContextClassLoader(jarClassLoader);

        System.out.println("All JARs have been loaded into the context class loader.");
    }
}
