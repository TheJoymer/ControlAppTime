package com.example.appcontroltime.models;

public class GoalItem {
    public String packageName;
    public String name;
    public int limit;
    public int used;
    public boolean exceeded;

    public GoalItem(String packageName, String name, int limit, int used, boolean exceeded) {
        this.packageName = packageName;
        this.name = name;
        this.limit = limit;
        this.used = used;
        this.exceeded = exceeded;
    }
}
