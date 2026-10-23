package com.ham.signal;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {

    // UBAH URL INI NANTI SETELAH SERVER PYTHON ANDA ONLINE
    private static final String BACKEND_URL = "https://your-backend-url.com/api";

    private LinearLayout root;
    private TextView statusText;
    private Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Desain Tema Gelap Premium (Dark Navy & Gold)
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF0B0F17); // Warna Latar
        
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 48, 32, 32);

        // Header
        TextView title = new TextView(this);
        title.setText("🐉 DONGHUA AI STUDIO");
        title.setTextColor(0xFFE5C07B); // Emas
        title.setTextSize(26);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);

        TextView subTitle = new TextView(this);
        subTitle.setText("Cinematic 3D Production Engine");
        subTitle.setTextColor(0xFF91A0B5);
        subTitle.setTextSize(14);
        subTitle.setGravity(Gravity.CENTER);
        subTitle.setPadding(0, 8, 0, 24);
        root.addView(subTitle);

        statusText = new TextView(this);
        statusText.setText("Status: Siap Produksi");
        statusText.setTextColor(0xFF16C784); // Hijau
        statusText.setPadding(16, 16, 16, 16);
        statusText.setBackgroundColor(0xFF1B2433);
        root.addView(statusText);

        // --- TOMBOL FITUR STUDIO ---
        addSection("FASE 1: PRA-PRODUKSI");
        addFeatureButton("1. AI Story Generator (Xianxia)", this::showStoryDialog);
        addFeatureButton("2. Character Studio (Desain)", this::showCharacterDialog);
        addFeatureButton("3. World Builder (Lokasi)", () -> toast("Fitur ini memerlukan integrasi Stable Diffusion (ComfyUI)."));

        addSection("FASE 2: PRODUKSI VIDEO");
        addFeatureButton("4. Generate Video Adegan (I2V)", this::showVideoDialog);
        addFeatureButton("5. Voice & Audio (TTS)", () -> toast("Fitur ini memerlukan API Text-to-Speech (Confucius/VITS)."));

        addSection("FASE 3: POST-PRODUKSI");
        addFeatureButton("6. Timeline & Render MP4", () -> toast("Gunakan FFmpeg (RxFFmpeg) untuk menggabungkan klip video di HP."));
        addFeatureButton("7. Pengaturan API Key", () -> toast("Simpan API Key Anda dengan aman (SharedPreferences)."));

        scroll.addView(root);
        setContentView(scroll);
    }

    void addSection(String title) {
        TextView section = new TextView(this);
        section.setText(title);
        section.setTextColor(0xFFE5C07B);
        section.setTextSize(14);
        section.setTypeface(null, android.graphics.Typeface.BOLD);
        section.setPadding(0, 32, 0, 16);
        root.addView(section);
    }

    void addFeatureButton(String text, Runnable action) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setAllCaps(false);
        btn.setTextColor(0xFFFFFFFF);
        btn.setBackgroundColor(0xFF1B2433); // Panel Abu Gelap
        btn.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        btn.setPadding(32, 0, 0, 0);
        
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 140);
        lp.bottomMargin = 16;
        btn.setLayoutParams(lp);
        btn.setOnClickListener(v -> action.run());
        root.addView(btn);
    }

    // ===================== DIALOG FITUR =====================

    void showStoryDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Buat Ide Cerita Cultivation");

        EditText input = new EditText(this);
        input.setHint("Contoh: Pemuda desa yang menemukan cincin kuno...");
        builder.setView(input);

        builder.setPositiveButton("Generate Cerita", (dialog, which) -> {
            String prompt = input.getText().toString();
            if(prompt.isEmpty()) return;
            statusText.setText("Menghubungi AI Storyteller...");
            statusText.setTextColor(0xFFFFC857);
            
            new Thread(() -> {
                try {
                    // Mengirim request ke Backend Python (FastAPI)
                    String jsonResponse = postToBackend("/story", "{\"prompt\":\"" + prompt + "\"}");
                    JSONObject obj = new JSONObject(jsonResponse);
                    String story = obj.optString("story", "Gagal generate.");
                    
                    handler.post(() -> {
                        statusText.setText("Cerita Berhasil Dibuat!");
                        statusText.setTextColor(0xFF16C784);
                        showResultDialog("Script Cerita (Fase 1)", story);
                    });
                } catch (Exception e) {
                    handler.post(() -> {
                        statusText.setText("Error: " + e.getMessage());
                        statusText.setTextColor(0xFFF6465D);
                    });
                }
            }).start();
        });
        builder.setNegativeButton("Batal", null);
        builder.show();
    }

    void showCharacterDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Deskripsi Karakter 3D");

        EditText input = new EditText(this);
        input.setHint("Contoh: Pria, rambut putih, jubah biru, membawa pedang...");
        builder.setView(input);

        builder.setPositiveButton("Generate Karakter", (dialog, which) -> {
            toast("Mengirim prompt ke ComfyUI/Backend... (Cek Logcat)");
        });
        builder.setNegativeButton("Batal", null);
        builder.show();
    }

    void showVideoDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Generate Adegan Video (5s)");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);

        EditText imgUrl = new EditText(this);
        imgUrl.setHint("URL Gambar Karakter (Image-to-Video)");
        layout.addView(imgUrl);

        EditText motionPrompt = new EditText(this);
        motionPrompt.setHint("Gerakan: (Contoh: Menebas pedang, efek api)");
        layout.addView(motionPrompt);

        builder.setView(layout);
        builder.setPositiveButton("Render Video", (dialog, which) -> {
            statusText.setText("Mengirim tugas ke Render Queue (Wan 2.2)...");
            statusText.setTextColor(0xFFFFC857);
            
            new Thread(() -> {
                try {
                    String payload = String.format("{\"image_url\":\"%s\", \"prompt\":\"%s\"}", 
                            imgUrl.getText().toString(), motionPrompt.getText().toString());
                    String jsonResponse = postToBackend("/video", payload);
                    JSONObject obj = new JSONObject(jsonResponse);
                    String videoUrl = obj.optString("video_url", "Belum selesai.");
                    
                    handler.post(() -> {
                        statusText.setText("Render Selesai!");
                        statusText.setTextColor(0xFF16C784);
                        showResultDialog("Video Siap", "Tautan Video:\n" + videoUrl);
                    });
                } catch (Exception e) {
                    handler.post(() -> {
                        statusText.setText("Error: " + e.getMessage());
                        statusText.setTextColor(0xFFF6465D);
                    });
                }
            }).start();
        });
        builder.setNegativeButton("Batal", null);
        builder.show();
    }

    // ===================== KONEKSI KE SERVER PYTHON =====================

    String postToBackend(String endpoint, String jsonPayload) throws Exception {
        URL url = new URL(BACKEND_URL + endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonPayload.getBytes());
            os.flush();
        }

        if (conn.getResponseCode() != 200) throw new Exception("HTTP " + conn.getResponseCode());

        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) response.append(line);
        br.close();
        return response.toString();
    }

    void showResultDialog(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
