package com.skala.mro.global.error;

import java.util.List;
import lombok.Getter;

@Getter
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final List<?> details;

    public ApiException(ErrorCode code) {
        this(code, code.getDefaultMessage(), null);
    }

    public ApiException(ErrorCode code, String message) {
        this(code, message, null);
    }

    public ApiException(ErrorCode code, String message, List<?> details) {
        super(message);
        this.code = code;
        this.details = details;
    }

    /** 권한이 없는 행도 404로 응답해 존재 여부를 숨깁니다. */
    public static ApiException notFound() {
        return new ApiException(ErrorCode.NOT_FOUND);
    }
}
