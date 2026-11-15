package com.example.movie_server.web.dto;

public record StatusRsp(String version, long moviesCount, long storageBytes) {
}
