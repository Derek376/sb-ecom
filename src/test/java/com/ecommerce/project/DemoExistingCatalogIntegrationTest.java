package com.ecommerce.project;

import com.ecommerce.project.model.Product;
import com.ecommerce.project.repositories.ProductRepository;
import com.ecommerce.project.service.DemoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "app.demo.enabled=true",
        "spring.datasource.url=jdbc:h2:mem:demo-existing-catalog;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
})
@Import(DemoExistingCatalogIntegrationTest.ExistingCatalog.class)
class DemoExistingCatalogIntegrationTest {
    @Autowired ProductRepository products;
    @Autowired DemoService demo;

    @TestConfiguration
    static class ExistingCatalog {
        @Bean
        @Order(Ordered.HIGHEST_PRECEDENCE)
        CommandLineRunner importExistingProducts(JdbcTemplate jdbc) {
            return args -> {
                // Imported IDs do not advance Hibernate's sequence, as on the deployed database.
                for (long id : new long[]{2, 102}) {
                    jdbc.update("INSERT INTO products (product_id, product_name, description, quantity, price, discount, special_price) "
                                    + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                            id, "Existing product " + id, "Existing catalog entry", 10,
                            new BigDecimal("25.00"), BigDecimal.ZERO, new BigDecimal("25.00"));
                }
            };
        }
    }

    @Test
    void startupPreservesExistingCatalogAndAdvancesGeneratedIds() {
        assertThat(products.findById(2L).orElseThrow().getProductName()).isEqualTo("Existing product 2");
        assertThat(products.findById(102L).orElseThrow().getProductName()).isEqualTo("Existing product 102");
        assertThat(products.count()).isEqualTo(10);
        assertThat(products.findAll().stream()
                .filter(product -> product.getProductName().startsWith("Existing product")).count()).isEqualTo(2);

        // Repeat initialization and then create a normal product to check the shared generator too.
        demo.initialize();
        assertThat(products.count()).isEqualTo(10);
        Product product = new Product();
        product.setProductName("New normal product");
        product.setDescription("Created after demo initialization");
        product.setQuantity(5);
        product.setPrice(BigDecimal.TEN);
        product.setDiscount(BigDecimal.ZERO);
        product.setSpecialPrice(BigDecimal.TEN);
        assertThat(products.saveAndFlush(product).getProductId()).isGreaterThan(102L);
    }
}
