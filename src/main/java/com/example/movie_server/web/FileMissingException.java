package com.example.movie_server.web;

public class FileMissingException extends RuntimeException {
    public FileMissingException(Long id) {
        super("file missed: " + id.toString());
    }
}
