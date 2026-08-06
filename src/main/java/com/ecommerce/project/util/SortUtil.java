package com.ecommerce.project.util;

import com.ecommerce.project.exceptions.APIexception;
import org.springframework.data.domain.Sort;

import java.util.Set;

public final class SortUtil {

    private SortUtil() {
    }

    public static Sort build(String sortBy,
                             String sortOrder,
                             Set<String> allowedFields,
                             String idField) {
        if (!allowedFields.contains(sortBy)) {
            throw new APIexception("Unsupported sort field: " + sortBy);
        }

        Sort.Direction direction;
        try {
            direction = Sort.Direction.fromString(sortOrder);
        } catch (IllegalArgumentException exception) {
            throw new APIexception("Sort order must be 'asc' or 'desc'");
        }

        Sort sort = Sort.by(direction, sortBy);
        if (!sortBy.equals(idField)) {
            sort = sort.and(Sort.by(Sort.Direction.ASC, idField));
        }
        return sort;
    }
}
