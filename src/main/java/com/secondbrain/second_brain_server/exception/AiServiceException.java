package com.secondbrain.second_brain_server.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class AiServiceException extends RuntimeException {

    public  AiServiceException(String message) {
        super(message);
    }
    public AiServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
