package com.example.hazardapp;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class AboutActivity extends AppCompatActivity {

    private static class Member {
        String name, details, task;
        int photoRes;
        Member(String name, String details, String task, int photoRes) {
            this.name = name;
            this.details = details;
            this.task = task;
            this.photoRes = photoRes;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        // Replace with your real group details
        Member[] team = {
                new Member("Muhammad Syamil Haqimi Bin Mahmud Shauqi", "2025112935 · CS251", "Mobile App Development", R.drawable.member1),
                new Member("Muhammad Aidil Bin Aznan", "2025112217 · CS251", "Web Server & Database", R.drawable.member2),
                new Member("Muhammad Zaim Irfan Bin Azizul", "2025302049 · CS251", "UI Design & About Page", R.drawable.member3),
                new Member("Muhammad Danish Ahza Bin Aziman", "2025179863 · CS251", "Report Writing", R.drawable.member4),
                new Member("Ahmad Adib Bin Murshidi", "2025137359 · CS251", "Video & GitHub", R.drawable.member5),
        };

        LinearLayout container = findViewById(R.id.teamContainer);
        LayoutInflater inflater = LayoutInflater.from(this);

        for (Member m : team) {
            View row = inflater.inflate(R.layout.item_team_member, container, false);
            ((TextView) row.findViewById(R.id.txtName)).setText(m.name);
            ((TextView) row.findViewById(R.id.txtDetails)).setText(m.details);
            ((TextView) row.findViewById(R.id.txtTask)).setText(m.task);
            if (m.photoRes != 0) {
                ((ImageView) row.findViewById(R.id.imgPhoto)).setImageResource(m.photoRes);
            }
            container.addView(row);
        }

        TextView githubLink = findViewById(R.id.githubLink);
        githubLink.setOnClickListener(v -> {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/yourgroup/hazardapp"));
            startActivity(browserIntent);
        });
    }
}