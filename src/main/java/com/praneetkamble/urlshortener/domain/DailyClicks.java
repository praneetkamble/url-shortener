package com.praneetkamble.urlshortener.domain;

import java.time.LocalDate;

public record DailyClicks(LocalDate date, long clicks) {
}
