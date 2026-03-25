package com.nh.stockapi.infrastructure.kis;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "kis")
public class KisProperties {
    private String baseUrl   = "https://openapi.koreainvestment.com:9443";
    private String appKey    = "";
    private String appSecret = "";
    private String accountNo = "";
    private boolean mock     = true;   // true = 모의투자
}
