package com.ham.signal;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    private final int BG = Color.rgb(11, 15, 23);
    private final int PANEL = Color.rgb(20, 27, 39);
    private final int PANEL2 = Color.rgb(27, 36, 51);
    private final int WHITE = Color.rgb(238, 243, 250);
    private final int MUTED = Color.rgb(145, 158, 178);
    private final int GREEN = Color.rgb(22, 199, 132);
    private final int RED = Color.rgb(246, 70, 93);
    private final int BLUE = Color.rgb(72, 130, 255);

    private LinearLayout root, body, bottom;
    private TextView status;
    private EditText searchBox;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<Coin> coins = new ArrayList<>();
    private final Map<String, Double> holdings = new HashMap<>();
    private final Map<String, Double> avgPrices = new HashMap<>();
    private double cash = 10000.0;
    private String page = "Home";
    private String selectedCoin = "bitcoin";
    private String query = "";
    private String currency = "USD";
    private boolean loading = false;

    static class Coin {
        String id, name, symbol, image;
        double price, change, marketCap, volume, high, low;
        ArrayList<Double> spark = new ArrayList<>();
    }

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);
        buildShell();
        fetchMarkets();
    }

    private void buildShell() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        setContentView(root);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(18), dp(15), dp(18), dp(12));
        top.setBackgroundColor(BG);
        TextView logo = text("HAM", 25, WHITE, true);
        TextView badge = text("  CRYPTO SIGNAL", 13, GREEN, true);
        top.addView(logo);
        top.addView(badge);
        View spacer = new View(this);
        top.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));
        Button refresh = smallButton("↻");
        refresh.setOnClickListener(v -> fetchMarkets());
        top.addView(refresh);
        root.addView(top);

        status = text("Menghubungkan ke data pasar…", 11, MUTED, false);
        status.setPadding(dp(18), 0, dp(18), dp(8));
        root.addView(status);

        FrameLayout frame = new FrameLayout(this);
        root.addView(frame, new LinearLayout.LayoutParams(-1, 0, 1));
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        frame.addView(body, new FrameLayout.LayoutParams(-1, -1));

        bottom = new LinearLayout(this);
        bottom.setBackgroundColor(PANEL);
        bottom.setPadding(dp(3), dp(7), dp(3), dp(8));
        bottom.setGravity(Gravity.CENTER);
        String[] labels = {"Home", "Markets", "Scanner", "Trade", "Assets"};
        String[] icons = {"⌂", "▥", "⌕", "⇄", "◉"};
        for (int i = 0; i < labels.length; i++) {
            final String name = labels[i];
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            TextView icon = text(icons[i], 20, name.equals(page) ? GREEN : MUTED, true);
            TextView label = text(labels[i], 10, name.equals(page) ? GREEN : MUTED, true);
            item.addView(icon);
            item.addView(label);
            item.setPadding(0, dp(2), 0, dp(2));
            item.setOnClickListener(v -> showPage(name));
            bottom.addView(item, new LinearLayout.LayoutParams(0, -2, 1));
        }
        root.addView(bottom);
        showPage("Home");
    }

    private void showPage(String name) {
        page = name;
        body.removeAllViews();
        if (name.equals("Home")) renderHome();
        else if (name.equals("Markets")) renderMarkets(false);
        else if (name.equals("Scanner")) renderMarkets(true);
        else if (name.equals("Trade")) renderTrade();
        else renderAssets();
        rebuildBottom();
    }

    private void rebuildBottom() {
        if (bottom == null) return;
        bottom.removeAllViews();
        String[] labels = {"Home", "Markets", "Scanner", "Trade", "Assets"};
        String[] icons = {"⌂", "▥", "⌕", "⇄", "◉"};
        for (int i = 0; i < labels.length; i++) {
            final String name = labels[i];
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.addView(text(icons[i], 20, name.equals(page) ? GREEN : MUTED, true));
            item.addView(text(labels[i], 10, name.equals(page) ? GREEN : MUTED, true));
            item.setPadding(0, dp(2), 0, dp(2));
            item.setOnClickListener(v -> showPage(name));
            bottom.addView(item, new LinearLayout.LayoutParams(0, -2, 1));
        }
    }

    private void renderHome() {
        ScrollView sc = scroll();
        LinearLayout content = column();
        sc.addView(content);
        body.addView(sc);
        content.addView(titleBlock("Dashboard", "Ringkasan pasar kripto real-time"));
        Coin btc = find("bitcoin");
        Coin eth = find("ethereum");

        LinearLayout hero = cardColumn();
        hero.addView(text("TOTAL SALDO SIMULASI", 11, MUTED, true));
        hero.addView(text("$" + money(totalEquity()), 30, WHITE, true));
        hero.addView(text("Saldo virtual • bukan uang sungguhan", 11, MUTED, false));
        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, dp(15), 0, 0);
        Button buy = actionButton("Mulai Trading", GREEN);
        buy.setOnClickListener(v -> showPage("Trade"));
        Button scanner = actionButton("Buka Scanner", PANEL2);
        scanner.setOnClickListener(v -> showPage("Scanner"));
        row.addView(buy, new LinearLayout.LayoutParams(0, dp(44), 1));
        row.addView(space(dp(8)));
        row.addView(scanner, new LinearLayout.LayoutParams(0, dp(44), 1));
        hero.addView(row);
        content.addView(hero);

        content.addView(sectionHeader("Pasar utama", "Lihat semua", () -> showPage("Markets")));
        if (btc != null) content.addView(coinRow(btc, true));
        if (eth != null) content.addView(coinRow(eth, true));

        content.addView(sectionHeader("Peluang scanner", "Semua sinyal", () -> showPage("Scanner")));
        ArrayList<Coin> ranked = new ArrayList<>(coins);
        ranked.sort((a,b) -> Double.compare(b.change, a.change));
        int count = Math.min(5, ranked.size());
        for (int i=0; i<count; i++) content.addView(signalRow(ranked.get(i)));

        content.addView(cardNote("Catatan", "Sinyal dihitung sederhana dari perubahan harga 24 jam dan data pasar. Ini bukan saran investasi dan tidak menjamin hasil."));
    }

    private void renderMarkets(boolean scanner) {
        ScrollView sc = scroll();
        LinearLayout content = column();
        sc.addView(content);
        body.addView(sc);
        content.addView(titleBlock(scanner ? "Coin Scanner" : "Pasar Kripto",
                scanner ? "Peringkat peluang berdasarkan perubahan 24 jam" : "Harga, volume, kapitalisasi pasar"));
        searchBox = new EditText(this);
        searchBox.setSingleLine(true);
        searchBox.setTextColor(WHITE);
        searchBox.setHintTextColor(MUTED);
        searchBox.setHint("Cari nama atau simbol koin…");
        searchBox.setTextSize(14);
        searchBox.setPadding(dp(14), 0, dp(14), 0);
        searchBox.setBackground(round(PANEL2, 12));
        content.addView(searchBox, new LinearLayout.LayoutParams(-1, dp(48)));
        searchBox.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int st,int c,int a) {}
            public void onTextChanged(CharSequence s,int start,int before,int count) {
                query = s.toString().toLowerCase(Locale.US);
                refreshCoinList(content, scanner);
            }
            public void afterTextChanged(Editable e) {}
        });
        LinearLayout list = column();
        content.addView(list);
        fillCoinList(list, scanner);
    }

    private void refreshCoinList(LinearLayout content, boolean scanner) {
        // Keep the search box and rebuild the result section in place.
        if (content.getChildCount() < 3) return;
        LinearLayout list = (LinearLayout) content.getChildAt(2);
        fillCoinList(list, scanner);
    }

    private void fillCoinList(LinearLayout list, boolean scanner) {
        list.removeAllViews();
        ArrayList<Coin> shown = new ArrayList<>();
        for (Coin c : coins) {
            if (!query.isEmpty() && !(c.name.toLowerCase(Locale.US).contains(query) ||
                    c.symbol.toLowerCase(Locale.US).contains(query))) continue;
            shown.add(c);
        }
        if (scanner) shown.sort((a,b) -> Double.compare(b.change, a.change));
        if (shown.isEmpty()) {
            list.addView(cardNote("Data sedang dimuat", "Tekan tombol ↻ di bagian atas untuk mengambil data pasar."));
            return;
        }
        for (Coin c : shown) list.addView(scanner ? signalRow(c) : coinRow(c, false));
    }

    private void renderCoinDetail(Coin c) {
        selectedCoin = c.id;
        body.removeAllViews();
        ScrollView sc = scroll();
        LinearLayout content = column();
        sc.addView(content);
        body.addView(sc);
        content.addView(titleBlock(c.name + " (" + c.symbol.toUpperCase(Locale.US) + ")", "Detail aset • data 24 jam"));
        LinearLayout priceCard = cardColumn();
        priceCard.addView(text("$" + price(c.price), 29, WHITE, true));
        priceCard.addView(text((c.change >= 0 ? "+" : "") + pct(c.change) + "% dalam 24 jam",
                13, c.change >= 0 ? GREEN : RED, true));
        priceCard.addView(new SparkChart(c.spark, c.change >= 0 ? GREEN : RED), new LinearLayout.LayoutParams(-1, dp(150)));
        content.addView(priceCard);
        LinearLayout stats = new LinearLayout(this);
        stats.addView(statCard("Market Cap", "$" + compact(c.marketCap)), new LinearLayout.LayoutParams(0, -2, 1));
        stats.addView(space(dp(8)));
        stats.addView(statCard("Volume 24j", "$" + compact(c.volume)), new LinearLayout.LayoutParams(0, -2, 1));
        content.addView(stats);
        LinearLayout stats2 = new LinearLayout(this);
        stats2.addView(statCard("Tertinggi 24j", "$" + price(c.high)), new LinearLayout.LayoutParams(0, -2, 1));
        stats2.addView(space(dp(8)));
        stats2.addView(statCard("Terendah 24j", "$" + price(c.low)), new LinearLayout.LayoutParams(0, -2, 1));
        content.addView(stats2);
        content.addView(sectionHeader("Sinyal HAM", null, null));
        String signal = c.change > 3 ? "MOMENTUM NAIK" : c.change < -3 ? "TEKANAN JUAL" : "NETRAL / TUNGGU";
        int color = c.change > 3 ? GREEN : c.change < -3 ? RED : MUTED;
        content.addView(cardNote(signal, "Perubahan 24 jam: " + pct(c.change) + "%. Gunakan indikator ini sebagai informasi awal, bukan kepastian arah harga."));
        LinearLayout buttons = new LinearLayout(this);
        Button buy = actionButton("Beli Simulasi", GREEN);
        Button sell = actionButton("Jual Simulasi", RED);
        buy.setOnClickListener(v -> tradeDialog(c, true));
        sell.setOnClickListener(v -> tradeDialog(c, false));
        buttons.addView(buy, new LinearLayout.LayoutParams(0, dp(46), 1));
        buttons.addView(space(dp(8)));
        buttons.addView(sell, new LinearLayout.LayoutParams(0, dp(46), 1));
        content.addView(buttons);
        Button back = actionButton("← Kembali ke pasar", PANEL2);
        back.setOnClickListener(v -> showPage("Markets"));
        content.addView(back);
    }

    private void renderTrade() {
        ScrollView sc = scroll();
        LinearLayout content = column();
        sc.addView(content);
        body.addView(sc);
        content.addView(titleBlock("Paper Trading", "Latihan beli/jual dengan saldo virtual"));
        LinearLayout balance = cardColumn();
        balance.addView(text("SALDO TERSEDIA", 11, MUTED, true));
        balance.addView(text("$" + money(cash), 29, WHITE, true));
        balance.addView(text("Total nilai akun: $" + money(totalEquity()), 12, GREEN, true));
        content.addView(balance);
        content.addView(sectionHeader("Pilih aset", null, null));
        for (Coin c : coins) {
            if (holdings.getOrDefault(c.id, 0.0) > 0 || c.id.equals("bitcoin") || c.id.equals("ethereum")) {
                LinearLayout r = coinRow(c, false);
                r.setOnClickListener(v -> { selectedCoin = c.id; tradeDialog(c, true); });
                content.addView(r);
            }
        }
        content.addView(cardNote("Cara kerja", "Pilih koin lalu masukkan nilai USD untuk membeli. Penjualan mengurangi jumlah aset virtual. Tidak ada order yang dikirim ke bursa."));
    }

    private void renderAssets() {
        ScrollView sc = scroll();
        LinearLayout content = column();
        sc.addView(content);
        body.addView(sc);
        content.addView(titleBlock("Aset Saya", "Portofolio simulasi"));
        content.addView(cardNote("Nilai akun", "$" + money(totalEquity()) + " • Saldo tunai $" + money(cash)));
        boolean any = false;
        for (Coin c : coins) {
            double qty = holdings.getOrDefault(c.id, 0.0);
            if (qty <= 0) continue;
            any = true;
            LinearLayout card = cardColumn();
            LinearLayout r = new LinearLayout(this);
            r.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout names = column();
            names.addView(text(c.name, 15, WHITE, true));
            names.addView(text(qty + " " + c.symbol.toUpperCase(Locale.US), 11, MUTED, false));
            r.addView(names, new LinearLayout.LayoutParams(0, -2, 1));
            r.addView(text("$" + money(qty*c.price), 15, WHITE, true));
            card.addView(r);
            content.addView(card);
        }
        if (!any) content.addView(cardNote("Belum ada aset", "Buka menu Trade untuk mencoba transaksi simulasi."));
        Button reset = actionButton("Reset portofolio simulasi", PANEL2);
        reset.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Reset simulasi?")
                .setMessage("Saldo kembali ke $10.000 dan semua aset simulasi dihapus.")
                .setNegativeButton("Batal", null).setPositiveButton("Reset", (d,w) -> {
                    cash = 10000; holdings.clear(); avgPrices.clear(); showPage("Assets");
                }).show());
        content.addView(reset);
    }

    private void tradeDialog(Coin c, boolean buy) {
        EditText input = new EditText(this);
        input.setInputType(8194);
        input.setSingleLine(true);
        input.setText("100");
        input.setHint("Nilai USD");
        new AlertDialog.Builder(this)
                .setTitle((buy ? "Beli " : "Jual ") + c.symbol.toUpperCase(Locale.US) + " (simulasi)")
                .setMessage("Harga saat ini: $" + price(c.price) + "\nMasukkan nilai transaksi dalam USD.")
                .setView(input)
                .setNegativeButton("Batal", null)
                .setPositiveButton(buy ? "Beli" : "Jual", (d,w) -> {
                    double amount;
                    try { amount = Double.parseDouble(input.getText().toString().trim()); }
                    catch (Exception e) { toast("Masukkan angka yang benar"); return; }
                    if (amount <= 0 || c.price <= 0) { toast("Nilai transaksi tidak valid"); return; }
                    double qty = amount / c.price;
                    double owned = holdings.getOrDefault(c.id, 0.0);
                    if (buy) {
                        if (amount > cash) { toast("Saldo virtual tidak cukup"); return; }
                        double oldCost = avgPrices.getOrDefault(c.id, c.price) * owned;
                        cash -= amount;
                        holdings.put(c.id, owned + qty);
                        avgPrices.put(c.id, (oldCost + amount) / (owned + qty));
                        toast("Beli simulasi berhasil");
                    } else {
                        if (owned <= 0) { toast("Kamu belum memiliki aset ini"); return; }
                        double sellQty = Math.min(qty, owned);
                        cash += sellQty * c.price;
                        double remaining = owned - sellQty;
                        if (remaining < 1e-12) { holdings.remove(c.id); avgPrices.remove(c.id); }
                        else holdings.put(c.id, remaining);
                        toast("Jual simulasi berhasil");
                    }
                    showPage(page);
                }).show();
    }

    private LinearLayout coinRow(Coin c, boolean compactRow) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(13), dp(12), dp(13), dp(12));
        card.setBackground(round(PANEL, 13));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView symbol = text(c.symbol.substring(0, Math.min(1,c.symbol.length())).toUpperCase(Locale.US), 17, WHITE, true);
        symbol.setGravity(Gravity.CENTER);
        symbol.setBackground(round(PANEL2, 24));
        row.addView(symbol, new LinearLayout.LayoutParams(dp(38), dp(38)));
        LinearLayout nameBox = column();
        nameBox.setPadding(dp(10), 0, dp(4), 0);
        nameBox.addView(text(c.name, 14, WHITE, true));
        nameBox.addView(text(c.symbol.toUpperCase(Locale.US) + " • Cap $" + compact(c.marketCap), 10, MUTED, false));
        row.addView(nameBox, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout right = column();
        right.setGravity(Gravity.RIGHT);
        right.addView(text("$" + price(c.price), 14, WHITE, true));
        right.addView(text((c.change >= 0 ? "+" : "") + pct(c.change) + "%", 12, c.change >= 0 ? GREEN : RED, true));
        row.addView(right);
        card.addView(row);
        card.setOnClickListener(v -> renderCoinDetail(c));
        card.setClickable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(7);
        card.setLayoutParams(lp);
        return card;
    }

    private LinearLayout signalRow(Coin c) {
        LinearLayout card = coinRow(c, false);
        TextView tag = text(c.change > 3 ? "▲ MOMENTUM" : c.change < -3 ? "▼ TEKANAN JUAL" : "• NETRAL / TUNGGU",
                10, c.change > 3 ? GREEN : c.change < -3 ? RED : MUTED, true);
        tag.setPadding(dp(48), dp(3), 0, 0);
        card.addView(tag);
        return card;
    }

    private View titleBlock(String title, String subtitle) {
        LinearLayout box = column();
        box.setPadding(0, dp(6), 0, dp(14));
        box.addView(text(title, 23, WHITE, true));
        box.addView(text(subtitle, 12, MUTED, false));
        return box;
    }

    private View sectionHeader(String title, String action, Runnable run) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(18), 0, dp(9));
        row.addView(text(title, 16, WHITE, true), new LinearLayout.LayoutParams(0, -2, 1));
        if (action != null) {
            TextView a = text(action + " →", 12, GREEN, true);
            a.setOnClickListener(v -> run.run());
            row.addView(a);
        }
        return row;
    }

    private LinearLayout cardColumn() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(16), dp(16), dp(16));
        c.setBackground(round(PANEL, 15));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(9);
        c.setLayoutParams(lp);
        return c;
    }

    private View statCard(String label, String value) {
        LinearLayout c = cardColumn();
        c.addView(text(label, 10, MUTED, true));
        c.addView(text(value, 15, WHITE, true));
        return c;
    }

    private View cardNote(String title, String bodyText) {
        LinearLayout c = cardColumn();
        c.addView(text(title, 13, WHITE, true));
        TextView t = text(bodyText, 11, MUTED, false);
        t.setPadding(0, dp(5), 0, 0);
        c.addView(t);
        return c;
    }

    private void fetchMarkets() {
        if (loading) return;
        loading = true;
        status.setText("Mengambil data pasar CoinGecko…");
        new Thread(() -> {
            ArrayList<Coin> result = new ArrayList<>();
            String error = null;
            HttpURLConnection conn = null;
            try {
                URL url = new URL("https://api.coingecko.com/api/v3/coins/markets?vs_currency=usd&order=market_cap_desc&per_page=100&page=1&sparkline=true&price_change_percentage=24h");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(12000);
                conn.setReadTimeout(15000);
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("User-Agent", "HAM-Crypto-Signal-Android/1.0");
                int code = conn.getResponseCode();
                if (code != 200) throw new Exception("HTTP " + code);
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                JSONArray arr = new JSONArray(sb.toString());
                for (int i=0; i<arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    Coin c = new Coin();
                    c.id = o.optString("id");
                    c.name = o.optString("name", c.id);
                    c.symbol = o.optString("symbol", "?");
                    c.image = o.optString("image");
                    c.price = o.optDouble("current_price", 0);
                    c.change = o.optDouble("price_change_percentage_24h", 0);
                    c.marketCap = o.optDouble("market_cap", 0);
                    c.volume = o.optDouble("total_volume", 0);
                    c.high = o.optDouble("high_24h", c.price);
                    c.low = o.optDouble("low_24h", c.price);
                    JSONObject sp = o.optJSONObject("sparkline_in_7d");
                    JSONArray points = sp == null ? null : sp.optJSONArray("price");
                    if (points != null) {
                        int step = Math.max(1, points.length()/50);
                        for (int j=0; j<points.length(); j+=step) c.spark.add(points.optDouble(j, c.price));
                    }
                    result.add(c);
                }
            } catch (Exception e) { error = e.getMessage(); }
            finally { if (conn != null) conn.disconnect(); }
            final String err = error;
            handler.post(() -> {
                loading = false;
                if (!result.isEmpty()) {
                    coins.clear();
                    coins.addAll(result);
                    status.setText("LIVE • CoinGecko • " + coins.size() + " aset • diperbarui saat refresh");
                    showPage(page);
                } else {
                    status.setText("Data gagal dimuat • " + (err == null ? "periksa koneksi" : err));
                    if (coins.isEmpty()) {
                        body.removeAllViews();
                        LinearLayout c = column();
                        c.addView(titleBlock("HAM Crypto Signal", "Data pasar belum tersedia"));
                        c.addView(cardNote("Tidak dapat terhubung", "Periksa koneksi internet lalu tekan tombol ↻. Pastikan izin INTERNET tersedia di AndroidManifest.xml."));
                        body.addView(c);
                    }
                    toast("Data pasar gagal dimuat. Coba refresh.");
                }
            });
        }).start();
    }

    private Coin find(String id) {
        for (Coin c : coins) if (c.id.equals(id)) return c;
        return null;
    }

    private double totalEquity() {
        double total = cash;
        for (Coin c : coins) total += holdings.getOrDefault(c.id, 0.0) * c.price;
        return total;
    }

    private ScrollView scroll() {
        ScrollView s = new ScrollView(this);
        s.setFillViewport(false);
        s.setClipToPadding(false);
        s.setPadding(dp(16), dp(4), dp(16), dp(16));
        return s;
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private Button actionButton(String label, int color) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(12);
        b.setTextColor(color == PANEL2 ? WHITE : (color == GREEN ? Color.rgb(7,25,21) : Color.WHITE));
        b.setAllCaps(false);
        b.setBackground(round(color, 10));
        b.setPadding(dp(8), 0, dp(8), 0);
        return b;
    }

    private Button smallButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(19);
        b.setTextColor(WHITE);
        b.setAllCaps(false);
        b.setMinWidth(dp(42));
        b.setPadding(0, 0, 0, 0);
        b.setBackground(round(PANEL2, 10));
        return b;
    }

    private View space(int width) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(width, 1));
        return v;
    }

    private android.graphics.drawable.GradientDrawable round(int color, int radius) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private int dp(float v) { return (int)(getResources().getDisplayMetrics().density * v + 0.5f); }
    private String money(double v) { return new DecimalFormat("#,##0.00").format(v); }
    private String pct(double v) { return new DecimalFormat("0.00").format(v); }
    private String price(double v) {
        if (v >= 1000) return new DecimalFormat("#,##0.00").format(v);
        if (v >= 1) return new DecimalFormat("0.0000").format(v);
        if (v >= 0.01) return new DecimalFormat("0.00000").format(v);
        return new DecimalFormat("0.########").format(v);
    }
    private String compact(double v) {
        if (v >= 1e12) return new DecimalFormat("0.00").format(v/1e12) + "T";
        if (v >= 1e9) return new DecimalFormat("0.00").format(v/1e9) + "B";
        if (v >= 1e6) return new DecimalFormat("0.00").format(v/1e6) + "M";
        if (v >= 1e3) return new DecimalFormat("0.00").format(v/1e3) + "K";
        return money(v);
    }
    private void toast(String msg) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); }

    private class SparkChart extends View {
        private final ArrayList<Double> points;
        private final int lineColor;
        private final Paint p = new Paint(3);
        SparkChart(ArrayList<Double> pts, int color) {
            super(MainActivity.this);
            points = new ArrayList<>(pts);
            lineColor = color;
            setBackground(round(PANEL, 8));
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (points.size() < 2) {
                p.setColor(MUTED); p.setTextSize(dp(12));
                canvas.drawText("Grafik 7 hari tidak tersedia", dp(8), getHeight()/2f, p);
                return;
            }
            double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
            for (double v : points) { min = Math.min(min, v); max = Math.max(max, v); }
            if (max-min < 1e-12) max = min + 1;
            float pad = dp(10), w = getWidth()-2*pad, h = getHeight()-2*pad;
            Path path = new Path();
            for (int i=0;i<points.size();i++) {
                float x = pad + w*i/(points.size()-1);
                float y = pad + h - (float)((points.get(i)-min)/(max-min))*h;
                if (i==0) path.moveTo(x,y); else path.lineTo(x,y);
            }
            p.setColor(lineColor); p.setStrokeWidth(dp(2)); p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
            canvas.drawPath(path,p);
            p.setStyle(Paint.Style.FILL);
        }
    }
                                    }
