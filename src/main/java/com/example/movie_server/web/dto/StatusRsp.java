package com.example.movie_server.web.dto;

/** Health-check: GET /api/status */
public record StatusRsp(String version, long moviesCount, long storageBytes) {
}
