package com.ecommerce.project.util;

import com.ecommerce.project.exceptions.APIexception;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SortUtilTest {

    private static final Set<String> ALLOWED_FIELDS = Set.of("productId", "price");

    @Test
    void addsIdAsTieBreakerForNonIdSorts() {
        Sort sort = SortUtil.build("price", "desc", ALLOWED_FIELDS, "productId");

        assertEquals(Sort.Direction.DESC, sort.getOrderFor("price").getDirection());
        assertEquals(Sort.Direction.ASC, sort.getOrderFor("productId").getDirection());
    }

    @Test
    void doesNotDuplicateTheIdSort() {
        Sort sort = SortUtil.build("productId", "asc", ALLOWED_FIELDS, "productId");

        assertEquals(1, sort.stream().count());
    }

    @Test
    void rejectsUnknownFieldsAndDirections() {
        assertThrows(
                APIexception.class,
                () -> SortUtil.build("password", "asc", ALLOWED_FIELDS, "productId")
        );
        assertThrows(
                APIexception.class,
                () -> SortUtil.build("price", "sideways", ALLOWED_FIELDS, "productId")
        );
    }
}
