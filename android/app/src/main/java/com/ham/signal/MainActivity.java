package com.ham.signal;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.text.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {

    final int BG=0xff0b0f17, PANEL=0xff141b27,
        PANEL2=0xff1b2433, WHITE=0xffeef3fa,
        MUTED=0xff91a0b5, GREEN=0xff16c784,
        RED=0xfff6465d, BLUE=0xff4882ff,
        YELLOW=0xffffc857;

    static final int BINANCE_INTERVAL = 10000;
    static final int GECKO_INTERVAL = 300000;
    static final int KLINES_TTL = 300000;

    static final String[] BINANCE_HOSTS = {
        "https://api.binance.com",
        "https://api1.binance.com",
        "https://api2.binance.com",
        "https://api3.binance.com"
    };

    LinearLayout root, body, bottom, activeCoinList;
    TextView status;
    Handler handler = new Handler(Looper.getMainLooper());
    ExecutorService ioPool = Executors.newFixedThreadPool(4);

    ArrayList<Coin> coins = new ArrayList<>();
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
    boolean loadingBinance = false;
    boolean loadingGecko = false;
    boolean costBasisNeedsMigration = false;
    String searchQuery = "";
    boolean activeListIsScanner = false;

    int binanceHostIndex = 0;
    int binanceFailures = 0;
    long binanceBackoffUntil = 0;
    long lastBinanceFetch = 0;
    long lastGeckoFetch = 0;

    static class Coin {
        String id = "", name = "", symbol = "", source = "GECKO";
        int rank;
        double price, change, change1h, change7d;
        double cap, volume, high, low;
        ArrayList<Double> spark = new ArrayList<>();
    }

    static class Tx {
        String symbol, side;
        double qty, price, total;
        long time;
        Tx(String s, String a, double q, double p, double t, long tm) {
            symbol = s; side = a; qty = q; price = p; total = t; time = tm;
        }
    }

    static class BinanceTicker {
        double price, change, volume, high, low;
    }

    static class RateLimitException extends Exception {
        long retryAfterMs = 60000;
        RateLimitException(String retryAfter) {
            super("Rate limited");
            try { retryAfterMs = Math.max(1000, Long.parseLong(retryAfter) * 1000); }
            catch (Exception ignored) {}
        }
    }

    final Runnable fastRefresh = new Runnable() {
        @Override
        public void run() {
            if (!isFinishing()) {
                fetchBinance();
                handler.postDelayed(this, BINANCE_INTERVAL);
            }
        }
    };

    final Runnable slowRefresh = new Runnable() {
        @Override
        public void run() {
            if (!isFinishing()) {
                fetchCoinGecko();
                handler.postDelayed(this, GECKO_INTERVAL);
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
        fetchCoinGecko();
        fetchBinance();
        handler.postDelayed(fastRefresh, BINANCE_INTERVAL);
        handler.postDelayed(slowRefresh, GECKO_INTERVAL);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        ioPool.shutdownNow();
        super.onDestroy();
    }

    // ===================== AKUN =====================

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
                history.add(new Tx(o.optString("s"), o.optString("a"),
                        o.optDouble("q"), o.optDouble("p"), o.optDouble("v"), o.optLong("t")));
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
                o.put("p", t.price); o.put("v", t.total); o.put("t", t.time);
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

    void migrateOldCostBasis() {
        if (!costBasisNeedsMigration) return;
        for (String sym : holdings.keySet()) {
            double qty = holdings.getOrDefault(sym, 0.0);
            Coin coin = findSymbol(sym);
            if (coin != null && qty > 0) costBasis.put(sym, qty * coin.price);
        }
        costBasisNeedsMigration = false;
        saveAccount();
    }

    // ===================== KERANGKA UI =====================

    void buildShell() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        setContentView(root);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(16), dp(12), dp(16), dp(8));
        top.addView(text("HAM", 25, GREEN, true));
        top.addView(text(" CRYPTO SIGNAL", 14, WHITE, true));
        top.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));

        Button infoBtn = button("ℹ", PANEL2);
        infoBtn.setOnClickListener(v -> showInfoDialog());
        top.addView(infoBtn);

        Button refreshBtn = button("↻", PANEL2);
        refreshBtn.setOnClickListener(v -> { fetchBinance(); fetchCoinGecko(); });
        top.addView(refreshBtn);
        root.addView(top);

        status = text("Menghubungkan ke pasar...", 11, MUTED, false);
        status.setPadding(dp(16), 0, dp(16), dp(8));
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
        else marketPage(true);

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
        StringBuilder sb = new StringBuilder();
        sb.append("Sumber data:\n");
        sb.append("• Binance Spot API — harga real-time, volume, high/low 24 jam\n");
        sb.append("• CoinGecko — market cap, rank, sparkline 7 hari\n");
        sb.append("• Endpoint fallback otomatis: api → api1 → api2 → api3\n\n");
        sb.append("Refresh:\n");
        sb.append("• Harga Binance: setiap 10 detik\n");
        sb.append("• Metadata CoinGecko: setiap 5 menit\n\n");
        sb.append("Status: ").append(lastBinanceFetch == 0 ? "belum tersambung" : 
            "Binance aktif (" + binanceTickers.size() + " pair USDT)").append("\n\n");
        sb.append("Transaksi disimpan lokal di perangkat. Belum terhubung ke bursa sungguhan. ");
        sb.append("Bukan nasihat keuangan.");
        new AlertDialog.Builder(this)
            .setTitle("Tentang data")
            .setMessage(sb.toString())
            .setPositiveButton("OK", null)
            .show();
    }

    // ===================== DASHBOARD =====================

    void home() {
        LinearLayout c = column();
        c.setPadding(dp(15), dp(4), dp(15), dp(18));
        body.addView(c);
        c.addView(title("HAM Trading Dashboard", "Pemantauan harga real-time dan analisis pasar"));
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

        c.addView(section("Sinyal teratas (live)"));
        ArrayList<Coin> sorted = new ArrayList<>(coins);
        sorted.sort((a, b) -> Integer.compare(Math.abs(score(b)), Math.abs(score(a))));
        for (int i = 0; i < Math.min(12, sorted.size()); i++)
            c.addView(coinRow(sorted.get(i), true));

        c.addView(section("Transaksi terakhir"));
        addHistory(c, 5);
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
        double totalPnL = realizedPnL + assetValue - totalCost;
        card.addView(text("TOTAL EKUITAS", 12, MUTED, true));
        card.addView(text("$" + fmt(cash + assetValue), 27, WHITE, true));
        card.addView(spaceVertical(8));
        card.addView(text("TOTAL PROFIT / LOSS", 11, MUTED, true));
        card.addView(text(signedMoney(totalPnL), 20, totalPnL >= 0 ? GREEN : RED, true));
        card.addView(spaceVertical(8));
        addMetric(card, "Saldo tersedia", cash, WHITE);
        addMetric(card, "Nilai aset", assetValue, WHITE);
        addMetric(card, "PnL terealisasi", realizedPnL, realizedPnL >= 0 ? GREEN : RED);
        addMetric(card, "PnL belum terealisasi", assetValue - totalCost,
            assetValue - totalCost >= 0 ? GREEN : RED);
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
        c.addView(title("Wallet", "Saldo dan kepemilikan"));
        c.addView(balanceCard());

        LinearLayout actions = new LinearLayout(this);
        Button buy = button("Beli", GREEN);
        Button sell = button("Jual", PANEL2);
        buy.setOnClickListener(v -> chooseTrade(true));
        sell.setOnClickListener(v -> chooseTrade(false));
        actions.addView(buy, new LinearLayout.LayoutParams(0, dp(44), 1));
        actions.addView(space(8));
        actions.addView(sell, new LinearLayout.LayoutParams(0, dp(44), 1));
        c.addView(actions);

        c.addView(section("Aset yang dimiliki"));
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
            asset.addView(text("Harga: $" + price(p) + (coin != null ? " (" + coin.source + ")" : ""),
                12, MUTED, false));
            asset.addView(text("Nilai aset: $" + fmt(value), 13, WHITE, true));
            asset.addView(text("PnL: " + signedMoney(pnl), 13, pnl >= 0 ? GREEN : RED, true));
            c.addView(asset);
        }
        if (!any) c.addView(note("Belum ada aset", "Tekan Beli untuk membeli aset."));

        c.addView(section("Riwayat transaksi"));
        addHistory(c, 100);
    }

    // ===================== TRANSAKSI =====================

    void chooseTrade(boolean isBuy) {
        if (coins.isEmpty()) { toast("Harga pasar belum tersedia."); return; }
        EditText amount = new EditText(this);
        amount.setSingleLine(true);
        amount.setInputType(8194);
        amount.setTextColor(WHITE);
        amount.setHintTextColor(MUTED);
        amount.setHint(isBuy ? "Nominal USD" : "Jumlah koin");

        Spinner spinner = new Spinner(this);
        ArrayList<String> names = new ArrayList<>();
        for (Coin coin : coins)
            names.add(coin.symbol.toUpperCase() + " — " + coin.name);

        spinner.setAdapter(new ArrayAdapter<String>(this,
            android.R.layout.simple_spinner_dropdown_item, names));

        LinearLayout box = column();
        box.setPadding(dp(12), dp(8), dp(12), dp(8));
        box.addView(spinner);
        box.addView(amount);

        new AlertDialog.Builder(this)
            .setTitle(isBuy ? "Beli aset" : "Jual aset")
            .setView(box)
            .setNegativeButton("Batal", null)
            .setPositiveButton("Konfirmasi", (d, w) -> {
                try {
                    double n = Double.parseDouble(amount.getText().toString().trim().replace(",", "."));
                    int pos = spinner.getSelectedItemPosition();
                    if (pos < 0 || pos >= coins.size()) return;
                    Coin coin = coins.get(pos);
                    if (!Double.isFinite(n) || n <= 0 || coin.price <= 0) {
                        toast("Masukkan angka yang valid."); return;
                    }
                    executeTrade(coin, isBuy, isBuy ? n / coin.price : n);
                } catch (Exception e) {
                    toast("Angka tidak valid.");
                }
            }).show();
    }

    void tradeOne(Coin coin, boolean buy) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(8194);
        input.setTextColor(WHITE);
        input.setHintTextColor(MUTED);
        input.setHint(buy ? "Nominal USD" : "Jumlah koin");
        new AlertDialog.Builder(this)
            .setTitle((buy ? "Beli " : "Jual ") + coin.symbol.toUpperCase())
            .setView(input)
            .setNegativeButton("Batal", null)
            .setPositiveButton("Konfirmasi", (d, w) -> {
                try {
                    double n = Double.parseDouble(input.getText().toString().trim().replace(",", "."));
                    if (!Double.isFinite(n) || n <= 0 || coin.price <= 0) {
                        toast("Angka tidak valid."); return;
                    }
                    executeTrade(coin, buy, buy ? n / coin.price : n);
                } catch (Exception e) { toast("Angka tidak valid."); }
            }).show();
    }

    void executeTrade(Coin coin, boolean buy, double qty) {
        double owned = holdings.getOrDefault(coin.symbol, 0.0);
        double value = qty * coin.price;
        if (!Double.isFinite(value) || value <= 0) { toast("Nilai transaksi tidak valid."); return; }
        if (buy) {
            if (value > cash) { toast("Saldo tidak cukup."); return; }
            cash -= value;
            holdings.put(coin.symbol, owned + qty);
            costBasis.put(coin.symbol, costBasis.getOrDefault(coin.symbol, 0.0) + value);
        } else {
            if (qty > owned) { toast("Jumlah koin tidak cukup."); return; }
            double basis = costBasis.getOrDefault(coin.symbol, 0.0);
            double soldCost = owned > 0 ? basis * (qty / owned) : 0;
            realizedPnL += value - soldCost;
            costBasis.put(coin.symbol, Math.max(0, basis - soldCost));
            holdings.put(coin.symbol, owned - qty);
            cash += value;
        }
        history.add(new Tx(coin.symbol, buy ? "BELI" : "JUAL", qty, coin.price, value, System.currentTimeMillis()));
        saveAccount();
        showPage(page);
        toast("Transaksi dicatat.");
    }

    // ===================== PASAR DAN SCANNER =====================

    void marketPage(boolean scanner) {
        LinearLayout c = column();
        c.setPadding(dp(15), dp(4), dp(15), dp(18));
        body.addView(c);
        c.addView(title(scanner ? "Coin Scanner" : "Pasar Kripto",
            scanner ? "Analisis momentum dan risiko (live)" : "Harga, volume, dan perubahan pasar (live)"));

        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setTextColor(WHITE);
        search.setHintTextColor(MUTED);
        search.setHint("Cari nama atau simbol koin");
        search.setPadding(dp(12), 0, dp(12), 0);
        search.setBackground(bg(PANEL2, 10));
        if (!searchQuery.isEmpty()) { search.setText(searchQuery); search.setSelection(searchQuery.length()); }
        c.addView(search, new LinearLayout.LayoutParams(-1, dp(46)));

        TextView counter = text("", 11, MUTED, false);
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
            ArrayList<Coin> found = new ArrayList<>();
            for (Coin coin : coins) {
                if (coin.name.toLowerCase(Locale.US).contains(q) ||
                    coin.symbol.toLowerCase(Locale.US).contains(q)) {
                    found.add(coin);
                }
            }
            if (scanner) found.sort((a, b) ->
                Integer.compare(Math.abs(score(b)), Math.abs(score(a))));
            counter.setText(found.size() + " koin ditemukan • Binance " + binanceTickers.size() + " pair");
            for (Coin coin : found) list.addView(coinRow(coin, scanner));
        };

        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) { update.run(); }
            public void afterTextChanged(Editable e) {}
        });

        update.run();
    }

    View coinRow(Coin coin, boolean scanner) {
        LinearLayout row = card();
        LinearLayout line = new LinearLayout(this);
        line.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout left = column();
        left.addView(text("#" + coin.rank + "  " + coin.symbol.toUpperCase() +
            "  " + badge(coin.source), 14, WHITE, true));
        left.addView(text(coin.name, 11, MUTED, false));
        left.addView(text("$" + price(coin.price), 13, WHITE, true));
        line.addView(left, new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout right = column();
        right.setGravity(Gravity.RIGHT);
        right.addView(text((coin.change >= 0 ? "+" : "") + fmt(coin.change) + "%",
            13, coin.change >= 0 ? GREEN : RED, true));
        right.addView(text("Vol $" + compact(coin.volume), 10, MUTED, false));
        line.addView(right);
        row.addView(line);

        if (scanner) {
            row.addView(spaceVertical(5));
            row.addView(text(signal(coin) + " • Skor " + score(coin),
                11, score(coin) >= 0 ? GREEN : RED, true));
            row.addView(text("RSI " + fmt(rsi(coin.spark)) +
                " | EMA9 " + price(ema(coin.spark, 9)) +
                " | EMA21 " + price(ema(coin.spark, 21)),
                10, MUTED, false));
        }

        row.setOnClickListener(v -> detail(coin));
        return row;
    }

    String badge(String source) {
        if ("BINANCE".equals(source)) return "●BIN";
        if ("DEX".equals(source)) return "●DEX";
        return "○GC";
    }

    void detail(Coin coin) {
        LinearLayout box = column();
        box.setPadding(dp(18), dp(12), dp(18), dp(12));

        box.addView(text("#" + coin.rank + " " + coin.name + " (" +
            coin.symbol.toUpperCase() + ")", 19, WHITE, true));
        box.addView(text("$" + price(coin.price), 27, WHITE, true));
        box.addView(text("Sumber: " + coin.source + " • 24 jam: " + fmt(coin.change) + "%",
            13, coin.change >= 0 ? GREEN : RED, true));

        String pair = coin.symbol.toUpperCase(Locale.US) + "USDT";
        JSONArray klines = klinesCache.get(pair);
        if (klines != null && klines.length() >= 2) {
            box.addView(new CandleChart(klines),
                new LinearLayout.LayoutParams(-1, dp(180)));
        } else {
            box.addView(new SparkChart(coin.spark),
                new LinearLayout.LayoutParams(-1, dp(130)));
            fetchKlinesFor(pair);
        }

        box.addView(note("Sinyal HAM", signal(coin) + " • skor " + score(coin)));
        box.addView(note("RSI (14)", fmt(rsi(coin.spark))));
        box.addView(note("EMA 9 / 21 / 50",
            price(ema(coin.spark, 9)) + " / " +
            price(ema(coin.spark, 21)) + " / " +
            price(ema(coin.spark, 50))));
        box.addView(note("MACD", fmt(macd(coin.spark))));
        box.addView(note("Market cap", "$" + compact(coin.cap)));
        box.addView(note("Volume 24 jam", "$" + compact(coin.volume)));
        box.addView(note("High / Low 24 jam",
            "$" + price(coin.high) + " / $" + price(coin.low)));
        box.addView(note("Perubahan 1 jam / 7 hari",
            fmt(coin.change1h) + "% / " + fmt(coin.change7d) + "%"));

        new AlertDialog.Builder(this)
            .setView(box)
            .setPositiveButton("Beli", (d, w) -> tradeOne(coin, true))
            .setNeutralButton("Jual", (d, w) -> tradeOne(coin, false))
            .setNegativeButton("CoinGecko", (d, w) -> {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://www.coingecko.com/en/coins/" + coin.id)));
                } catch (Exception ignored) {}
            })
            .show();
    }

    // ===================== HTTP =====================

    String httpGet(String urlStr, int connTimeout, int readTimeout) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(connTimeout);
            conn.setReadTimeout(readTimeout);
            conn.setRequestProperty("User-Agent", "HAM-Crypto-Signal/2.0");
            conn.setRequestProperty("Accept", "application/json");
            int code = conn.getResponseCode();
            if (code == 429) throw new RateLimitException(conn.getHeaderField("Retry-After"));
            if (code != 200) throw new IOException("HTTP " + code);
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

    // ===================== BINANCE (harga real-time) =====================

    void fetchBinance() {
        if (loadingBinance) return;
        if (System.currentTimeMillis() < binanceBackoffUntil) return;
        loadingBinance = true;
        ioPool.execute(() -> {
            try {
                String url = BINANCE_HOSTS[binanceHostIndex] + "/api/v3/ticker/24hr";
                String responseBody = httpGet(url, 10000, 15000);
                JSONArray arr = new JSONArray(responseBody);
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
                final HashMap<String, BinanceTicker> newMap = map;
                binanceFailures = 0;
                handler.post(() -> {
                    binanceTickers = newMap;
                    lastBinanceFetch = System.currentTimeMillis();
                    loadingBinance = false;
                    mergeBinanceData();
                    fastUpdateUi();
                    updateStatusBar();
                });
            } catch (RateLimitException rl) {
                binanceBackoffUntil = System.currentTimeMillis() + rl.retryAfterMs;
                binanceFailures = 0;
                handler.post(() -> { loadingBinance = false; updateStatusBar(); });
            } catch (Exception e) {
                binanceFailures++;
                if (binanceFailures >= 3) {
                    binanceHostIndex = (binanceHostIndex + 1) % BINANCE_HOSTS.length;
                    binanceBackoffUntil = System.currentTimeMillis() + 30000;
                    binanceFailures = 0;
                }
                handler.post(() -> { loadingBinance = false; updateStatusBar(); });
            }
        });
    }

    void fetchKlinesFor(String pair) {
        Long cached = klinesCacheTime.get(pair);
        if (cached != null && System.currentTimeMillis() - cached < KLINES_TTL) return;
        ioPool.execute(() -> {
            try {
                String url = BINANCE_HOSTS[binanceHostIndex] + "/api/v3/klines?symbol=" + pair + "&interval=1h&limit=100";
                String respBody = httpGet(url, 10000, 15000);
                JSONArray arr = new JSONArray(respBody);
                klinesCache.put(pair, arr);
                klinesCacheTime.put(pair, System.currentTimeMillis());
            } catch (Exception ignored) {}
        });
    }

    void mergeBinanceData() {
        for (Coin c : coins) {
            String pair = c.symbol.toUpperCase(Locale.US) + "USDT";
            BinanceTicker bt = binanceTickers.get(pair);
            if (bt != null && bt.price > 0) {
                c.price = bt.price;
                c.change = bt.change;
                c.volume = bt.volume;
                if (bt.high > 0) c.high = bt.high;
                if (bt.low > 0) c.low = bt.low;
                c.source = "BINANCE";
            } else if (c.source == null || c.source.isEmpty()) {
                c.source = "GECKO";
            }
        }
        migrateOldCostBasis();
    }

    void fastUpdateUi() {
        if (page.equals("Markets") || page.equals("Scanner")) {
            if (activeCoinList != null) {
                activeCoinList.removeAllViews();
                String q = searchQuery.toLowerCase(Locale.US);
                ArrayList<Coin> found = new ArrayList<>();
                for (Coin coin : coins) {
                    if (coin.name.toLowerCase(Locale.US).contains(q) ||
                        coin.symbol.toLowerCase(Locale.US).contains(q)) found.add(coin);
                }
                if (activeListIsScanner) found.sort((a, b) ->
                    Integer.compare(Math.abs(score(b)), Math.abs(score(a))));
                for (Coin coin : found) activeCoinList.addView(coinRow(coin, activeListIsScanner));
            }
        } else if (page.equals("Home") || page.equals("Wallet")) {
            showPage(page);
        }
    }

    void updateStatusBar() {
        if (status == null) return;
        StringBuilder sb = new StringBuilder();
        sb.append("● ");
        if (lastBinanceFetch > 0) sb.append("BINANCE ").append(binanceTickers.size()).append(" pair");
        else sb.append("Binance belum tersambung");
        sb.append(" | ");
        sb.append("CoinGecko ").append(coins.size()).append(" koin");
        if (lastBinanceFetch > 0) {
            sb.append(" | ").append(new SimpleDateFormat("HH:mm:ss", Locale.US)
                .format(new Date(lastBinanceFetch)));
        }
        if (loadingBinance) sb.append(" • memuat...");
        status.setText(sb.toString());
    }

    // ===================== COINGECKO (metadata) =====================

    void fetchCoinGecko() {
        if (loadingGecko) return;
        loadingGecko = true;
        if (status != null && coins.isEmpty()) status.setText("Memuat metadata CoinGecko...");
        ioPool.execute(() -> {
            ArrayList<Coin> result = new ArrayList<>();
            String error = null;
            try {
                for (int pageNo = 1; pageNo <= 3; pageNo++) {
                    String address = "https://api.coingecko.com/api/v3/coins/markets" +
                        "?vs_currency=usd&order=market_cap_desc&per_page=100&page=" + pageNo +
                        "&sparkline=true&price_change_percentage=1h%2C24h%2C7d";
                    String respBody = httpGet(address, 15000, 20000);
                    JSONArray arr = new JSONArray(respBody);
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        Coin c = new Coin();
                        c.id = o.optString("id", "");
                        c.name = o.optString("name", "");
                        c.symbol = o.optString("symbol", "?");
                        c.rank = o.optInt("market_cap_rank", result.size() + 1);
                        c.price = o.optDouble("current_price", 0);
                        c.change = o.optDouble("price_change_percentage_24h", 0);
                        c.change1h = o.optDouble("price_change_percentage_1h_in_currency", 0);
                        c.change7d = o.optDouble("price_change_percentage_7d_in_currency", 0);
                        c.cap = o.optDouble("market_cap", 0);
                        c.volume = o.optDouble("total_volume", 0);
                        c.high = o.optDouble("high_24h", c.price);
                        c.low = o.optDouble("low_24h", c.price);
                        c.source = "GECKO";

                        JSONObject sp = o.optJSONObject("sparkline_in_7d");
                        JSONArray pts = sp == null ? null : sp.optJSONArray("price");
                        if (pts != null) {
                            int step = Math.max(1, pts.length() / 120);
                            for (int j = 0; j < pts.length(); j += step) {
                                double val = pts.optDouble(j, c.price);
                                if (val > 0) c.spark.add(val);
                            }
                            if (pts.length() > 0) {
                                double last = pts.optDouble(pts.length() - 1, c.price);
                                if (last > 0 && (c.spark.isEmpty() || c.spark.get(c.spark.size() - 1) != last)) {
                                    c.spark.add(last);
                                }
                            }
                        }
                        if (c.price > 0 && !c.id.isEmpty()) result.add(c);
                    }
                    if (pageNo < 3) Thread.sleep(350);
                }
            } catch (Exception e) {
                error = e.getMessage();
            }
            final String err = error;
            final ArrayList<Coin> loaded = result;
            handler.post(() -> {
                loadingGecko = false;
                if (!loaded.isEmpty()) {
                    coins.clear();
                    coins.addAll(loaded);
                    lastGeckoFetch = System.currentTimeMillis();
                    mergeBinanceData();
                    updateStatusBar();
                    if (page.equals("Home") || page.equals("Markets") || page.equals("Scanner") || page.equals("Wallet"))
                        showPage(page);
                } else if (err != null) {
                    status.setText("Gagal CoinGecko: " + err + " (mencoba Binance saja)");
                }
            });
        });
    }

    // ===================== ALGORITMA =====================

    int score(Coin c) {
        int s = 0;
        if (c.change1h > 1) s += 20; else if (c.change1h > 0) s += 8;
        if (c.change1h < -1) s -= 20; else if (c.change1h < 0) s -= 8;
        if (c.change > 5) s += 15; else if (c.change > 1) s += 7;
        if (c.change < -5) s -= 15; else if (c.change < -1) s -= 7;
        if (c.change7d > 8) s += 12; else if (c.change7d < -8) s -= 12;
        double r = rsi(c.spark);
        if (r > 75) s -= 10;
        if (r < 35 && c.change > 0) s += 8;
        double e9 = ema(c.spark, 9);
        double e21 = ema(c.spark, 21);
        if (e9 > e21 && e21 > 0) s += 10;
        if (e9 < e21 && e21 > 0) s -= 10;
        double m = macd(c.spark);
        if (m > 0) s += 5; else if (m < 0) s -= 5;
        if (c.cap > 0 && c.volume / c.cap > 0.1) s += c.change >= 0 ? 8 : -8;
        return Math.max(-100, Math.min(100, s));
    }

    String signal(Coin c) {
        int s = score(c);
        double r = rsi(c.spark);
        if (s >= 50) return "MOMENTUM KUAT";
        if (s >= 25) return "POTENSI BREAKOUT";
        if (s <= -50) return "RISIKO JUAL TINGGI";
        if (s <= -25) return "TEKANAN JUAL";
        if (r < 35) return "RSI RENDAH • PANTAU";
        if (r > 70) return "RSI TINGGI • WASPADA";
        return "MENUNGGU KONFIRMASI";
    }

    double ema(ArrayList<Double> data, int period) {
        if (data == null || data.isEmpty()) return 0;
        double result = data.get(0);
        double alpha = 2.0 / (period + 1.0);
        for (int i = 1; i < data.size(); i++)
            result = alpha * data.get(i) + (1 - alpha) * result;
        return result;
    }

    double rsi(ArrayList<Double> data) {
        if (data == null || data.size() < 3) return 50;
        int start = Math.max(1, data.size() - 14);
        double gain = 0, loss = 0;
        int count = 0;
        for (int i = start; i < data.size(); i++) {
            double diff = data.get(i) - data.get(i - 1);
            if (diff > 0) gain += diff; else loss -= diff;
            count++;
        }
        if (count == 0) return 50;
        if (loss == 0) return gain == 0 ? 50 : 100;
        double rs = (gain / count) / (loss / count);
        return 100.0 - (100.0 / (1.0 + rs));
    }

    double macd(ArrayList<Double> data) {
        if (data == null || data.size() < 2) return 0;
        return ema(data, 12) - ema(data, 26);
    }

    // ===================== RIWAYAT =====================

    void addHistory(LinearLayout target, int limit) {
        if (history.isEmpty()) {
            target.addView(note("Belum ada transaksi", "Transaksi beli dan jual muncul di sini."));
            return;
        }
        int count = Math.min(limit, history.size());
        for (int i = 0; i < count; i++) {
            Tx t = history.get(i);
            String date = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date(t.time));
            target.addView(note(t.side + " " + t.symbol.toUpperCase(),
                fmt(t.qty) + " koin × $" + price(t.price) + " = $" + fmt(t.total) + "\n" + date));
        }
    }

    Coin findSymbol(String symbol) {
        for (Coin c : coins) if (c.symbol.equalsIgnoreCase(symbol)) return c;
        return null;
    }

    // ===================== KOMPONEN UI =====================

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

    // ===================== GRAFIK =====================

    class SparkChart extends View {
        ArrayList<Double> points;
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        SparkChart(ArrayList<Double> data) {
            super(MainActivity.this);
            points = new ArrayList<>(data);
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (points.size() < 2) {
                paint.setColor(MUTED);
                paint.setTextSize(dp(12));
                canvas.drawText("Grafik belum tersedia", dp(8), getHeight() / 2f, paint);
                return;
            }
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
            paint.setStyle(Paint.Style.FILL);
        }
    }

    class CandleChart extends View {
        JSONArray klines;
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        CandleChart(JSONArray data) {
            super(MainActivity.this);
            klines = data;
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (klines == null || klines.length() < 2) {
                paint.setColor(MUTED);
                paint.setTextSize(dp(12));
                canvas.drawText("Candle belum tersedia", dp(8), getHeight() / 2f, paint);
                return;
            }
            int n = klines.length();
            double[] open = new double[n], high = new double[n], low = new double[n], close = new double[n];
            double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
            try {
                for (int i = 0; i < n; i++) {
                    JSONArray k = klines.getJSONArray(i);
                    open[i] = Double.parseDouble(k.getString(1));
                    high[i] = Double.parseDouble(k.getString(2));
                    low[i] = Double.parseDouble(k.getString(3));
                    close[i] = Double.parseDouble(k.getString(4));
                    if (low[i] < min) min = low[i];
                    if (high[i] > max) max = high[i];
                }
            } catch (Exception e) { return; }
            if (max <= min) max = min + 1;
            float pad = dp(8);
            float w = getWidth() - 2 * pad;
            float h = getHeight() - 2 * pad;
            float cw = Math.max(1f, w / n * 0.7f);
            for (int i = 0; i < n; i++) {
                float cx = pad + w * i / (float) n + cw / 2f;
                float yO = pad + h - (float) ((open[i] - min) / (max - min)) * h;
                float yC = pad + h - (float) ((close[i] - min) / (max - min)) * h;
                float yH = pad + h - (float) ((high[i] - min) / (max - min)) * h;
                float yL = pad + h - (float) ((low[i] - min) / (max - min)) * h;
                boolean up = close[i] >= open[i];
                paint.setColor(up ? GREEN : RED);
                paint.setStrokeWidth(dp(1));
                canvas.drawLine(cx, yH, cx, yL, paint);
                float top = Math.min(yO, yC);
                float bot = Math.max(yO, yC);
                if (bot - top < dp(1)) bot = top + dp(1);
                canvas.drawRect(cx - cw / 2f, top, cx + cw / 2f, bot, paint);
            }
        }
    }
                                          }
