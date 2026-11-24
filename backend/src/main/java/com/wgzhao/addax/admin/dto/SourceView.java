package com.wgzhao.addax.admin.dto;

import com.wgzhao.addax.admin.model.EtlSource;
import lombok.Data;
import org.springframework.beans.BeanUtils;
import tools.jackson.databind.JsonNode;

import java.time.LocalTime;

/**
 * Response shape of {@code /sources}.
 *
 * <p>The URL is metadata: it identifies the endpoint and the operator manual requires credentials to
 * be entered in the separate account/password fields, never embedded in the URL. The account,
 * structured config, prerequisite and pre-script stay operational reads (ADR-0008/ADR-0020), so
 * only an account that can operate a source receives them. Returning a DTO instead of the entity
 * also keeps future entity columns from silently becoming part of the public API.</p>
 */
@Data
public class SourceView
{
    private int id;
    private String name;
    private String url;
    private String username;
    private String pass;
    private LocalTime startAt;
    private String calendar;
    private String prerequisite;
    private String preScript;
    private String remark;
    private boolean enabled;
    private Integer maxConcurrency;
    private String dbType;
    private JsonNode connectConfig;
    private Long tableCount;
    private Long validTableCount;

    public static SourceView of(EtlSource source, boolean operational)
    {
        SourceView view = new SourceView();
        BeanUtils.copyProperties(source, view);
        // Never let the entity's real password survive the copy, even for operators.
        view.setPass(operational ? EtlSource.PASS_SENTINEL : null);
        if (!operational) {
            view.setUsername(null);
            view.setPrerequisite(null);
            view.setPreScript(null);
            view.setConnectConfig(null);
        }
        return view;
    }
}
