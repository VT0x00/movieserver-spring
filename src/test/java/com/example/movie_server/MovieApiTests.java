package com.example.movie_server;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.example.movie_server.repo.MovieRepo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Интеграционные тесты REST API: реальный контекст, SQLite в target/, MockMvc. */
@SpringBootTest
@AutoConfigureMockMvc
class MovieApiTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MovieRepo repo;

    // В Boot 4 автоконфигурируемый бин — уже Jackson 3 (tools.jackson),
    // поэтому ObjectMapper создаём локально, только для парсинга ответа
    private static final ObjectMapper JSON = new ObjectMapper();

    @Value("${movie_server.storage-dir}")
    Path storageDir;

    @AfterEach
    void cleanup() throws Exception {
        repo.deleteAll();
        if (Files.isDirectory(storageDir)) {
            try (var files = Files.walk(storageDir)) {
                files.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (Exception ignored) {
                    }
                });
            }
        }
    }

    private long upload(String title, String genre, int year, String filename) throws Exception {
        MockMultipartFile file = new MockMultipartFile("movie", filename, "video/mp4", "fake-video".getBytes());
        MvcResult result = mockMvc.perform(multipart("/api/movies")
                        .file(file)
                        .param("title", title)
                        .param("genre", genre)
                        .param("year", String.valueOf(year))
                        .param("duration_seconds", "5400"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode json = JSON.readTree(result.getResponse().getContentAsByteArray());
        return json.get("id").asLong();
    }

    @Test
    void uploadAndGetMovie() throws Exception {
        long id = upload("Dune", "sci-fi", 1965, "dune.mp4");

        mockMvc.perform(get("/api/movies/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Dune"))
                .andExpect(jsonPath("$.year").value(1965))
                .andExpect(jsonPath("$.genre").value("sci-fi"))
                .andExpect(jsonPath("$.fileName").value("dune.mp4"));
    }

    @Test
    void getUnknownMovieReturns404() throws Exception {
        mockMvc.perform(get("/api/movies/{id}", 999999))
                .andExpect(status().isNotFound());
    }

    @Test
    void uploadRejectsUnsupportedExtension() throws Exception {
        MockMultipartFile file = new MockMultipartFile("movie", "dune.txt", "text/plain", "x".getBytes());

        mockMvc.perform(multipart("/api/movies")
                        .file(file)
                        .param("title", "Dune")
                        .param("year", "1965")
                        .param("duration_seconds", "100"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listWithFiltersAndPagination() throws Exception {
        upload("Dune", "sci-fi", 1965, "dune.mp4");
        upload("Dune Part Two", "sci-fi", 2021, "dune2.mp4");
        upload("The Godfather", "drama", 1972, "godfather.mp4");

        // фильтр по жанру
        mockMvc.perform(get("/api/movies").param("genre", "sci-fi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movies.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(2));

        // частичное совпадение по названию
        mockMvc.perform(get("/api/movies").param("title", "Dune"))
                .andExpect(jsonPath("$.movies.length()").value(2));

        // пагинация
        mockMvc.perform(get("/api/movies").param("page", "0").param("size", "1"))
                .andExpect(jsonPath("$.movies.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    void updateChangesOnlyProvidedFields() throws Exception {
        long id = upload("Dune", "sci-fi", 1965, "dune.mp4");

        mockMvc.perform(put("/api/movies/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"year": 2021}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Dune"))
                .andExpect(jsonPath("$.year").value(2021));
    }

    @Test
    void updateUnknownMovieReturns404() throws Exception {
        mockMvc.perform(put("/api/movies/{id}", 999999)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"year": 2021}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteRemovesRecordAndFile() throws Exception {
        long id = upload("Dune", "sci-fi", 1965, "dune.mp4");
        Path file = Path.of(repo.findById(id).orElseThrow().getFilePath());
        assertTrue(Files.exists(file));

        mockMvc.perform(delete("/api/movies/{id}", id))
                .andExpect(status().isNoContent());

        assertFalse(Files.exists(file));
        mockMvc.perform(get("/api/movies/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void downloadReturnsFileWithAttachmentHeader() throws Exception {
        long id = upload("Dune", "sci-fi", 1965, "dune.mp4");

        mockMvc.perform(get("/api/movies/{id}/download", id))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(content().bytes("fake-video".getBytes()));
    }

    @Test
    void statusReflectsStorage() throws Exception {
        upload("Dune", "sci-fi", 1965, "dune.mp4");
        upload("Blade Runner", "sci-fi", 1982, "bladerunner.mp4");

        mockMvc.perform(get("/api/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moviesCount").value(2))
                .andExpect(jsonPath("$.storageBytes").value(20));
    }
}