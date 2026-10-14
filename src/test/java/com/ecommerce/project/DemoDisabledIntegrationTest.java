package com.ecommerce.project;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.demo.enabled=false")
@AutoConfigureMockMvc
class DemoDisabledIntegrationTest {
    @Autowired MockMvc mvc;

    @Test
    void disabledDemoReportsUnavailableAndDoesNotIssueCookie() throws Exception {
        mvc.perform(get("/api/auth/demo")).andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
        mvc.perform(post("/api/auth/demo-login").with(csrf()).contentType("application/json")
                        .content("{\"role\":\"admin\"}"))
                .andExpect(status().isNotFound()).andExpect(cookie().doesNotExist("springBootEcom"));
    }
}
