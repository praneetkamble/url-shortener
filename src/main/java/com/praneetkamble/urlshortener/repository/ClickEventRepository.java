package com.praneetkamble.urlshortener.repository;

import com.praneetkamble.urlshortener.domain.ClickEvent;
import com.praneetkamble.urlshortener.domain.DailyClicks;
import com.praneetkamble.urlshortener.domain.ReferrerCount;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {

    @Query("""
            select new com.praneetkamble.urlshortener.domain.DailyClicks(cast(c.clickedAt as LocalDate), count(c))
            from ClickEvent c
            where c.shortUrlId = :urlId and c.clickedAt >= :since
            group by cast(c.clickedAt as LocalDate)
            order by cast(c.clickedAt as LocalDate)
            """)
    List<DailyClicks> countByDay(@Param("urlId") Long urlId, @Param("since") Instant since);

    @Query("""
            select new com.praneetkamble.urlshortener.domain.ReferrerCount(coalesce(c.referrer, 'direct'), count(c))
            from ClickEvent c
            where c.shortUrlId = :urlId
            group by coalesce(c.referrer, 'direct')
            order by count(c) desc, coalesce(c.referrer, 'direct')
            """)
    List<ReferrerCount> topReferrers(@Param("urlId") Long urlId, Pageable pageable);
}
