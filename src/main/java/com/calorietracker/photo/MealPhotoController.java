package com.calorietracker.photo;

import com.calorietracker.photo.MealPhotoService.PhotoContent;
import com.calorietracker.photo.MealPhotoService.Variant;
import com.calorietracker.photo.dto.MealPhotoResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Meal photos. A day's photos are listed through {@code GET /api/diary/{date}}
 * ({@code meals[].photos}). Errors from these endpoints carry a JSON body
 * {@code {"message": "..."}} the app can show as-is.
 */
@RestController
public class MealPhotoController {

    /**
     * Browsers may reuse a photo response for 5 minutes. For S3 that is the redirect, which stays
     * shorter than the 10-minute life of the presigned URL it points to.
     */
    static final String CACHE_CONTROL = "private, max-age=300";

    private final MealPhotoService service;

    public MealPhotoController(MealPhotoService service) {
        this.service = service;
    }

    /**
     * {@code POST /api/diary/meals/{mealId}/photos}, multipart with JPEG parts {@code full} (at
     * most 3 MB) and {@code thumb} (at most 500 KB), and optional fields {@code width} and
     * {@code height} of the full image → <b>201</b>
     * {@code {"id": 1, "thumbUrl": "/api/photos/1/thumb", "fullUrl": "/api/photos/1/full"}}.
     * 400 if a part is missing; 413 if a part is too large; 415 if a part is not a JPEG;
     * 404 if no such meal; 409 if the meal already has 6 photos; 503 if photo storage is not
     * configured.
     */
    @PostMapping("/api/diary/meals/{mealId}/photos")
    public ResponseEntity<MealPhotoResponse> upload(
            @PathVariable Long mealId,
            @RequestPart(name = "full", required = false) MultipartFile full,
            @RequestPart(name = "thumb", required = false) MultipartFile thumb,
            @RequestParam(required = false) Integer width,
            @RequestParam(required = false) Integer height) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.upload(mealId, full, thumb, width, height));
    }

    /**
     * {@code GET /api/photos/{id}/thumb}: with S3, <b>302</b> to a presigned URL valid for
     * 10 minutes; with local storage, <b>200</b> {@code image/jpeg}. Either way
     * {@code Cache-Control: private, max-age=300}. Works in a plain {@code <img src>}.
     * 404 if no such photo; 503 if photo storage is not configured.
     */
    @GetMapping("/api/photos/{id}/thumb")
    public ResponseEntity<byte[]> thumb(@PathVariable Long id) {
        return serve(id, Variant.THUMB);
    }

    /** {@code GET /api/photos/{id}/full}: as {@link #thumb}, for the full-size image. */
    @GetMapping("/api/photos/{id}/full")
    public ResponseEntity<byte[]> full(@PathVariable Long id) {
        return serve(id, Variant.FULL);
    }

    /**
     * {@code DELETE /api/photos/{id}}: deletes both files, then the photo → <b>204</b>.
     * 404 if no such photo; 503 if photo storage is not configured.
     */
    @DeleteMapping("/api/photos/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<byte[]> serve(Long id, Variant variant) {
        return switch (service.load(id, variant)) {
            case PhotoContent.Redirect redirect -> ResponseEntity.status(HttpStatus.FOUND)
                    .location(redirect.url())
                    .header(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL)
                    .build();
            case PhotoContent.Jpeg jpeg -> ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG)
                    .header(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL)
                    .body(jpeg.bytes());
        };
    }

    /** Errors from the endpoints above: the status, with {@code {"message": reason}}. */
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String, String>> error(ResponseStatusException e) {
        String message = e.getReason() != null ? e.getReason() : e.getStatusCode().toString();
        return ResponseEntity.status(e.getStatusCode())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("message", message));
    }
}
