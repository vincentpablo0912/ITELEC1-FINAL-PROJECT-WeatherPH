package com.example.weatherappphfinal.ui;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.example.weatherappphfinal.ui.OnboardingActivity;
import com.example.weatherappphfinal.R;
import com.example.weatherappphfinal.listeners.WeatherListener;
import com.example.weatherappphfinal.managers.WeatherManager;
import com.example.weatherappphfinal.models.DailyForecastModel;
import com.example.weatherappphfinal.models.WeatherModel;
import com.example.weatherappphfinal.services.LocationService;
import com.example.weatherappphfinal.services.WeatherWorker;
import com.example.weatherappphfinal.utils.WeatherCodeConverter;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * MainActivity handles the primary UI for the Weather App.
 * It allows searching for weather by city, refreshing via location,
 * showing forecasts, and scheduling daily notifications.
 */
public class MainActivity extends AppCompatActivity implements WeatherListener {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 100; // Request code for location permission

    // UI elements
    private AutoCompleteTextView searchEditText; // Search bar for city input
    private Button searchButton; // Button to trigger search
    private ImageButton exitButton, refreshButton, profileButton; // Exit, refresh and profile buttons
    private TextView greetingText, locationText, dateText, weatherIcon, temperatureText;
    private TextView feelsLikeText, weatherDescText, humidityValue, windSpeedValue, precipitationValue, pressureValue;
    private ProgressBar loadingProgress; // Progress bar during weather load
    private LinearLayout forecastContainer; // Container for daily forecast items

    // Data and services
    private MainViewModel mainViewModel; // ViewModel to persist weather data across configuration changes
    private WeatherManager weatherManager; // Handles API requests and location fetching
    private SharedPreferences sharedPreferences; // Stores user preferences like last city and name

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize ViewModel and WeatherManager
        mainViewModel = new ViewModelProvider(this).get(MainViewModel.class);
        weatherManager = new WeatherManager(this);

        // Initialize all UI views and setup adapters
        initViews();
        setupUI();

        // If data already exists in ViewModel, use it to update the UI
        if (mainViewModel.getWeather().getValue() != null && mainViewModel.getCityName().getValue() != null) {
            updateUI(mainViewModel.getWeather().getValue(), mainViewModel.getCityName().getValue());
        } else {
            // Otherwise, load weather for the last saved city
            handleInitialLoad();
        }

        // Schedule daily weather notifications at 7 AM
        scheduleDailyWeatherNotification();

        // Setup click listeners for buttons
        setupClickListeners();
    }

    /**
     * Initialize all UI views by finding them from the layout
     */
    private void initViews() {
        searchEditText = findViewById(R.id.searchEditText);
        searchButton = findViewById(R.id.searchButton);
        exitButton = findViewById(R.id.exitButton);
        refreshButton = findViewById(R.id.refreshButton);
        profileButton = findViewById(R.id.profileButton);
        greetingText = findViewById(R.id.greetingText);
        locationText = findViewById(R.id.locationText);
        dateText = findViewById(R.id.dateText);
        weatherIcon = findViewById(R.id.weatherIcon);
        temperatureText = findViewById(R.id.temperatureText);
        feelsLikeText = findViewById(R.id.feelsLikeText);
        weatherDescText = findViewById(R.id.weatherDescText);
        humidityValue = findViewById(R.id.humidityValue);
        windSpeedValue = findViewById(R.id.windSpeedValue);
        precipitationValue = findViewById(R.id.precipitationValue);
        pressureValue = findViewById(R.id.pressureValue);
        loadingProgress = findViewById(R.id.loadingProgress);
        forecastContainer = findViewById(R.id.forecastContainer);
    }

    /**
     * Setup initial UI elements like greeting and search adapter
     */
    private void setupUI() {
        sharedPreferences = getSharedPreferences("WeatherAppPrefs", MODE_PRIVATE);

        // Set greeting message
        updateGreeting();

        // Setup autocomplete with all Philippine locations
        String[] locations = LocationService.getAllPhilippineLocations();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, locations);
        searchEditText.setAdapter(adapter);
    }

    /**
     * Load weather for the last saved city or default to Manila
     */
    private void handleInitialLoad() {
        String lastCity = sharedPreferences.getString("lastCity", "Manila");
        loadWeather(lastCity);
    }

    /**
     * Setup click listeners for exit, search, and refresh buttons
     */
    private void setupClickListeners() {
        // Exit button confirmation
        exitButton.setOnClickListener(v -> {
            new AlertDialog.Builder(this, R.style.CustomAlertDialog)
                    .setTitle(R.string.exit_confirmation)
                    .setMessage(R.string.exit_message)
                    .setPositiveButton(R.string.yes, (dialog, which) -> finish())
                    .setNegativeButton(R.string.no, null)
                    .show();
        });

        // Search button triggers weather loading for entered city
        searchButton.setOnClickListener(v -> {
            String location = searchEditText.getText().toString().trim();
            if (!location.isEmpty()) {
                loadWeather(location);
            } else {
                Toast.makeText(this, "Enter location", Toast.LENGTH_SHORT).show();
            }
        });

        // Refresh button fetches current location weather
        refreshButton.setOnClickListener(v -> {
            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                Toast.makeText(this, "Please turn on your location", Toast.LENGTH_LONG).show();
                startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            } else {
                requestLocationAndLoadWeather();
            }
        });

        profileButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, OnboardingActivity.class);
            startActivity(intent);
        });
    }

    /**
     * Request location permission if not granted, then load weather
     */
    private void requestLocationAndLoadWeather() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            loadingProgress.setVisibility(View.VISIBLE);
            setButtonsEnabled(false);
            weatherManager.fetchCurrentLocation(this);
        }
    }

    /**
     * Handle location permission result
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                requestLocationAndLoadWeather();
            } else {
                Toast.makeText(this, "Location permission denied.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Schedule daily weather notification at 7 AM using WorkManager
     */
    private void scheduleDailyWeatherNotification() {
        PeriodicWorkRequest weatherWorkRequest = new PeriodicWorkRequest.Builder(WeatherWorker.class, 24, TimeUnit.HOURS)
                .setInitialDelay(calculateInitialDelay(), TimeUnit.MILLISECONDS)
                .build();
        WorkManager.getInstance(this).enqueue(weatherWorkRequest);
    }

    /**
     * Calculate milliseconds delay until next 7 AM
     */
    private long calculateInitialDelay() {
        Calendar now = Calendar.getInstance();
        Calendar nextRun = Calendar.getInstance();
        nextRun.set(Calendar.HOUR_OF_DAY, 7);
        nextRun.set(Calendar.MINUTE, 0);
        nextRun.set(Calendar.SECOND, 0);
        if (now.after(nextRun)) {
            nextRun.add(Calendar.DAY_OF_YEAR, 1);
        }
        return nextRun.getTimeInMillis() - now.getTimeInMillis();
    }

    /**
     * Load weather data for a specific city
     */
    private void loadWeather(String locationName) {
        loadingProgress.setVisibility(View.VISIBLE);
        setButtonsEnabled(false);
        weatherManager.loadWeather(locationName, this);
    }

    /**
     * Callback when weather is successfully loaded
     */
    @Override
    public void onWeatherLoaded(WeatherModel weather, String cityName) {
        updateUI(weather, cityName);
    }

    /**
     * Callback when there is an error loading weather
     */
    @Override
    public void onWeatherError(String message) {
        loadingProgress.setVisibility(View.GONE);
        setButtonsEnabled(true);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    /**
     * Update UI with weather data
     */
    private void updateUI(WeatherModel weather, String cityName) {
        mainViewModel.setWeather(weather);
        mainViewModel.setCityName(cityName);

        loadingProgress.setVisibility(View.GONE);
        setButtonsEnabled(true);

        // Update main weather info
        locationText.setText(cityName);
        searchEditText.setText("");
        temperatureText.setText(String.format("%d°C", (int) weather.getTemperature()));
        WeatherCodeConverter.WeatherCondition currentCondition = WeatherCodeConverter.convert(weather.getWeatherCode());
        feelsLikeText.setText(currentCondition.description);
        weatherDescText.setText(currentCondition.condition);
        humidityValue.setText(String.format("%d%%", weather.getHumidity()));
        windSpeedValue.setText(String.format("%.1f m/s", weather.getWindSpeed()));
        precipitationValue.setText(String.format("%d%%", weather.getPrecipitationProbability()));
        pressureValue.setText(String.format("%.0f hPa", weather.getPressure()));
        weatherIcon.setText(currentCondition.emoji);
        dateText.setText(new SimpleDateFormat("EEEE, MMM dd, yyyy", Locale.getDefault()).format(new Date()));

        // Update daily forecast
        updateForecast(weather);

        // Save last searched city
        sharedPreferences.edit().putString("lastCity", cityName).apply();
    }

    private void updateGreeting() {
        String userName = sharedPreferences.getString("userName", "");
        greetingText.setText(userName.isEmpty() ? "Here's the weather today" : "Here's the weather today, " + userName);
    }

    /**
     * Enable or disable main buttons
     */
    private void setButtonsEnabled(boolean enabled) {
        searchButton.setEnabled(enabled);
        exitButton.setEnabled(enabled);
        refreshButton.setEnabled(enabled);
        profileButton.setEnabled(enabled);
    }

    /**
     * Populate forecast container with daily forecast items
     */
    private void updateForecast(WeatherModel weather) {
        forecastContainer.removeAllViews();
        for (DailyForecastModel daily : weather.getDailyForecast()) {
            WeatherCodeConverter.WeatherCondition cond = WeatherCodeConverter.convert(daily.getWeatherCode());
            forecastContainer.addView(createForecastItem(daily.getDate(), cond.emoji, daily.getMaxTemp(), daily.getMinTemp(), cond.condition));
        }
    }

    /**
     * Create a forecast item view
     */
    private View createForecastItem(String date, String emoji, int maxTemp, int minTemp, String condition) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setPadding(16, 24, 16, 24);
        item.setBackgroundResource(R.drawable.forecast_bubble);
        item.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams((int) (110 * getResources().getDisplayMetrics().density), LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(8, 0, 8, 0);
        item.setLayoutParams(params);

        // Day of week
        TextView dayText = new TextView(this);
        dayText.setText(formatDateToDay(date));
        dayText.setGravity(Gravity.CENTER);
        dayText.setTextColor(Color.WHITE);

        // Date MM/dd
        TextView dateText = new TextView(this);
        dateText.setText(formatDateToMonthDay(date));
        dateText.setGravity(Gravity.CENTER);
        dateText.setTextColor(Color.WHITE);

        // Weather emoji
        TextView iconText = new TextView(this);
        iconText.setText(emoji);
        iconText.setTextSize(40);
        iconText.setGravity(Gravity.CENTER);

        // Condition text
        TextView condText = new TextView(this);
        condText.setText(condition);
        condText.setGravity(Gravity.CENTER);
        condText.setSingleLine(true);
        condText.setTextColor(Color.WHITE);

        // Max/Min temperature
        TextView tempText = new TextView(this);
        tempText.setText(String.format("%d°/%d°", maxTemp, minTemp));
        tempText.setGravity(Gravity.CENTER);
        tempText.setTextColor(Color.WHITE);

        item.addView(dayText);
        item.addView(dateText);
        item.addView(iconText);
        item.addView(condText);
        item.addView(tempText);

        return item;
    }

    /**
     * Convert date string to day abbreviation (e.g., Mon, Tue)
     */
    private String formatDateToDay(String dateString) {
        try {
            return new SimpleDateFormat("EEE", Locale.getDefault())
                    .format(new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateString));
        } catch (ParseException e) {
            e.printStackTrace();
            return dateString;
        }
    }

    /**
     * Convert date string to MM/dd format
     */
    private String formatDateToMonthDay(String dateString) {
        try {
            return new SimpleDateFormat("MM/dd", Locale.getDefault())
                    .format(new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateString));
        } catch (ParseException e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * Shutdown WeatherManager when activity is destroyed
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        weatherManager.shutdown();
    }
}
