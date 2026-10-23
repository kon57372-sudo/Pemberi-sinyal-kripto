package com.ham.signal;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    final int BG=0xff0b0f17, PANEL=0xff141b27, PANEL2=0xff1b2433,
            WHITE=0xffeef3fa, MUTED=0xff91a0b5, GREEN=0xff16c784,
            RED=0xfff6465d, BLUE=0xff4882ff, YELLOW=0xffffc857;

    static final double FEE_RATE = 0.001; // 0.1% Fee Exchange
    static final int REFRESH_INTERVAL = 15000; // 15 Detik
    static final int KLINES_TTL = 300000;

    // Multiple endpoints untuk bypass blokir internet
    static final String[] BINANCE_HOSTS = {
            "https://api.binance.com", "https://api1.binance.com",
            "https://api2.binance.com", "https://api3.binance.com", "https://api4.binance.com"
    };

    LinearLayout root, body, bottom, activeCoinList;
    TextView status;
    Handler handler = new Handler(Looper.getMainLooper());
    ExecutorService ioPool = Executors.newFixedThreadPool(5);

    ArrayList<Coin> coins = new ArrayList<>();
    ArrayList<Coin> scannerCoins = new ArrayList<>();
    ArrayList<Tx> history = new ArrayList<>();
    HashMap<String, Double> holdings = new HashMap<>();
    HashMap<String, Double> costBasis = new HashMap<>();
    HashMap<String, BinanceTicker> binanceTickers = new HashMap<>();
    HashMap<String, JSONArray> klinesCache = new HashMap<>();
    HashMap<String, Long> klinesCacheTime = new HashMap<>();

    SharedPreferences prefs;
    double cash = 150000000000.0;
    double realizedPnL = 0.0;
    String page = "Home";
    boolean loading = false;
    boolean loadingScanner = false;
    boolean costBasisNeedsMigration = false;
    String searchQuery = "";
    boolean activeListIsScanner = false;

    int binanceHostIndex = 0;
    int binanceFailures = 0;
    long lastBinanceFetch = 0;

    static class Coin {
        String id = "", name = "", symbol = "", source = "GECKO";
        int rank;
        double price, change, change1h, change7d, cap, volume, high, low;
        ArrayList<Double> spark = new ArrayList<>();
        int realScore = 0;
        String realSignal = "MENUNGGU DATA KLINES";
    }

    static class Tx {
        String symbol, side;
        double qty, price, total, fee;
        long time;
        Tx(String s, String a, double q, double p, double t, double f, long tm) {
            symbol = s; side = a; qty = q; price = p; total = t; fee = f; time = tm;
        }
    }

    static class BinanceTicker {
        double price, change, volume, high, low;
    }

    final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            if (!isFinishing()) {
                fetchBinancePrices();
                handler.postDelayed(this, REFRESH_INTERVAL);
            }
        }
    };

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        prefs = getSharedPreferences("ham_account", MODE_PRIVATE);
        cash = Double.longBitsToDouble(prefs.getLong("cash", Double.doubleToLongBits(150000000000.0)));
        realizedPnL = Double.longBitsToDouble(prefs.getLong("realizedPnL", Double.doubleToLongBits(0.0)));
        loadAccount();
        costBasisNeedsMigration = !prefs.contains("costBasis") && !holdings.isEmpty();
        
        buildShell();
        fetchCoinGeckoMetadata();
        fetchBinancePrices();
        handler.postDelayed(refresh, REFRESH_INTERVAL);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        ioPool.shutdownNow();
        super.onDestroy();
    }

    void loadAccount() {
        try {
            JSONObject h = new JSONObject(prefs.getString("holdings", "{}"));
            Iterator<String> it = h.keys();
            while (it.hasNext()) { String k = it.next(); holdings.put(k, h.getDouble(k)); }
        } catch (Exception ignored) {}
        try {
            JSONObject cb = new JSONObject(prefs.getString("costBasis", "{}"));
            Iterator<String> it = cb.keys();
            while (it.hasNext()) { String k = it.next(); costBasis.put(k, cb.getDouble(k)); }
        } catch (Exception ignored) {}
        try {
            JSONArray a = new JSONArray(prefs.getString("history", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                history.add(new Tx(o.optString("s"), o.optString("a"), o.optDouble("q"), o.optDouble("p"), o.optDouble("v"), o.optDouble("f"), o.optLong("t")));
            }
        } catch (Exception ignored) {}
    }

    void saveAccount() {
        JSONObject h = new JSONObject();
        JSONObject cb = new JSONObject();
        JSONArray a = new JSONArray();
        try {
            for (String k : holdings.keySet()) h.put(k, holdings.get(k));
            for (String k : costBasis.keySet()) cb.put(k, costBasis.get(k));
        } catch (Exception ignored) {}
        for (Tx t : history) {
            try {
                JSONObject o = new JSONObject();
                o.put("s", t.symbol); o.put("a", t.side); o.put("q", t.qty);
                o.put("p", t.price); o.put("v", t.total); o.put("f", t.fee); o.put("t", t.time);
                a.put(o);
            } catch (Exception ignored) {}
        }
        prefs.edit()
                .putLong("cash", Double.doubleToLongBits(cash))
                .putLong("realizedPnL", Double.doubleToLongBits(realizedPnL))
                .putString("holdings", h.toString())
                .putString("costBasis", cb.toString())
                .putString("history", a.toString())
                .apply();
    }

    void buildShell() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        setContentView(root);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(16), dp(12), dp(16), dp(8));
        top.addView(text("HAM", 25, GREEN, true));
        top.addView(text(" EXCHANGE", 14, WHITE, true));
        top.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));

        Button refreshBtn = button("↻", PANEL2);
        refreshBtn.setOnClickListener(v -> { fetchBinancePrices(); fetchCoinGeckoMetadata(); });
        top.addView(refreshBtn);
        root.addView(top);

        status = text("Menghubungkan...", 11, MUTED, false);
        status.setPadding(dp(16), 0, dp(16), dp(8));
        status.setOnClickListener(v -> showInfoDialog());
        root.addView(status);

        FrameLayout frame = new FrameLayout(this);
        root.addView(frame, new LinearLayout.LayoutParams(-1, 0, 1));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        body = column();
        scroll.addView(body);
        frame.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        bottom = new LinearLayout(this);
        bottom.setBackgroundColor(PANEL);
        root.addView(bottom);

        showPage("Home");
    }

    void showPage(String p) {
        page = p;
        activeCoinList = null;
        body.removeAllViews();
        
        if (p.equals("Home")) home();
        else if (p.equals("Wallet")) wallet();
        else if (p.equals("Markets")) marketPage(false);
        else if (p.equals("Scanner")) marketPage(true);

        bottom.removeAllViews();
        String[] names = {"Home", "Markets", "Scanner", "Wallet"};
        String[] icons = {"⌂", "▥", "⌕", "◉"};
        for (int i = 0; i < names.length; i++) {
            final String n = names[i];
            LinearLayout item = column();
            item.setGravity(Gravity.CENTER);
            item.setPadding(0, dp(8), 0, dp(8));
            item.addView(text(icons[i], 20, n.equals(page) ? GREEN : MUTED, true));
            item.addView(text(n, 10, n.equals(page) ? GREEN : MUTED, true));
            item.setOnClickListener(v -> showPage(n));
            bottom.addView(item, new LinearLayout.LayoutParams(0, -2, 1));
        }
    }

    void showInfoDialog() {
        String info = "Sumber Harga: Binance (Real-time) & CoinGecko (Backup).\n" +
                "Sistem akan otomatis mencari server Binance (api1-api4) jika terblokir internet.\n\n" +
                "Status saat ini: " + (lastBinanceFetch > 0 ? "BINANCE TERHUBUNG" : "HANYA COINGECKO") + "\n\n" +
                "Catatan: Data transaksi disimpan di HP Anda (Lokal). Tidak ada koneksi ke uang asli.";
        new AlertDialog.Builder(this).setTitle("Status Sistem").setMessage(info).setPositiveButton("OK", null).show();
    }

    // ===================== DASHBOARD =====================

    void home() {
        LinearLayout c = column();
        c.setPadding(dp(15), dp(4), dp(15), dp(18));
        body.addView(c);
        c.addView(title("Portofolio", "Dashboard Ekuitas Anda"));
        c.addView(balanceCard());

        LinearLayout actions = new LinearLayout(this);
        Button buy = button("＋ Beli", GREEN);
        Button sell = button("－ Jual", PANEL2);
        buy.setOnClickListener(v -> chooseTrade(true));
        sell.setOnClickListener(v -> chooseTrade(false));
        actions.addView(buy, new LinearLayout.LayoutParams(0, dp(46), 1));
        actions.addView(space(8));
        actions.addView(sell, new LinearLayout.LayoutParams(0, dp(46), 1));
        c.addView(actions);

        c.addView(section("Pasar Trending (Live)"));
        ArrayList<Coin> sorted = new ArrayList<>(coins);
        sorted.sort((a, b) -> Double.compare(b.volume, a.volume)); // Sortir berdasar volume tertinggi
        for (int i = 0; i < Math.min(5, sorted.size()); i++) {
            c.addView(coinRow(sorted.get(i), false));
        }
    }

    View balanceCard() {
        LinearLayout card = card();
        double assetValue = 0, totalCost = 0;
        for (String sym : holdings.keySet()) {
            double qty = holdings.getOrDefault(sym, 0.0);
            Coin coin = findSymbol(sym);
            if (coin != null) assetValue += qty * coin.price;
            totalCost += costBasis.getOrDefault(sym, 0.0);
        }
        double totalPnL = realizedPnL + (assetValue - totalCost);
        
        card.addView(text("TOTAL EKUITAS (USD)", 12, MUTED, true));
        card.addView(text("$" + fmt(cash + assetValue), 27, WHITE, true));
        card.addView(spaceVertical(8));
        card.addView(text("TOTAL PROFIT / LOSS", 11, MUTED, true));
        card.addView(text(signedMoney(totalPnL), 20, totalPnL >= 0 ? GREEN : RED, true));
        card.addView(spaceVertical(8));
        
        addMetric(card, "Saldo Kas (Tersedia)", cash, WHITE);
        addMetric(card, "Nilai Koin", assetValue, WHITE);
        addMetric(card, "PnL Terealisasi", realizedPnL, realizedPnL >= 0 ? GREEN : RED);
        addMetric(card, "PnL Belum Terealisasi", assetValue - totalCost, assetValue - totalCost >= 0 ? GREEN : RED);
        
        TextView feeInfo = text("Biaya transaksi exchange: 0.1%", 10, MUTED, false);
        feeInfo.setPadding(0, dp(10), 0, 0);
        card.addView(feeInfo);
        return card;
    }

    void addMetric(LinearLayout parent, String label, double value, int color) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));
        row.addView(text(label, 12, MUTED, false), new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(text("$" + fmt(value), 12, color, true));
        parent.addView(row);
    }

    String signedMoney(double n) {
        return (n > 0 ? "+" : n < 0 ? "-" : "") + "$" + fmt(Math.abs(n));
    }

    // ===================== WALLET =====================

    void wallet() {
        LinearLayout c = column();
        c.setPadding(dp(15), dp(4), dp(15), dp(18));
        body.addView(c);
        c.addView(title("Wallet", "Rincian Aset dan Riwayat"));
        c.addView(balanceCard());

        c.addView(section("Koin yang Dimiliki"));
        boolean any = false;
        for (String sym : new ArrayList<>(holdings.keySet())) {
            double qty = holdings.getOrDefault(sym, 0.0);
            if (qty <= 0) continue;
            any = true;
            Coin coin = findSymbol(sym);
            double p = coin == null ? 0 : coin.price;
            double basis = costBasis.getOrDefault(sym, 0.0);
            double value = qty * p;
            double pnl = value - basis;

            LinearLayout asset = card();
            asset.addView(text(sym.toUpperCase() + " • " + fmt(qty), 14, WHITE, true));
            asset.addView(text("Harga saat ini: $" + price(p) + " (" + (coin != null ? coin.source : "N/A") + ")", 12, MUTED, false));
            asset.addView(text("Nilai Aset: $" + fmt(value), 13, WHITE, true));
            asset.addView(text("PnL: " + signedMoney(pnl), 13, pnl >= 0 ? GREEN : RED, true));
            c.addView(asset);
        }
        if (!any) c.addView(note("Belum ada aset", "Silakan gunakan saldo kas Anda untuk membeli koin pertama."));

        c.addView(section("Riwayat Transaksi"));
        addHistory(c, 100);
    }

    // ===================== TRADING =====================

    void chooseTrade(boolean isBuy) {
        if (coins.isEmpty()) { toast("Data pasar belum siap."); return; }
        EditText amount = new EditText(this);
        amount.setSingleLine(true);
        amount.setInputType(8194);
        amount.setTextColor(WHITE);
        amount.setHintTextColor(MUTED);
        amount.setHint(isBuy ? "Nominal USD (Contoh: 100)" : "Jumlah Koin (Contoh: 0.5)");

        Spinner spinner = new Spinner(this);
        ArrayList<String> names = new ArrayList<>();
        for (Coin coin : coins) names.add(coin.symbol.toUpperCase() + " — $" + price(coin.price));
        spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names));

        LinearLayout box = column();
        box.setPadding(dp(12), dp(8), dp(12), dp(8));
        box.addView(spinner);
        box.addView(amount);

        new AlertDialog.Builder(this)
            .setTitle(isBuy ? "Beli Aset (Limit Market)" : "Jual Aset (Limit Market)")
            .setView(box)
            .setNegativeButton("Batal", null)
            .setPositiveButton("Eksekusi", (d, w) -> {
                try {
                    double n = Double.parseDouble(amount.getText().toString().trim().replace(",", "."));
                    int pos = spinner.getSelectedItemPosition();
                    if (pos < 0 || pos >= coins.size()) return;
                    Coin coin = coins.get(pos);
                    if (!Double.isFinite(n) || n <= 0 || coin.price <= 0) { toast("Angka tidak valid."); return; }
                    executeTrade(coin, isBuy, isBuy ? n / coin.price : n);
                } catch (Exception e) { toast("Input tidak valid."); }
            }).show();
    }

    void executeTrade(Coin coin, boolean buy, double qty) {
        double owned = holdings.getOrDefault(coin.symbol, 0.0);
        double grossValue = qty * coin.price;
        double fee = grossValue * FEE_RATE;
        
        if (!Double.isFinite(grossValue) || grossValue <= 0) { toast("Nilai transaksi tidak valid."); return; }
        
        if (buy) {
            double totalCost = grossValue + fee;
            if (totalCost > cash) { toast("Saldo USD tidak cukup (termasuk fee)."); return; }
            cash -= totalCost;
            holdings.put(coin.symbol, owned + qty);
            costBasis.put(coin.symbol, costBasis.getOrDefault(coin.symbol, 0.0) + grossValue);
        } else {
            if (qty > owned) { toast("Jumlah koin tidak cukup."); return; }
            double netProceed = grossValue - fee;
            double basis = costBasis.getOrDefault(coin.symbol, 0.0);
            double soldCost = owned > 0 ? basis * (qty / owned) : 0;
            
            realizedPnL += (netProceed - soldCost);
            costBasis.put(coin.symbol, Math.max(0, basis - soldCost));
            holdings.put(coin.symbol, owned - qty);
            cash += netProceed;
        }
        
        history.add(0, new Tx(coin.symbol, buy ? "BELI" : "JUAL", qty, coin.price, grossValue, fee, System.currentTimeMillis()));
        saveAccount();
        showPage("Wallet"); // Langsung pindah ke wallet agar user melihat perubahannya
        toast("Transaksi Berhasil! Fee: $" + fmt(fee));
    }

    // ===================== PASAR & SCANNER =====================

    void marketPage(boolean scanner) {
        LinearLayout c = column();
        c.setPadding(dp(15), dp(4), dp(15), dp(18));
        body.addView(c);
        
        c.addView(title(scanner ? "Scanner Sinyal AI" : "Pasar Kripto",
                scanner ? "Deteksi dini Pump / Dump (Berdasar Candle Binance)" : "Harga real-time seluruh aset"));

        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setTextColor(WHITE);
        search.setHintTextColor(MUTED);
        search.setHint("Cari koin...");
        search.setPadding(dp(12), 0, dp(12), 0);
        search.setBackground(bg(PANEL2, 10));
        c.addView(search, new LinearLayout.LayoutParams(-1, dp(46)));

        TextView counter = text(scanner ? "Memindai data Candle (Klines)..." : "Memuat pasar...", 11, MUTED, false);
        counter.setPadding(0, dp(8), 0, dp(8));
        c.addView(counter);

        LinearLayout list = column();
        c.addView(list);
        activeCoinList = list;
        activeListIsScanner = scanner;

        Runnable update = () -> {
            list.removeAllViews();
            String q = search.getText().toString().trim().toLowerCase(Locale.US);
            searchQuery = q;
            ArrayList<Coin> source = scanner ? scannerCoins : coins;
            ArrayList<Coin> found = new ArrayList<>();
            for (Coin coin : source) {
                if (coin.name.toLowerCase(Locale.US).contains(q) || coin.symbol.toLowerCase(Locale.US).contains(q)) found.add(coin);
            }
            if (scanner) {
                found.sort((a, b) -> Integer.compare(Math.abs(b.realScore), Math.abs(a.realScore)));
                counter.setText("Pemindaian Selesai. Menampilkan " + found.size() + " Sinyal Terkuat.");
            } else {
                counter.setText(found.size() + " Koin tersedia.");
            }
            for (Coin coin : found) list.addView(coinRow(coin, scanner));
        };

        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) { update.run(); }
            public void afterTextChanged(Editable e) {}
        });

        update.run();
        
        if (scanner && scannerCoins.isEmpty()) {
            runRealScanner(counter, update);
        }
    }

    // Fungsi ini mengambil data candle asli untuk mendapatkan sinyal yang akurat
    void runRealScanner(TextView counter, Runnable updateList) {
        if (loadingScanner) return;
        loadingScanner = true;
        ioPool.execute(() -> {
            ArrayList<Coin> topCoins = new ArrayList<>();
            // Ambil 15 koin volume tertinggi untuk dipindai
            ArrayList<Coin> sorted = new ArrayList<>(coins);
            sorted.sort((a, b) -> Double.compare(b.volume, a.volume));
            for(int i=0; i<Math.min(15, sorted.size()); i++) topCoins.add(sorted.get(i));

            for (Coin c : topCoins) {
                String pair = c.symbol.toUpperCase() + "USDT";
                try {
                    // Ambil Klines 1 Jam terakhir
                    String url = BINANCE_HOSTS[binanceHostIndex] + "/api/v3/klines?symbol=" + pair + "&interval=1h&limit=50";
                    JSONArray klines = new JSONArray(httpGet(url));
                    calculateRealSignal(c, klines);
                    c.source = "BINANCE";
                } catch (Exception e) {
                    c.realSignal = "DATA GAGAL DIMUAT";
                    c.realScore = 0;
                }
            }
            
            handler.post(() -> {
                scannerCoins.clear();
                scannerCoins.addAll(topCoins);
                loadingScanner = false;
                updateList.run();
            });
        });
    }

    // ===================== ALGORITMA SINYAL KUAT (RSI + VOLUME SPIKE) =====================
    
    void calculateRealSignal(Coin c, JSONArray klines) {
        if (klines.length() < 20) { c.realSignal = "DATA TERLALU SEDIKIT"; return; }
        double[] close = new double[klines.length()];
        double[] volume = new double[klines.length()];
        
        try {
            for (int i = 0; i < klines.length(); i++) {
                JSONArray k = klines.getJSONArray(i);
                close[i] = Double.parseDouble(k.getString(4)); // Close price
                volume[i] = Double.parseDouble(k.getString(5)); // Volume
            }
        } catch (Exception e) { return; }

        int n = close.length;
        double rsi = calculateRSI(close, 14);
        
        // Deteksi Volume Spike (Volume saat ini dibandingkan rata-rata 10 candle sebelumnya)
        double avgVol = 0;
        for(int i = n-11; i < n-1; i++) avgVol += volume[i];
        avgVol = avgVol / 10.0;
        double volRatio = volume[n-1] / avgVol;

        // Sinyal Logic (Momentum & Exhaustion)
        int score = 0;
        String sig = "NETRAL";
        
        // 1. Indikasi Pump (Harga naik + Volume meledak)
        if (volRatio > 2.0 && rsi > 60) {
            score = 85;
            sig = "🚀 POTENSI PUMP (VOL SPIKE)";
        } 
        // 2. Indikasi Dump (Harga jatuh + Volume meledak)
        else if (volRatio > 2.0 && rsi < 40) {
            score = -85;
            sig = "🔻 POTENSI DUMP (JUAL MASIF)";
        }
        // 3. Oversold (Harga murah, siap mantul)
        else if (rsi < 30) {
            score = 60;
            sig = "📉 OVERSOLD (PANTAU REVERSAL)";
        }
        // 4. Overbought (Harga tinggi, siap turun)
        else if (rsi > 75) {
            score = -60;
            sig = "📈 OVERBOUGHT (SIAP KOREKSI)";
        }
        // 5. Trending
        else if (rsi > 50 && close[n-1] > close[n-5]) {
            score = 40;
            sig = "📊 TREN NAIK SEHAT";
        }

        c.realScore = score;
        c.realSignal = sig + " (Skor: " + score + ")";
    }

    double calculateRSI(double[] close, int period) {
        double gain = 0, loss = 0;
        int n = close.length;
        for (int i = n - period; i < n; i++) {
            double diff = close[i] - close[i - 1];
            if (diff > 0) gain += diff; else loss -= diff;
        }
        if (loss == 0) return 100;
        double rs = (gain / period) / (loss / period);
        return 100 - (100 / (1 + rs));
    }

    View coinRow(Coin coin, boolean scanner) {
        LinearLayout row = card();
        LinearLayout line = new LinearLayout(this);
        line.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout left = column();
        left.addView(text("#" + coin.rank + "  " + coin.symbol.toUpperCase() + "  (" + coin.source + ")", 14, WHITE, true));
        left.addView(text(coin.name, 11, MUTED, false));
        left.addView(text("$" + price(coin.price), 13, WHITE, true));
        line.addView(left, new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout right = column();
        right.setGravity(Gravity.RIGHT);
        right.addView(text((coin.change >= 0 ? "+" : "") + fmt(coin.change) + "%", 13, coin.change >= 0 ? GREEN : RED, true));
        right.addView(text("Vol $" + compact(coin.volume), 10, MUTED, false));
        line.addView(right);
        row.addView(line);

        if (scanner) {
            row.addView(spaceVertical(5));
            row.addView(text(coin.realSignal, 12, coin.realScore > 0 ? GREEN : (coin.realScore < 0 ? RED : YELLOW), true));
        }

        row.setOnClickListener(v -> detail(coin));
        return row;
    }

    void detail(Coin coin) {
        LinearLayout box = column();
        box.setPadding(dp(18), dp(12), dp(18), dp(12));
        box.addView(text(coin.name + " (" + coin.symbol.toUpperCase() + ")", 19, WHITE, true));
        box.addView(text("$" + price(coin.price), 27, WHITE, true));
        box.addView(text("Sumber Data: " + coin.source, 12, MUTED, false));
        
        // Tampilkan grafik sparkline atau pesan
        box.addView(new SparkChart(coin.spark), new LinearLayout.LayoutParams(-1, dp(130)));
        
        box.addView(note("Status Scanner", coin.realSignal));
        box.addView(note("High / Low 24h", "$" + price(coin.high) + " / $" + price(coin.low)));

        new AlertDialog.Builder(this)
            .setView(box)
            .setPositiveButton("Beli", (d, w) -> chooseTrade(true))
            .setNegativeButton("Tutup", null)
            .show();
    }

    // ===================== DATA FETCHING =====================

    void fetchBinancePrices() {
        if (loading) return;
        loading = true;
        ioPool.execute(() -> {
            try {
                String url = BINANCE_HOSTS[binanceHostIndex] + "/api/v3/ticker/24hr";
                String json = httpGet(url);
                JSONArray arr = new JSONArray(json);
                HashMap<String, BinanceTicker> map = new HashMap<>();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    String sym = o.optString("symbol", "");
                    if (!sym.endsWith("USDT")) continue;
                    BinanceTicker bt = new BinanceTicker();
                    bt.price = o.optDouble("lastPrice", 0);
                    bt.change = o.optDouble("priceChangePercent", 0);
                    bt.volume = o.optDouble("quoteVolume", 0);
                    bt.high = o.optDouble("highPrice", 0);
                    bt.low = o.optDouble("lowPrice", 0);
                    if (bt.price > 0) map.put(sym, bt);
                }
                binanceFailures = 0;
                handler.post(() -> {
                    binanceTickers = map;
                    lastBinanceFetch = System.currentTimeMillis();
                    loading = false;
                    mergeData();
                    fastUpdateUi();
                    updateStatusBar();
                });
            } catch (Exception e) {
                loading = false;
                binanceFailures++;
                if (binanceFailures >= 2) { // Ganti host jika gagal
                    binanceHostIndex = (binanceHostIndex + 1) % BINANCE_HOSTS.length;
                    binanceFailures = 0;
                }
                handler.post(this::updateStatusBar);
            }
        });
    }

    void fetchCoinGeckoMetadata() {
        ioPool.execute(() -> {
            ArrayList<Coin> result = new ArrayList<>();
            try {
                String address = "https://api.coingecko.com/api/v3/coins/markets?vs_currency=usd&order=market_cap_desc&per_page=100&page=1&sparkline=true&price_change_percentage=1h,24h,7d";
                JSONArray arr = new JSONArray(httpGet(address));
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    Coin c = new Coin();
                    c.id = o.optString("id", "");
                    c.name = o.optString("name", "");
                    c.symbol = o.optString("symbol", "?");
                    c.rank = o.optInt("market_cap_rank", 999);
                    c.price = o.optDouble("current_price", 0);
                    c.change = o.optDouble("price_change_percentage_24h", 0);
                    c.volume = o.optDouble("total_volume", 0);
                    c.high = o.optDouble("high_24h", c.price);
                    c.low = o.optDouble("low_24h", c.price);
                    
                    JSONObject sp = o.optJSONObject("sparkline_in_7d");
                    if (sp != null) {
                        JSONArray pts = sp.optJSONArray("price");
                        if (pts != null) {
                            int step = Math.max(1, pts.length() / 120);
                            for (int j = 0; j < pts.length(); j += step) c.spark.add(pts.optDouble(j, c.price));
                        }
                    }
                    if (c.price > 0) result.add(c);
                }
                handler.post(() -> {
                    coins.clear();
                    coins.addAll(result);
                    mergeData();
                    updateStatusBar();
                    if (!page.equals("Scanner")) showPage(page);
                });
            } catch (Exception ignored) {}
        });
    }

    void mergeData() {
        for (Coin c : coins) {
            BinanceTicker bt = binanceTickers.get(c.symbol.toUpperCase() + "USDT");
            if (bt != null && bt.price > 0) {
                c.price = bt.price;
                c.change = bt.change;
                c.volume = bt.volume;
                c.high = bt.high;
                c.low = bt.low;
                c.source = "BINANCE";
            } else {
                c.source = "GECKO";
            }
        }
        // Sinkronisasi perubahan ke holding wallet
        for (String sym : new ArrayList<>(holdings.keySet())) {
            Coin c = findSymbol(sym);
            if (c != null && costBasis.get(sym) == null) {
                costBasis.put(sym, holdings.get(sym) * c.price);
            }
        }
    }

    void fastUpdateUi() {
        if (page.equals("Markets") || page.equals("Scanner")) {
            if (activeCoinList != null) {
                // Render ulang list secara diam-diam tanpa mengganggu scroll jika memungkinkan
                // (Implementasi sederhana: kita biarkan user refresh manual jika ingin update list)
            }
        } else if (page.equals("Home") || page.equals("Wallet")) {
            showPage(page);
        }
    }

    void updateStatusBar() {
        if (status == null) return;
        String binanceStatus = lastBinanceFetch > 0 ? "BINANCE TERHUBUNG ("+binanceTickers.size()+" pair)" : "MENCARI SERVER BINANCE...";
        status.setText("● " + binanceStatus + " | CoinGecko: " + coins.size() + " koin");
    }

    String httpGet(String urlStr) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
            conn.setRequestProperty("Accept", "application/json");
            if (conn.getResponseCode() != 200) throw new Exception("HTTP " + conn.getResponseCode());
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            return sb.toString();
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    // ===================== UTILITIES =====================

    void addHistory(LinearLayout target, int limit) {
        if (history.isEmpty()) { target.addView(note("Belum ada transaksi", "Transaksi akan muncul di sini.")); return; }
        for (int i = 0; i < Math.min(limit, history.size()); i++) {
            Tx t = history.get(i);
            String date = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date(t.time));
            target.addView(note(t.side + " " + t.symbol.toUpperCase(),
                    fmt(t.qty) + " koin @ $" + price(t.price) + "\nTotal: $" + fmt(t.total) + " (Fee: $" + fmt(t.fee) + ")\n" + date));
        }
    }

    Coin findSymbol(String symbol) {
        for (Coin c : coins) if (c.symbol.equalsIgnoreCase(symbol)) return c;
        return null;
    }

    View title(String heading, String sub) {
        LinearLayout l = column();
        l.setPadding(0, dp(8), 0, dp(14));
        l.addView(text(heading, 23, WHITE, true));
        l.addView(text(sub, 12, MUTED, false));
        return l;
    }

    View section(String heading) {
        TextView t = text(heading, 16, WHITE, true);
        t.setPadding(0, dp(18), 0, dp(9));
        return t;
    }

    LinearLayout card() {
        LinearLayout l = column();
        l.setPadding(dp(14), dp(13), dp(14), dp(13));
        l.setBackground(bg(PANEL, 13));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        l.setLayoutParams(lp);
        return l;
    }

    View note(String heading, String sub) {
        LinearLayout l = card();
        l.addView(text(heading, 13, WHITE, true));
        TextView t = text(sub, 11, MUTED, false);
        t.setPadding(0, dp(5), 0, 0);
        l.addView(t);
        return l;
    }

    LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(null, 1);
        return t;
    }

    Button button(String label, int color) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(12);
        b.setAllCaps(false);
        b.setTextColor(color == GREEN ? 0xff071915 : WHITE);
        b.setBackground(bg(color, 10));
        return b;
    }

    View space(int width) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(width), 1));
        return v;
    }

    View spaceVertical(int height) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(height)));
        return v;
    }

    GradientDrawable bg(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    int dp(float n) {
        return (int) (getResources().getDisplayMetrics().density * n + 0.5f);
    }

    String fmt(double n) {
        if (Double.isNaN(n) || Double.isInfinite(n)) return "0";
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.US);
        return new DecimalFormat("#,##0.00", symbols).format(n);
    }

    String price(double n) {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.US);
        if (n >= 1000) return new DecimalFormat("#,##0.00", symbols).format(n);
        if (n >= 1) return new DecimalFormat("0.0000", symbols).format(n);
        if (n >= 0.01) return new DecimalFormat("0.00000", symbols).format(n);
        return new DecimalFormat("0.########", symbols).format(n);
    }

    String compact(double n) {
        if (n >= 1e12) return fmt(n / 1e12) + "T";
        if (n >= 1e9) return fmt(n / 1e9) + "B";
        if (n >= 1e6) return fmt(n / 1e6) + "M";
        if (n >= 1e3) return fmt(n / 1e3) + "K";
        return fmt(n);
    }

    void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    class SparkChart extends View {
        ArrayList<Double> points;
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        SparkChart(ArrayList<Double> data) {
            super(MainActivity.this);
            points = new ArrayList<>(data);
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (points.size() < 2) return;
            double min = Collections.min(points);
            double max = Collections.max(points);
            if (max <= min) max = min + 1;
            float pad = dp(8);
            float w = getWidth() - 2 * pad;
            float h = getHeight() - 2 * pad;
            Path path = new Path();
            for (int i = 0; i < points.size(); i++) {
                float x = pad + w * i / (points.size() - 1);
                float y = pad + h - (float) ((points.get(i) - min) / (max - min)) * h;
                if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
            }
            paint.setColor(points.get(points.size() - 1) >= points.get(0) ? GREEN : RED);
            paint.setStrokeWidth(dp(2));
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawPath(path, paint);
        }
    }
            }
