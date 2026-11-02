package com.praneetkamble.urlshortener.web;

import com.praneetkamble.urlshortener.domain.DailyClicks;
import com.praneetkamble.urlshortener.domain.ReferrerCount;
import java.util.List;

public record StatsResponse(String code, long totalClicks, List<DailyClicks> clicksByDay,
                            List<ReferrerCount> topReferrers) {
}
