package com.example.appcontroltime.models;

public class AppUsage {
    private String packageName;
    private long timeInForeground;
    private String appName;

    public AppUsage(String packageName, long timeInForeground, String appName) {
        this.packageName = packageName;
        this.timeInForeground = timeInForeground;
        this.appName = appName;
    }

    public AppUsage(String packageName, long timeInForeground) {
        this.packageName = packageName;
        this.timeInForeground = timeInForeground;
        this.appName = packageName;
    }

    public String getPackageName() {
        return packageName;
    }

    public long getTimeInForeground() {
        return timeInForeground;
    }

}
