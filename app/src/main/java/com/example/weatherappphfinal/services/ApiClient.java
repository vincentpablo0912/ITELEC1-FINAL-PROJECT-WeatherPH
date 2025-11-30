package com.example.weatherappphfinal.services;

import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

public class ApiClient {

    // Make a GET request to the given URL and return the response as a JSONObject
    public static JSONObject get(String urlString) {
        try {
            // Open a connection to the URL
            HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();

            // Set the request method to GET
            conn.setRequestMethod("GET");
            conn.connect();

            // Check if the response code is 200 (OK), otherwise return null
            if (conn.getResponseCode() != 200) {
                conn.disconnect();
                return null;
            }

            // Read the response using a Scanner
            StringBuilder resultJson = new StringBuilder();
            Scanner scanner = new Scanner(conn.getInputStream());
            while (scanner.hasNext()) {
                resultJson.append(scanner.nextLine());
            }
            scanner.close();

            // Disconnect after reading the response
            conn.disconnect();

            // Convert the response string to JSONObject and return
            return new JSONObject(resultJson.toString());

        } catch (Exception e) {
            // Print error if request fails and return null
            e.printStackTrace();
            return null;
        }
    }
}
