package com.ecommerce.project;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityAuthorizationIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void sellerAdministrationRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/sellers"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void normalUserCannotAccessSellerAdministration() throws Exception {
        mockMvc.perform(get("/api/admin/sellers").with(user("user").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanListSellers() throws Exception {
        mockMvc.perform(get("/api/admin/sellers").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void csrfTokenEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk());
    }

    @Test
    void unsafePublicRequestStillRequiresCsrfToken() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType("application/json")
                        .content("{\"username\":\"newuser\",\"email\":\"new@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isForbidden());
    }
}
