package com.ecommerce.project.controller;

import com.ecommerce.project.config.AppConstant;
import com.ecommerce.project.payload.ProductDTO;
import com.ecommerce.project.payload.ProductResponse;
import com.ecommerce.project.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api")
public class ProductController {
    @Autowired
    private ProductService productService;

    @PostMapping("/admin/categories/{categoryId}/product")
    public ResponseEntity<ProductDTO> addProduct(@PathVariable Long categoryId,
                                                 @Valid @RequestBody ProductDTO productDTO){
        ProductDTO savedProductDTO=productService.addProduct(categoryId,productDTO);
        return new ResponseEntity<>(savedProductDTO, HttpStatus.CREATED);
    }

    @GetMapping("/public/products")
    public ResponseEntity<ProductResponse> getAllProducts(
            @RequestParam(name="pageNumber",defaultValue = AppConstant.PAGE_NUMBER,required = false) Integer pageNumber,
            @RequestParam(name="pageSize",defaultValue = AppConstant.PAGE_SIZE,required = false) Integer pageSize,
            @RequestParam(name="sortBy",defaultValue = AppConstant.SORT_PRODUCTS_BY,required = false) String sortBy,
            @RequestParam(name="sortOrder",defaultValue = AppConstant.SORT_DIR,required = false) String sortOrder
    ){
        ProductResponse productResponse=productService.getAllProducts(pageNumber,pageSize,sortBy,sortOrder);
        return new ResponseEntity<>(productResponse,HttpStatus.OK);
    }

    @GetMapping("/public/categories/{categoryId}/products")
    public ResponseEntity<ProductResponse> getProductsByCategory(
            @PathVariable Long categoryId,
            @RequestParam(name="pageNumber",defaultValue = AppConstant.PAGE_NUMBER,required = false) Integer pageNumber,
            @RequestParam(name="pageSize",defaultValue = AppConstant.PAGE_SIZE,required = false) Integer pageSize,
            @RequestParam(name="sortBy",defaultValue = AppConstant.SORT_PRODUCTS_BY,required = false) String sortBy,
            @RequestParam(name="sortOrder",defaultValue = AppConstant.SORT_DIR,required = false) String sortOrder
    ){
        ProductResponse productResponse=productService.getProductsByCategory(categoryId,pageNumber,pageSize,sortBy,sortOrder);
        return new ResponseEntity<>(productResponse,HttpStatus.OK);
    }

    @GetMapping("/public/products/keyword/{keyword}")
    public ResponseEntity<ProductResponse> getProductsByKeyword(
            @PathVariable String keyword,
            @RequestParam(name="pageNumber",defaultValue = AppConstant.PAGE_NUMBER,required = false) Integer pageNumber,
            @RequestParam(name="pageSize",defaultValue = AppConstant.PAGE_SIZE,required = false) Integer pageSize,
            @RequestParam(name="sortBy",defaultValue = AppConstant.SORT_PRODUCTS_BY,required = false) String sortBy,
            @RequestParam(name="sortOrder",defaultValue = AppConstant.SORT_DIR,required = false) String sortOrder
    ){
        ProductResponse productResponse=productService.getProductsByKeyword(keyword,pageNumber,pageSize,sortBy,sortOrder);
        return new ResponseEntity<>(productResponse,HttpStatus.FOUND);
    }

    @PutMapping("/admin/products/{productId}")
    public ResponseEntity<ProductDTO> updateProduct(@PathVariable Long productId,
                                                    @Valid @RequestBody ProductDTO productDTO){
        ProductDTO updatedproductDTO=productService.updateProduct(productId,productDTO);
        return new ResponseEntity<>(updatedproductDTO, HttpStatus.OK);
    }

    @DeleteMapping("/admin/products/{productId}")
    public ResponseEntity<ProductDTO> deleteProduct(@PathVariable Long productId){
        ProductDTO deletedproductDTO=productService.deleteProduct(productId);
        return new ResponseEntity<>(deletedproductDTO,HttpStatus.OK);
    }

    @PutMapping("/products/{productId}/image")
    public ResponseEntity<ProductDTO> UpdateProductImage(@PathVariable Long productId,
                                                         @RequestParam("image")MultipartFile image) throws IOException {
        ProductDTO updatedproductDTO=productService.UpdateProductImage(productId,image);
        return new ResponseEntity<>(updatedproductDTO,HttpStatus.OK);
    }
}
