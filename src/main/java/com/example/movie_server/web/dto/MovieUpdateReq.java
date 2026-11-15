package com.example.movie_server.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record MovieUpdateReq(
        @Size (min = 1, max = 255) String title,
        @Min (1900) @Max (2030) Integer year,
        @Size(max = 100) String genre,
        @Min(1) Integer durationSeconds) {
}
