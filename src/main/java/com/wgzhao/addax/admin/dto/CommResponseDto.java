package com.wgzhao.addax.admin.dto;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
public class CommResponseDto<T> {
    private int code;
    private String message;
    private T data;

    @Override
    public String toString() {
        return "CommResponseDto{" +
                "code=" + code +
                ", message='" + message + '\'' +
                ", data=" + data +
                '}';
    }
}
