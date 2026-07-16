package com.intern;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "nexusmind.bootstrap.admin.username=test_admin",
        "nexusmind.bootstrap.admin.password=test-admin-password",
        "nexusmind.bootstrap.agent.username=test_agent",
        "nexusmind.bootstrap.agent.password=test-agent-password"
})
class InternApplicationTests {

    @Test
    void contextLoads() {
    }

}
