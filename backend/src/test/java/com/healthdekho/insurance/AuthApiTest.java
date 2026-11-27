package com.healthdekho.insurance;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.healthdekho.insurance.support.IntegrationTest;

class AuthApiTest extends IntegrationTest {

    @Test
    void customerCanLogInAndReadOwnProfile() throws Exception {
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(AARAV)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Aarav Sharma"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.dateOfBirth").value("1990-04-15"));
    }

    @Test
    void staffLoginCarriesStaffRole() throws Exception {
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(OPS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("STAFF"));
    }

    @Test
    void wrongPasswordIsRejectedWithoutSayingWhichPartWasWrong() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"aarav.sharma@example.com\",\"password\":\"nope\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password."));
    }

    @Test
    void unknownEmailGetsTheSameMessage() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\",\"password\":\"Demo@1234\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password."));
    }

    @Test
    void protectedEndpointsNeedAToken() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void staffCannotUseCustomerOnlyEndpoints() throws Exception {
        mvc.perform(get("/api/customers/me/policies").header("Authorization", bearer(OPS)))
                .andExpect(status().isForbidden());
    }
}
