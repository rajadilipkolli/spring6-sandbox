package com.example.demo.domain.repository;

import com.example.demo.domain.model.Post;
import com.example.demo.domain.model.Status;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;

@Slf4j
@RequiredArgsConstructor
@Component
public class R2dbcPostRepository implements PostRepository {

    public static final BiFunction<Row, RowMetadata, Post> MAPPING_FUNCTION = (row, rowMetaData) -> Post.builder()
            .id(row.get("id", UUID.class))
            .title(row.get("title", String.class))
            .content(row.get("content", String.class))
            .status(row.get("status", Status.class))
            .createdAt(row.get("created_at", LocalDateTime.class))
            .build();

    private final DatabaseClient databaseClient;

    @Override
    public Flux<Post> findByTitleContains(String name) {
        var sql = "SELECT * FROM posts WHERE title LIKE :title";
        return this.databaseClient
                .sql(sql)
                .bind("title", "%" + name + "%")
                .map(MAPPING_FUNCTION)
                .all();
    }

    @Override
    public Flux<Post> findAll() {
        var sql = "SELECT * FROM posts";
        return this.databaseClient
                .sql(sql)
                .filter((statement, executeFunction) -> statement.fetchSize(10).execute())
                .map(MAPPING_FUNCTION)
                .all();
    }

    // see: https://stackoverflow.com/questions/64267699/spring-data-r2dbc-and-group-by
    @Override
    public Flux<Map<Object, Object>> countByStatus() {
        var sql = "SELECT count(*) as cnt, status FROM posts group by status";
        return this.databaseClient
                .sql(sql)
                .map((row, rowMetadata) -> {
                    Long cnt = row.get("cnt", Long.class);
                    Status s = row.get("status", Status.class);

                    return Map.<Object, Object>of("cnt", cnt, "status", s);
                })
                .all();
    }

    @Override
    public Mono<Post> findById(UUID id) {
        var sql = "SELECT * FROM posts WHERE id=:id";
        return this.databaseClient
                .sql(sql)
                .bind("id", id)
                .map(MAPPING_FUNCTION)
                .one();
    }

    @Override
    public Mono<UUID> save(Post p) {
        var sql = "INSERT INTO  posts (title, content, status) VALUES (:title, :content, :status)";
        return this.databaseClient.sql(sql)
                .filter((statement, executeFunction) -> statement.returnGeneratedValues("id").execute())
                .bind("title", p.getTitle())
                .bind("content", p.getContent())
                .bind("status", p.getStatus())
                .fetch()
                .first()
                .map(r -> (UUID) r.get("id"));
    }

    @Override
    public Flux<UUID> saveAll(List<Post> data) {
        if(data == null || data.isEmpty()) {
            throw new IllegalArgumentException("The parameter is invalid");
        }
        var sql = "INSERT INTO  posts (title, content, status) VALUES ($1, $2, $3)";
        return this.databaseClient.inConnectionMany(connection -> {

            var statement = connection.createStatement(sql)
                    .returnGeneratedValues("id");
            var len = data.size();

            if (len > 1) {
                for (Post p : data.subList(0, len - 1)) {
                    statement.bind(0, p.getTitle())
                            .bind(1, p.getContent())
                            .bind(2, p.getStatus())
                            .add();
                }
            }
            var last = data.get(len - 1);

            statement.bind(0, last.getTitle())
                    .bind(1, last.getContent())
                    .bind(2, last.getStatus())
                    .add();

            return Flux.from(statement.execute()).flatMap(result -> result.map((row, rowMetadata) -> row.get("id", UUID.class)));
        });
    }

    @Override
    public Mono<Long> update(Post p) {
        var sql = "UPDATE posts set title=:title, content=:content, metadata=:metadata, status=:status WHERE id=:id";
        return this.databaseClient.sql(sql)
                .bind("title", p.getTitle())
                .bind("content", p.getContent())
                .bind("status", p.getStatus())
                .bind("id", p.getId())
                .fetch()
                .rowsUpdated();
    }

    @Override
    public Mono<Long> deleteById(UUID id) {
        var sql = "DELETE FROM posts WHERE id=:id";
        return this.databaseClient.sql(sql)
                .bind("id", id)
                .fetch()
                .rowsUpdated();
    }

    @Override
    public Mono<Long> deleteAll() {
        var sql = "DELETE FROM posts";
        return this.databaseClient.sql(sql)
                .fetch()
                .rowsUpdated();
    }
}