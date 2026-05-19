package ru.ytkab0bp.sapil;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface Method {
    /**
     * HTTP request type for the method
     */
    RequestType requestType() default RequestType._DEFAULT;

    /**
     * HTTP POST data template
     */
    String data() default "";

    /**
     * Request URL
     */
    String value();
}
