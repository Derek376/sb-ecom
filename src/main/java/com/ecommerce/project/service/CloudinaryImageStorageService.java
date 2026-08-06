package com.ecommerce.project.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.ecommerce.project.exceptions.APIexception;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

@Service
public class CloudinaryImageStorageService implements ImageStorageService {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private final Cloudinary cloudinary;

    public CloudinaryImageStorageService(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    @Override
    public String uploadProductImage(Long productId, MultipartFile file)
            throws IOException {

        validate(file);

        Map<?, ?> result = cloudinary.uploader().upload(
                file.getBytes(),
                ObjectUtils.asMap(
                        "resource_type", "image",
                        "folder", "e-shop/products",
                        "public_id", "product-" + productId,
                        "overwrite", true,
                        "invalidate", true
                )
        );

        return result.get("secure_url").toString();
    }

    @Override
    public void deleteProductImage(Long productId) throws IOException {
        cloudinary.uploader().destroy(
                "e-shop/products/product-" + productId,
                ObjectUtils.asMap(
                        "resource_type", "image",
                        "invalidate", true
                )
        );
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new APIexception("Please select an image");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new APIexception("Image must be smaller than 5 MB");
        }

        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            throw new APIexception(
                    "Only JPEG, PNG and WebP images are supported"
            );
        }
    }
}