package com.example.movie_server.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import com.example.movie_server.domain.Movie;
import com.example.movie_server.repo.MovieRepo;
import com.example.movie_server.web.dto.UploadedMovieRsp;

/** Unit-тесты MovieController: repo в моке, файлы — в временном каталоге. */
class MovieControllerTest {

    @TempDir
    Path storageDir;

    MovieRepo repo;
    MovieController controller;

    @BeforeEach
    void setUp() {
        repo = mock(MovieRepo.class);
        controller = new MovieController(repo, storageDir.toString(), "test");
    }

    @Test
    void pushStoresFileAndPersistsServerSideMetadata() throws IOException {
        when(repo.save(any(Movie.class))).thenAnswer(inv -> {
            Movie m = inv.getArgument(0);
            m.setId(42L);
            return m;
        });

        MultipartFile file = new MockMultipartFile("movie", "dune.mp4", "video/mp4", "fake-bytes".getBytes());
        ResponseEntity<UploadedMovieRsp> rsp = controller.push("Dune", 1965, "sci-fi", 6600, file);

        assertEquals(HttpStatus.CREATED, rsp.getStatusCode());
        assertEquals(42L, rsp.getBody().id());

        // fileName/filePath/fileSize/createdAt сервер вычисляет сам
        ArgumentCaptor<Movie> captor = ArgumentCaptor.forClass(Movie.class);
        verify(repo).save(captor.capture());
        Movie saved = captor.getValue();
        assertEquals("dune.mp4", saved.getFileName());
        assertEquals(10L, saved.getFileSize());
        assertNotNull(saved.getCreatedAt());
        Path stored = Path.of(saved.getFilePath());
        assertTrue(Files.isRegularFile(stored));
        assertEquals("fake-bytes", Files.readString(stored));
        assertTrue(stored.getFileName().toString().endsWith(".mp4"));
    }

    @Test
    void pushRejectsUnsupportedExtension() {
        MultipartFile file = new MockMultipartFile("movie", "notes.txt", "text/plain", "x".getBytes());
        assertThrows(IllegalArgumentException.class, () -> controller.push("Dune", 1965, null, 100, file));
        verify(repo, never()).save(any());
    }

    @Test
    void pushRejectsFileWithoutExtension() {
        MultipartFile file = new MockMultipartFile("movie", "dune", "video/mp4", "x".getBytes());
        assertThrows(IllegalArgumentException.class, () -> controller.push("Dune", 1965, null, 100, file));
        verify(repo, never()).save(any());
    }

    @Test
    void pushRejectsEmptyFile() {
        MultipartFile file = new MockMultipartFile("movie", "dune.mp4", "video/mp4", new byte[0]);
        assertThrows(IllegalArgumentException.class, () -> controller.push("Dune", 1965, null, 100, file));
        verify(repo, never()).save(any());
    }
}