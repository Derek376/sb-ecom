package com.ecommerce.project.controller;

import com.ecommerce.project.config.AppConstant;
import com.ecommerce.project.payload.SellerDTO;
import com.ecommerce.project.payload.UserResponse;
import com.ecommerce.project.security.request.SignupRequest;
import com.ecommerce.project.service.AuthService;
import com.ecommerce.project.util.SortUtil;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/admin/sellers")
public class AdminSellerController {
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "userId", "userName", "email"
    );

    private final AuthService authService;

    public AdminSellerController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping
    public ResponseEntity<UserResponse> getSellers(
            @RequestParam(name = "pageNumber", defaultValue = AppConstant.PAGE_NUMBER) Integer pageNumber,
            @RequestParam(name = "pageSize", defaultValue = AppConstant.PAGE_SIZE) Integer pageSize,
            @RequestParam(name = "sortBy", defaultValue = AppConstant.SORT_USERS_BY) String sortBy,
            @RequestParam(name = "sortOrder", defaultValue = AppConstant.SORT_USERS_DIR) String sortOrder
    ) {
        Sort sort = SortUtil.build(sortBy, sortOrder, ALLOWED_SORT_FIELDS, "userId");
        Pageable page = PageRequest.of(pageNumber, pageSize, sort);
        return ResponseEntity.ok(authService.getAllSellers(page));
    }

    @PostMapping
    public ResponseEntity<SellerDTO> createSeller(@Valid @RequestBody SignupRequest request) {
        return new ResponseEntity<>(authService.registerSeller(request), HttpStatus.CREATED);
    }
}
