package com.ecommerce.project.exceptions;

import java.io.Serial;

public class APIexception extends RuntimeException{
    @Serial
    private static final long serialVersionUID=1L;

    public APIexception() {
    }

    public APIexception(String message) {
        super(message);
    }
}
