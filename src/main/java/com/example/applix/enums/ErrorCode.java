package com.example.applix.enums;

import lombok.Getter;

@Getter
public enum ErrorCode {
    FILE_NOT_FOUND("FNF"),
    NO_ERROR(""),
    GENERIC_ERROR("GE")
    ;

    private String error;
    ErrorCode(String error) {
        this.error = error;
    }
}
