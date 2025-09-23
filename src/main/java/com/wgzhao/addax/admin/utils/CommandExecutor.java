package com.wgzhao.addax.admin.utils;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

/**
 * 命令执行工具类
 */
@Slf4j
public class CommandExecutor
{
    public record CommandResult(int exitCode, String output) {}
    /**
     * 执行命令并返回输出结果
     *
     * @param command 要执行的命令
     * @return 命令的输出结果
     */
    public static CommandResult executeForOutput(String command)
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

            int exitCode = process.waitFor();

            if (exitCode != 0) {
                try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String errLine;
                    while ((errLine = errorReader.readLine()) != null) {
                        output.append(errLine).append("\n");
                    }
                }
            }

            return new CommandResult(exitCode, output.toString());
        }
        catch (IOException | InterruptedException e) {
            if (process != null) {
                process.destroy();
            }
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new CommandResult(-1, "");
        }
    }

    public static int executeWithResult(String command)
    {
        try {
            Process process = Runtime.getRuntime().exec(new String[] {"bash", "-c", command});
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
            }

            int exitCode = process.waitFor();

            if (exitCode != 0) {
                try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String errLine;
                    while ((errLine = errorReader.readLine()) != null) {
                        System.err.println(errLine);
                    }
                }
            }
            return process.waitFor();
        }
        catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
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
            Runtime.getRuntime().exec(new String[] {"bash", "-c", command});
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}