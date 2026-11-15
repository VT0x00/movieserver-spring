package com.example.movie_server.web.dto;

import com.example.movie_server.domain.Movie;

public record MovieDto(String title, Integer year, String genre,
                        Integer durationSeconds, String fileName, String filePath,
                        Long fileSize, String createdAt) {

    public static MovieDto from(Movie movie) {
        return new MovieDto(movie.getTitle(), movie.getYear(), movie.getGenre(),
                            movie.getDurationSeconds(), movie.getFileName(), movie.getFilePath(),
                            movie.getFileSize(), movie.getCreatedAt());
    }
}
