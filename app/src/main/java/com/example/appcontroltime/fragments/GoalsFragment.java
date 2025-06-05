package com.example.appcontroltime.fragments;

import android.annotation.SuppressLint;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.*;

import com.example.appcontroltime.R;
import com.example.appcontroltime.adapters.GoalAdapter;
import com.example.appcontroltime.models.GoalItem;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;

import java.text.SimpleDateFormat;
import java.util.*;

public class GoalsFragment extends Fragment {

    private RecyclerView recyclerView;
    private GoalAdapter adapter;
    private List<GoalItem> goalList = new ArrayList<>();
    private FirebaseFirestore db;
    private String uid;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_goals, container, false);

        recyclerView = view.findViewById(R.id.goalsRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new GoalAdapter(goalList, this::onGoalClick);
        recyclerView.setAdapter(adapter);

        Button btnAdd = view.findViewById(R.id.btnAddGoal);
        btnAdd.setOnClickListener(v -> showAddGoalDialog());

        db = FirebaseFirestore.getInstance();
        uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        loadGoals();

        return view;
    }

    private void loadGoals() {
        goalList.clear();
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        db.collection("users").document(uid).collection("goals")
                .get().addOnSuccessListener(goalSnap -> {
                    for (DocumentSnapshot doc : goalSnap) {
                        String pkg = doc.getId();
                        String name = doc.getString("name");
                        Long limit = doc.getLong("limit");

                        if (name != null && limit != null) {
                            db.collection("users").document(uid)
                                    .collection("usage").document(today)
                                    .collection("apps").document(pkg)
                                    .get().addOnSuccessListener(usageDoc -> {
                                        long used = 0;
                                        if (usageDoc.exists()) {
                                            Long u = usageDoc.getLong("usageTime");
                                            if (u != null) used = u;
                                        }
                                        boolean exceeded = used > limit;
                                        GoalItem goal = new GoalItem(pkg, name, limit.intValue(), (int) used, exceeded);
                                        goalList.add(goal);
                                        adapter.notifyDataSetChanged();
                                        if (exceeded) showNotification(goal);
                                    });
                        }
                    }
                });
    }

    private void showAddGoalDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Добавить цель");

        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_add_goal, null);
        builder.setView(dialogView);

        Spinner spinner = dialogView.findViewById(R.id.spinnerApps);
        EditText limitInput = dialogView.findViewById(R.id.inputLimit);

        List<String> appLabels = new ArrayList<>();
        List<String> appPackages = new ArrayList<>();

        PackageManager pm = requireContext().getPackageManager();
        @SuppressLint("QueryPermissionsNeeded") List<ApplicationInfo> allApps = pm.getInstalledApplications(PackageManager.GET_META_DATA);

        // Получаем даты: сегодня и вчера
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        Calendar calendar = Calendar.getInstance();
        String today = sdf.format(calendar.getTime());
        calendar.add(Calendar.DAY_OF_YEAR, -1);
        String yesterday = sdf.format(calendar.getTime());

        Set<String> usedPackages = new HashSet<>();
        CollectionReference usageRef = db.collection("users").document(uid).collection("usage");

        usageRef.document(today).collection("apps").get().addOnSuccessListener(todaySnap -> {
            for (DocumentSnapshot doc : todaySnap.getDocuments()) {
                usedPackages.add(doc.getId());
            }

            usageRef.document(yesterday).collection("apps").get().addOnSuccessListener(yesterdaySnap -> {
                for (DocumentSnapshot doc : yesterdaySnap.getDocuments()) {
                    usedPackages.add(doc.getId());
                }

                for (String pkg : usedPackages) {
                    try {
                        ApplicationInfo appInfo = pm.getApplicationInfo(pkg, 0);
                        String label = pm.getApplicationLabel(appInfo).toString();
                        appLabels.add(label + " (" + pkg + ")");
                    } catch (PackageManager.NameNotFoundException e) {
                        appLabels.add(pkg); // fallback: только packageName
                    }
                    appPackages.add(pkg);
                }

                if (appLabels.isEmpty()) {
                    Toast.makeText(getContext(), "Нет недавно использованных приложений", Toast.LENGTH_SHORT).show();
                }

                ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, appLabels);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinner.setAdapter(adapter);
            });
        });

        builder.setPositiveButton("Сохранить", (dialog, which) -> {
            int index = spinner.getSelectedItemPosition();
            if (index < 0 || index >= appPackages.size()) {
                Toast.makeText(getContext(), "Выберите приложение", Toast.LENGTH_SHORT).show();
                return;
            }

            String selectedLabel = appLabels.get(index);
            String selectedPackage = appPackages.get(index);
            String limitStr = limitInput.getText().toString().trim();

            if (!limitStr.isEmpty()) {
                int limit = Integer.parseInt(limitStr);

                Map<String, Object> goalData = new HashMap<>();
                goalData.put("name", selectedLabel);
                goalData.put("limit", limit);

                db.collection("users").document(uid)
                        .collection("goals").document(selectedPackage)
                        .set(goalData)
                        .addOnSuccessListener(unused ->
                                Toast.makeText(getContext(), "Цель добавлена", Toast.LENGTH_SHORT).show());
            }
        });

        builder.setNegativeButton("Отмена", null);
        builder.create().show();
    }

    private void onGoalClick(GoalItem item) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Редактировать цель");

        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_edit_goal, null);
        builder.setView(view);

        TextView name = view.findViewById(R.id.editGoalAppName);
        EditText inputLimit = view.findViewById(R.id.editGoalLimit);

        name.setText(item.name);
        inputLimit.setText(String.valueOf(item.limit));

        builder.setPositiveButton("Сохранить", (dialog, which) -> {
            String limitStr = inputLimit.getText().toString().trim();
            if (!limitStr.isEmpty()) {
                int newLimit = Integer.parseInt(limitStr);
                db.collection("users").document(uid)
                        .collection("goals").document(item.packageName)
                        .update("limit", newLimit)
                        .addOnSuccessListener(v -> {
                            Toast.makeText(getContext(), "Цель обновлена", Toast.LENGTH_SHORT).show();
                            loadGoals();
                        });
            }
        });

        builder.setNegativeButton("Удалить", (dialog, which) -> {
            db.collection("users").document(uid)
                    .collection("goals").document(item.packageName)
                    .delete()
                    .addOnSuccessListener(v -> {
                        Toast.makeText(getContext(), "Цель удалена", Toast.LENGTH_SHORT).show();
                        loadGoals();
                    });
        });

        builder.setNeutralButton("Отмена", null);
        builder.show();
    }

    private void showNotification(GoalItem item) {

    }
}
