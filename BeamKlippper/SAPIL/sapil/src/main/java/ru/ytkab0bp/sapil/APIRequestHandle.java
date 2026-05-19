package ru.ytkab0bp.sapil;

public interface APIRequestHandle {
    boolean isRunning();
    void cancel();
}
