package com.example.movie_server.web.dto;

import java.util.List;

public record MoviesRsp(List<MovieDto> movies, long totalElements, int totalPages) {
}
