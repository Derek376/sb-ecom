package com.ecommerce.project.service;

import com.ecommerce.project.model.Category;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.model.User;
import com.ecommerce.project.payload.ProductDTO;
import com.ecommerce.project.repositories.CategoryRepository;
import com.ecommerce.project.repositories.ProductRepository;
import com.ecommerce.project.util.AuthUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock ImageStorageService imageStorageService;
    @Mock CategoryRepository categoryRepository;
    @Mock ProductRepository productRepository;
    @Mock ModelMapper modelMapper;
    @Mock AuthUtil authUtil;

    @Test
    void calculatesAndRoundsTheDiscountedPriceWithBigDecimal() {
        ProductServiceImpl productService = new ProductServiceImpl(imageStorageService);
        ReflectionTestUtils.setField(productService, "categoryRepository", categoryRepository);
        ReflectionTestUtils.setField(productService, "productRepository", productRepository);
        ReflectionTestUtils.setField(productService, "modelMapper", modelMapper);
        ReflectionTestUtils.setField(productService, "authUtil", authUtil);

        Category category = new Category();
        category.setCategoryId(1L);
        category.setProducts(new ArrayList<>());

        ProductDTO request = new ProductDTO();
        request.setProductName("Coffee Grinder");
        request.setPrice(new BigDecimal("19.99"));
        request.setDiscount(new BigDecimal("15.00"));

        Product mappedProduct = new Product();
        mappedProduct.setProductName(request.getProductName());
        mappedProduct.setPrice(request.getPrice());
        mappedProduct.setDiscount(request.getDiscount());

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(modelMapper.map(request, Product.class)).thenReturn(mappedProduct);
        when(authUtil.loggedInUser()).thenReturn(new User());
        when(productRepository.save(any(Product.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(modelMapper.map(any(Product.class), org.mockito.ArgumentMatchers.eq(ProductDTO.class)))
                .thenReturn(new ProductDTO());

        productService.addProduct(1L, request);

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        org.mockito.Mockito.verify(productRepository).save(productCaptor.capture());
        assertThat(productCaptor.getValue().getSpecialPrice())
                .isEqualByComparingTo("16.99");
    }
}
