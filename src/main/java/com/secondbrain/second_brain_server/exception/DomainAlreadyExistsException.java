package com.secondbrain.second_brain_server.exception;

import com.secondbrain.second_brain_server.enums.DomainType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class DomainAlreadyExistsException extends RuntimeException {
    public DomainAlreadyExistsException(DomainType type) {
        super(String.format("Domain with type '%s' already exists for this user.", type));
    }
}
