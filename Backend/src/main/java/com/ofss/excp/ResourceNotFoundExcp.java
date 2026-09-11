package com.ofss.excp;

public class ResourceNotFoundExcp extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ResourceNotFoundExcp(String message) { super(message); }
}
