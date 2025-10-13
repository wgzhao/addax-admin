package com.wgzhao.addax.admin.dto

import lombok.*

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
class JwtResponseDTO {
    private var accessToken: String? = null
}
