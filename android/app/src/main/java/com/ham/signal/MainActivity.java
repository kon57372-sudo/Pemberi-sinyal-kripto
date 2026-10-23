package com.ham.signal;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private FrameLayout contentFrame;
    private TextView tvLastUpdate, navHome, navMarkets, navScanner, navWallet, navSettings;
    private EditText etSearch;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final PortfolioManager portfolio = new PortfolioManager(this);
    private List<Coin> allCoins = new ArrayList<>();
    private List<Coin> filteredCoins = new ArrayList<>();
    private CoinAdapter adapter;
    private String currentPage = "Home";
    private long lastFetchTime = 0;

    static class Coin {
        String symbol = "";
        String name = "";
        double price;
        double change24h;
        double volume;
        double marketCap;
        int rank;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        contentFrame = findViewById(R.id.contentFrame);
        tvLastUpdate = findViewById(R.id.tvLastUpdate);
        etSearch = findViewById(R.id.etSearch);
        navHome = findViewById(R.id.navHome);
        navMarkets = findViewById(R.id.navMarkets);
        navScanner = findViewById(R.id.navScanner);
        navWallet = findViewById(R.id.navWallet);
        navSettings = findViewById(R.id.navSettings);
        navHome.setOnClickListener(v -> showPage("Home"));
        navMarkets.setOnClickListener(v -> showPage("Markets"));
        navScanner.setOnClickListener(v -> showPage("Scanner"));
        navWallet.setOnClickListener(v -> startActivity(new Intent(this, WalletActivity.class)));
        navSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filterCoins(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });
        showPage("Home");
        fetchMarkets();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void showPage(String page) {
        currentPage = page;
        setNavSelected(page);
        if (page.equals("Home")) buildHome();
        else if (page.equals("Markets")) buildMarkets();
        else if (page.equals("Scanner")) buildScanner();
    }

    private void setNavSelected(String page) {
        int active = getColor(R.color.green);
        int inactive = getColor(R.color.muted);
        navHome.setTextColor(page.equals("Home") ? active : inactive);
        navMarkets.setTextColor(page.equals("Markets") ? active : inactive);
        navScanner.setTextColor(page.equals("Scanner") ? active : inactive);
        navWallet.setTextColor(inactive);
        navSettings.setTextColor(inactive);
    }

    private void buildHome() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(8), dp(16), dp(8));
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(getDrawable(R.drawable.bg_input));
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        TextView tvLabel = new TextView(this);
        tvLabel.setText("Total Estimasi Aset");
        tvLabel.setTextColor(getColor(R.color.muted));
        tvLabel.setTextSize(13);
        card.addView(tvLabel);
        TextView tvTotal = new TextView(this);
        tvTotal.setTextColor(getColor(R.color.white));
        tvTotal.setTextSize(28);
        tvTotal.setTypeface(null, android.graphics.Typeface.BOLD);
        double cash = portfolio.getCash();
        double holdingsValue = estimateHoldingsValue();
        tvTotal.setText(formatCurrency(cash + holdingsValue));
        card.addView(tvTotal);
        TextView tvPnl = new TextView(this);
        tvPnl.setTextColor(getColor(R.color.green));
        tvPnl.setTextSize(13);
        tvPnl.setText("Realized PnL: " + formatCurrency(portfolio.getRealizedPnL()));
        card.addView(tvPnl);
        root.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView tvWatch = new TextView(this);
        tvWatch.setText("Watchlist");
        tvWatch.setTextColor(getColor(R.color.white));
        tvWatch.setTextSize(16);
        tvWatch.setTypeface(null, android.graphics.Typeface.BOLD);
        tvWatch.setPadding(0, dp(16), 0, dp(8));
        root.addView(tvWatch);
        RecyclerView rv = new RecyclerView(this);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CoinAdapter(filteredCoins, coin -> {
            Intent i = new Intent(MainActivity.this, CoinDetailActivity.class);
            i.putExtra("symbol", coin.symbol);
            startActivity(i);
        });
        rv.setAdapter(adapter);
        root.addView(rv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        contentFrame.removeAllViews();
        contentFrame.addView(root);
    }

    private void buildMarkets() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        RecyclerView rv = new RecyclerView(this);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CoinAdapter(filteredCoins, coin -> {
            Intent i = new Intent(MainActivity.this, CoinDetailActivity.class);
            i.putExtra("symbol", coin.symbol);
            startActivity(i);
        });
        rv.setAdapter(adapter);
        root.addView(rv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        contentFrame.removeAllViews();
        contentFrame.addView(root);
    }

    private void buildScanner() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(8), dp(16), dp(8));
        TextView tvTitle = new TextView(this);
        tvTitle.setText("Signal Scanner");
        tvTitle.setTextColor(getColor(R.color.white));
        tvTitle.setTextSize(18);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(tvTitle);
        TextView tvDesc = new TextView(this);
        tvDesc.setText("Analisis multi-indikator dari candle Binance. Skor 0-100. Bukan jaminan profit.");
        tvDesc.setTextColor(getColor(R.color.muted));
        tvDesc.setTextSize(12);
        tvDesc.setPadding(0, dp(4), 0, dp(12));
        root.addView(tvDesc);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);
        contentFrame.removeAllViews();
        contentFrame.addView(root);
        executor.execute(() -> {
            List<SignalScanner.SignalResult> results = new ArrayList<>();
            try {
                JSONArray markets = ApiClient.getCoinGeckoMarkets(1, 10);
                for (int i = 0; i < markets.length(); i++) {
                    JSONObject m = markets.getJSONObject(i);
                    String symbol = m.getString("symbol").toUpperCase() + "USDT";
                    try {
                        JSONArray klines = ApiClient.getBinanceKlines(symbol, "1h", 100);
                        if (klines.length() > 0) results.add(SignalScanner.analyze(symbol, klines));
                    } catch (Exception ignored) {}
                }
            } catch (Exception e) {
                handler.post(() -> {
                    TextView err = new TextView(this);
                    err.setText("Gagal memuat scanner: " + e.getMessage());
                    err.setTextColor(getColor(R.color.red));
                    list.addView(err);
                });
                return;
            }
            handler.post(() -> {
                for (SignalScanner.SignalResult r : results) {
                    LinearLayout row = new LinearLayout(this);
                    row.setOrientation(LinearLayout.VERTICAL);
                    row.setBackground(getDrawable(R.drawable.bg_input));
                    row.setPadding(dp(12), dp(10), dp(12), dp(10));
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    lp.bottomMargin = dp(8);
                    row.setLayoutParams(lp);
                    TextView tvSym = new TextView(this);
                    tvSym.setText(r.symbol + "  -  " + r.category);
                    tvSym.setTextColor(getColor(r.category.equals("BUY SETUP") ? R.color.green :
                            r.category.equals("SELL SETUP") ? R.color.red : R.color.yellow));
                    tvSym.setTextSize(15);
                    tvSym.setTypeface(null, android.graphics.Typeface.BOLD);
                    row.addView(tvSym);
                    TextView tvScore = new TextView(this);
                    tvScore.setText("Skor: " + r.score + "/100  |  Harga: " + formatPrice(r.price));
                    tvScore.setTextColor(getColor(R.color.white));
                    tvScore.setTextSize(13);
                    row.addView(tvScore);
                    TextView tvReason = new TextView(this);
                    tvReason.setText("Alasan: " + String.join(", ", r.reasons));
                    tvReason.setTextColor(getColor(R.color.muted));
                    tvReason.setTextSize(12);
                    row.addView(tvReason);
                    if (!r.risks.isEmpty()) {
                        TextView tvRisk = new TextView(this);
                        tvRisk.setText("Risiko: " + String.join(", ", r.risks));
                        tvRisk.setTextColor(getColor(R.color.red));
                        tvRisk.setTextSize(12);
                        row.addView(tvRisk);
                    }
                    list.addView(row);
                }
            });
        });
    }

    private void fetchMarkets() {
        long now = System.currentTimeMillis();
        if (now - lastFetchTime < 30000) return;
        lastFetchTime = now;
        executor.execute(() -> {
            try {
                JSONArray markets = ApiClient.getCoinGeckoMarkets(1, 100);
                JSONArray tickers = ApiClient.getBinance24hrTickers();
                Map<String, JSONObject> binanceMap = new HashMap<>();
                for (int i = 0; i < tickers.length(); i++) {
                    JSONObject t = tickers.getJSONObject(i);
                    binanceMap.put(t.getString("symbol"), t);
                }
                List<Coin> list = new ArrayList<>();
                for (int i = 0; i < markets.length(); i++) {
                    JSONObject m = markets.getJSONObject(i);
                    Coin c = new Coin();
                    c.name = m.getString("name");
                    c.symbol = m.getString("symbol").toUpperCase();
                    c.rank = m.optInt("market_cap_rank", 999);
                    c.marketCap = m.optDouble("market_cap", 0);
                    c.volume = m.optDouble("total_volume", 0);
                    String pair = c.symbol + "USDT";
                    if (binanceMap.containsKey(pair)) {
                        JSONObject t = binanceMap.get(pair);
                        c.price = Double.parseDouble(t.getString("lastPrice"));
                        c.change24h = Double.parseDouble(t.getString("priceChangePercent"));
                    } else {
                        c.price = m.optDouble("current_price", 0);
                        c.change24h = m.optDouble("price_change_percentage_24h", 0);
                    }
                    list.add(c);
                }
                allCoins = list;
                filteredCoins.clear();
                filteredCoins.addAll(list);
                handler.post(() -> {
                    if (adapter != null) adapter.notifyDataSetChanged();
                    tvLastUpdate.setText(new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date()) + " UTC");
                    if (currentPage.equals("Home")) buildHome();
                    else if (currentPage.equals("Markets")) buildMarkets();
                });
            } catch (Exception e) {
                handler.post(() -> tvLastUpdate.setText("Gagal: " + e.getMessage()));
            }
        });
    }

    private void filterCoins(String q) {
        filteredCoins.clear();
        if (q.isEmpty()) filteredCoins.addAll(allCoins);
        else {
            String query = q.toUpperCase();
            for (Coin c : allCoins) {
                if (c.symbol.contains(query) || c.name.toUpperCase().contains(query)) filteredCoins.add(c);
            }
        }
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private double estimateHoldingsValue() {
        Map<String, Double> holdings = portfolio.getHoldings();
        double total = 0;
        for (Map.Entry<String, Double> e : holdings.entrySet()) {
            for (Coin c : allCoins) {
                if (c.symbol.equals(e.getKey())) {
                    total += c.price * e.getValue();
                    break;
                }
            }
        }
        return total;
    }

    static class CoinAdapter extends RecyclerView.Adapter<CoinAdapter.VH> {
        private final List<Coin> data;
        private final OnCoinClick listener;
        interface OnCoinClick { void onClick(Coin coin); }
        CoinAdapter(List<Coin> data, OnCoinClick l) { this.data = data; this.listener = l; }
        @Override public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_coin, parent, false);
            return new VH(v);
        }
        @Override public void onBindViewHolder(VH h, int pos) {
            Coin c = data.get(pos);
            h.tvSymbol.setText(c.symbol + "/USDT");
            h.tvName.setText(c.name);
            h.tvPrice.setText(formatPrice(c.price));
            h.tvChange.setText(String.format(Locale.US, "%+.2f%%", c.change24h));
            h.tvChange.setTextColor(h.itemView.getContext().getColor(c.change24h >= 0 ? R.color.green : R.color.red));
            h.itemView.setOnClickListener(v -> listener.onClick(c));
        }
        @Override public int getItemCount() { return data.size(); }
        static class VH extends RecyclerView.ViewHolder {
            TextView tvSymbol, tvName, tvPrice, tvChange;
            VH(View v) {
                super(v);
                tvSymbol = v.findViewById(R.id.tvSymbol);
                tvName = v.findViewById(R.id.tvName);
                tvPrice = v.findViewById(R.id.tvPrice);
                tvChange = v.findViewById(R.id.tvChange);
            }
        }
    }

    private int dp(int dp) { return (int) (dp * getResources().getDisplayMetrics().density); }

    static String formatPrice(double p) {
        if (p >= 1000) return String.format(Locale.US, "%,.2f", p);
        if (p >= 1) return String.format(Locale.US, "%.2f", p);
        return String.format(Locale.US, "%.6f", p);
    }

    static String formatCurrency(double v) {
        DecimalFormat df = new DecimalFormat("#,###.00");
        return "$" + df.format(v);
    }
}
