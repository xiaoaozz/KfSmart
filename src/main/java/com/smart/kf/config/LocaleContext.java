package com.smart.kf.config;

/**
 * ThreadLocal holder for the current request's locale (e.g. "zh-CN", "en-US", "ja-JP").
 * Set by LocaleInterceptor on every request; cleared in afterCompletion to prevent leaks.
 */
public class LocaleContext {

    private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

    public static void set(String lang) {
        HOLDER.set(lang);
    }

    public static String get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
