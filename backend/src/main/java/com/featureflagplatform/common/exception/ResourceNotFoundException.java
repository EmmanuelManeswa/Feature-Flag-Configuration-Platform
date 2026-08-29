package com.featureflagplatform.common.exception;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String entityType, Object id) {
        super("%s not found: %s".formatted(entityType, id));
    }
}
