package com.ecommerce.project.exceptions;

public class APIexception extends RuntimeException{
    private static final long serialVersionUID=1L;

    public APIexception() {
    }

    public APIexception(String message) {
        super(message);
    }
}
