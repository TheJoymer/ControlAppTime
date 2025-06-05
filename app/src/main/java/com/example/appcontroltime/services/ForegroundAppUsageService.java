package com.example.appcontroltime.services;
import android.app.*;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.appcontroltime.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.*;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class ForegroundAppUsageService extends Service {
    private ScheduledExecutorService scheduler;
    private static final String CHANNEL_ID = "usage_channel";
    private final Map<String, Long> localUsageMap = new HashMap<>();

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("AppControlTime работает")
                .setContentText("Отслеживание текущего приложения")
                .setSmallIcon(R.drawable.ic_notification)
                .build();

        startForeground(1, notification);

        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::trackCurrentApp, 0, 1, TimeUnit.MINUTES);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "App Usage Tracking",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Уведомления о превышении лимита использования приложений");
            channel.enableLights(true);
            channel.enableVibration(true);
            channel.setImportance(NotificationManager.IMPORTANCE_HIGH); // для надёжности

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void trackCurrentApp() {
        try {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user == null) {
                Log.w("ForegroundService", "❌ Пользователь не авторизован");
                return;
            }

            UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) {
                Log.e("ForegroundService", "❌ UsageStatsManager недоступен");
                return;
            }

            long endTime = System.currentTimeMillis();
            long beginTime = endTime - 60000;

            UsageEvents events = usm.queryEvents(beginTime, endTime);
            UsageEvents.Event event = new UsageEvents.Event();
            String currentApp = null;

            while (events.hasNextEvent()) {
                events.getNextEvent(event);
                if (event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    currentApp = event.getPackageName();
                }
            }

            if (currentApp != null) {
                long currentTime = System.currentTimeMillis();
                localUsageMap.put(currentApp, localUsageMap.getOrDefault(currentApp, 0L) + 60000);
                Log.d("ForegroundService", "📱 Активное приложение: " + currentApp);

                uploadUsageData(user.getUid(), currentApp, localUsageMap.get(currentApp));
            } else {
                Log.d("ForegroundService", "⚠️ Не удалось определить активное приложение");
            }

        } catch (SecurityException se) {
            Log.e("ForegroundService", "❌ Нет разрешения USAGE_STATS", se);
        } catch (Exception e) {
            Log.e("ForegroundService", "❌ Ошибка при отслеживании текущего приложения", e);
        }
    }

    private void uploadUsageData(String uid, String packageName, long newUsageMs) {
        long newMinutes = TimeUnit.MILLISECONDS.toMinutes(newUsageMs);
        if (newMinutes <= 0) return;

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String dateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        DocumentReference appRef = db.collection("users")
                .document(uid)
                .collection("usage")
                .document(dateStr)
                .collection("apps")
                .document(packageName);

        long[] totalMinutes = new long[]{newMinutes};

        appRef.get().addOnSuccessListener(snapshot -> {
            if (snapshot.exists()) {
                Long existing = snapshot.getLong("usageTime");
                if (existing != null) {
                    totalMinutes[0] += existing;
                }
            }

            Map<String, Object> data = new HashMap<>();
            data.put("packageName", packageName);
            data.put("usageTime", totalMinutes[0]);
            data.put("timestamp", FieldValue.serverTimestamp());

            appRef.set(data)
                    .addOnSuccessListener(aVoid ->
                            Log.d("ForegroundService", "⬆️ Обновлено (сумма): " + packageName + " = " + totalMinutes[0] + " мин"))
                    .addOnFailureListener(e ->
                            Log.e("ForegroundService", "❌ Ошибка записи в Firebase", e));

            // Проверка цели
            db.collection("users").document(uid)
                    .collection("goals").document(packageName)
                    .get().addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            Long limit = doc.getLong("limit");
                            String name = doc.getString("name");
                            if (limit != null && totalMinutes[0] > limit) {
                                sendNotification(packageName, name, totalMinutes[0], limit);
                            }
                        }
                    });

        }).addOnFailureListener(e -> Log.e("ForegroundService", "❌ Ошибка чтения текущего usage", e));
    }


    private void sendNotification(String pkg, String appName, long usedMin, long limitMin) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Превышен лимит")
                .setContentText(appName + ": " + usedMin + " мин (лимит: " + limitMin + " мин)")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        manager.notify(pkg.hashCode(), builder.build());
    }

    @Override
    public void onDestroy() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
