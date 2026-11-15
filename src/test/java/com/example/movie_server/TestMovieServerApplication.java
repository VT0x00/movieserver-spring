package com.example.movie_server;

import org.springframework.boot.SpringApplication;

public class TestMovieServerApplication {

	public static void main(String[] args) {
		SpringApplication.from(MovieServerApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
