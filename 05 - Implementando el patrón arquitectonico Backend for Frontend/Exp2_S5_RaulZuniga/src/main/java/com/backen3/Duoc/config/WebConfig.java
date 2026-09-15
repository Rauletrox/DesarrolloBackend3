package com.backen3.Duoc.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final ChannelAuthInterceptor channelAuthInterceptor;

    public WebConfig(ChannelAuthInterceptor channelAuthInterceptor) {
        this.channelAuthInterceptor = channelAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(channelAuthInterceptor)
                .addPathPatterns("/api/bff/**");
    }
}
