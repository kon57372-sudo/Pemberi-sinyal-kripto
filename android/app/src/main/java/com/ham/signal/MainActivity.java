package com.ham.signal;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.*;
import android.view.ViewGroup;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    TextView status, results;
    Button scanButton;

    static class Coin {
        String name, symbol;
        double price, change, ratio;
        int score, rank;
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(20, 28, 20, 12);
        root.setBackgroundColor(0xFFF5F7FA);

        TextView title = new TextView(this);
        title.setText("HAM SIGNAL");
        title.setTextSize(27);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(0xFF163A70);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Pemindai pasar kripto • Data CoinGecko");
        subtitle.setTextSize(14);
        root.addView(subtitle);

        status = new TextView(this);
        status.setText("Siap memindai hingga 1.000 koin.");
        status.setTextSize(14);
        status.setPadding(0, 16, 0, 12);
        root.addView(status);

        scanButton = new Button(this);
        scanButton.setText("PINDAI 1.000 KOIN");
        root.addView(scanButton);

        ScrollView scroll = new ScrollView(this);
        results = new TextView(this);
        results.setTextSize(14);
        results.setTextColor(0xFF17212F);
        results.setPadding(4, 16, 4, 24);
        scroll.addView(results);
        root.addView(scroll, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
        scanButton.setOnClickListener(v -> scanMarket());
    }

    void scanMarket() {
        scanButton.setEnabled(false);
        status.setText("Mengambil data pasar. Mohon tunggu...");
        results.setText("");

        new Thread(() -> {
            try {
                List<Coin> coins = new ArrayList<>();
                String[] stable = {
                    "usdt","usdc","dai","busd","tusd","usde",
                    "usds","usdd","frax","fdusd","pyusd"
                };

                for (int page = 1; page <= 4; page++) {
                    String address =
                        "https://api.coingecko.com/api/v3/coins/markets"
                        + "?vs_currency=usd&order=market_cap_desc"
                        + "&per_page=250&page=" + page
                        + "&sparkline=false&price_change_percentage=24h";

                    HttpURLConnection conn =
                        (HttpURLConnection) new URL(address).openConnection();
                    conn.setConnectTimeout(20000);
                    conn.setReadTimeout(25000);
                    conn.setRequestProperty("User-Agent", "HAMSignal/1.0");

                    int code = conn.getResponseCode();
                    if (code != 200) {
                        conn.disconnect();
                        throw new Exception("CoinGecko HTTP " + code
                            + ". Coba lagi beberapa saat.");
                    }

                    StringBuilder body = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            body.append(line);
                        }
                    }
                    conn.disconnect();

                    JSONArray data = new JSONArray(body.toString());
                    for (int i = 0; i < data.length(); i++) {
                        JSONObject obj = data.getJSONObject(i);
                        String sym = obj.optString("symbol", "").toLowerCase(Locale.ROOT);
                        boolean isStable = false;
                        for (String s : stable) {
                            if (s.equals(sym)) isStable = true;
                        }
                        if (isStable) continue;

                        double price = obj.optDouble("current_price", 0);
                        double cap = obj.optDouble("market_cap", 0);
                        double volume = obj.optDouble("total_volume", 0);
                        double change = obj.optDouble("price_change_percentage_24h", 0);
                        int rank = obj.optInt("market_cap_rank", 999999);

                        if (price <= 0 || cap <= 0 || volume < 25000) continue;
                        if (change < -5 || change > 5) continue;

                        double ratio = volume / cap * 100.0;
                        if (ratio < 2) continue;

                        int score = 50;
                        if (change > 0.2 && change <= 3) score += 15;
                        else if (change > -2 && change <= 0.2) score += 10;
                        if (ratio >= 20) score += 20;
                        else if (ratio >= 10) score += 15;
                        else if (ratio >= 5) score += 8;
                        if (rank <= 300) score += 5;

                        Coin c = new Coin();
                        c.name = obj.optString("name", sym);
                        c.symbol = sym.toUpperCase(Locale.ROOT);
                        c.price = price;
                        c.change = change;
                        c.ratio = ratio;
                        c.score = score;
                        c.rank = rank;
                        coins.add(c);
                    }

                    
                        final int currentPage = page;
                              runOnUiThread(() ->
                                 status.setText("Memindai halaman " + currentPage + " dari 4..."));

                             if (page < 4) Thread.sleep(2500);

                        

                    
                }

                Collections.sort(coins, (a, b) ->
                    Integer.compare(b.score, a.score));

                StringBuilder report = new StringBuilder();
                report.append("KANDIDAT: ").append(coins.size())
                    .append("\nData: hingga 1.000 koin\n")
                    .append("Skor adalah indikator heuristik, bukan probabilitas.\n\n");

                int count = Math.min(50, coins.size());
                for (int i = 0; i < count; i++) {
                    Coin c = coins.get(i);
                    String label = c.change > 0.05 ? "POTENSI NAIK"
                        : c.change < -0.05 ? "POTENSI TURUN" : "PANTAU";

                    report.append(i + 1).append(". ")
                        .append(c.name).append(" (").append(c.symbol).append(")\n")
                        .append("   ").append(label)
                        .append(" • Skor ").append(c.score).append("/90\n")
                        .append("   Harga: $").append(
                            String.format(Locale.US, "%.8f", c.price)).append("\n")
                        .append("   Perubahan 24 jam: ")
                        .append(String.format(Locale.US, "%.2f%%", c.change))
                        .append(" • Volume/kapitalisasi: ")
                        .append(String.format(Locale.US, "%.2f%%", c.ratio))
                        .append("\n\n");
                }

                final String output = report.toString();
                runOnUiThread(() -> {
                    status.setText("Selesai. Kandidat ditemukan: " + coins.size());
                    results.setText(output);
                    scanButton.setEnabled(true);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    status.setText("Pemindaian gagal: " + e.getMessage());
                    results.setText(
                        "Periksa koneksi internet. Jika muncul HTTP 429, "
                        + "tunggu sebelum mencoba lagi.");
                    scanButton.setEnabled(true);
                });
            }
        }).start();
    }
}
