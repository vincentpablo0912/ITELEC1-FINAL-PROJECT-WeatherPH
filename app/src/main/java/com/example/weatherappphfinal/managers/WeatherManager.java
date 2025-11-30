package com.example.weatherappphfinal.managers;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import androidx.core.app.ActivityCompat;
import com.example.weatherappphfinal.listeners.WeatherListener;
import com.example.weatherappphfinal.models.LocationModel;
import com.example.weatherappphfinal.models.WeatherModel;
import com.example.weatherappphfinal.services.LocationService;
import com.example.weatherappphfinal.services.WeatherService;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * WeatherManager handles fetching weather data and device location asynchronously.
 * It communicates results to a WeatherListener on the main thread.
 */
public class WeatherManager {

    private final ExecutorService executorService; // For background tasks
    private final Handler mainHandler; // To post results back to the main thread
    private final FusedLocationProviderClient fusedLocationClient; // For device location
    private final Activity activity; // Reference to the activity

    /**
     * Constructor initializes background executor, main thread handler, and location client
     */
    public WeatherManager(Activity activity) {
        this.activity = activity;
        this.executorService = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.fusedLocationClient = LocationServices.getFusedLocationProviderClient(activity);
    }

    /**
     * Load weather by city name
     * @param locationName Name of the city or location
     * @param listener Callback interface to return weather results
     */
    public void loadWeather(String locationName, WeatherListener listener) {
        executorService.execute(() -> {
            // Get coordinates for the Philippine city
            LocationModel location = LocationService.getPhilippineLocation(locationName);
            if (location == null) {
                // Post error back to main thread if location not found
                mainHandler.post(() -> listener.onWeatherError("Location not found"));
                return;
            }

            // Fetch weather data from service
            WeatherModel weather = WeatherService.getWeather(location.getLatitude(), location.getLongitude());
            if (weather == null) {
                mainHandler.post(() -> listener.onWeatherError("Failed to fetch weather"));
                return;
            }

            // Post successful result back to main thread
            mainHandler.post(() -> listener.onWeatherLoaded(weather, location.getName()));
        });
    }

    /**
     * Load weather by latitude and longitude
     * @param latitude Latitude of the location
     * @param longitude Longitude of the location
     * @param listener Callback interface to return weather results
     */
    public void loadWeather(double latitude, double longitude, WeatherListener listener) {
        executorService.execute(() -> {
            WeatherModel weather = WeatherService.getWeather(latitude, longitude);
            if (weather == null) {
                mainHandler.post(() -> listener.onWeatherError("Failed to fetch weather for your location"));
                return;
            }

            // Try to get the human-readable location name
            String locationName = LocationService.getLocationNameFromCoordinates(latitude, longitude);
            if (locationName == null) {
                locationName = "Your Location"; // fallback
            }

            final String finalLocationName = locationName;
            mainHandler.post(() -> listener.onWeatherLoaded(weather, finalLocationName));
        });
    }

    /**
     * Fetch the device's current location and then load weather for that location
     * @param listener Callback interface to return weather results
     */
    public void fetchCurrentLocation(WeatherListener listener) {
        // Check if location permission is granted
        if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            mainHandler.post(() -> listener.onWeatherError("Location permission not granted"));
            return;
        }

        // Get current location with high accuracy
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(activity, location -> {
                    if (location != null) {
                        // Load weather for current coordinates
                        loadWeather(location.getLatitude(), location.getLongitude(), listener);
                    } else {
                        mainHandler.post(() -> listener.onWeatherError("Could not get current location"));
                    }
                })
                .addOnFailureListener(activity, e -> mainHandler.post(() -> listener.onWeatherError("Failed to get current location")));
    }

    /**
     * Shutdown the executor to free resources
     */
    public void shutdown() {
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}
