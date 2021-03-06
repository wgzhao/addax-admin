/*
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

/*
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.wgzhao.addax.admin.server.impl;

import com.wgzhao.addax.admin.common.ServerResponse;
import com.wgzhao.addax.admin.mapper.TableInfoCustomMapper;
import com.wgzhao.addax.admin.pojo.TableInfo;
import com.wgzhao.addax.admin.server.DataChangeRecordService;
import com.wgzhao.addax.admin.server.TableService;
import com.wgzhao.addax.admin.vo.TableInfoVo;
import com.wgzhao.addax.admin.vo.TableMainVo;
import com.wgzhao.addax.admin.vo.TreeNodeVo;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author liuting
 */
@Service
@Log4j2
public class TableServiceImpl
        implements TableService
{
    @Resource
    private TableInfoCustomMapper tableInfoCustomMapper;

    @Resource
    private DataChangeRecordService dataChangeRecordService;

    @Override
    public List<TableInfo> getAllTableInfoList(String sourceConfigId, String sourceTableSchema,
            String sourceTableName)
    {
        return tableInfoCustomMapper.getAllTableInfoList(sourceConfigId, sourceTableSchema, sourceTableName);
    }

    @Override
    public ServerResponse<List<TreeNodeVo>> queryTreeNodes()
    {
        //???????????????
        List<TreeNodeVo> rootMenu = tableInfoCustomMapper.queryAllSourceConfigs();
        //???????????????
        List<TreeNodeVo> resultList = new ArrayList<>();
        // ??????????????????????????????
        for (int i = 0; i < rootMenu.size(); i++) {
            // ??????????????????parentCode
            if (StringUtils.isBlank(rootMenu.get(i).getParentCode())) {
                resultList.add(rootMenu.get(i));
            }
        }
        // ?????????????????????????????????getChild??????????????????
        for (TreeNodeVo vo : resultList) {
            vo.setChildMenus(getChild(vo.getNodeCode(), rootMenu, 0));
        }
        return ServerResponse.createBySuccess("??????", resultList);
    }

    @Override
    public ServerResponse<TableMainVo> getTableInfo(String nodeCode)
    {
        //???????????????????????????
        Date updateTime = dataChangeRecordService.getUpdateTimeByNodeCode(nodeCode);
        TableMainVo tableMainVo = new TableMainVo();
        tableMainVo.setUpdateTime(updateTime);
        //?????????????????????
        List<TableInfoVo> tableInfoVoList = tableInfoCustomMapper.getTableInfoByNodeCode(nodeCode);
        tableMainVo.setTableInfoList(tableInfoVoList);
        return ServerResponse.createBySuccess("??????", tableMainVo);
    }

    @Override
    public List<TableInfo> getTableInfoBySubTaskId(String subTaskId, String sourceId, String db,
            String tbl)
    {
        return tableInfoCustomMapper.getTableInfoBySubTaskId(subTaskId, sourceId, db, tbl);
    }

    /**
     * ?????????????????????
     *
     * @param nodeCode ????????????code
     * @param rootMenu ??????????????????
     * @return List<TreeNodeVo>
     */
    private List<TreeNodeVo> getChild(String nodeCode, List<TreeNodeVo> rootMenu, Integer isTableLevelFlag)
    {
        // ???????????????
        List<TreeNodeVo> childList = new ArrayList<>();
        for (TreeNodeVo menu : rootMenu) {
            // ?????????????????????????????????code???????????????code??????
            if (StringUtils.isNotBlank(menu.getParentCode())) {
                if (menu.getParentCode().equals(nodeCode)) {
                    childList.add(menu);
                    menu.setIsTableLevelFlag(isTableLevelFlag);
                }
            }
        }
        // ???????????????????????????????????????
        for (TreeNodeVo menu : childList) {
            // ??????
            menu.setChildMenus(getChild(menu.getNodeCode(), rootMenu, 1));
        }
        // ??????????????????
        if (childList.size() == 0) {
            return null;
        }
        return childList;
    }
}
