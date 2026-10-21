package com.skala.mro.global.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

public record ErrorResponse(Body error) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Body(String code, String message, List<?> details) {
    }

    public static ErrorResponse of(ErrorCode code, String message, List<?> details) {
        return new ErrorResponse(new Body(code.name(), message, details));
    }

    public static ErrorResponse of(ErrorCode code) {
        return of(code, code.getDefaultMessage(), null);
    }
}
