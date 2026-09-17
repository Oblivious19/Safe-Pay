package com.safepay.assistant;
public class AppError extends RuntimeException {
    private final int status;
    public AppError(int status,String message) { super(message); this.status=status; }
    public int status() { return status; }
}
