package com.example.movie_server.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.PathResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;

import com.example.movie_server.domain.Movie;
import com.example.movie_server.repo.MovieRepo;
import com.example.movie_server.web.dto.MovieDto;
import com.example.movie_server.web.dto.MovieUpdateReq;
import com.example.movie_server.web.dto.MoviesRsp;
import com.example.movie_server.web.dto.StatusRsp;
import com.example.movie_server.web.dto.UploadedMovieRsp;

import jakarta.validation.Valid;

/*

+ POST   /api/movies               Загрузка: `MultipartFile` + метаданные (title, year, genre) в multipart-запросе
+ GET    /api/movies               Список с пагинацией (`page`, `size`) и фильтрами: `title` (частичное), `genre`, `year`
+ GET    /api/movies/{id}          Карточка фильма
+ GET    /api/movies/{id}/download Скачивание файла (`Content-Disposition: attachment`)
+ PUT    /api/movies/{id}          Обновление метаданных
+ DELETE /api/movies/{id}          Удаление записи + файла с диска
+ GET    /api/status`              Health-check: версия, количество фильмов, размер хранилища

*/

@RestController
public class MovieController {
    private static final Logger log = LoggerFactory.getLogger(MovieController.class);
    private final MovieRepo repo;
    private final Path storageDir;
    private final Set<String> ALLOWED_EXTENSIONS = Set.of(".mp4", ".mkv", ".avi", ".mov");
    private final String version;

    public MovieController(MovieRepo repo,
            @Value("${movie_server.storage-dir:movies}") String storageDir,
            @Value("${app.version:dev}") String version) {
        this.repo = repo;
        this.storageDir = Path.of(storageDir);
        this.version = version;
    }

    @PostMapping("/api/movies")
    public ResponseEntity<UploadedMovieRsp> push(
            @RequestParam(name = "title", required = true) String title,
            @RequestParam(name = "year", required = true) Integer year,
            @RequestParam(name = "genre", required = false) String genre,
            @RequestParam(name = "duration_seconds", required = true) Integer durationSeconds,
            @RequestParam(name = "movie", required = true) MultipartFile movie) throws IOException {

        if (movie.isEmpty()) {
            throw new IllegalArgumentException("movie file is empty");
        }

        String original = movie.getOriginalFilename();
        int dot = original == null ? -1 : original.lastIndexOf('.');
        if (dot < 0) {
            throw new IllegalArgumentException("file must have an extension");
        }
        String ext = original.substring(dot).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("unsupported file type: " + ext);
        }

        Files.createDirectories(storageDir);
        Path tmp = Files.createTempFile(storageDir, UUID.randomUUID() + "-", ".tmp");
        try {
            movie.transferTo(tmp);
            Path target = storageDir.resolve(UUID.randomUUID() + ext);
            Movie saved = repo.save(Movie.builder()
                    .title(title)
                    .year(year)
                    .genre(genre)
                    .durationSeconds(durationSeconds)
                    .fileName(original)
                    .filePath(target.toString())
                    .fileSize(movie.getSize())
                    .createdAt(Instant.now().toString())
                    .build());
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
            log.info("Uploaded movie {} -> {}", saved.getId(), target);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new UploadedMovieRsp(saved.getId()));
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(tmp);
            throw e;
        }
    }

    @GetMapping("/api/movies")
    public MoviesRsp getMovies(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "title", required = false) String title,
            @RequestParam(name = "genre", required = false) String genre,
            @RequestParam(name = "year", required = false) Integer year) {
        Page<Movie> result = repo.search(title, genre, year, PageRequest.of(page, size));
        return new MoviesRsp(
                result.getContent().stream().map(MovieDto::from).toList(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @GetMapping("/api/movies/{id}")
    public MovieDto getMovie(@PathVariable Long id) {
        log.info("Movie ID: {}", id);
        return repo.findById(id)
                .map(MovieDto::from)
                .orElseThrow(() -> new MovieNotFoundException(id));
    }

    @GetMapping("/api/movies/{id}/download")
    public ResponseEntity<PathResource> download(@PathVariable Long id) {
        Movie movie = repo.findById(id)
                .orElseThrow(() -> new MovieNotFoundException(id));
        Path path = Path.of(movie.getFilePath());
        if (!Files.isRegularFile(path)) {
            throw new FileMissingException(id);
        }

        String filename = movie.getTitle().replaceAll("[\"\\\\\\r\\n]", "_")
                + path.getFileName().toString().substring(path.getFileName().toString().lastIndexOf('.'));

        // Content-Type задаём явно: в Framework 7 автоопределение из расширения
        // при content negotiation может проиграть (приходит application/json)
        return ResponseEntity.ok()
                .contentType(MediaTypeFactory.getMediaType(path.getFileName().toString())
                        .orElse(MediaType.APPLICATION_OCTET_STREAM))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(filename, StandardCharsets.UTF_8)
                                .build().toString())
                .body(new PathResource(path));
    }

    @PutMapping("/api/movies/{id}")
    public MovieDto update(@PathVariable Long id,
            @Valid @RequestBody MovieUpdateReq req) {
        Movie movie = repo.findById(id)
                .orElseThrow(() -> new MovieNotFoundException(id));

        if (req.title() != null)
            movie.setTitle(req.title());
        if (req.year() != null)
            movie.setYear(req.year());
        if (req.genre() != null)
            movie.setGenre(req.genre());
        if (req.durationSeconds() != null)
            movie.setDurationSeconds(req.durationSeconds());

        return MovieDto.from(repo.save(movie));
    }

    @DeleteMapping("/api/movies/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Movie movie = repo.findById(id)
                .orElseThrow(() -> new MovieNotFoundException(id));

        repo.delete(movie);

        Path path = Path.of(movie.getFilePath());
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Record {} deleted, but file {} could not be removed: {}",
                    id, path, e.getMessage());
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/status")
    public StatusRsp status() {
        long count = repo.count();

        long storageBytes = 0L;
        if (Files.isDirectory(storageDir)) {
            try (var files = Files.walk(storageDir)) {
                storageBytes = files.filter(Files::isRegularFile)
                        .mapToLong(this::safeSize)
                        .sum();
            } catch (IOException e) {
                log.warn("Could not walk storage dir {}: {}", storageDir, e.getMessage());
            }
        }
        return new StatusRsp(version, count, storageBytes);
    }

    private long safeSize(Path p) {
        try {
            return Files.size(p);
        } catch (IOException e) {
            return 0L;
        }
    }
}
