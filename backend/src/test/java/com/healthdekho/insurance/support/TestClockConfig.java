package com.healthdekho.insurance.support;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Pins "today" so assertions about grace periods and ages do not drift with the calendar. */
@TestConfiguration
public class TestClockConfig {

    public static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    public static final LocalDate TODAY = LocalDate.of(2026, 11, 25);

    @Bean
    @Primary
    Clock fixedClock() {
        return Clock.fixed(TODAY.atTime(10, 0).atZone(ZONE).toInstant(), ZONE);
    }
}
