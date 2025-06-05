package com.example.appcontroltime.adapters;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.appcontroltime.R;
import com.example.appcontroltime.models.AppUsage;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class UsageAdapter extends RecyclerView.Adapter<UsageAdapter.ViewHolder> {
    private List<AppUsage> appList;
    private Context context;
    public UsageAdapter(List<AppUsage> appList, Context context) {
        this.appList = appList;
        this.context = context;
    }

    @NonNull
    @Override
    public UsageAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_app_usage, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UsageAdapter.ViewHolder holder, int position) {
        AppUsage app = appList.get(position);
        String pkg = app.getPackageName();
        long time = app.getTimeInForeground();
        String appName;
        PackageManager pm = context.getPackageManager();
        try {
            appName = pm.getApplicationLabel(pm.getApplicationInfo(pkg, PackageManager.GET_META_DATA)).toString();
            CharSequence label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0));
            Drawable icon = pm.getApplicationIcon(pkg);

            holder.appName.setText(label);
            holder.appName.setText(appName);
            holder.appIcon.setImageDrawable(icon);
        } catch (Exception e) {
            holder.appName.setText(pkg);
        }

        holder.usageTime.setText(formatTime(time));
    }

    @Override
    public int getItemCount() {
        return appList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView appName, usageTime;
        ImageView appIcon;

        public ViewHolder(View itemView) {
            super(itemView);
            appName = itemView.findViewById(R.id.appName);
            usageTime = itemView.findViewById(R.id.usageTime);
            appIcon = itemView.findViewById(R.id.appIcon);
        }
    }

    private String formatTime(long millis) {
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
        long hours = minutes / 60;
        long mins = minutes % 60;
        return hours + " ч " + mins + " мин";
    }
}