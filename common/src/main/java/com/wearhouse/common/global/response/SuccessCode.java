package com.wearhouse.common.global.response;

import org.springframework.http.HttpStatus;

public interface SuccessCode {

    HttpStatus status();

    String code();

    String message();
}
