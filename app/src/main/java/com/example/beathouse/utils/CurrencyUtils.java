package com.example.beathouse.utils;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CurrencyUtils {
    private static final String TAG = "CurrencyUtils";
    private static final String API_URL = "https://www.cbr-xml-daily.ru/daily_json.js";
    private static Double cachedRate = null;
    private static final double FALLBACK_RATE = 95.0;
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface CurrencyCallback {
        void onRateReceived(double rate);
    }

    public static void getUsdToRubRate(CurrencyCallback callback) {
        if (cachedRate != null) {
            callback.onRateReceived(cachedRate);
            return;
        }

        Handler handler = new Handler(Looper.getMainLooper());

        executor.execute(() -> {
            double rate = FALLBACK_RATE;
            try {
                URL url = new URL(API_URL);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String inputLine;
                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    in.close();

                    JSONObject jsonResponse = new JSONObject(response.toString());
                    JSONObject valute = jsonResponse.getJSONObject("Valute");
                    JSONObject usd = valute.getJSONObject("USD");
                    rate = usd.getDouble("Value");
                    cachedRate = rate;
                    Log.d(TAG, "Rate fetched successfully: " + rate);
                } else {
                    Log.e(TAG, "HTTP error: " + responseCode);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error fetching rate: " + e.getMessage());
            }

            final double finalRate = rate;
            handler.post(() -> callback.onRateReceived(finalRate));
        });
    }

    public static String formatRub(double usdAmount, double rate) {
        double rubAmount = usdAmount * rate;
        return String.format("%.0f ₽", rubAmount);
    }
}
