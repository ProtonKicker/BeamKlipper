package ru.ytkab0bp.sapil;

public interface APIEnclosure {
    boolean canExecuteRequests();
    void addLifecycleDestroyListener(LifecycleDestroyListener destroyListener);
    void removeLifecycleDestroyListener(LifecycleDestroyListener destroyListener);

    interface LifecycleDestroyListener {
        void onDestroy();
    }
}
