package com.ham.signal;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class MainActivity extends Activity {

    private final int BG = Color.rgb(10, 14, 22);
    private final int PANEL = Color.rgb(20, 28, 41);
    private final int PANEL2 = Color.rgb(30, 40, 56);
    private final int WHITE = Color.rgb(239, 244, 250);
    private final int MUTED = Color.rgb(145, 159, 179);
    private final int GREEN = Color.rgb(22, 199, 132);
    private final int RED = Color.rgb(246, 70, 93);
    private final int BLUE = Color.rgb(74, 133, 255);
    private final int GOLD = Color.rgb(245, 190, 75);

    private LinearLayout root, body, bottom;
    private TextView status;
    private EditText searchBox;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<Coin> coins = new ArrayList<>();
    private final Map<String, Double> livePrices = new HashMap<>();
    private final Map<String, Double> liveChanges = new HashMap<>();
    private final Map<String, Double> liveVolumes = new HashMap<>();
    private final Map<String, Double> minuteMomentum = new HashMap<>();
    private final Map<String, Double> previousMinutePrices = new HashMap<>();
    private final Map<String, Long> previousMinuteTimes = new HashMap<>();
    private final Map<String, Integer> symbolCounts = new HashMap<>();

    private OkHttpClient client;
    private WebSocket socket;

    private String page = "Home";
    private String query = "";
    private boolean loading = false;
    private boolean connected = false;
    private boolean destroyed = false;
    private long lastMessage = 0;
    private long lastRender = 0;

    private static class Coin {
        String id, name, symbol;
        double price, change, change1h, change7d;
        double marketCap, volume, high, low;
        ArrayList<Double> spark = new ArrayList<>();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        client = new OkHttpClient.Builder()
                .pingInterval(20, TimeUnit.SECONDS)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build();

        buildShell();
        fetchMarkets();
        connectBinance();
        handler.postDelayed(refreshTask, 60000);
    }

    private final Runnable refreshTask = new Runnable() {
        @Override public void run() {
            if (destroyed) return;
            fetchMarkets();
            handler.postDelayed(this, 60000);
        }
    };

    @Override
    protected void onDestroy() {
        destroyed = true;
        handler.removeCallbacksAndMessages(null);
        if (socket != null) socket.cancel();
        if (client != null) {
            client.dispatcher().cancelAll();
            client.connectionPool().evictAll();
            client.dispatcher().executorService().shutdown();
        }
        super.onDestroy();
    }

    private void connectBinance() {
        if (destroyed || client == null) return;

        Request request = new Request.Builder()
                .url("wss://stream.binance.com:9443/ws/!miniTicker@arr")
                .build();

        socket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response response) {
                connected = true;
                handler.post(() -> updateStatus());
            }

            @Override
            public void onMessage(WebSocket ws, String message) {
                try {
                    JSONArray arr = new JSONArray(message);
                    long now = System.currentTimeMillis();

                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.optJSONObject(i);
                        if (o == null) continue;

                        String pair = o.optString("s", "");
                        if (!pair.endsWith("USDT")) continue;

                        String symbol = pair.substring(0, pair.length() - 4)
                                .toLowerCase(Locale.US);

                        double price = o.optDouble("c", 0);
                        double open = o.optDouble("o", 0);
                        double volume = o.optDouble("q", 0);

                        if (price <= 0) continue;

                        synchronized (livePrices) {
                            Double prev = previousMinutePrices.get(symbol);
                            Long prevTime = previousMinuteTimes.get(symbol);

                            if (prev == null) {
                                previousMinutePrices.put(symbol, price);
                                previousMinuteTimes.put(symbol, now);
                            } else if (prevTime != null
                                    && now - prevTime >= 60000L) {
                                if (prev > 0) {
                                    minuteMomentum.put(symbol,
                                            (price / prev - 1.0) * 100.0);
                                }
                                previousMinutePrices.put(symbol, price);
                                previousMinuteTimes.put(symbol, now);
                            }

                            livePrices.put(symbol, price);

                            if (open > 0) {
                                liveChanges.put(symbol,
                                        (price / open - 1.0) * 100.0);
                            }

                            liveVolumes.put(symbol, volume);
                        }
                    }

                    lastMessage = now;
                    updateVisiblePage();
                } catch (Exception ignored) {
                    // Abaikan pesan yang tidak valid.
                }
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                connected = false;
                handler.post(() -> updateStatus());
                handler.postDelayed(() -> {
                    if (!destroyed) connectBinance();
                }, 5000);
            }

            @Override
            public void onClosed(WebSocket ws, int code, String reason) {
                connected = false;
                handler.post(() -> updateStatus());
                if (!destroyed) {
                    handler.postDelayed(() -> {
                        if (!destroyed) connectBinance();
                    }, 5000);
                }
            }
        });
    }

    private void updateStatus() {
        if (status == null) return;

        if (connected && System.currentTimeMillis() - lastMessage < 15000) {
            status.setText("● BINANCE LIVE  •  " + coins.size()
                    + " aset  •  data tambahan CoinGecko");
            status.setTextColor(GREEN);
        } else if (connected) {
            status.setText("● BINANCE TERHUBUNG • menunggu data");
            status.setTextColor(GOLD);
        } else {
            status.setText("○ Menyambungkan Binance • "
                    + coins.size() + " aset tersedia");
            status.setTextColor(MUTED);
        }
    }

    private void updateVisiblePage() {
        long now = System.currentTimeMillis();
        if (now - lastRender < 5000) return;
        lastRender = now;

        handler.post(() -> {
            if (destroyed) return;
            mergeLiveData();
            updateStatus();
            showPage(page);
        });
    }

    private void mergeLiveData() {
        synchronized (coins) {
            for (Coin c : coins) {
                String s = c.symbol.toLowerCase(Locale.US);

                // Hindari pencocokan jika beberapa koin memiliki simbol sama.
                if (symbolCounts.containsKey(s) && symbolCounts.get(s) == 1) {
                    synchronized (livePrices) {
                        if (livePrices.containsKey(s)) {
                            c.price = livePrices.get(s);
                            c.change = liveChanges.containsKey(s)
                                    ? liveChanges.get(s) : c.change;
                            c.volume = liveVolumes.containsKey(s)
                                    ? liveVolumes.get(s) : c.volume;
                        }
                    }
                }
            }
        }
    }

    private double liveMomentum(Coin c) {
        synchronized (livePrices) {
            Double v = minuteMomentum.get(c.symbol.toLowerCase(Locale.US));
            return v == null ? 0 : v;
        }
    }

    private void buildShell() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        setContentView(root);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(17), dp(13), dp(17), dp(9));

        TextView logo = text("HAM", 26, WHITE, true);
        top.addView(logo);
        top.addView(text("  CRYPTO SIGNAL", 12, GREEN, true));

        View spacer = new View(this);
        top.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));

        Button refresh = button("↻", PANEL2, WHITE);
        refresh.setOnClickListener(v -> fetchMarkets());
        top.addView(refresh);

        root.addView(top);

        status = text("Menghubungkan ke pasar...", 11, MUTED, false);
        status.setPadding(dp(17), 0, dp(17), dp(10));
        root.addView(status);

        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));

        bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.CENTER);
        bottom.setBackgroundColor(PANEL);
        bottom.setPadding(dp(4), dp(7), dp(4), dp(7));
        root.addView(bottom);

        rebuildBottom();
        showPage("Home");
    }

    private void rebuildBottom() {
        bottom.removeAllViews();

        String[] labels = {"Home", "Markets", "Scanner", "Signals"};
        String[] icons = {"⌂", "▥", "⌕", "⚡"};

        for (int i = 0; i < labels.length; i++) {
            final String p = labels[i];

            LinearLayout item = new LinearLayout(this);
            item.setGravity(Gravity.CENTER);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(0, dp(3), 0, dp(3));

            int color = p.equals(page) ? GREEN : MUTED;
            item.addView(text(icons[i], 21, color, true));
            item.addView(text(labels[i], 10, color, true));
            item.setOnClickListener(v -> showPage(p));

            bottom.addView(item, new LinearLayout.LayoutParams(0, -2, 1));
        }
    }

    private void showPage(String p) {
        page = p;
        body.removeAllViews();
        query = "";

        if (p.equals("Home")) renderHome();
        else if (p.equals("Markets")) renderMarkets(false);
        else if (p.equals("Scanner")) renderMarkets(true);
        else renderSignals();

        rebuildBottom();
    }

    private ScrollView newScroll() {
        ScrollView sc = new ScrollView(this);
        sc.setFillViewport(false);
        sc.setPadding(dp(15), dp(5), dp(15), dp(15));
        return sc;
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private void renderHome() {
        ScrollView sc = newScroll();
        LinearLayout content = column();
        sc.addView(content);
        body.addView(sc);

        content.addView(title("HAM Signal Center",
                "Pemantauan momentum, breakout dan tekanan jual"));

        int up = 0, down = 0, watch = 0;
        for (Coin c : coins) {
            int s = score(c);
            if (s >= 45) up++;
            else if (s <= -40) down++;
            else watch++;
        }

        LinearLayout card = card();
        card.addView(text("RINGKASAN PASAR", 11, MUTED, true));
        card.addView(text(coins.size() + " aset dipantau", 24, WHITE, true));
        card.addView(text(up + " sinyal naik kuat", 14, GREEN, true));
        card.addView(text(down + " risiko turun", 14, RED, true));
        card.addView(text(watch + " menunggu konfirmasi", 12, MUTED, false));
        card.addView(text(connected ? "Binance: tersambung" :
                "Binance: menghubungkan", 12,
                connected ? GREEN : GOLD, true));
        content.addView(card);

        content.addView(section("Sinyal teratas", "Lihat semua", () -> showPage("Signals")));

        ArrayList<Coin> ranked = new ArrayList<>(coins);
        ranked.sort((a, b) -> Integer.compare(Math.abs(score(b)), Math.abs(score(a))));

        for (int i = 0; i < Math.min(10, ranked.size()); i++) {
            content.addView(signalRow(ranked.get(i)));
        }

        content.addView(note("Catatan",
                "Harga Binance diperbarui melalui WebSocket. Market cap dan riwayat grafik berasal dari CoinGecko. Sinyal adalah indikator probabilistik, bukan jaminan pump atau dump."));
    }

    private void renderMarkets(boolean scanner) {
        ScrollView sc = newScroll();
        LinearLayout content = column();
        sc.addView(content);
        body.addView(sc);

        content.addView(title(scanner ? "Coin Scanner" : "Pasar Kripto",
                scanner ? "Urutan sinyal dan momentum" : "Harga, volume dan kapitalisasi"));

        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextColor(WHITE);
        input.setHintTextColor(MUTED);
        input.setHint("Cari nama atau simbol koin");
        input.setTextSize(14);
        input.setPadding(dp(12), 0, dp(12), 0);
        input.setBackground(shape(PANEL2, 12));
        content.addView(input, new LinearLayout.LayoutParams(-1, dp(47)));

        LinearLayout list = column();
        content.addView(list);

        Runnable fill = () -> {
            list.removeAllViews();
            String q = input.getText().toString().toLowerCase(Locale.US);
            ArrayList<Coin> results = new ArrayList<>();

            for (Coin c : coins) {
                if (c.name.toLowerCase(Locale.US).contains(q)
                        || c.symbol.toLowerCase(Locale.US).contains(q)) {
                    results.add(c);
                }
            }

            if (scanner) {
                results.sort((a, b) ->
                        Integer.compare(Math.abs(score(b)), Math.abs(score(a))));
            }

            if (results.isEmpty()) {
                list.addView(note("Belum ada data",
                        "Periksa internet atau coba pencarian lain."));
            }

            for (Coin c : results) {
                list.addView(scanner ? signalRow(c) : coinRow(c));
            }
        };

        input.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                fill.run();
            }
            @Override public void afterTextChanged(android.text.Editable e) {}
        });

        fill.run();
    }

    private void renderSignals() {
        ScrollView sc = newScroll();
        LinearLayout content = column();
        sc.addView(content);
        body.addView(sc);

        content.addView(title("Pusat Sinyal",
                "Kategori berdasarkan kondisi data saat ini"));

        String[] categories = {
                "POTENSI PUMP",
                "MOMENTUM NAIK",
                "BREAKOUT WATCH",
                "POTENSI PANTULAN",
                "VOLUME TINGGI",
                "RISIKO DUMP",
                "TEKANAN JUAL",
                "RSI TINGGI",
                "RSI RENDAH",
                "MENUNGGU KONFIRMASI"
        };

        for (String category : categories) {
            LinearLayout section = card();
            section.addView(text(category, 13, GOLD, true));

            int count = 0;
            for (Coin c : coins) {
                if (categoryFor(c).equals(category)) {
                    section.addView(signalRow(c));
                    count++;
                    if (count >= 5) break;
                }
            }

            if (count == 0) {
                section.addView(text("Tidak ada aset dengan kondisi ini saat ini.",
                        11, MUTED, false));
            }

            content.addView(section);
        }
    }

    private LinearLayout coinRow(Coin c) {
        LinearLayout card = card();
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView badge = text(c.symbol.substring(0,
                Math.min(1, c.symbol.length())).toUpperCase(Locale.US),
                17, WHITE, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(shape(PANEL2, 22));
        row.addView(badge, new LinearLayout.LayoutParams(dp(38), dp(38)));

        LinearLayout left = column();
        left.setPadding(dp(10), 0, dp(5), 0);
        left.addView(text(c.name, 14, WHITE, true));
        left.addView(text(c.symbol.toUpperCase(Locale.US)
                + " • Cap $" + compact(c.marketCap), 10, MUTED, false));
        row.addView(left, new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout right = column();
        right.setGravity(Gravity.RIGHT);
        right.addView(text("$" + price(c.price), 14, WHITE, true));
        right.addView(text(signed(c.change) + "%",
                12, c.change >= 0 ? GREEN : RED, true));
        row.addView(right);

        card.addView(row);
        card.setOnClickListener(v -> renderDetail(c));
        return card;
    }

    private LinearLayout signalRow(Coin c) {
        LinearLayout card = coinRow(c);
        int s = score(c);
        int color = s >= 25 ? GREEN : s <= -25 ? RED : GOLD;

        TextView label = text(categoryFor(c) + "  •  SKOR "
                + (s > 0 ? "+" : "") + s, 11, color, true);
        label.setPadding(dp(3), dp(8), 0, dp(2));
        card.addView(label);

        double m = liveMomentum(c);
        TextView metrics = text("1m " + signed(m) + "%  •  24j "
                + signed(c.change) + "%  •  RSI " + (int) rsi(c.spark)
                + "  •  Volume/Cap " + pct(c.marketCap > 0
                ? c.volume / c.marketCap * 100 : 0) + "%",
                10, MUTED, false);
        metrics.setPadding(dp(3), 0, 0, dp(2));
        card.addView(metrics);

        return card;
    }

    private void renderDetail(Coin c) {
        ScrollView sc = newScroll();
        LinearLayout content = column();
        sc.addView(content);
        body.removeAllViews();
        body.addView(sc);

        content.addView(title(c.name + " (" +
                c.symbol.toUpperCase(Locale.US) + ")", "Detail aset"));

        LinearLayout priceCard = card();
        priceCard.addView(text("$" + price(c.price), 29, WHITE, true));
        priceCard.addView(text(signed(c.change) + "% dalam 24 jam",
                14, c.change >= 0 ? GREEN : RED, true));
        priceCard.addView(new SparkChart(c.spark,
                c.change >= 0 ? GREEN : RED),
                new LinearLayout.LayoutParams(-1, dp(160)));
        content.addView(priceCard);

        content.addView(pair("Market cap", "$" + compact(c.marketCap),
                "Volume 24j", "$" + compact(c.volume)));
        content.addView(pair("High 24j", "$" + price(c.high),
                "Low 24j", "$" + price(c.low)));
        content.addView(pair("Momentum 1m", signed(liveMomentum(c)) + "%",
                "Perubahan 1h", signed(c.change1h) + "%"));
        content.addView(pair("Perubahan 7 hari", signed(c.change7d) + "%",
                "RSI perkiraan", String.valueOf((int) rsi(c.spark))));
        content.addView(pair("EMA 9/21", emaTrend(c.spark),
                "MACD perkiraan", macd(c.spark)));
        content.addView(pair("Bollinger", bands(c.spark),
                "Volatilitas sampel", pct(volatility(c.spark)) + "%"));

        content.addView(note(categoryFor(c),
                "Skor " + score(c) + "/100. Indikator ini menggunakan data pasar yang tersedia dan tidak memastikan arah harga berikutnya."));

        Button back = button("← Kembali", PANEL2, WHITE);
        back.setOnClickListener(v -> showPage("Markets"));
        content.addView(back);
    }

    private int score(Coin c) {
        int s = 0;
        double m = liveMomentum(c);
        double r = rsi(c.spark);
        double volRatio = c.marketCap > 0 ? c.volume / c.marketCap : 0;

        if (m >= 1.0) s += 25;
        else if (m >= 0.3) s += 12;
        else if (m <= -1.0) s -= 25;
        else if (m <= -0.3) s -= 12;

        if (c.change >= 3) s += 12;
        else if (c.change >= 1) s += 6;
        else if (c.change <= -3) s -= 12;
        else if (c.change <= -1) s -= 6;

        if (c.change1h >= 1) s += 12;
        else if (c.change1h <= -1) s -= 12;

        if (c.change7d >= 8) s += 8;
        else if (c.change7d <= -8) s -= 8;

        if (r > 78) s -= 12;
        else if (r < 30 && m > 0) s += 8;
        else if (r > 55 && m > 0) s += 5;

        if (volRatio >= 0.10) s += m >= 0 ? 10 : -10;
        else if (volRatio >= 0.03) s += m >= 0 ? 4 : -4;

        if (Math.abs(c.change) > 20) s += c.change > 0 ? -8 : 5;

        return Math.max(-100, Math.min(100, s));
    }

    private String categoryFor(Coin c) {
        int s = score(c);
        double m = liveMomentum(c);
        double r = rsi(c.spark);
        double vr = c.marketCap > 0 ? c.volume / c.marketCap : 0;

        if (s <= -40) return "RISIKO DUMP";
        if (s >= 55 && m > 0) return "POTENSI PUMP";
        if (s >= 25) return "MOMENTUM NAIK";
        if (m > 0.5 && distanceHigh(c.spark) > -3)
            return "BREAKOUT WATCH";
        if (r < 32 && m > 0) return "POTENSI PANTULAN";
        if (vr > 0.10) return "VOLUME TINGGI";
        if (s <= -20) return "TEKANAN JUAL";
        if (r > 72) return "RSI TINGGI";
        if (r < 32) return "RSI RENDAH";
        return "MENUNGGU KONFIRMASI";
    }

    private double rsi(ArrayList<Double> p) {
        if (p == null || p.size() < 3) return 50;
        int start = Math.max(1, p.size() - 15);
        double gain = 0, loss = 0;
        int n = 0;

        for (int i = start; i < p.size(); i++) {
            double d = p.get(i) - p.get(i - 1);
            if (d > 0) gain += d;
            else loss -= d;
            n++;
        }

        if (n == 0 || loss == 0) return gain == 0 ? 50 : 100;
        double rs = gain / loss;
        return 100 - 100 / (1 + rs);
    }

    private double ema(ArrayList<Double> p, int period) {
        if (p == null || p.isEmpty()) return 0;
        double k = 2.0 / (period + 1.0);
        double e = p.get(0);
        for (int i = 1; i < p.size(); i++)
            e = p.get(i) * k + e * (1 - k);
        return e;
    }

    private String emaTrend(ArrayList<Double> p) {
        if (p == null || p.size() < 22) return "Data kurang";
        return ema(p, 9) > ema(p, 21) ? "Naik" : "Turun";
    }

    private String macd(ArrayList<Double> p) {
        if (p == null || p.size() < 26) return "Data kurang";
        return ema(p, 12) > ema(p, 26) ? "Bullish" : "Bearish";
    }

    private String bands(ArrayList<Double> p) {
        if (p == null || p.size() < 10) return "Data kurang";
        int from = Math.max(0, p.size() - 20);
        double mean = 0;
        for (int i = from; i < p.size(); i++) mean += p.get(i);
        mean /= p.size() - from;

        double variance = 0;
        for (int i = from; i < p.size(); i++)
            variance += Math.pow(p.get(i) - mean, 2);
        double sd = Math.sqrt(variance / (p.size() - from));

        double pos = (p.get(p.size() - 1) - (mean - 2 * sd))
                / Math.max(1e-12, 4 * sd);

        return pos > 0.9 ? "Dekat atas" :
                pos < 0.1 ? "Dekat bawah" : "Tengah";
    }

    private double volatility(ArrayList<Double> p) {
        if (p == null || p.size() < 3) return 0;
        double total = 0;
        int n = 0;
        for (int i = Math.max(1, p.size() - 24); i < p.size(); i++) {
            if (p.get(i - 1) != 0) {
                total += Math.abs((p.get(i) / p.get(i - 1) - 1) * 100);
                n++;
            }
        }
        return n == 0 ? 0 : total / n;
    }

    private double distanceHigh(ArrayList<Double> p) {
        if (p == null || p.size() < 2) return 0;
        double high = 0;
        for (double v : p) high = Math.max(high, v);
        return high == 0 ? 0 :
                (p.get(p.size() - 1) / high - 1) * 100;
    }

    private LinearLayout pair(String a, String av, String b, String bv) {
        LinearLayout row = new LinearLayout(this);
        row.addView(stat(a, av), new LinearLayout.LayoutParams(0, -2, 1));
        View gap = new View(this);
        row.addView(gap, new LinearLayout.LayoutParams(dp(7), 1));
        row.addView(stat(b, bv), new LinearLayout.LayoutParams(0, -2, 1));
        return row;
    }

    private View stat(String label, String value) {
        LinearLayout c = card();
        c.addView(text(label, 10, MUTED, true));
        c.addView(text(value, 14, WHITE, true));
        return c;
    }

    private View title(String t, String subtitle) {
        LinearLayout c = column();
        c.setPadding(0, dp(7), 0, dp(14));
        c.addView(text(t, 23, WHITE, true));
        c.addView(text(subtitle, 12, MUTED, false));
        return c;
    }

    private View section(String t, String action, Runnable run) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(13), 0, dp(8));
        row.addView(text(t, 16, WHITE, true),
                new LinearLayout.LayoutParams(0, -2, 1));

        TextView a = text(action + " →", 12, GREEN, true);
        a.setOnClickListener(v -> run.run());
        row.addView(a);
        return row;
    }

    private LinearLayout card() {
        LinearLayout c = column();
        c.setPadding(dp(13), dp(12), dp(13), dp(12));
        c.setBackground(shape(PANEL, 13));

        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        c.setLayoutParams(lp);
        return c;
    }

    private View note(String title, String description) {
        LinearLayout c = card();
        c.addView(text(title, 13, WHITE, true));
        TextView t = text(description, 11, MUTED, false);
        t.setPadding(0, dp(5), 0, 0);
        c.addView(t);
        return c;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private Button button(String label, int bg, int fg) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(12);
        b.setTextColor(fg);
        b.setAllCaps(false);
        b.setBackground(shape(bg, 10));
        return b;
    }

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private int dp(float v) {
        return (int) (getResources().getDisplayMetrics().density * v + 0.5f);
    }

    private String price(double v) {
        if (v >= 1000) return new DecimalFormat("#,##0.00").format(v);
        if (v >= 1) return new DecimalFormat("0.0000").format(v);
        if (v >= 0.01) return new DecimalFormat("0.000000").format(v);
        return new DecimalFormat("0.########").format(v);
    }

    private String pct(double v) {
        return new DecimalFormat("0.00").format(v);
    }

    private String signed(double v) {
        return (v > 0 ? "+" : "") + pct(v);
    }

    private String compact(double v) {
        if (v >= 1e12) return pct(v / 1e12) + "T";
        if (v >= 1e9) return pct(v / 1e9) + "B";
        if (v >= 1e6) return pct(v / 1e6) + "M";
        if (v >= 1e3) return pct(v / 1e3) + "K";
        return pct(v);
    }

    private void fetchMarkets() {
        if (loading || destroyed) return;
        loading = true;
        updateStatus();

        new Thread(() -> {
            ArrayList<Coin> result = new ArrayList<>();
            String error = null;
            HttpURLConnection conn = null;

            try {
                URL url = new URL(
                        "https://api.coingecko.com/api/v3/coins/markets"
                        + "?vs_currency=usd&order=market_cap_desc"
                        + "&per_page=100&page=1&sparkline=true"
                        + "&price_change_percentage=1h%2C24h%2C7d");

                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(12000);
                conn.setReadTimeout(15000);
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("User-Agent", "HAM-Crypto-Signal/1.1");

                int code = conn.getResponseCode();
                if (code != 200) throw new Exception("HTTP " + code);

                BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();

                JSONArray arr = new JSONArray(sb.toString());

                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    Coin c = new Coin();

                    c.id = o.optString("id", "");
                    c.name = o.optString("name", c.id);
                    c.symbol = o.optString("symbol", "?");
                    c.price = o.optDouble("current_price", 0);
                    c.change = o.optDouble("price_change_percentage_24h", 0);
                    c.change1h = o.optDouble(
                            "price_change_percentage_1h_in_currency", 0);
                    c.change7d = o.optDouble(
                            "price_change_percentage_7d_in_currency", 0);
                    c.marketCap = o.optDouble("market_cap", 0);
                    c.volume = o.optDouble("total_volume", 0);
                    c.high = o.optDouble("high_24h", c.price);
                    c.low = o.optDouble("low_24h", c.price);

                    JSONObject sp = o.optJSONObject("sparkline_in_7d");
                    JSONArray points = sp == null ? null :
                            sp.optJSONArray("price");

                    if (points != null) {
                        int step = Math.max(1, points.length() / 60);
                        for (int j = 0; j < points.length(); j += step) {
                            double v = points.optDouble(j, 0);
                            if (v > 0) c.spark.add(v);
                        }
                    }

                    result.add(c);
                }
            } catch (Exception e) {
                error = e.getMessage();
            } finally {
                if (conn != null) conn.disconnect();
            }

            final String err = error;

            handler.post(() -> {
                loading = false;

                if (!result.isEmpty()) {
                    coins.clear();
                    coins.addAll(result);

                    symbolCounts.clear();
                    for (Coin c : coins) {
                        String s = c.symbol.toLowerCase(Locale.US);
                        symbolCounts.put(s, symbolCounts.containsKey(s)
                                ? symbolCounts.get(s) + 1 : 1);
                    }

                    mergeLiveData();
                    updateStatus();
                    showPage(page);
                } else {
                    updateStatus();
                    if (coins.isEmpty()) {
                        body.removeAllViews();
                        LinearLayout c = column();
                        c.addView(title("HAM Crypto Signal", "Data belum tersedia"));
                        c.addView(note("Gagal memuat pasar",
                                "Periksa internet, lalu tekan tombol refresh. "
                                + (err == null ? "" : err)));
                        body.addView(c);
                    }
                    Toast.makeText(MainActivity.this,
                            "Data pasar gagal dimuat", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private class SparkChart extends View {
        private final ArrayList<Double> points;
        private final int color;
        private final Paint paint = new Paint(3);

        SparkChart(ArrayList<Double> data, int lineColor) {
            super(MainActivity.this);
            points = new ArrayList<>(data);
            color = lineColor;
            setBackground(shape(PANEL, 8));
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            if (points.size() < 2) {
                paint.setColor(MUTED);
                paint.setTextSize(dp(12));
                canvas.drawText("Grafik belum tersedia",
                        dp(10), getHeight() / 2f, paint);
                return;
            }

            double min = Collections.min(points);
            double max = Collections.max(points);
            if (max <= min) max = min + 1;

            float pad = dp(10);
            float w = getWidth() - 2 * pad;
            float h = getHeight() - 2 * pad;
            Path path = new Path();

            for (int i = 0; i < points.size(); i++) {
                float x = pad + w * i / (points.size() - 1);
                float y = pad + h - (float)
                        ((points.get(i) - min) / (max - min)) * h;

                if (i == 0) path.moveTo(x, y);
                else path.lineTo(x, y);
            }

            paint.setColor(color);
            paint.setStrokeWidth(dp(2));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawPath(path, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }
}
