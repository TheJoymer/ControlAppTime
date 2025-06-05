// UsageUtils.java
package com.example.appcontroltime.utils;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;

import java.util.*;

public class UsageUtils {
    public static Map<String, Long> getTodayUsage(Context context) {
        UsageStatsManager usageStatsManager = (UsageStatsManager)
                context.getSystemService(Context.USAGE_STATS_SERVICE);

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);

        long start = cal.getTimeInMillis();
        long end = System.currentTimeMillis();

        List<UsageStats> stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end);

        Map<String, Long> result = new HashMap<>();
        for (UsageStats stat : stats) {
            long total = stat.getTotalTimeInForeground() / 60000;
            if (total > 0) {
                result.put(stat.getPackageName(), total);
            }
        }
        return result;
    }
}
