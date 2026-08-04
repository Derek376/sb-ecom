package com.ecommerce.project;

import com.ecommerce.project.model.AppRole;
import com.ecommerce.project.model.Role;
import com.ecommerce.project.model.User;
import com.ecommerce.project.repositories.RoleRepository;
import com.ecommerce.project.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SecurityAuthorizationIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

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
    void sellerCannotAccessSellerAdministration() throws Exception {
        mockMvc.perform(get("/api/admin/sellers").with(user("seller").roles("SELLER")))
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

    @Test
    void normalUserCannotAccessSellerProductEndpoints() throws Exception {
        mockMvc.perform(get("/api/seller/products").with(user("user").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void sellerCanAccessSellerProductEndpoints() throws Exception {
        Role userRole = roleRepository.findByRoleName(AppRole.ROLE_USER).orElseThrow();
        Role sellerRole = roleRepository.findByRoleName(AppRole.ROLE_SELLER).orElseThrow();
        User seller = new User("catalog-seller", "seller@example.com", "encoded-password");
        seller.setRoles(java.util.Set.of(userRole, sellerRole));
        userRepository.save(seller);

        mockMvc.perform(get("/api/seller/products")
                        .with(user("catalog-seller").roles("SELLER")))
                .andExpect(status().isOk());
    }

    @Test
    void adminSellerCreationRequiresCsrfToken() throws Exception {
        mockMvc.perform(post("/api/admin/sellers")
                        .with(user("admin").roles("ADMIN"))
                        .contentType("application/json")
                        .content(validSellerJson("no-csrf-seller")))
                .andExpect(status().isForbidden());

        assertThat(userRepository.existsByUserName("no-csrf-seller")).isFalse();
    }

    @Test
    void sellerCannotCreateAnotherSeller() throws Exception {
        mockMvc.perform(post("/api/admin/sellers")
                        .with(user("seller").roles("SELLER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content(validSellerJson("unauthorized-seller")))
                .andExpect(status().isForbidden());

        assertThat(userRepository.existsByUserName("unauthorized-seller")).isFalse();
    }

    @Test
    void adminCanCreateSellerWithOnlyUserAndSellerRoles() throws Exception {
        mockMvc.perform(post("/api/admin/sellers")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType("application/json")
                        .content(validSellerJson("created-seller")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userName").value("created-seller"))
                .andExpect(jsonPath("$.email").value("created-seller@example.com"));

        User savedSeller = userRepository.findByUserName("created-seller").orElseThrow();
        assertThat(savedSeller.getRoles())
                .extracting(Role::getRoleName)
                .containsExactlyInAnyOrder(AppRole.ROLE_USER, AppRole.ROLE_SELLER)
                .doesNotContain(AppRole.ROLE_ADMIN);
        assertThat(savedSeller.getPassword()).isNotEqualTo("password123");
    }

    @Test
    void invalidSellerPayloadIsRejectedBeforePersistence() throws Exception {
        mockMvc.perform(post("/api/admin/sellers")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"username\":\"ab\",\"email\":\"invalid\",\"password\":\"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.username").exists())
                .andExpect(jsonPath("$.email").exists())
                .andExpect(jsonPath("$.password").exists());

        assertThat(userRepository.existsByUserName("ab")).isFalse();
    }

    private String validSellerJson(String username) {
        return "{\"username\":\"" + username
                + "\",\"email\":\"" + username
                + "@example.com\",\"password\":\"password123\"}";
    }
}
