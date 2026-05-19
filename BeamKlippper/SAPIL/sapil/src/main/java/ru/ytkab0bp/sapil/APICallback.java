package ru.ytkab0bp.sapil;

import android.util.Log;

public interface APICallback<T> {
    void onResponse(T response);

    default void onException(Exception e) {
        Log.e("sapil", "Failed to execute request", e);
    }
}
