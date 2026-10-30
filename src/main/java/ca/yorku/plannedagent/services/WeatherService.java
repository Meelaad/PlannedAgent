package ca.yorku.plannedagent.services;

import java.io.IOException;
import java.time.LocalDate;

/** Adapter pattern target for weather data. */
public interface WeatherService {

    Forecast forecast(LocalDate from, LocalDate to) throws IOException;
}
