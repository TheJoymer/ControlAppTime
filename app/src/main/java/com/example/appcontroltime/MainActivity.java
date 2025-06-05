package com.example.appcontroltime;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.example.appcontroltime.services.ForegroundAppUsageService;
import com.example.appcontroltime.workers.UsageLimitWorker;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.example.appcontroltime.fragments.*;

import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {
    BottomNavigationView bottomNavigation;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(new Intent(this, ForegroundAppUsageService.class));
        } else {
            startService(new Intent(this, ForegroundAppUsageService.class));
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bottomNavigation = findViewById(R.id.bottom_navigation);
        loadFragment(new ProfileFragment());
        bottomNavigation.setOnNavigationItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            int id = item.getItemId();

            if (id == R.id.nav_profile) selectedFragment = new ProfileFragment();
            else if (id == R.id.nav_usage) selectedFragment = new UsageFragment();
            else if (id == R.id.nav_stats) selectedFragment = new StatsFragment();
            else if (id == R.id.nav_goals) selectedFragment = new GoalsFragment();

            if (selectedFragment != null) {
                loadFragment(selectedFragment);
                return true;
            }
            return false;
        });
        PeriodicWorkRequest workRequest =
                new PeriodicWorkRequest.Builder(UsageLimitWorker.class, 1, TimeUnit.MINUTES)
                        .build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "usageCheck", ExistingPeriodicWorkPolicy.REPLACE, workRequest);
    }
    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.frame_container, fragment)
                .commit();}
}
