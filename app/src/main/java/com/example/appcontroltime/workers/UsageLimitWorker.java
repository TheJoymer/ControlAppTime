package com.example.appcontroltime.workers;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.*;

import com.example.appcontroltime.R;
import com.example.appcontroltime.utils.UsageUtils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;

import java.text.SimpleDateFormat;
import java.util.*;

public class UsageLimitWorker extends Worker {

    public UsageLimitWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;

        if (uid == null) return Result.success();

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Context context = getApplicationContext();

        // 📌 Получение usage-данных
        Map<String, Long> usageMap = UsageUtils.getTodayUsage(context);
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        // 📤 Сохраняем usage в Firestore
        for (Map.Entry<String, Long> entry : usageMap.entrySet()) {
            Map<String, Object> data = new HashMap<>();
            data.put("usageTime", entry.getValue());

            db.collection("users").document(uid)
                    .collection("usage").document(date)
                    .collection("apps").document(entry.getKey())
                    .set(data, SetOptions.merge());
        }

        // ⏰ Далее проверяем цели, как раньше
        db.collection("users").document(uid).collection("goals")
                .get().addOnSuccessListener(goalSnapshot -> {
                    for (DocumentSnapshot goalDoc : goalSnapshot.getDocuments()) {
                        String pkg = goalDoc.getId();
                        String name = goalDoc.getString("name");
                        Long limit = goalDoc.getLong("limit");

                        if (limit != null) {
                            Long used = usageMap.get(pkg);
                            if (used != null && used > limit) {
                                sendNotification(pkg.hashCode(), name, used, limit);
                            }
                        }
                    }
                });

        return Result.success();
    }

    private void sendNotification(int id, String name, long used, long limit) {
        String channelId = "goal_notifications";
        NotificationManager manager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId, "Уведомления о целях", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Уведомления при превышении лимита");
            manager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), channelId)
                .setSmallIcon(R.drawable.ic_notification) // должен быть в drawable
                .setContentTitle("Лимит превышен!")
                .setContentText("Приложение " + name + ": " + used + "/" + limit + " мин")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        manager.notify(id, builder.build());
    }
}
