package ru.ytkab0bp.sapil;

import java.util.concurrent.Future;

class APIRequestHandleImpl implements APIRequestHandle {
    private Future<?> future;

    APIRequestHandleImpl(Future<?> f) {
        this.future = f;
    }

    @Override
    public boolean isRunning() {
        return !future.isDone();
    }

    @Override
    public void cancel() {
        future.cancel(true);
    }
}
