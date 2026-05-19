package ru.ytkab0bp.sapil;

import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        PlaceholderRepo.INSTANCE.getComments(10, response -> getWindow().getDecorView().post(() -> Toast.makeText(this, "Got comments: " + response, Toast.LENGTH_SHORT).show()));
        PlaceholderRepo.INSTANCE.getPosts(response -> {
            System.out.println("Posts" + response);
        });
    }
}