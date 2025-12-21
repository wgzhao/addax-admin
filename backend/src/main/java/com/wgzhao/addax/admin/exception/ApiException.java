package com.wgzhao.addax.admin.exception;

import lombok.Getter;

@Getter
public class ApiException
    extends RuntimeException
{
    private final int code;

    public ApiException(int code, String message)
    {
        super(message);
        this.code = code;
    }
}
