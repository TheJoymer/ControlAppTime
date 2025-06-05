package com.example.appcontroltime.fragments;

import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.*;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.example.appcontroltime.R;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.*;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.utils.ViewPortHandler;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;

import java.text.SimpleDateFormat;
import java.util.*;

public class StatsFragment extends Fragment {

    private PieChart pieChart;
    private TextView mostUsedApp, comparisonText;
    private FirebaseFirestore db;
    private String uid;
    private BarChart barChart;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_stats, container, false);

        pieChart = view.findViewById(R.id.pieChart);
        mostUsedApp = view.findViewById(R.id.mostUsedApp);
        comparisonText = view.findViewById(R.id.comparisonText);
        barChart = view.findViewById(R.id.barChart);
        loadWeeklyTotalUsage();
        db = FirebaseFirestore.getInstance();
        uid = Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid();

        loadPieChartForToday();
        loadUsageComparison();

        return view;
    }
    private void loadWeeklyTotalUsage() {
        Calendar calendar = Calendar.getInstance();
        List<String> last7Days = new ArrayList<>();
        List<BarEntry> entries = new ArrayList<>();

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        SimpleDateFormat labelFormat = new SimpleDateFormat("dd.MM", Locale.getDefault());

        db = FirebaseFirestore.getInstance();
        uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        Map<Integer, String> xAxisLabels = new HashMap<>();
        int[] counter = {0};  // индекс по X

        for (int i = 6; i >= 0; i--) {
            Calendar dayCal = (Calendar) calendar.clone();
            dayCal.add(Calendar.DAY_OF_YEAR, -i);
            String dateStr = sdf.format(dayCal.getTime());
            String label = labelFormat.format(dayCal.getTime());

            int xIndex = 6 - i;
            xAxisLabels.put(xIndex, label);

            db.collection("users").document(uid)
                    .collection("usage").document(dateStr).collection("apps")
                    .get().addOnSuccessListener(snapshot -> {
                        long totalMinutes = 0;
                        for (DocumentSnapshot doc : snapshot) {
                            Long minutes = doc.getLong("usageTime");
                            if (minutes != null) {
                                totalMinutes += minutes;
                            }
                        }
                        entries.add(new BarEntry(xIndex, totalMinutes));

                        // если загрузили все 7 дней
                        counter[0]++;
                        if (counter[0] == 7) {
                            drawBarChart(entries, xAxisLabels);
                        }
                    });
        }
    }
    private void drawBarChart(List<BarEntry> entries, Map<Integer, String> labelsMap) {
        entries.sort(Comparator.comparingDouble(BarEntry::getX));

        BarDataSet dataSet = new BarDataSet(entries, "Общее время (мин)");
        dataSet.setColor(Color.parseColor("#FF9800"));
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueTextSize(12f);

        BarData barData = new BarData(dataSet);
        barChart.setData(barData);
        barChart.getDescription().setEnabled(false);
        barChart.getAxisRight().setEnabled(false);
        barChart.getAxisLeft().setTextColor(Color.WHITE);
        barChart.getXAxis().setTextColor(Color.WHITE);
        barChart.getLegend().setTextColor(Color.WHITE);
        barChart.getAxisLeft().setAxisMinimum(0f);

        // ✅ Показываем даты, а не минуты
        barChart.getXAxis().setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return labelsMap.getOrDefault((int) value, "");
            }
        });

        barChart.getXAxis().setGranularity(1f);
        barChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        barChart.getXAxis().setDrawGridLines(false);

        barChart.invalidate();
    }



    private String formatAppName(String packageName) {
        String[] parts = packageName.split("\\.");
        if (parts.length == 2 || parts.length == 3) {
            return parts[1];
        }
        return packageName;
    }

    private void loadPieChartForToday() {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        CollectionReference appsRef = db.collection("users").document(uid)
                .collection("usage").document(today).collection("apps");

        appsRef.get().addOnSuccessListener(snapshot -> {
            Map<String, Integer> appUsage = new HashMap<>();
            int maxTime = 0;
            String topApp = null;

            for (DocumentSnapshot doc : snapshot) {
                String pkg = doc.getId();
                Long time = doc.getLong("usageTime");
                if (time != null) {
                    int minutes = time.intValue();
                    appUsage.put(pkg, minutes);
                    if (minutes > maxTime) {
                        maxTime = minutes;
                        topApp = pkg;
                    }
                }
            }

            if (topApp != null) {
                mostUsedApp.setText("Наиболее используемое приложение: " +
                        getAppNameFromPackage(topApp) + " (" + formatTime(maxTime) + ")");
            } else {
                mostUsedApp.setText("Нет данных за сегодня");
            }

            drawPieChart(appUsage);

        }).addOnFailureListener(e -> Log.e("StatsFragment", "Ошибка pie", e));
    }
    private String formatTime(int totalMinutes) {
        int hours = totalMinutes / 60;
        int mins = totalMinutes % 60;
        if (hours > 0) {
            return hours + " ч " + mins + " мин";
        } else {
            return mins + " мин";
        }
    }
    private String getAppNameFromPackage(String packageName) {
        try {
            return requireContext()
                    .getPackageManager()
                    .getApplicationLabel(
                            requireContext()
                                    .getPackageManager()
                                    .getApplicationInfo(packageName, 0))
                    .toString();
        } catch (Exception e) {
            return packageName; // если не найдено — вернуть packageName
        }
    }
    private void drawPieChart(Map<String, Integer> appUsage) {
        // Оставим только те, где usage > 5 мин
        Map<String, Integer> filteredUsage = new HashMap<>();
        for (Map.Entry<String, Integer> entry : appUsage.entrySet()) {
            if (entry.getValue() > 5) {
                filteredUsage.put(entry.getKey(), entry.getValue());
            }
        }

        if (filteredUsage.isEmpty()) {
            pieChart.clear();
            pieChart.setNoDataText("Нет приложений с использованием более 5 мин");
            pieChart.setNoDataTextColor(Color.WHITE);
            return;
        }

        List<PieEntry> entries = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : filteredUsage.entrySet()) {
            entries.add(new PieEntry(entry.getValue(), entry.getKey()));
        }

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(Color.CYAN, Color.MAGENTA, Color.YELLOW, Color.GREEN, Color.RED, Color.LTGRAY);
        dataSet.setValueTextSize(14f);
        dataSet.setValueFormatter(new StrokeValueFormatter());

        PieData data = new PieData(dataSet);
        pieChart.setDrawEntryLabels(false);
        pieChart.setData(data);
        Legend legend = pieChart.getLegend();
        legend.setEnabled(false);

        pieChart.setEntryLabelColor(Color.BLACK);
        pieChart.setEntryLabelTextSize(12f);

        LinearLayout legendContainer = requireView().findViewById(R.id.legendContainer);
        legendContainer.removeAllViews(); // Очистка

        List<Integer> colors = dataSet.getColors();
        int colorIndex = 0;

        for (Map.Entry<String, Integer> entry : filteredUsage.entrySet()) {
            String appName = entry.getKey();
            int time = entry.getValue();

            LinearLayout itemLayout = new LinearLayout(getContext());
            itemLayout.setOrientation(LinearLayout.HORIZONTAL);
            itemLayout.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            itemLayout.setPadding(0, 4, 0, 4);

            View colorDot = new View(getContext());
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(30, 30);
            dotParams.setMargins(0, 0, 16, 0);
            colorDot.setLayoutParams(dotParams);
            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.OVAL);
            drawable.setColor(colors.get(colorIndex % colors.size()));
            drawable.setSize(30, 30); // радиус
            colorDot.setBackground(drawable);

            TextView label = new TextView(getContext());
            label.setText(getAppNameFromPackage(appName) + " - " + formatTime(time));
            label.setTextColor(Color.WHITE);
            label.setTextSize(14f);

            itemLayout.addView(colorDot);
            itemLayout.addView(label);

            legendContainer.addView(itemLayout);
            colorIndex++;
        }

        pieChart.setHoleRadius(40f);
        pieChart.setTransparentCircleRadius(45f);
        pieChart.setDrawCenterText(true);
        pieChart.setCenterText("Сегодня");
        pieChart.setCenterTextColor(Color.WHITE);
        pieChart.setCenterTextSize(16f);

        pieChart.setDescription(null);
        pieChart.invalidate();
    }

    private void loadUsageComparison() {
        Calendar cal = Calendar.getInstance();
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.getTime());

        cal.add(Calendar.DAY_OF_YEAR, -1);
        String yesterday = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.getTime());

        db.collection("users").document(uid).collection("usage").document(today).collection("apps").get()
                .addOnSuccessListener(todaySnapshot -> {
                    db.collection("users").document(uid).collection("usage").document(yesterday).collection("apps").get()
                            .addOnSuccessListener(yesterdaySnapshot -> {
                                Map<String, Integer> todayApps = new HashMap<>();
                                Map<String, Integer> yesterdayApps = new HashMap<>();

                                for (DocumentSnapshot doc : todaySnapshot) {
                                    Long time = doc.getLong("usageTime");
                                    if (time != null) todayApps.put(doc.getId(), time.intValue());
                                }

                                for (DocumentSnapshot doc : yesterdaySnapshot) {
                                    Long time = doc.getLong("usageTime");
                                    if (time != null) yesterdayApps.put(doc.getId(), time.intValue());
                                }

                                Set<String> allApps = new HashSet<>();
                                allApps.addAll(todayApps.keySet());
                                allApps.addAll(yesterdayApps.keySet());

                                StringBuilder comparisonResult = new StringBuilder();
                                for (String app : allApps) {
                                    int todayTime = todayApps.getOrDefault(app, 0);
                                    int yesterdayTime = yesterdayApps.getOrDefault(app, 0);
                                    int diff = todayTime - yesterdayTime;

                                    String name = getAppNameFromPackage(app);

                                    if (diff > 0) {
                                        comparisonResult.append(name).append(": +").append(formatTime(diff)).append("\n");
                                    } else if (diff < 0) {
                                        comparisonResult.append(name).append(": -").append(formatTime(-diff)).append("\n");
                                    } else {
                                        comparisonResult.append(name).append(": без изменений\n");
                                    }
                                }

                                comparisonText.setTextColor(Color.WHITE);
                                if (comparisonResult.length() > 0) {
                                    comparisonText.setText("Сравнение с вчерашним днем:\n\n" + comparisonResult.toString());
                                } else {
                                    comparisonText.setText("Нет данных для сравнения с вчерашним днем.");
                                }

                            })
                            .addOnFailureListener(e -> {
                                comparisonText.setText("Ошибка при загрузке вчерашних данных");
                                Log.e("StatsFragment", "Ошибка загрузки вчерашних данных", e);
                            });
                })
                .addOnFailureListener(e -> {
                    comparisonText.setText("Ошибка при загрузке сегодняшних данных");
                    Log.e("StatsFragment", "Ошибка загрузки сегодняшних данных", e);
                });
    }

    // Встроенный форматтер с белым текстом и черной обводкой
    public static class StrokeValueFormatter extends ValueFormatter implements com.example.appcontroltime.fragments.StrokeValueFormatter {
        private final Paint strokePaint;
        private final Paint fillPaint;

        public StrokeValueFormatter() {
            strokePaint = new Paint();
            strokePaint.setColor(Color.BLACK);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(4f);
            strokePaint.setAntiAlias(true);
            strokePaint.setTextSize(32f);
            strokePaint.setTypeface(Typeface.DEFAULT_BOLD);

            fillPaint = new Paint();
            fillPaint.setColor(Color.WHITE);
            fillPaint.setStyle(Paint.Style.FILL);
            fillPaint.setAntiAlias(true);
            fillPaint.setTextSize(32f);
            fillPaint.setTypeface(Typeface.DEFAULT_BOLD);
        }

        @Override
        public String getFormattedValue(float value) {
            return ((int) value) + " мин";
        }

        @Override
        public void drawValue(Canvas c, String valueText, float x, float y, PieEntry entry, int dataSetIndex, ViewPortHandler vpHandler) {
            c.drawText(valueText, x, y, strokePaint);
            c.drawText(valueText, x, y, fillPaint);
        }
    }
}