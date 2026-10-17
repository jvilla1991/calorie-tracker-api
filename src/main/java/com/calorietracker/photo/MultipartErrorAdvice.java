package com.calorietracker.photo;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

/**
 * Multipart requests that fail while being parsed, answered with {@code {"message": "..."}} like
 * the photo endpoints' other errors:
 * <ul>
 *   <li>over the container caps ({@code spring.servlet.multipart.max-file-size=4MB},
 *       {@code max-request-size=8MB}) → 413;</li>
 *   <li>otherwise malformed (e.g. no boundary) → 400, instead of a 500.</li>
 * </ul>
 * Global on purpose: the body is parsed before a controller is chosen, so a handler on
 * {@link MealPhotoController} would never see these. Only multipart requests raise them, and the
 * photo upload is the only multipart endpoint.
 * <p>
 * The body is sent as a plain string so it goes out with a {@code Content-Length}. Tomcat may
 * close the connection right after a rejected upload without reading the rest of it; a
 * length-delimited response is still complete for the client then, while a chunked one would be
 * cut off before its final chunk.
 */
@RestControllerAdvice
public class MultipartErrorAdvice {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<String> tooLarge(MaxUploadSizeExceededException e) {
        return message(HttpStatus.CONTENT_TOO_LARGE,
                "Upload too large: 'full' must be at most 3 MB and 'thumb' at most 500 KB");
    }

    @ExceptionHandler(MultipartException.class)
    ResponseEntity<String> malformed(MultipartException e) {
        return message(HttpStatus.BAD_REQUEST, "Malformed multipart request");
    }

    /** {@code {"message": "<text>"}}; the texts above contain no characters that need escaping. */
    private static ResponseEntity<String> message(HttpStatus status, String text) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"message\":\"" + text + "\"}");
    }
}
