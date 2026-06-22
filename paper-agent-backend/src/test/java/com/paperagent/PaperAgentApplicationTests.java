package com.paperagent;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.sql.init.mode=never",
    "spring.ai.openai.api-key=test-key-for-context-load"
})
class PaperAgentApplicationTests {

    @Test
    void contextLoads() {
        // Verify Spring context loads with all beans wired correctly
    }
}
