package com.ecommerce.project;

import com.ecommerce.project.model.*;
import com.ecommerce.project.repositories.*;
import com.ecommerce.project.service.DemoService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.demo.enabled=true")
@AutoConfigureMockMvc
@Transactional
class DemoIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired DemoService demo;
    @Autowired UserRepository users;
    @Autowired ProductRepository products;
    @Autowired OrderRepository orders;
    @Autowired RoleRepository roles;

    private Cookie login(String role) throws Exception {
        return mvc.perform(post("/api/auth/demo-login").with(csrf())
                        .contentType("application/json").content("{\"role\":\"" + role + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.demo").value(true))
                .andExpect(cookie().httpOnly("springBootEcom", true))
                .andReturn().getResponse().getCookie("springBootEcom");
    }

    @ParameterizedTest
    @CsvSource({"admin,/api/admin/app/analytics", "seller,/api/seller/products", "customer,/api/users/orders"})
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void demoCookieAuthenticatesRealRole(String role, String path) throws Exception {
        Cookie cookie = login(role);
        mvc.perform(get(path).cookie(cookie)).andExpect(status().isOk());
        mvc.perform(get("/api/auth/user").cookie(cookie))
                .andExpect(jsonPath("$.username").value("demo-" + role))
                .andExpect(jsonPath("$.demo").value(true));
        if (!role.equals("admin")) {
            mvc.perform(get("/api/admin/app/analytics").cookie(cookie)).andExpect(status().isForbidden());
        }
    }

    @ParameterizedTest
    @CsvSource({"POST,/api/admin/categories", "PUT,/api/admin/products/1", "DELETE,/api/admin/products/1",
            "POST,/api/admin/sellers", "PUT,/api/seller/orders/1/status", "POST,/api/addresses",
            "POST,/api/carts/create", "POST,/api/order/stripe-client-secret", "POST,/api/orders",
            "POST,/api/auth/signup"})
    void demoAdminCannotMutateEvenWithCsrf(String method, String path) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), path).cookie(login("admin")).with(csrf())
                        .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DEMO_READ_ONLY"));
    }

    @Test
    void roleSwitchAndLogoutAreStillAllowed() throws Exception {
        Cookie admin = login("admin");
        Cookie customer = mvc.perform(post("/api/auth/demo-login").cookie(admin).with(csrf())
                        .contentType("application/json").content("{\"role\":\"customer\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("springBootEcom");
        mvc.perform(get("/api/admin/orders").cookie(customer)).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/signout").cookie(customer).with(csrf()))
                .andExpect(status().isOk()).andExpect(cookie().maxAge("springBootEcom", 0));
    }

    @Test
    void demoLoginRequiresCsrfAndWhitelistedRole() throws Exception {
        mvc.perform(post("/api/auth/demo-login").contentType("application/json")
                .content("{\"role\":\"admin\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/demo-login").with(csrf()).contentType("application/json")
                .content("{\"role\":\"ROLE_ADMIN\",\"username\":\"arbitrary\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void initializationIsRepeatableAndSeededPanelsHaveContent() throws Exception {
        long userCount = users.count(), productCount = products.count(), orderCount = orders.count();
        demo.initialize();
        assertThat(users.count()).isEqualTo(userCount);
        assertThat(products.count()).isEqualTo(productCount);
        assertThat(orders.count()).isEqualTo(orderCount);
        mvc.perform(get("/api/users/orders").cookie(login("customer")))
                .andExpect(jsonPath("$.length()").value(8));
        mvc.perform(get("/api/seller/products").cookie(login("seller")))
                .andExpect(jsonPath("$.totalElements").value(8));
        mvc.perform(get("/images/demo/headphones.svg")).andExpect(status().isOk());
    }

    @Test
    void sellerDoesNotSeeAnotherSellersProducts() throws Exception {
        User other = new User("other-seller", "other@example.com", "not-a-password");
        other.setRoles(Set.of(roles.findByRoleName(AppRole.ROLE_SELLER).orElseThrow()));
        other = users.save(other);
        Product product = new Product(); product.setProductName("Other seller item");
        product.setDescription("Unrelated seller product"); product.setUser(other); product.setQuantity(5);
        product.setPrice(java.math.BigDecimal.TEN); product.setDiscount(java.math.BigDecimal.ZERO);
        product.setSpecialPrice(java.math.BigDecimal.TEN); products.save(product);
        mvc.perform(get("/api/seller/products").cookie(login("seller")))
                .andExpect(jsonPath("$.totalElements").value(8));
    }

    @Test
    void normalAdminRetainsWriteAccess() throws Exception {
        mvc.perform(post("/api/admin/categories").with(user("real-admin").roles("ADMIN"))
                        .with(csrf()).contentType("application/json").content("{\"categoryName\":\"Normal category\"}"))
                .andExpect(status().isCreated());
    }
}
