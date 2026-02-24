package com.wgzhao.addax.admin.dto;

import java.util.List;

public record UserAdminDto(String username, boolean enabled, List<String> authorities)
{
}
