package com.ham.signal;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {

    private LinearLayout root;
    private TextView statusText;
    private Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    
    private String geminiApiKey = ""; 

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        prefs = getSharedPreferences("ai_studio_prefs", Context.MODE_PRIVATE);
        geminiApiKey = prefs.getString("gemini_api_key", "");
        
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF0B0F17); 
        
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 48, 32, 32);

        TextView title = new TextView(this);
        title.setText("🐉 DONGHUA AI STUDIO");
        title.setTextColor(0xFFE5C07B); 
        title.setTextSize(26);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);

        TextView subTitle = new TextView(this);
        subTitle.setText("Direct AI Engine (No Server)");
        subTitle.setTextColor(0xFF91A0B5);
        subTitle.setTextSize(14);
        subTitle.setGravity(Gravity.CENTER);
        subTitle.setPadding(0, 8, 0, 24);
        root.addView(subTitle);

        statusText = new TextView(this);
        statusText.setText(geminiApiKey.isEmpty() ? "Status: API Key Belum Diatur" : "Status: Siap Produksi");
        statusText.setTextColor(geminiApiKey.isEmpty() ? 0xFFF6465D : 0xFF16C784);
        statusText.setPadding(16, 16, 16, 16);
        statusText.setBackgroundColor(0xFF1B2433);
        root.addView(statusText);

        addSection("FASE 1: PRA-PRODUKSI");
        addFeatureButton("1. AI Story Generator", this::showStoryDialog);
        addFeatureButton("2. Character Studio", this::showCharacterDialog);
        
        addSection("PENGATURAN API");
        addFeatureButton("3. Set API Key Gemini", this::showApiKeyDialog);
        addFeatureButton("4. Tes Koneksi AI", this::testConnection);

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
        btn.setBackgroundColor(0xFF1B2433);
        btn.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        btn.setPadding(32, 0, 0, 0);
        
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 140);
        lp.bottomMargin = 16;
        btn.setLayoutParams(lp);
        btn.setOnClickListener(v -> action.run());
        root.addView(btn);
    }

    void showApiKeyDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Masukkan Gemini API Key");

        EditText input = new EditText(this);
        input.setHint("Contoh: AQ.Ab8RN6...");
        input.setText(geminiApiKey); 
        builder.setView(input);

        builder.setPositiveButton("Simpan", (dialog, which) -> {
            String key = input.getText().toString().trim();
            if(!key.isEmpty()) {
                geminiApiKey = key;
                prefs.edit().putString("gemini_api_key", key).apply();
                statusText.setText("Status: Siap Produksi");
                statusText.setTextColor(0xFF16C784);
                toast("API Key Disimpan!");
            } else {
                toast("API Key kosong.");
            }
        });
        builder.setNegativeButton("Batal", null);
        builder.show();
    }

    void showStoryDialog() {
        if(geminiApiKey.isEmpty()) {
            toast("Isi API Key dulu di menu nomor 3!");
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Buat Ide Cerita Cultivation");

        EditText input = new EditText(this);
        input.setHint("Contoh: Pemuda desa menemukan cincin kuno...");
        builder.setView(input);

        builder.setPositiveButton("Generate", (dialog, which) -> {
            String prompt = input.getText().toString();
            if(prompt.isEmpty()) return;
            statusText.setText("Menghubungi AI Gemini...");
            statusText.setTextColor(0xFFFFC857);
            
            new Thread(() -> {
                try {
                    String fullPrompt = "Buatkan sinopsis cerita donghua xianxia berdasarkan ide berikut: " + prompt + ". Buatkan 3 paragraf epik.";
                    String story = callGeminiAPI(fullPrompt);
                    handler.post(() -> {
                        statusText.setText("Cerita Berhasil Dibuat!");
                        statusText.setTextColor(0xFF16C784);
                        showResultDialog("Script Cerita", story);
                    });
                } catch (Exception e) {
                    handler.post(() -> {
                        statusText.setText("Error AI");
                        statusText.setTextColor(0xFFF6465D);
                        showResultDialog("Gagal", e.getMessage());
                    });
                }
            }).start();
        });
        builder.setNegativeButton("Batal", null);
        builder.show();
    }

    void showCharacterDialog() {
        if(geminiApiKey.isEmpty()) {
            toast("Isi API Key dulu di menu nomor 3!");
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Deskripsi Karakter 3D");

        EditText input = new EditText(this);
        input.setHint("Contoh: Pria, rambut putih, jubah biru...");
        builder.setView(input);

        builder.setPositiveButton("Generate", (dialog, which) -> {
            String prompt = input.getText().toString();
            statusText.setText("Mendesain Karakter...");
            statusText.setTextColor(0xFFFFC857);
            
            new Thread(() -> {
                try {
                    String fullPrompt = "Buatkan prompt visual detail untuk AI image generator karakter donghua 3D: " + prompt;
                    String result = callGeminiAPI(fullPrompt);
                    handler.post(() -> {
                        statusText.setText("Desain Selesai!");
                        statusText.setTextColor(0xFF16C784);
                        showResultDialog("Prompt Desain", result);
                    });
                } catch (Exception e) {
                    handler.post(() -> {
                        statusText.setText("Error AI");
                        statusText.setTextColor(0xFFF6465D);
                        showResultDialog("Gagal", e.getMessage());
                    });
                }
            }).start();
        });
        builder.setNegativeButton("Batal", null);
        builder.show();
    }

    void testConnection() {
        if(geminiApiKey.isEmpty()) {
            toast("Isi API Key dulu!");
            return;
        }
        
        statusText.setText("Mengetes koneksi...");
        new Thread(() -> {
            try {
                String res = callGeminiAPI("Halo, balas satu kata: SUKSES");
                handler.post(() -> {
                    statusText.setText("Koneksi Berhasil!");
                    showResultDialog("Tes Koneksi", "AI Merespon: " + res);
                });
            } catch (Exception e) {
                handler.post(() -> {
                    statusText.setText("Error Koneksi");
                    statusText.setTextColor(0xFFF6465D);
                    showResultDialog("Gagal", e.getMessage());
                });
            }
        }).start();
    }

    String callGeminiAPI(String prompt) throws Exception {
        String urlStr = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent";
        
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-goog-api-key", geminiApiKey); 
        conn.setDoOutput(true);

        String jsonInputString = "{\"contents\": [{\"parts\":[{\"text\": \"" + prompt.replace("\"", "\\\"") + "\"}]}]}";

        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = jsonInputString.getBytes("utf-8");
            os.write(input, 0, input.length);
        }

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
            StringBuilder errorResponse = new StringBuilder();
            String line;
            while ((line = errorReader.readLine()) != null) {
                errorResponse.append(line);
            }
            throw new Exception("HTTP " + responseCode + " : " + errorResponse.toString());
        }

        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
        StringBuilder response = new StringBuilder();
        String responseLine;
        while ((responseLine = br.readLine()) != null) {
            response.append(responseLine.trim());
        }
        
        JSONObject jsonObject = new JSONObject(response.toString());
        JSONArray candidates = jsonObject.getJSONArray("candidates");
        JSONObject content = candidates.getJSONObject(0).getJSONObject("content");
        JSONArray parts = content.getJSONArray("parts");
        return parts.getJSONObject(0).getString("text");
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
