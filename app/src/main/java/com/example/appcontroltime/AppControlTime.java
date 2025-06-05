package com.example.appcontroltime;


import android.app.Application;

import com.google.firebase.FirebaseApp;

public class AppControlTime extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        FirebaseApp.initializeApp(this);

    }
}