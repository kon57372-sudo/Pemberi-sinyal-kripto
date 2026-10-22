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
    private String page = "Home";
    private String selectedCoin = "bitcoin";
    private String query = "";
    private String currency = "USD";
    private boolean loading = false;

    static class Coin {
        String id, name, symbol, image;
        double price, change, change1h, change7d, marketCap, volume, high, low;
        ArrayList<Double> spark = new ArrayList<>();
    }

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);
        buildShell();
        fetchMarkets();
        handler.postDelayed(autoRefresh, 60000);
    }

    private final Runnable autoRefresh = new Runnable() {
        @Override public void run() {
            if (!isFinishing()) fetchMarkets();
            handler.postDelayed(this, 60000);
        }
    };

    @Override protected void onDestroy() {
        handler.removeCallbacks(autoRefresh);
        super.onDestroy();
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
        String[] labels = {"Home", "Markets", "Scanner", "Signals"};
        String[] icons = {"⌂", "▥", "⌕", "⚡"};
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
        else if (name.equals("Scanner") || name.equals("Signals")) renderMarkets(true);
        else renderMarkets(false);
        rebuildBottom();
    }

    private void rebuildBottom() {
        if (bottom == null) return;
        bottom.removeAllViews();
        String[] labels = {"Home", "Markets", "Scanner", "Signals"};
        String[] icons = {"⌂", "▥", "⌕", "⚡"};
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
        content.addView(titleBlock("HAM Signal Center", "Peringatan momentum awal, breakout, pembalikan, dan tekanan jual"));
        int pump=0, dump=0, bullish=0, bearish=0;
        for (Coin c : coins) {
            int score = signalScore(c);
            if (score >= 55) pump++;
            else if (score <= -45) dump++;
            else if (score >= 25) bullish++;
            else if (score <= -25) bearish++;
        }
        LinearLayout summary = cardColumn();
        summary.addView(text("PERINGATAN PASAR • " + coins.size() + " ASET", 11, MUTED, true));
        summary.addView(text(pump + " potensi pump awal  •  " + dump + " potensi dump awal", 16, WHITE, true));
        summary.addView(text(bullish + " momentum naik  •  " + bearish + " tekanan jual", 12, MUTED, false));
        LinearLayout actions = new LinearLayout(this);
        Button scan = actionButton("Pindai semua sinyal", GREEN);
        scan.setOnClickListener(v -> showPage("Scanner"));
        Button market = actionButton("Lihat pasar", PANEL2);
        market.setOnClickListener(v -> showPage("Markets"));
        actions.addView(scan, new LinearLayout.LayoutParams(0, dp(44), 1));
        actions.addView(space(dp(8)));
        actions.addView(market, new LinearLayout.LayoutParams(0, dp(44), 1));
        summary.addView(actions);
        content.addView(summary);

        content.addView(sectionHeader("Peringatan paling kuat", "Semua sinyal", () -> showPage("Scanner")));
        ArrayList<Coin> ranked = new ArrayList<>(coins);
        ranked.sort((a,b) -> Integer.compare(Math.abs(signalScore(b)), Math.abs(signalScore(a))));
        int count = Math.min(8, ranked.size());
        for (int i=0; i<count; i++) content.addView(signalRow(ranked.get(i)));
        content.addView(cardNote("Indikator aktif",
                "Perubahan 1j/24j/7h, RSI, EMA 9/21, MACD perkiraan, posisi Bollinger, volatilitas sampel, jarak dari high 7 hari, volume/market cap, momentum, dan skor gabungan. Refresh otomatis tiap 60 detik; tekan ↻ untuk memperbarui sekarang. Tidak ada indikator yang dapat memastikan pump/dump lebih awal."));
    }

    private void renderMarkets(boolean scanner) {
        ScrollView sc = scroll();
        LinearLayout content = column();
        sc.addView(content);
        body.addView(sc);
        content.addView(titleBlock(scanner ? "Coin Scanner" : "Pasar Kripto",
                scanner ? "Peringatan dini momentum dan risiko • urut kekuatan sinyal" : "Harga, volume, kapitalisasi pasar"));
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
        if (scanner) shown.sort((a,b) -> Integer.compare(Math.abs(signalScore(b)), Math.abs(signalScore(a))));
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
        content.addView(titleBlock(c.name + " (" + c.symbol.toUpperCase(Locale.US) + ")", "Detail aset • pembaruan pasar otomatis"));
        LinearLayout priceCard = cardColumn();
        priceCard.addView(text("$" + price(c.price), 29, WHITE, true));
        priceCard.addView(text("1 jam " + signedPct(c.change1h) + "%   •   24 jam " + signedPct(c.change) + "%   •   7 hari " + signedPct(c.change7d) + "%",
                12, c.change >= 0 ? GREEN : RED, true));
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
        content.addView(sectionHeader("Analisis sinyal HAM", null, null));
        int score = signalScore(c);
        content.addView(cardNote(signalLabel(c), "Skor arah: " + (score > 0 ? "+" : "") + score +
                "/100 • Kualitas setup: " + setupQuality(c) + ". Peringatan dini berbasis data pasar; bukan prediksi pasti."));
        addIndicatorPair(content, "RSI (perkiraan)", String.valueOf((int)rsi(c.spark)), "Momentum pendek", signedPct(shortMomentum(c.spark)) + "%");
        addIndicatorPair(content, "EMA 9 vs EMA 21", emaTrendLabel(c.spark), "MACD (perkiraan)", macdLabel(c.spark));
        addIndicatorPair(content, "Bollinger position", bandPosition(c.spark), "Volatilitas sampel", pct(volatility(c.spark)) + "%");
        addIndicatorPair(content, "Posisi dari high 7h", pct(distanceFromHigh(c.spark)) + "%", "Volume / market cap", pct(c.marketCap > 0 ? c.volume / c.marketCap * 100 : 0) + "%");
        addIndicatorPair(content, "Perubahan 1 jam", signedPct(c.change1h) + "%", "Perubahan 7 hari", signedPct(c.change7d) + "%");
        content.addView(cardNote("Cara membaca peringatan",
                "Breakout watch = harga mendekati puncak sampel; momentum awal = kenaikan mulai menguat; risiko pembalikan = kenaikan terlalu tajam atau RSI tinggi; tekanan turun = momentum dan tren melemah. Data historis 7 hari bukan order book atau transaksi real-time, jadi sinyal bisa terlambat."));
        Button back = actionButton("← Kembali ke pasar", PANEL2);
        back.setOnClickListener(v -> showPage("Markets"));
        content.addView(back);
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
        int score = signalScore(c);
        int color = score >= 25 ? GREEN : score <= -25 ? RED : MUTED;
        TextView tag = text(signalLabel(c) + "  •  SKOR " + (score > 0 ? "+" : "") + score,
                10, color, true);
        tag.setPadding(dp(48), dp(4), 0, dp(2));
        card.addView(tag);
        TextView metrics = text("1j " + signedPct(c.change1h) + "% | RSI " + (int)rsi(c.spark) +
                " | Momen " + signedPct(shortMomentum(c.spark)) + "% | Tren " + signedPct(c.change7d) + "%",
                10, MUTED, false);
        metrics.setPadding(dp(48), 0, 0, 0);
        card.addView(metrics);
        return card;
    }

    private String signalLabel(Coin c) {
        int score = signalScore(c);
        double r = rsi(c.spark);
        double mom = shortMomentum(c.spark);
        double volRatio = c.marketCap > 0 ? c.volume / c.marketCap : 0;
        if (score >= 65 && mom > 0 && c.change1h > 0) return "MOMENTUM AWAL KUAT";
        if (score <= -60 && mom < 0 && c.change1h < 0) return "RISIKO DUMP TINGGI";
        if (nearHigh(c.spark) && mom > 0 && c.change1h > 0) return "BREAKOUT WATCH";
        if (r <= 32 && mom > 0 && c.change1h > 0) return "POTENSI PANTULAN";
        if (r >= 72 && c.change > 5) return "RISIKO PEMBALIKAN / PUMP";
        if (volRatio > 0.10 && Math.abs(c.change) > 2) return "AKTIVITAS VOLUME TINGGI";
        if (score >= 30) return "MOMENTUM NAIK";
        if (score <= -30) return "TEKANAN JUAL";
        if (r >= 68) return "RSI TINGGI • WASPADA";
        if (r <= 32) return "RSI RENDAH • PANTAU";
        return "NETRAL • MENUNGGU KONFIRMASI";
    }

    // Approximate indicator calculations from the available 7-day price samples.
    private double sevenDayTrend(ArrayList<Double> p) {
        if (p == null || p.size() < 2 || p.get(0) == 0) return 0;
        return (p.get(p.size()-1) / p.get(0) - 1) * 100;
    }

    private double shortMomentum(ArrayList<Double> p) {
        if (p == null || p.size() < 6) return 0;
        int n = p.size();
        double old = p.get(Math.max(0, n - Math.max(4, n/5) - 1));
        double now = p.get(n-1);
        if (old == 0) return 0;
        return (now / old - 1) * 100;
    }

    private double rsi(ArrayList<Double> p) {
        if (p == null || p.size() < 3) return 50;
        int start = Math.max(1, p.size()-15);
        double gain=0, loss=0; int count=0;
        for (int i=start; i<p.size(); i++) {
            double d = p.get(i)-p.get(i-1);
            if (d > 0) gain += d; else loss -= d;
            count++;
        }
        if (count == 0) return 50;
        if (loss == 0) return gain == 0 ? 50 : 100;
        double rs = (gain/count)/(loss/count);
        return 100 - (100/(1+rs));
    }

    private int signalScore(Coin c) {
        int score = 0;
        double mom = shortMomentum(c.spark), trend = sevenDayTrend(c.spark), r = rsi(c.spark);
        double volRatio = c.marketCap > 0 ? c.volume / c.marketCap : 0;
        double distanceHigh = distanceFromHigh(c.spark);
        if (c.change1h >= 1.0) score += 16; else if (c.change1h >= 0.25) score += 8;
        else if (c.change1h <= -1.0) score -= 16; else if (c.change1h <= -0.25) score -= 8;
        if (mom >= 1.5) score += 20; else if (mom >= 0.4) score += 10;
        else if (mom <= -1.5) score -= 20; else if (mom <= -0.4) score -= 10;
        if (c.change >= 5) score += 12; else if (c.change >= 1.5) score += 6;
        else if (c.change <= -5) score -= 12; else if (c.change <= -1.5) score -= 6;
        if (c.change7d >= 10 || trend >= 8) score += 10; else if (c.change7d >= 2 || trend >= 2) score += 5;
        else if (c.change7d <= -10 || trend <= -8) score -= 10; else if (c.change7d <= -2 || trend <= -2) score -= 5;
        if (r >= 78) score -= 12; else if (r >= 55 && mom > 0) score += 7;
        else if (r <= 25 && mom < 0) score -= 5; else if (r <= 35 && mom > 0) score += 6;
        if (volRatio >= 0.15) score += mom >= 0 ? 9 : -9;
        else if (volRatio >= 0.05) score += mom >= 0 ? 4 : -4;
        if (nearHigh(c.spark) && mom > 0 && c.change1h > 0) score += 8;
        if (volatility(c.spark) > 12) score -= 5;
        if (Math.abs(c.change) >= 15) score += c.change > 0 ? -10 : 5;
        return Math.max(-100, Math.min(100, score));
    }

    private void addIndicatorPair(LinearLayout target, String a, String av, String b, String bv) {
        LinearLayout row = new LinearLayout(this);
        row.addView(statCard(a, av), new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(space(dp(7)));
        row.addView(statCard(b, bv), new LinearLayout.LayoutParams(0, -2, 1));
        target.addView(row);
    }

    private String setupQuality(Coin c) {
        int s = Math.abs(signalScore(c));
        if (s >= 70) return "kuat, tetap berisiko";
        if (s >= 45) return "menengah";
        if (s >= 25) return "awal / lemah";
        return "belum terkonfirmasi";
    }

    private double ema(ArrayList<Double> p, int period) {
        if (p == null || p.isEmpty()) return 0;
        double k = 2.0 / (period + 1.0), e = p.get(0);
        for (int i = 1; i < p.size(); i++) e = p.get(i) * k + e * (1-k);
        return e;
    }
    private String emaTrendLabel(ArrayList<Double> p) {
        if (p == null || p.size() < 22) return "Data kurang";
        double e9 = ema(new ArrayList<>(p.subList(Math.max(0,p.size()-30), p.size())), 9);
        double e21 = ema(new ArrayList<>(p.subList(Math.max(0,p.size()-60), p.size())), 21);
        return e9 > e21 ? "Naik" : e9 < e21 ? "Turun" : "Datar";
    }
    private String macdLabel(ArrayList<Double> p) {
        if (p == null || p.size() < 26) return "Data kurang";
        double fast = ema(new ArrayList<>(p.subList(Math.max(0,p.size()-26),p.size())), 12);
        double slow = ema(new ArrayList<>(p.subList(Math.max(0,p.size()-50),p.size())), 26);
        return fast > slow ? "Bullish" : fast < slow ? "Bearish" : "Netral";
    }
    private String bandPosition(ArrayList<Double> p) {
        if (p == null || p.size() < 10) return "Data kurang";
        int from = Math.max(0,p.size()-20); double mean=0;
        for(int i=from;i<p.size();i++) mean += p.get(i);
        mean /= (p.size()-from); double var=0;
        for(int i=from;i<p.size();i++) var += Math.pow(p.get(i)-mean,2);
        double sd=Math.sqrt(var/(p.size()-from));
        double lo=mean-2*sd, hi=mean+2*sd, now=p.get(p.size()-1);
        if (hi==lo) return "Tengah";
        double pos=(now-lo)/(hi-lo);
        if(pos>0.9) return "Dekat atas";
        if(pos<0.1) return "Dekat bawah";
        return "Tengah";
    }
    private double volatility(ArrayList<Double> p) {
        if (p == null || p.size() < 3) return 0;
        double sum=0; int n=0;
        int from=Math.max(1,p.size()-24);
        for(int i=from;i<p.size();i++) if(p.get(i-1)!=0) { sum += Math.abs((p.get(i)/p.get(i-1)-1)*100); n++; }
        return n==0?0:sum/n;
    }
    private boolean nearHigh(ArrayList<Double> p) { return distanceFromHigh(p) >= -3.0; }
    private double distanceFromHigh(ArrayList<Double> p) {
        if (p == null || p.size()<2) return 0;
        double high=0; for(double v:p) high=Math.max(high,v);
        return high<=0?0:(p.get(p.size()-1)/high-1)*100;
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
                URL url = new URL("https://api.coingecko.com/api/v3/coins/markets?vs_currency=usd&order=market_cap_desc&per_page=100&page=1&sparkline=true&price_change_percentage=1h%2C24h%2C7d");
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
                    c.change1h = o.optDouble("price_change_percentage_1h_in_currency", 0);
                    c.change7d = o.optDouble("price_change_percentage_7d_in_currency", 0);
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
                    status.setText("LIVE • CoinGecko • " + coins.size() + " aset • refresh otomatis tiap 60 detik");
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
    private String signedPct(double v) { return (v > 0 ? "+" : "") + pct(v); }
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
                canvas.drawText("Grafik harga 7 hari tidak tersedia", dp(8), getHeight()/2f, p);
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
