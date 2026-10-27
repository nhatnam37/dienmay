package com.dienmay.config;

import com.dienmay.common.pagination.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

@Configuration @EnableConfigurationProperties(QueryInputProperties.class)
public class QueryInputWebConfig implements WebMvcConfigurer {
    private final QueryInputProperties properties;
    public QueryInputWebConfig(QueryInputProperties properties){this.properties=properties;}
    @Override public void addInterceptors(InterceptorRegistry registry){registry.addInterceptor(new QueryInputInterceptor(properties));}
}
