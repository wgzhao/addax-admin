package com.wgzhao.addax.admin.utils;


/**
 * Redis操作工具类
 */
public class RedisUtils {

    /**
     * 获取Redis中的值
     * @param key 键
     * @return 值
     */
    public static String get(String key) {
        return CommandExecutor.executeForOutput("rds \"get " + key + "\"").trim();
    }

    /**
     * 设置Redis中的值
     * @param key 键
     * @param value 值
     * @return 操作结果
     */
    public static String set(String key, String value) {
        return CommandExecutor.executeForOutput("rds \"set " + key + " " + value + "\"").trim();
    }

    /**
     * 删除Redis中的键
     * @param key 键
     * @return 操作结果
     */
    public static String delete(String key) {
        return CommandExecutor.executeForOutput("rds \"del " + key + "\"").trim();
    }

    /**
     * 添加标志
     * @param flag 标志名
     * @return 操作结果
     */
    public static String flagAdd(String flag) {
        return CommandExecutor.executeForOutput("rfg add " + flag).trim();
    }

    /**
     * 移除标志
     * @param flag 标志名
     * @return 操作结果
     */
    public static String flagRemove(String flag) {
        return CommandExecutor.executeForOutput("rfg rem " + flag).trim();
    }

    /**
     * 检查标志是否存在
     * @param flag 标志名
     * @return 操作结果
     */
    public static String flagHas(String flag) {
        return CommandExecutor.executeForOutput("rfg has " + flag).trim();
    }
}
