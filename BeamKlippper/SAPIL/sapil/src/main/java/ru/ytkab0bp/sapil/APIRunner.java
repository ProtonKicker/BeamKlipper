package ru.ytkab0bp.sapil;

import java.util.Map;

import ru.ytkab0bp.sapil.util.Pair;

public interface APIRunner {
    String DEFAULT_USER_AGENT = "SAPIL v" + BuildConfig.VERSION + "/" + BuildConfig.VERSION_CODE;

    interface RunnerConfig {
        default String getBaseURL() {
            return null;
        }

        default String getPathPrefix() {
            return "";
        }

        default Pair<String, String> getNamingTransformPolicy() {
            return null;
        }

        default boolean getBooleansAsInt() {
            return true;
        }

        default String getDefaultUserAgent() {
            return DEFAULT_USER_AGENT;
        }

        default Map<String, String> getDefaultHeaders() {
            return null;
        }
    }
}
