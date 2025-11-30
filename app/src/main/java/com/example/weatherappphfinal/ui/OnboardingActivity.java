package com.example.weatherappphfinal.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.example.weatherappphfinal.R;
import com.example.weatherappphfinal.services.WeatherWorker;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

/**
 * OnboardingActivity is responsible for greeting the user and capturing their name.
 * This is typically shown only on the first launch of the app.
 */
public class OnboardingActivity extends AppCompatActivity {

    // UI elements for name input and submission.
    private EditText nameEditText;
    private Button continueButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        // Initialize views from the layout.
        nameEditText = findViewById(R.id.nameEditText);
        continueButton = findViewById(R.id.continueButton);

        // Set a listener for the continue button.
        continueButton.setOnClickListener(v -> {
            // Get the name from the EditText and remove any leading/trailing whitespace.
            String name = nameEditText.getText().toString().trim();

            // Validate that the name is not empty.
            if (name.isEmpty()) {
                Toast.makeText(this, "Please enter your name", Toast.LENGTH_SHORT).show();
            } else {
                // If the name is valid, save it to SharedPreferences.
                SharedPreferences prefs = getSharedPreferences("WeatherAppPrefs", MODE_PRIVATE);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString("userName", name);
                editor.putBoolean("onboardingComplete", true); // Mark onboarding as complete
                editor.apply(); // Apply the changes asynchronously.

                // Schedule the daily weather notification
                scheduleDailyWeatherWorker();

                // Navigate to the main screen.
                Intent intent = new Intent(OnboardingActivity.this, MainActivity.class);
                startActivity(intent);
                finish(); // Finish this activity so the user cannot navigate back to it.
            }
        });
    }

    private void scheduleDailyWeatherWorker() {
        // Set constraints for the worker
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        // Calculate the initial delay to 7 AM
        Calendar now = Calendar.getInstance();
        Calendar nextRun = Calendar.getInstance();
        nextRun.set(Calendar.HOUR_OF_DAY, 7);
        nextRun.set(Calendar.MINUTE, 0);
        nextRun.set(Calendar.SECOND, 0);

        if (now.after(nextRun)) {
            // If it's already past 7 AM today, schedule for 7 AM tomorrow
            nextRun.add(Calendar.DAY_OF_YEAR, 1);
        }

        long initialDelay = nextRun.getTimeInMillis() - now.getTimeInMillis();

        // Create a periodic work request
        PeriodicWorkRequest dailyWeatherRequest =
                new PeriodicWorkRequest.Builder(WeatherWorker.class, 24, TimeUnit.HOURS)
                        .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                        .setConstraints(constraints)
                        .build();

        // Enqueue the work request
        WorkManager.getInstance(getApplicationContext()).enqueueUniquePeriodicWork(
                "dailyWeatherCheck",
                ExistingPeriodicWorkPolicy.KEEP,
                dailyWeatherRequest);
    }
}
