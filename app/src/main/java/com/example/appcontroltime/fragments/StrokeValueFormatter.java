package com.example.appcontroltime.fragments;

import android.graphics.Canvas;

import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.utils.ViewPortHandler;

public interface StrokeValueFormatter {
    void drawValue(Canvas c, String valueText, float x, float y, PieEntry entry, int dataSetIndex, ViewPortHandler vpHandler);
}
