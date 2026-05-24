package com.example.beathouse.activities;
import com.example.beathouse.R;
import com.example.beathouse.App;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.beathouse.utils.LocaleHelper;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public class SalesChartActivity extends AppCompatActivity {

    private LineChart salesChart;
    private TextView tvTotalEarned;
    private MaterialButtonToggleGroup toggleGroup;
    private FirebaseFirestore db;
    private String userId;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.setLocale(newBase, LocaleHelper.getLanguage(newBase)));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LocaleHelper.applyLanguage(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sales_chart);

        db = FirebaseFirestore.getInstance();
        userId = FirebaseAuth.getInstance().getUid();

        salesChart = findViewById(R.id.salesChart);
        tvTotalEarned = findViewById(R.id.tvTotalEarned);
        toggleGroup = findViewById(R.id.toggleGroup);

        findViewById(R.id.toolbar).setOnClickListener(v -> finish());
        // Using setNavigationOnClickListener for the back button
        ((com.google.android.material.appbar.MaterialToolbar)findViewById(R.id.toolbar))
                .setNavigationOnClickListener(v -> finish());

        setupChart();
        setupToggleGroup();

        loadSalesData(7); // Default to 7 days
    }

    private void setupChart() {
        salesChart.getDescription().setEnabled(false);
        salesChart.setTouchEnabled(true);
        salesChart.setDragEnabled(true);
        salesChart.setScaleEnabled(true);
        salesChart.setPinchZoom(true);
        salesChart.setDrawGridBackground(false);

        XAxis xAxis = salesChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(getColor(R.color.on_surface));
        xAxis.setDrawGridLines(false);

        salesChart.getAxisLeft().setTextColor(getColor(R.color.on_surface));
        salesChart.getAxisRight().setEnabled(false);
        salesChart.getLegend().setTextColor(getColor(R.color.on_surface));
    }

    private void setupToggleGroup() {
        toggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.btn7Days) {
                    loadSalesData(7);
                } else if (checkedId == R.id.btnMonth) {
                    loadSalesData(30);
                } else if (checkedId == R.id.btnYear) {
                    loadSalesData(365);
                }
            }
        });
    }

    private void loadSalesData(int days) {
        if (userId == null) return;

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -days);
        long startTime = cal.getTimeInMillis();

        db.collection("orders")
                .whereEqualTo("producerId", userId)
                .whereEqualTo("status", "paid")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    TreeMap<Long, Double> dailyTotals = new TreeMap<>();
                    double totalEarned = 0;

                    // Initialize all days in range with 0
                    Calendar tempCal = Calendar.getInstance();
                    for (int i = 0; i < days; i++) {
                        Calendar day = (Calendar) Calendar.getInstance().clone();
                        day.add(Calendar.DAY_OF_YEAR, -i);
                        day.set(Calendar.HOUR_OF_DAY, 0);
                        day.set(Calendar.MINUTE, 0);
                        day.set(Calendar.SECOND, 0);
                        day.set(Calendar.MILLISECOND, 0);
                        dailyTotals.put(day.getTimeInMillis(), 0.0);
                    }

                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        Double amount = doc.getDouble("total");
                        Long paidAt = doc.getLong("paidAt");
                        if (amount != null && paidAt != null && paidAt >= startTime) {
                            totalEarned += amount;

                            Calendar dayCal = Calendar.getInstance();
                            dayCal.setTimeInMillis(paidAt);
                            dayCal.set(Calendar.HOUR_OF_DAY, 0);
                            dayCal.set(Calendar.MINUTE, 0);
                            dayCal.set(Calendar.SECOND, 0);
                            dayCal.set(Calendar.MILLISECOND, 0);
                            long dayStart = dayCal.getTimeInMillis();

                            if (dailyTotals.containsKey(dayStart)) {
                                dailyTotals.put(dayStart, dailyTotals.get(dayStart) + amount);
                            } else {
                                // If for some reason the day wasn't initialized (e.g. slight timing diff)
                                dailyTotals.put(dayStart, amount);
                            }
                        }
                    }

                    updateChart(dailyTotals, days);
                    tvTotalEarned.setText(getString(R.string.total_earned_label) + "$" + String.format(Locale.US, "%.0f", totalEarned));
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error loading sales: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void updateChart(TreeMap<Long, Double> dailyTotals, int days) {
        List<Entry> entries = new ArrayList<>();
        final List<Long> dates = new ArrayList<>(dailyTotals.keySet());

        boolean hasData = false;
        for (int i = 0; i < dates.size(); i++) {
            float val = dailyTotals.get(dates.get(i)).floatValue();
            entries.add(new Entry(i, val));
            if (val > 0) hasData = true;
        }

        if (!hasData) {
            salesChart.clear();
            salesChart.setNoDataText(getString(R.string.no_sales));
            salesChart.setNoDataTextColor(getColor(R.color.on_surface));
            salesChart.invalidate();
            return;
        }

        int primaryColor = getColor(R.color.primary);
        LineDataSet dataSet = new LineDataSet(entries, getString(R.string.sales) + " ($)");
        dataSet.setColor(primaryColor);
        dataSet.setCircleColor(primaryColor);
        dataSet.setLineWidth(2f);
        dataSet.setCircleRadius(4f);
        dataSet.setDrawCircleHole(false);
        dataSet.setValueTextColor(getColor(R.color.on_surface));
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(primaryColor);
        dataSet.setFillAlpha(50);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);

        LineData lineData = new LineData(dataSet);
        salesChart.setData(lineData);

        XAxis xAxis = salesChart.getXAxis();
        xAxis.setValueFormatter(new ValueFormatter() {
            private final SimpleDateFormat mFormat = new SimpleDateFormat("dd MMM", new Locale(LocaleHelper.getLanguage(SalesChartActivity.this)));
            @Override
            public String getFormattedValue(float value) {
                int index = (int) value;
                if (index >= 0 && index < dates.size()) {
                    return mFormat.format(new Date(dates.get(index)));
                }
                return "";
            }
        });
        xAxis.setLabelCount(Math.min(days, 5));

        salesChart.invalidate();
    }
}
