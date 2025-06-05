package com.example.appcontroltime.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.example.appcontroltime.R;
import com.example.appcontroltime.activity.LoginActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.*;

public class ProfileFragment extends Fragment {

    private TextView userNameTextView, userEmailTextView, userPhoneTextView;
    private Button logoutButton;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        userNameTextView = view.findViewById(R.id.userNameTextView);
        userEmailTextView = view.findViewById(R.id.userEmailTextView);
        userPhoneTextView = view.findViewById(R.id.userPhoneTextView);
        logoutButton = view.findViewById(R.id.logoutButton);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            String uid = user.getUid();

            db.collection("users").document(uid).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            String name = documentSnapshot.getString("name");
                            String surname = documentSnapshot.getString("surname");
                            String phone = documentSnapshot.getString("phone");
                            String email = user.getEmail();

                            userNameTextView.setText(name + " " + surname);
                            userEmailTextView.setText("Email: " + email);
                            userPhoneTextView.setText("Телефон: " + phone);
                        } else {
                            userNameTextView.setText("Пользователь не найден");
                            userEmailTextView.setText("");
                            userPhoneTextView.setText("");
                        }
                    })
                    .addOnFailureListener(e -> {
                        userNameTextView.setText("Ошибка");
                        userEmailTextView.setText("Ошибка загрузки данных");
                        userPhoneTextView.setText(e.getMessage());
                    });
        }

        logoutButton.setOnClickListener(v -> {
            auth.signOut();
            startActivity(new Intent(getActivity(), LoginActivity.class));
            requireActivity().finish();
        });

        return view;
    }
}
