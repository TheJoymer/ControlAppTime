package com.example.appcontroltime.adapters;

import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.appcontroltime.R;
import com.example.appcontroltime.models.GoalItem;

import java.util.List;

public class GoalAdapter extends RecyclerView.Adapter<GoalAdapter.GoalViewHolder> {
    private final List<GoalItem> items;
    private final OnGoalClickListener listener;
    public interface OnGoalClickListener {
        void onGoalClick(GoalItem item);
    }

    public GoalAdapter(List<GoalItem> items, OnGoalClickListener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public GoalViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_goal, parent, false);
        return new GoalViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GoalViewHolder holder, int position) {
        GoalItem item = items.get(position);
        holder.name.setText(item.name);
        holder.info.setText("Лимит: " + item.limit + " мин | Использовано: " + item.used + " мин");

        if (item.exceeded) {
            holder.status.setText("❌ Превышен");
            holder.status.setTextColor(0xFFE53935);
        } else {
            holder.status.setText("✅ Норма");
            holder.status.setTextColor(0xFF43A047);
        }

        holder.itemView.setOnClickListener(v -> listener.onGoalClick(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class GoalViewHolder extends RecyclerView.ViewHolder {
        TextView name, info, status;

        GoalViewHolder(View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.goalAppName);
            info = itemView.findViewById(R.id.goalInfo);
            status = itemView.findViewById(R.id.goalStatus);
        }
    }
}
