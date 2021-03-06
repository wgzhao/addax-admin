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

package com.wgzhao.addax.admin.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.ParameterBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.schema.ModelRef;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.Parameter;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yangkai
 */
@Configuration
@EnableSwagger2
public class SwaggerConfig
{
    /**
     * ??????API??????
     * apiInfo() ??????API????????????
     * ??????select()??????????????????ApiSelectorBuilder??????,?????????????????????????????????Swagger????????????
     * ????????????????????????????????????????????????????????????API????????????
     *
     * @return
     */
    @Bean
    public Docket createRestApi()
    {
        List<Parameter> parameters = new ArrayList<>();
        parameters.add(new ParameterBuilder()
                .name("token")
                .description("??????token")
                .modelRef(new ModelRef("string"))
                .parameterType("header")
                .required(false)
                .build());
        //???????????????swagger2
        return new Docket(DocumentationType.SWAGGER_2)
                //???????????????????????????apiInfo??????????????????????????????
                .apiInfo(apiInfo())
                .select()
                //?????????????????????API??????
                .apis(RequestHandlerSelectors.basePackage("com.cxzq.ds.zeus.controller"))
                .paths(PathSelectors.any())
                .build()
                .globalOperationParameters(parameters);
    }

    /**
     * ?????????API??????????????????????????????????????????????????????????????????
     * ???????????????http://??????????????????/swagger-ui.html
     *
     * @return
     */
    private ApiInfo apiInfo()
    {
        return new ApiInfoBuilder()
                //??????????????????????????????
                .title("??????????????????API")
                //API?????????
                .description("??????????????????API")
                //??????url???
                .termsOfServiceUrl("")
                .version("1.0")
                .build();
    }
}
