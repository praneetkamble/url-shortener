package com.praneetkamble.urlshortener.repository;

import com.praneetkamble.urlshortener.domain.ShortUrl;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShortUrlRepository extends JpaRepository<ShortUrl, Long> {

    Optional<ShortUrl> findByCode(String code);

    boolean existsByCode(String code);

    /** Reserves the next unique numeric id from the database sequence. */
    @Query(value = "select nextval('short_url_seq')", nativeQuery = true)
    long nextId();

    @Modifying
    @Query("update ShortUrl u set u.clickCount = u.clickCount + 1 where u.id = :id")
    int incrementClickCount(@Param("id") Long id);

    @Modifying
    @Query("delete from ShortUrl u where u.expiresAt is not null and u.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
