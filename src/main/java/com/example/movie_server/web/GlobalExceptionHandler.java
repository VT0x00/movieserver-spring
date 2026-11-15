package com.example.movie_server.web;

import com.example.movie_server.web.dto.ErrorRsp;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(MovieNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorRsp handleNotFound(MovieNotFoundException e) {
        return new ErrorRsp(e.getMessage());
    }

    @ExceptionHandler(FileMissingException.class)
    @ResponseStatus(HttpStatus.GONE)
    public ErrorRsp handleFileMissing(FileMissingException e) {
        return new ErrorRsp(e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorRsp handleBadRequest(IllegalArgumentException e) {
        return new ErrorRsp(e.getMessage());
    }
}
