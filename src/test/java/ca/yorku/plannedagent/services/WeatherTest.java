package ca.yorku.plannedagent.services;

import ca.yorku.plannedagent.StubServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WeatherTest {

    private static final LocalDate FRIDAY = LocalDate.of(2026, 10, 30);
    private static final Clock CLOCK = Clock.fixed(FRIDAY.atTime(9, 0).atZone(ZoneId.of("America/Toronto")).toInstant(),
            ZoneId.of("America/Toronto"));

    private StubServer server;
    private OpenMeteoWeather weather;

    @BeforeEach
    void setUp() throws Exception {
        server = new StubServer();
        weather = new OpenMeteoWeather(43.7735, -79.5019, server.url(), HttpClient.newHttpClient(), CLOCK);
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void readsHourlyRainChance() throws Exception {
        server.reply = """
                {"latitude": 43.77, "hourly_units": {"precipitation_probability": "%"},
                 "hourly": {"time": ["2026-10-30T14:00", "2026-10-30T15:00", "2026-10-30T16:00"],
                            "precipitation_probability": [10, 65, null]}}""";

        Forecast forecast = weather.forecast(FRIDAY, FRIDAY.plusDays(1));

        assertFalse(forecast.isRainy(FRIDAY.atTime(14, 0), FRIDAY.atTime(15, 0)));
        assertTrue(forecast.isRainy(FRIDAY.atTime(14, 30), FRIDAY.atTime(15, 30)));
        assertEquals(-1, forecast.maxChance(FRIDAY.atTime(16, 0), FRIDAY.atTime(17, 0)));
    }

    @Test
    void asksForTheRightPlaceAndDays() throws Exception {
        server.reply = "{\"hourly\": {\"time\": [], \"precipitation_probability\": []}}";
        weather.forecast(FRIDAY.minusDays(2), FRIDAY.plusDays(30));

        assertEquals("/v1/forecast", server.lastPath);
        assertTrue(server.lastQuery.contains("latitude=43.7735"));
        assertTrue(server.lastQuery.contains("longitude=-79.5019"));
        assertTrue(server.lastQuery.contains("timezone=America%2FToronto"));
        assertTrue(server.lastQuery.contains("start_date=2026-10-30"));
        assertTrue(server.lastQuery.contains("end_date=2026-11-14"));
    }

    @Test
    void daysBeyondTheForecastNeedNoCall() throws Exception {
        server.close();
        assertTrue(weather.forecast(FRIDAY.plusDays(20), FRIDAY.plusDays(25)).isEmpty());
    }

    @Test
    void serverErrorIsAnIOException() {
        server.status = 503;
        assertThrows(IOException.class, () -> weather.forecast(FRIDAY, FRIDAY));
    }

    @Test
    void brokenReplyIsAnIOException() {
        server.reply = "{\"hourly\": {\"time\": [\"2026-10-30T14:00\"], \"precipitation_probability\": []}}";
        assertThrows(IOException.class, () -> weather.forecast(FRIDAY, FRIDAY));
        server.reply = "not json";
        assertThrows(IOException.class, () -> weather.forecast(FRIDAY, FRIDAY));
    }

    @Test
    void unreachableIsAnIOException() {
        server.close();
        assertThrows(IOException.class, () -> weather.forecast(FRIDAY, FRIDAY));
    }

    @Test
    void forecastUsesFortyPercentLimit() {
        LocalDateTime two = FRIDAY.atTime(14, 0);
        Forecast forecast = new Forecast(Map.of(two, 39, two.plusHours(1), 40));
        assertFalse(forecast.isRainy(two, two.plusHours(1)));
        assertTrue(forecast.isRainy(two, two.plusMinutes(61)));
        assertFalse(Forecast.none().isRainy(two, two.plusHours(5)));
    }

    @Test
    void rejectsImpossibleCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> new OpenMeteoWeather(95, 0));
    }
}
