package com.wgzhao.addax.admin.utils;

import com.wgzhao.addax.admin.service.AddaxLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.DriverManager;

/**
 * 命令执行工具类
 */
@Slf4j
public class CommandExecutor
{
    /**
     * 执行命令并返回输出结果
     *
     * @param command 要执行的命令
     * @return 命令的输出结果
     */
    public static String executeForOutput(String command)
    {
        StringBuilder output = new StringBuilder();
        Process process = null;

        try {
            process = Runtime.getRuntime().exec(new String[] {"bash", "-c", command});

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            process.waitFor();

            // 移除最后一个换行符
            if (output.length() > 0) {
                output.setLength(output.length() - 1);
            }

            return output.toString();
        }
        catch (IOException | InterruptedException e) {
            if (process != null) {
                process.destroy();
            }
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return "";
        }
    }

    public static int executeWithResult(String command)
    {
        try {
            return Runtime.getRuntime().exec(new String[] {command}).waitFor();
        }
        catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 执行命令但不关心输出
     *
     * @param command 要执行的命令
     */
    public static void execute(String command)
    {
        try {
            Runtime.getRuntime().exec(new String[] {command}, null, null);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 获取当前主机名
     *
     * @return 主机名
     */
    public static String getHostname()
    {
        return executeForOutput("hostname").trim();
    }

    /**
     * 获取当前进程ID
     *
     * @return 进程ID
     */
    public static String getPid()
    {
        return executeForOutput("echo $$").trim();
    }

    /**
     * 获取IP地址
     *
     * @return IP地址
     */
    public static String getIpAddress()
    {
        return executeForOutput("ifconfig | grep '188\\.175\\.' | awk '{print $2}'").trim();
    }
}