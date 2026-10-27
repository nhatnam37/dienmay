package com.dienmay.common.pagination;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated @ConfigurationProperties("app.web.query")
public class QueryInputProperties {
    @Min(0) private int maxPage;
    @Min(1) private int maxSize;
    @Min(1) private int maxKeywordLength;
    public int getMaxPage(){return maxPage;} public void setMaxPage(int value){maxPage=value;}
    public int getMaxSize(){return maxSize;} public void setMaxSize(int value){maxSize=value;}
    public int getMaxKeywordLength(){return maxKeywordLength;} public void setMaxKeywordLength(int value){maxKeywordLength=value;}
}
