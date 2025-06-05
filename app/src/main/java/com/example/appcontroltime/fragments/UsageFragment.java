package com.example.appcontroltime.fragments;

import android.app.*;
import android.app.usage.*;
import android.content.*;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.*;
import com.example.appcontroltime.R;
import com.example.appcontroltime.adapters.UsageAdapter;
import com.example.appcontroltime.models.AppUsage;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.*;

import java.text.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class UsageFragment extends Fragment {

    private RecyclerView recyclerView;
    private UsageAdapter adapter;
    private TextView dateTextView;
    private Date selectedDate = new Date(); // по умолчанию — сегодня

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_usage, container, false);

        Button btnPreviousDay = view.findViewById(R.id.btnPreviousDay);
        Button btnNextDay = view.findViewById(R.id.btnNextDay);
        recyclerView = view.findViewById(R.id.usageRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        dateTextView = view.findViewById(R.id.dateTextView);

        btnPreviousDay.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            cal.setTime(selectedDate);
            cal.add(Calendar.DAY_OF_MONTH, -1);
            selectedDate = cal.getTime();
            updateDateLabel();
            loadUsageStatsForDate(selectedDate);
        });

        btnNextDay.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            cal.setTime(selectedDate);
            cal.add(Calendar.DAY_OF_MONTH, 1);
            selectedDate = cal.getTime();
            updateDateLabel();
            loadUsageStatsForDate(selectedDate);
        });

        dateTextView.setOnClickListener(v -> showDatePicker());

        updateDateLabel();

        if (!hasUsageAccessPermission()) {
            // Переадресация на настройки, чтобы дать доступ Usage Stats
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        } else {
            loadUsageStatsForDate(selectedDate);
        }

        return view;
    }

    private void updateDateLabel() {
        String formatted = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(selectedDate);
        dateTextView.setText("Дата: " + formatted);
    }

    private void showDatePicker() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(selectedDate);

        new DatePickerDialog(requireContext(), (view, year, month, day) -> {
            calendar.set(year, month, day);
            selectedDate = calendar.getTime();
            updateDateLabel();
            loadUsageStatsForDate(selectedDate);
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private boolean hasUsageAccessPermission() {
        AppOpsManager appOps = (AppOpsManager) requireContext().getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), requireContext().getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private void loadUsageStatsForDate(Date date) {
        String todayStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        String requestedStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date);

        if (todayStr.equals(requestedStr)) {
            loadFromDeviceAndSave(date);
        } else {
            loadFromFirestore(date);
        }
    }

    private void loadFromDeviceAndSave(Date date) {
        UsageStatsManager usm = (UsageStatsManager) requireContext().getSystemService(Context.USAGE_STATS_SERVICE);
        PackageManager pm = requireContext().getPackageManager(); // Получаем PackageManager

        Calendar calendarStart = Calendar.getInstance();
        calendarStart.setTime(date);
        calendarStart.set(Calendar.HOUR_OF_DAY, 0);
        calendarStart.set(Calendar.MINUTE, 0);
        calendarStart.set(Calendar.SECOND, 0);
        calendarStart.set(Calendar.MILLISECOND, 0);
        long startTime = calendarStart.getTimeInMillis();

        Calendar calendarEnd = Calendar.getInstance();
        calendarEnd.setTime(date);
        calendarEnd.set(Calendar.HOUR_OF_DAY, 23);
        calendarEnd.set(Calendar.MINUTE, 59);
        calendarEnd.set(Calendar.SECOND, 59);
        calendarEnd.set(Calendar.MILLISECOND, 999);
        long endTime = calendarEnd.getTimeInMillis();

        List<UsageStats> stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime);
        Map<String, Long> appUsageMap = new HashMap<>();

        if (stats != null) {
            for (UsageStats usage : stats) {
                long time = usage.getTotalTimeInForeground();
                if (time > 300000) { // больше 5 минут
                    appUsageMap.put(usage.getPackageName(), time);
                }
            }
        }

        List<AppUsage> appUsages = new ArrayList<>();
        for (Map.Entry<String, Long> entry : appUsageMap.entrySet()) {
            String packageName = entry.getKey();
            long usageTime = entry.getValue();
            String appLabel;

            try {
                ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
                appLabel = (String) pm.getApplicationLabel(appInfo);
            } catch (PackageManager.NameNotFoundException e) {
                appLabel = packageName; // fallback, если имя не найдено
            }

            appUsages.add(new AppUsage(packageName, usageTime, appLabel));
        }

        // Сортировка по убыванию времени
        appUsages.sort((a, b) -> Long.compare(b.getTimeInForeground(), a.getTimeInForeground()));

        adapter = new UsageAdapter(appUsages, requireContext());
        recyclerView.setAdapter(adapter);

        saveToFirestore(appUsages, date);
    }


    private void loadFromFirestore(Date date) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        String uid = user.getUid();
        String dateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date);
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("users").document(uid)
                .collection("usage").document(dateStr)
                .collection("apps")
                .get().addOnSuccessListener(snapshot -> {
                    if (snapshot != null && !snapshot.isEmpty()) {
                        // 🔹 Данные в Firebase есть — отобразим
                        List<AppUsage> appUsages = new ArrayList<>();
                        for (DocumentSnapshot doc : snapshot) {
                            String pkg = doc.getString("packageName");
                            Long minutes = doc.getLong("usageTime");
                            if (pkg != null && minutes != null) {
                                long millis = TimeUnit.MINUTES.toMillis(minutes);
                                if (millis > 300000) {
                                    appUsages.add(new AppUsage(pkg, millis));
                                }
                            }
                        }

                        appUsages.sort((a, b) -> Long.compare(b.getTimeInForeground(), a.getTimeInForeground()));
                        adapter = new UsageAdapter(appUsages, requireContext());
                        recyclerView.setAdapter(adapter);
                    } else {
                        // 🔸 Данных нет — пробуем загрузить с устройства (если это <= 7 дней назад)
                        Calendar sevenDaysAgo = Calendar.getInstance();
                        sevenDaysAgo.add(Calendar.DAY_OF_MONTH, -7);

                        if (!date.before(sevenDaysAgo.getTime()) && hasUsageAccessPermission()) {
                            loadFromDeviceAndSave(date);
                        } else {
                            // 🔻 Невозможно загрузить: слишком старая дата или нет разрешения
                            Toast.makeText(getContext(), "Нет данных за выбранную дату", Toast.LENGTH_SHORT).show();
                            recyclerView.setAdapter(new UsageAdapter(Collections.emptyList(), requireContext()));
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Ошибка загрузки данных", Toast.LENGTH_SHORT).show();
                });
    }

    private void saveToFirestore(List<AppUsage> appUsages, Date date) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String uid = user.getUid();

        String dateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date);
        CollectionReference appsRef = db.collection("users")
                .document(uid)
                .collection("usage")
                .document(dateStr)
                .collection("apps");

        // Удаляем старые данные и записываем новые в батче
        appsRef.get().addOnSuccessListener(snapshot -> {
            WriteBatch batch = db.batch();

            // Удаляем старые документы
            for (DocumentSnapshot doc : snapshot) {
                batch.delete(doc.getReference());
            }

            batch.commit().addOnSuccessListener(unused -> {
                WriteBatch batchWrite = db.batch();
                for (AppUsage app : appUsages) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("packageName", app.getPackageName());
                    data.put("usageTime", TimeUnit.MILLISECONDS.toMinutes(app.getTimeInForeground()));
                    batchWrite.set(appsRef.document(app.getPackageName()), data);
                }
                batchWrite.commit();
            });
        });
    }
}
