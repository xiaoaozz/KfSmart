package com.smart.kf.config;

import com.fasterxml.jackson.core.json.JsonWriteFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Web配置类
 * 确保HTTP响应中的中文字符正确显示
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private LoggingInterceptor loggingInterceptor;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 配置静态资源处理（仅匹配静态资源路径，不拦截API请求）
        registry.addResourceHandler("/static/**")
                .addResourceLocations("classpath:/static/");
        registry.addResourceHandler("/avatars/**")
                .addResourceLocations("file:data/avatars/");
        // 注意：不添加 /** catch-all handler，避免将未匹配的API请求误当静态资源处理
        // Spring Boot 默认的 ResourceHttpRequestHandler 会自动处理静态资源
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册日志拦截器，排除静态资源
        registry.addInterceptor(loggingInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/static/**", "/css/**", "/js/**", "/images/**", "/*.ico", "/*.html");
    }

    /**
     * 配置HTTP消息转换器，确保中文字符正确编码
     */
    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        // 配置字符串转换器，使用UTF-8编码
        StringHttpMessageConverter stringConverter = new StringHttpMessageConverter(StandardCharsets.UTF_8);
        stringConverter.setWriteAcceptCharset(false); // 避免在响应头中添加charset参数
        converters.add(stringConverter);
        
        // 配置JSON转换器
        MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter();
        ObjectMapper objectMapper = jsonConverter.getObjectMapper();
        
        // 确保中文字符不被转义为Unicode编码
        objectMapper.getFactory().configure(
            JsonWriteFeature.ESCAPE_NON_ASCII.mappedFeature(), false
        );
        
        jsonConverter.setObjectMapper(objectMapper);
        converters.add(jsonConverter);
    }
} 
