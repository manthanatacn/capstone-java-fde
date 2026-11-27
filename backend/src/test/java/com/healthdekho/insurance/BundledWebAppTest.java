package com.healthdekho.insurance;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.healthdekho.insurance.support.IntegrationTest;

/** The web app packaged in the jar (a stand-in index.html lives in src/test/resources/static). */
class BundledWebAppTest extends IntegrationTest {

    @Test
    void rootAndClientSideRoutesServeTheAppWithoutAToken() throws Exception {
        // "/" goes through Spring Boot's welcome page (a forward to index.html), so only its status is checked here
        mvc.perform(get("/")).andExpect(status().isOk());
        for (String path : new String[] {"/dashboard", "/payments", "/register/HD-TERM-001"}) {
            mvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("bundled app")));
        }
    }

    @Test
    void missingFilesAreNotAnsweredWithThePage() throws Exception {
        mvc.perform(get("/assets/missing.js")).andExpect(status().isNotFound());
    }

    @Test
    void apiPathsStillNeedATokenAndNeverFallBackToThePage() throws Exception {
        mvc.perform(get("/api/no-such-endpoint")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/policies/HDP-2026-0001").header("Authorization", bearer(AARAV)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/no-such-endpoint").header("Authorization", bearer(AARAV)))
                .andExpect(status().isNotFound());
    }

    @Test
    void onlyReadsArePublicOutsideTheApi() throws Exception {
        mvc.perform(post("/dashboard")).andExpect(status().isUnauthorized());
    }
}
