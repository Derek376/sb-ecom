package com.ecommerce.project.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface ImageStorageService {

    String uploadProductImage(Long productId, MultipartFile file)
            throws IOException;

    void deleteProductImage(Long productId) throws IOException;
}