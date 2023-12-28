package com.wgzhao.fsbrowser.model.oracle;

public interface VwImpSystemProjection {
    // use sysKind,sysid,sysName,dbConstr,dbUser as column name
    String getSysKind();
    String getSysid();
    String getSysName();
    String getDbConstr();
    String getDbUser();

}
