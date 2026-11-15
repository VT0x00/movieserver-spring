package com.example.movie_server.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.movie_server.domain.Movie;

public interface MovieRepo extends JpaRepository<Movie, Long> {
    Optional<Movie> findByTitle(String title);

    @Query("select m.title from Movie m")
    List<String> findTitles();

    @Query("""
            select m from Movie m
            where (:title is null or m.title like concat('%', :title, '%'))
              and (:genre is null or m.genre = :genre)
              and (:year is null or m.year = :year)
            """)
    Page<Movie> search(@Param("title") String title,
                       @Param("genre") String genre,
                       @Param("year") Integer year,
                       Pageable pageable);
}
