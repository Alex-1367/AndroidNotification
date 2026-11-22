package com.vbnet.gewerbenotification;

import android.app.Dialog;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private static final String SUPABASE_URL = Secrets.SUPABASE_URL;
    private static final String ANON_KEY = Secrets.ANON_KEY;
    private static final String TAG = "MainActivity";

    private final OkHttpClient client = new OkHttpClient();
    private LinearLayout container;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        container = findViewById(R.id.container);

        // Notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 101);
        }

        // FCM token (for your reference, not used for display)
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (task.isSuccessful()) Log.d(TAG, "Token: " + task.getResult());
        });

        loadRequests();
    }

    /** Fetch Request list, newest first */
    private void loadRequests() {
        String url = SUPABASE_URL + "/rest/v1/Request?select=*&order=created_at.desc";
        Request req = new Request.Builder()
                .url(url)
                .addHeader("apikey", ANON_KEY)
                .addHeader("Authorization", "Bearer " + ANON_KEY)
                .build();

        client.newCall(req).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                showError("Request list failed: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    showError("HTTP " + response.code() + ": " + response.body().string());
                    return;
                }
                String json = response.body().string();
                Log.d("SUPABASE_LIST", json);

                try {
                    JSONArray arr = new JSONArray(json);
                    runOnUiThread(() -> container.removeAllViews());
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.getJSONObject(i);
                        long id = obj.getLong("id");
                        String txt = obj.optString("txt", "");
                        String createdAt = obj.optString("created_at", "");
                        runOnUiThread(() -> addRequestCard(id, txt, createdAt));
                    }
                } catch (Exception e) {
                    showError("Parse error: " + e.getMessage());
                }
            }
        });
    }

    /** One card per Request: header + a dedicated image container */
    private void addRequestCard(long requestId, String txt, String createdAt) {
        // Header
        TextView header = new TextView(this);
        header.setText("#" + requestId + "   " + formatDate(createdAt) + "\n" + txt);
        header.setTextSize(16);
        header.setPadding(0, 24, 0, 8);
        container.addView(header);

        // Divider
        View divider = new View(this);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 2));
        divider.setBackgroundColor(0xFFCCCCCC);
        container.addView(divider);

        // Dedicated image container for THIS request
        LinearLayout imageContainer = new LinearLayout(this);
        imageContainer.setOrientation(LinearLayout.VERTICAL);
        imageContainer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        container.addView(imageContainer);

        loadImages(requestId, imageContainer);
    }

    /** Fetch image URLs for a given request and display them in its own container */
    private void loadImages(long requestId, LinearLayout imageContainer) {
        String url = SUPABASE_URL + "/rest/v1/request_images?request_id=eq."
                + requestId + "&select=image_url&order=id.asc";

        Request req = new Request.Builder()
                .url(url)
                .addHeader("apikey", ANON_KEY)
                .addHeader("Authorization", "Bearer " + ANON_KEY)
                .build();

        client.newCall(req).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("SUPABASE", "Images failed for " + requestId, e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) return;
                String json = response.body().string();
                try {
                    JSONArray arr = new JSONArray(json);
                    for (int i = 0; i < arr.length(); i++) {
                        String imageUrl = arr.getJSONObject(i).getString("image_url");
                        runOnUiThread(() -> addImageView(imageUrl, imageContainer));
                    }
                } catch (Exception e) {
                    Log.e("SUPABASE", "Image parse error", e);
                }
            }
        });
    }

    /** Add an ImageView into the given container, with click-to-enlarge */
    private void addImageView(String imageUrl, LinearLayout imageContainer) {
        ImageView iv = new ImageView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 600);
        params.setMargins(0, 8, 0, 16);
        iv.setLayoutParams(params);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setOnClickListener(v -> showFullImage(imageUrl));
        imageContainer.addView(iv);

        Glide.with(this)
                .load(imageUrl)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_report_image)
                .into(iv);
    }
    /** Full-size image in a simple dialog */
    private void showFullImage(String imageUrl) {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        ImageView full = new ImageView(this);
        full.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        full.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Glide.with(this).load(imageUrl).into(full);
        full.setOnClickListener(v -> dialog.dismiss());
        dialog.setContentView(full);
        dialog.show();
    }

    private void showError(String msg) {
        Log.e("SUPABASE", msg);
        runOnUiThread(() -> {
            TextView err = new TextView(this);
            err.setText(msg);
            err.setPadding(0, 16, 0, 16);
            err.setTextColor(0xFFFF0000);
            container.addView(err);
        });
    }
    private String formatDate(String isoString) {
        try {
            // Parse ISO 8601 (with optional fractional seconds)
            SimpleDateFormat input = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            input.setTimeZone(TimeZone.getTimeZone("UTC"));
            // Truncate fractional seconds before parsing
            String cleaned = isoString.split("\\.")[0];
            java.util.Date date = input.parse(cleaned);

            // Format for display
            SimpleDateFormat output = new SimpleDateFormat("MMM d, EEEE, HH:mm", Locale.getDefault());
            output.setTimeZone(TimeZone.getDefault()); // show in local time
            return output.format(date);
        } catch (Exception e) {
            return isoString; // fallback
        }
    }
}