package com.calorietracker.photo.dto;

/**
 * A meal photo as the app sees it:
 * {@code {"id": 1, "thumbUrl": "/api/photos/1/thumb", "fullUrl": "/api/photos/1/full"}}.
 * The URLs are API paths (relative to the API's base URL) that work in a plain {@code <img src>}.
 */
public record MealPhotoResponse(Long id, String thumbUrl, String fullUrl) {

    public static MealPhotoResponse of(Long id) {
        return new MealPhotoResponse(id, "/api/photos/" + id + "/thumb", "/api/photos/" + id + "/full");
    }
}
