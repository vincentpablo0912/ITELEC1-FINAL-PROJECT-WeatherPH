package com.example.weatherappphfinal.services;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.weatherappphfinal.models.LocationModel;
import com.example.weatherappphfinal.models.WeatherModel;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

import java.util.concurrent.ExecutionException;

// Worker that fetches weather data and triggers notifications
public class WeatherWorker extends Worker {

    public WeatherWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        // Check if GPS is enabled
        LocationManager lm = (LocationManager) getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
        boolean isGpsEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);

        // If GPS is enabled and permission granted → fetch current location weather
        if (isGpsEnabled && ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            return fetchCurrentLocationWeather();
        }
        // Otherwise → fetch weather of last searched city
        else {
            return fetchLastSearchedCityWeather();
        }
    }

    // Fetch weather using current GPS location
    private Result fetchCurrentLocationWeather() {
        FusedLocationProviderClient fusedLocationClient = LocationServices.getFusedLocationProviderClient(getApplicationContext());
        try {
            Task<Location> locationTask = fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null);
            Location location = Tasks.await(locationTask);

            if (location != null) {
                WeatherModel weather = WeatherService.getWeather(location.getLatitude(), location.getLongitude());
                String locationName = LocationService.getLocationNameFromCoordinates(location.getLatitude(), location.getLongitude());

                // If data is valid → trigger notification
                if (weather != null && locationName != null) {
                    enqueueNotificationWorker(locationName, weather);
                    return Result.success();
                }
            }
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }
        return Result.failure();
    }

    // Fetch weather using last searched city stored in SharedPreferences
    private Result fetchLastSearchedCityWeather() {
        SharedPreferences prefs = getApplicationContext().getSharedPreferences("WeatherAppPrefs", Context.MODE_PRIVATE);
        String lastCity = prefs.getString("lastCity", "Manila");

        LocationModel location = LocationService.getPhilippineLocation(lastCity);
        if (location == null) return Result.failure();

        WeatherModel weather = WeatherService.getWeather(location.getLatitude(), location.getLongitude());

        // If data is valid → trigger notification
        if (weather != null) {
            enqueueNotificationWorker(location.getName(), weather);
            return Result.success();
        }
        return Result.failure();
    }

    // Prepare data and enqueue NotificationWorker
    private void enqueueNotificationWorker(String locationName, WeatherModel weather) {
        Data inputData = new Data.Builder()
                .putString(NotificationWorker.KEY_LOCATION_NAME, locationName)
                .putDouble(NotificationWorker.KEY_TEMP, weather.getTemperature())
                .putString(NotificationWorker.KEY_DESC, weather.getWeatherDescription())
                .putInt(NotificationWorker.KEY_HUMIDITY, weather.getHumidity())
                .putDouble(NotificationWorker.KEY_WIND_SPEED, weather.getWindSpeed())
                .putInt(NotificationWorker.KEY_PRECIPITATION, weather.getPrecipitationProbability())
                .putDouble(NotificationWorker.KEY_PRESSURE, weather.getPressure())
                .build();

        OneTimeWorkRequest notificationWorkRequest = new OneTimeWorkRequest.Builder(NotificationWorker.class)
                .setInputData(inputData)
                .build();

        WorkManager.getInstance(getApplicationContext()).enqueue(notificationWorkRequest);
    }
}
