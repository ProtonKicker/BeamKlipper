package ru.ytkab0bp.sapil;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringReader;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ru.ytkab0bp.sapil.util.Pair;

public class APILibrary {
    private final static List<String> SUPPORTED_PROTOCOLS = Arrays.asList(
            "http",
            "https"
    );
    private final static ExecutorService IO_POOL = Executors.newCachedThreadPool();

    // Default config
    private static APILibraryConfig mConfig = new APILibraryConfig();

    /**
     * Sets global API Library config
     */
    public static void setConfig(APILibraryConfig config) {
        mConfig = config;
    }

    /**
     * Constructs new API Runner
     *
     * @param <T>       Your type of the runner
     * @param clz       Class of the type, required for construction, must be same class as type
     * @param config    Runner configuration, might be null
     *
     * @return Newly created runner
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T extends APIRunner> T newRunner(Class<T> clz, APIRunner.RunnerConfig config) {
        return (T) Proxy.newProxyInstance(clz.getClassLoader(), new Class[]{clz}, (proxy, method, args) -> {
            Method mt = method.getAnnotation(Method.class);
            if (mt != null) {
                String url = mt.value();
                if (config != null && config.getNamingTransformPolicy() != null) {
                    Pair<String, String> policy = config.getNamingTransformPolicy();
                    url = url.replaceAll(policy.first, policy.second);
                }
                RequestType type = mt.requestType() != RequestType._DEFAULT ? mt.requestType() : mConfig.defaultRequestType;

                APICallback<?> callback = null;
                Type callbackType = null;
                APIEnclosure enclosure = null;
                Map<String, String> params = new HashMap<>();
                Map<String, String> headers = new HashMap<>();
                if (config != null) headers.put("User-Agent", config.getDefaultUserAgent());
                else headers.put("User-Agent", APIRunner.DEFAULT_USER_AGENT);

                Map<String, String> defHeaders = config != null ? config.getDefaultHeaders() : null;
                if (defHeaders != null) headers.putAll(defHeaders);

                for (int i = 0; i < args.length; i++) {
                    Class<?> argClz = method.getParameterTypes()[i];

                    if (argClz.isAssignableFrom(APICallback.class)) {
                        if (callback == null) {
                            callbackType = ((ParameterizedType) method.getGenericParameterTypes()[i]).getActualTypeArguments()[0];
                            callback = (APICallback<?>) args[i];
                        } else {
                            throw new UnsupportedOperationException("Method confusion: more than one callback");
                        }
                    } else if (argClz.isAssignableFrom(APIEnclosure.class)) {
                        if (enclosure == null) {
                            enclosure = (APIEnclosure) args[i];
                        } else {
                            throw new UnsupportedOperationException("Method confusion: more than one enclosure");
                        }
                    } else {
                        Annotation[] annotations = method.getParameterAnnotations()[i];
                        String key = null;
                        boolean header = false;
                        if (annotations != null) {
                            for (Annotation ann : annotations) {
                                if (ann instanceof Arg) {
                                    key = ((Arg) ann).value();
                                    break;
                                }
                                if (ann instanceof Header) {
                                    key = ((Header) ann).value();
                                    header = true;
                                    break;
                                }
                            }
                        }

                        String value;
                        if (args[i] instanceof Boolean) {
                            value = config == null || config.getBooleansAsInt() ? ((boolean) args[i] ? "1" : "0") : args[i].toString();
                        } else if (args[i] instanceof List) {
                            value = mConfig.gson.toJson(args[i]);
                        } else {
                            value = args[i].toString();
                        }

                        if (header) {
                            headers.put(key, value);
                        } else if (key == null) {
                            int j = url.indexOf("{}");
                            if (j == -1) {
                                throw new UnsupportedOperationException("Method confusion: unknown argument without name");
                            }
                            url = url.substring(0, j) + URLEncoder.encode(value, "UTF-8") + url.substring(j + 2);
                        } else {
                            params.put(key, value);
                        }
                    }
                }

                String fullUrl;
                if (config != null) {
                    fullUrl = config.getBaseURL() + config.getPathPrefix() + url;
                } else {
                    fullUrl = url;
                }

                if (type == RequestType.GET) {
                    StringBuilder sb = new StringBuilder(fullUrl);
                    sb.append("?");
                    boolean first = true;
                    for (Map.Entry<String, String> en : params.entrySet()) {
                        if (first) first = false;
                        else sb.append("&");

                        sb.append(URLEncoder.encode(en.getKey(), "UTF-8")).append("=").append(URLEncoder.encode(en.getValue(), "UTF-8"));
                    }
                    fullUrl = sb.toString();
                }

                URL urlObj = new URL(fullUrl);
                if (!SUPPORTED_PROTOCOLS.contains(urlObj.getProtocol())) {
                    throw new UnsupportedOperationException("Unsupported protocol: " + urlObj.getProtocol());
                }
                if (urlObj.getHost() == null || urlObj.getHost().isEmpty()) {
                    throw new UnsupportedOperationException("Host is null or empty");
                }

                APICallback finalCallback = callback;
                Type finalCallbackType = callbackType;
                APIRequestHandleImpl handle = new APIRequestHandleImpl(IO_POOL.submit(() -> {
                    try {
                        HttpURLConnection con = (HttpURLConnection) urlObj.openConnection();
                        con.setRequestMethod(type.name());
                        con.setDoOutput(type != RequestType.GET);
                        for (Map.Entry<String, String> en : headers.entrySet()) {
                            con.addRequestProperty(en.getKey(), en.getValue());
                        }
                        if (con.getDoOutput()) {
                            OutputStream out = con.getOutputStream();

                            // Raw data support
                            if (params.size() == 1 && params.containsKey("")) {
                                out.write(params.get("").getBytes(StandardCharsets.UTF_8));
                            } else {
                                StringBuilder sb = new StringBuilder();
                                boolean first = true;
                                for (Map.Entry<String, String> en : params.entrySet()) {
                                    if (first) first = false;
                                    else sb.append("&");

                                    sb.append(URLEncoder.encode(en.getKey(), "UTF-8")).append("=").append(URLEncoder.encode(en.getValue(), "UTF-8"));
                                }
                                out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
                            }

                            // TODO: Multipart support

                            out.close();
                        }

                        Object result = null;
                        if (finalCallback != null) {
                            InputStream in = con.getInputStream();
                            if (finalCallbackType == InputStream.class) {
                                result = in;
                            } else {
                                BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                                if (finalCallbackType == String.class) {
                                    result = reader.readLine();
                                } else if (finalCallbackType == Boolean.class) {
                                    result = Boolean.parseBoolean(reader.readLine());
                                } else if (finalCallbackType == Integer.class) {
                                    result = Integer.parseInt(reader.readLine());
                                } else if (finalCallbackType == Float.class) {
                                    result = Float.parseFloat(reader.readLine());
                                } else if (finalCallbackType == Long.class) {
                                    result = Long.parseLong(reader.readLine());
                                } else if (finalCallbackType == Double.class) {
                                    result = Double.parseDouble(reader.readLine());
                                } else if (finalCallbackType == Byte.class) {
                                    result = Byte.parseByte(reader.readLine());
                                } else {
                                    JsonElement el = JsonParser.parseReader(reader);
                                    result = mConfig.gson.fromJson(el, finalCallbackType);
                                }

                                reader.close();
                                in.close();
                            }
                        }

                        if (result != null) {
                            finalCallback.onResponse(result);
                        }
                        pingQueue();
                    } catch (Exception e) {
                        if (finalCallback != null) {
                            finalCallback.onException(e);
                        }
                    }
                }));

                if (enclosure != null) {
                    enclosure.addLifecycleDestroyListener(handle::cancel);
                }

                if (method.getReturnType() == APIRequestHandle.class) {
                    return handle;
                } else {
                    return null;
                }
            }

            throw new UnsupportedOperationException("Method " + method.getName() + " is unsupported.");
        });
    }

    private static void pingQueue() {
        // No-op for now
    }
}
