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

package com.wgzhao.addax.admin.server;

import com.wgzhao.addax.admin.common.ServerResponse;
import com.wgzhao.addax.admin.dto.EditJsonDto;
import com.wgzhao.addax.admin.dto.JsonDto;
import com.wgzhao.addax.admin.dto.JsonTaskDto;
import com.wgzhao.addax.admin.dto.QueryJsonDto;
import com.wgzhao.addax.admin.dto.RandomStrDto;
import com.wgzhao.addax.admin.vo.JsonProcessVo;
import com.wgzhao.addax.admin.vo.JsonVo;
import com.wgzhao.addax.admin.vo.QueryJsonVo;
import com.wgzhao.addax.admin.vo.UserVo;
import com.github.pagehelper.PageInfo;

import java.util.List;

/**
 * @author liuting
 */
public interface JsonService
{
    /**
     * 分页查询json列表
     *
     * @param queryJsonDto 查询参数
     * @return PageInfo<QueryJsonVo>>
     */
    ServerResponse<PageInfo<QueryJsonVo>> queryJsonListWithPage(QueryJsonDto queryJsonDto);

    /**
     * 编辑json
     *
     * @param editJsonDto 参数
     * @return ServerResponse<String>
     */
    ServerResponse<String> updateJsonInfo(EditJsonDto editJsonDto);

    /**
     * 生成json
     *
     * @param jsonDto 参数
     * @param userVo 用户信息
     * @param randomStr 随机数
     * @return ServerResponse<String>
     */
    ServerResponse<String> generateJson(JsonDto jsonDto, UserVo userVo,String randomStr);

    /**
     * 编辑json文件描述
     * @param dto 参数
     * @return ServerResponse<String>
     */
    ServerResponse<String> updateJsonDesc(JsonTaskDto dto);

    /**
     * 查询构建json进程
     * @param dto 参数
     * @return ServerResponse<JsonProcessVo>
     */
    ServerResponse<JsonProcessVo> getAllGenerateJsonProcess(RandomStrDto dto);

    /**
     * 查询已构建的json
     * @param dto 参数
     * @return ServerResponse<List<JsonVo>>
     */
    ServerResponse<List<JsonVo>> getAllGenerateJson(JsonTaskDto dto);

    /**
     * 构建json前的校验
     * @param jsonDto 参数
     */
    void verification(JsonDto jsonDto);

    /**
     * 元数据变更更新json
     * @return
     */
    ServerResponse<String> updateJson();
}
