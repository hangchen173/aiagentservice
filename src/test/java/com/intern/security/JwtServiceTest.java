package com.intern.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {
    @Test
    void createsAndParsesToken() {
        JwtService jwtService = new JwtService("test-secret", 3600);

        String token = jwtService.createToken(7L, "admin", "ADMIN");
        AuthUser user = jwtService.parseToken(token);

        assertThat(user.id()).isEqualTo(7L);
        assertThat(user.username()).isEqualTo("admin");
        assertThat(user.role()).isEqualTo("ADMIN");
    }
}
