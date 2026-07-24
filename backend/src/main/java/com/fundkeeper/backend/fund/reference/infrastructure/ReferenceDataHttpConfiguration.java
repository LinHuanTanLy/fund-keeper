package com.fundkeeper.backend.fund.reference.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class ReferenceDataHttpConfiguration {

    @Bean
    @ConditionalOnMissingBean(RestClient.Builder.class)
    RestClient.Builder fundReferenceRestClientBuilder() {
        return RestClient.builder();
    }
}
