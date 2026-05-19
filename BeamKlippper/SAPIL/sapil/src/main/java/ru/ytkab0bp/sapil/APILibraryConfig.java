package ru.ytkab0bp.sapil;

import com.google.gson.Gson;

public class APILibraryConfig {
    /* package */ RequestType defaultRequestType = RequestType.GET;
    /* package */ Gson gson = new Gson();

    public APILibraryConfig() {}

    public APILibraryConfig setDefaultRequestType(RequestType defaultRequestType) {
        this.defaultRequestType = defaultRequestType;
        return this;
    }

    public APILibraryConfig setGson(Gson gson) {
        this.gson = gson;
        return this;
    }
}
