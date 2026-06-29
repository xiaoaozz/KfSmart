package com.smart.kf.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Reads the Accept-Language header from each request and stores it in LocaleContext
 * so that service-layer code can localise dynamic content without threading concerns.
 */
@Component
public class LocaleInterceptor implements HandlerInterceptor {

    private static final String DEFAULT_LANG = "zh-CN";

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        String lang = request.getHeader("Accept-Language");
        LocaleContext.set((lang != null && !lang.isBlank()) ? lang.trim() : DEFAULT_LANG);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) {
        LocaleContext.clear();
    }
}
