package com.paperagent.config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiTimeoutConfigurationTest {

    @ParameterizedTest
    @ValueSource(strings = {"application-dev.yml", "application-dev.example.yml"})
    void devOpenAiTimeoutAllowsLongReviewStreams(String resourceName) {
        Properties properties = loadYaml(resourceName);

        assertThat(properties.getProperty("spring.ai.openai.timeout")).isEqualTo("5m");
    }

    private Properties loadYaml(String resourceName) {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource(resourceName));
        Properties properties = factory.getObject();
        assertThat(properties).isNotNull();
        return properties;
    }
}
