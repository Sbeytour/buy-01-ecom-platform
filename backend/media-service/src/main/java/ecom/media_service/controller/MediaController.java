package ecom.media_service.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import ecom.media_service.dto.response.ProductImagesResponse;
import ecom.media_service.dto.response.ProfileImageResponse;
import ecom.media_service.service.MediaService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;

    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductImagesResponse> uploadProductImages(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam("productId") String productId,
            @RequestPart("files") MultipartFile[] files) {

        ProductImagesResponse response = mediaService.uploadProductImages(jwt.getSubject(), productId, files);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/images/{productId}")
    public ResponseEntity<ProductImagesResponse> getProductImages(@PathVariable String productId) {
        return ResponseEntity.ok(mediaService.getProductImages(productId));
    }

    @PostMapping(value = "/profile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProfileImageResponse> uploadProfileImage(
            @AuthenticationPrincipal Jwt jwt,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(mediaService.uploadProfileImage(jwt.getSubject(), file));
    }

    @GetMapping("/profile/{userId}")
    public ResponseEntity<ProfileImageResponse> getProfileImage(@PathVariable String userId) {
        return ResponseEntity.ok(mediaService.getProfileImage(userId));
    }
}
