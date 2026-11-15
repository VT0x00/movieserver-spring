package com.example.movie_server.web;

public class MovieNotFoundException extends RuntimeException {
    public MovieNotFoundException(Long id) {
        super("movie not found: " + id.toString());
    }
}
