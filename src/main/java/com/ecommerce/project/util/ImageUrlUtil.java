package com.ecommerce.project.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ImageUrlUtil {
    @Value("${image.base.url}")
    private String imageBaseUrl;

    public String constructImageUrl(String imageName) {
        if (imageName == null || imageName.isBlank()) {
            return imageName;
        }
        // Absolute URLs (Unsplash / Cloudinary / etc.) — do not prepend base URL
        if (imageName.startsWith("http://") || imageName.startsWith("https://")) {
            return imageName;
        }
        return imageBaseUrl.endsWith("/")
                ? imageBaseUrl + imageName
                : imageBaseUrl + "/" + imageName;
    }
}
