package com.wgzhao.addax.admin.utils;

import cn.hutool.json.JSON;
import cn.hutool.json.JSONObject;
import lombok.AllArgsConstructor;

import java.util.Map;

/**
 * Addax JSON 文件生成帮助类
 */
public class AddaxJsonHelper
{
    private JSONObject addaxJson;

    public AddaxJsonHelper() {
        this.addaxJson = new JSONObject();
        addaxJson.put("job", new JSONObject().put("content", new Object[0]).put("setting", new JSONObject()));
    }

    public void setRDBMSReader(String kind, Map<String, Object> readerParams) {
        JSONObject reader = new JSONObject();
        reader.put("name", "rdbmsreader");
        reader.put("parameter", new JSONObject(readerParams));
        addaxJson.getJSONObject("job").getJSONArray("content").set(0, new JSONObject().put("reader", reader).put("writer", null));
    }
}
