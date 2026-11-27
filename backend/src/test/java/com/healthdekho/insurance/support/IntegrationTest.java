package com.healthdekho.insurance.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Base for API tests: full Spring context, real PostgreSQL (healthdekho_test, seeded by data.sql and
 * demo-data.sql), fixed clock at 2026-11-25, and every test rolled back so tests never see each other's writes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestClockConfig.class)
@Transactional
public abstract class IntegrationTest {

    public static final String AARAV = "aarav.sharma@example.com";   // owns HDP-2026-0001 (TERM)
    public static final String MEERA = "meera.iyer@example.com";     // owns HDP-2026-0002, HDP-2026-0003 (overdue)
    public static final String ROHAN = "rohan.verma@example.com";    // no policies
    public static final String OPS = "ops.desk@example.com";         // staff
    public static final String CUSTOMER_PASSWORD = "Demo@1234";
    public static final String STAFF_PASSWORD = "Ops@1234";

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    /** Returns an "Authorization" header value for the given seeded user. */
    protected String bearer(String email) throws Exception {
        String password = OPS.equals(email) ? STAFF_PASSWORD : CUSTOMER_PASSWORD;
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new Credentials(email, password))))
                .andReturn().getResponse().getContentAsString();
        JsonNode node = json.readTree(body);
        return "Bearer " + node.get("token").asText();
    }

    private record Credentials(String email, String password) {
    }
}
