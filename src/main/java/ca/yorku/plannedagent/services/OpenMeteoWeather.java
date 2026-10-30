package ca.yorku.plannedagent.services;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Adapter for the free Open-Meteo forecast API (hourly precipitation probability, no key). */
public class OpenMeteoWeather implements WeatherService {

    static final String BASE_URL = "https://api.open-meteo.com";
    static final int MAX_DAYS_AHEAD = 15;

    private final HttpClient http;
    private final double latitude;
    private final double longitude;
    private final String baseUrl;
    private final Clock clock;

    public OpenMeteoWeather(double latitude, double longitude) {
        this(latitude, longitude, BASE_URL,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(), Clock.systemDefaultZone());
    }

    OpenMeteoWeather(double latitude, double longitude, String baseUrl, HttpClient http, Clock clock) {
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Latitude or longitude is out of range");
        }
        this.latitude = latitude;
        this.longitude = longitude;
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
        this.http = Objects.requireNonNull(http, "http");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Days outside the forecast range (today to about 15 days ahead) are simply not included. */
    @Override
    public Forecast forecast(LocalDate from, LocalDate to) throws IOException {
        LocalDate today = LocalDate.now(clock);
        LocalDate start = from.isBefore(today) ? today : from;
        LocalDate end = to.isAfter(today.plusDays(MAX_DAYS_AHEAD)) ? today.plusDays(MAX_DAYS_AHEAD) : to;
        if (start.isAfter(end)) {
            return Forecast.none();
        }
        String url = baseUrl + "/v1/forecast"
                + "?latitude=" + String.format(Locale.ROOT, "%.4f", latitude)
                + "&longitude=" + String.format(Locale.ROOT, "%.4f", longitude)
                + "&hourly=precipitation_probability"
                + "&timezone=" + URLEncoder.encode(clock.getZone().getId(), StandardCharsets.UTF_8)
                + "&start_date=" + start + "&end_date=" + end;
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).GET().build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("The weather call was interrupted");
        }
        if (response.statusCode() != 200) {
            throw new IOException("Open-Meteo returned HTTP " + response.statusCode());
        }
        return parse(response.body());
    }

    private Forecast parse(String json) throws IOException {
        try {
            JsonObject hourly = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("hourly");
            if (hourly == null) {
                throw new IOException("The weather reply has no hourly data");
            }
            JsonArray times = hourly.getAsJsonArray("time");
            JsonArray chances = hourly.getAsJsonArray("precipitation_probability");
            if (times == null || chances == null || times.size() != chances.size()) {
                throw new IOException("The weather reply is incomplete");
            }
            Map<LocalDateTime, Integer> rain = new HashMap<>();
            for (int i = 0; i < times.size(); i++) {
                JsonElement chance = chances.get(i);
                if (!chance.isJsonNull()) {
                    rain.put(LocalDateTime.parse(times.get(i).getAsString()), chance.getAsInt());
                }
            }
            return new Forecast(rain);
        } catch (JsonParseException | IllegalStateException | ClassCastException
                 | NumberFormatException | DateTimeParseException e) {
            throw new IOException("Could not read the weather reply", e);
        }
    }
}
