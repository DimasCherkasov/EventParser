package com.eventparser.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Конфигурация для создания бина RestTemplate.
 * Этот бин используется для выполнения HTTP-запросов к внешним API.
 */
@Configuration
public class RestTemplateConfig {

    /**
     * Создает и настраивает бин RestTemplate.
     *
     * @return Настроенный экземпляр RestTemplate
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}