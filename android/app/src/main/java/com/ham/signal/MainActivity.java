// ============================================================
// FILE: android/build.gradle
// ============================================================
plugins {
    id "com.android.application" version "8.7.0" apply false
}

// ============================================================
// FILE: android/settings.gradle
// ============================================================
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url "https://jitpack.io" }
    }
}
rootProject.name = "HAMSignal"
include(":app")

// ============================================================
// FILE: android/app/build.gradle
// ============================================================
plugins {
    id "com.android.application"
}
android {
    namespace "com.ham.signal"
    compileSdk 35
    defaultConfig {
        applicationId "com.ham.signal"
        minSdk 23
        targetSdk 35
        versionCode 3
        versionName "2.0"
    }
    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
        }
    }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
}
dependencies {
    implementation "androidx.appcompat:appcompat:1.7.0"
    implementation "androidx.recyclerview:recyclerview:1.3.2"
    implementation "androidx.swiperefreshlayout:swiperefreshlayout:1.1.0"
    implementation "com.google.android.material:material:1.12.0"
    implementation "com.squareup.okhttp3:okhttp:4.12.0"
    implementation "com.github.PhilJay:MPAndroidChart:v3.1.0"
    implementation "androidx.lifecycle:lifecycle-viewmodel:2.8.7"
    implementation "androidx.lifecycle:lifecycle-livedata:2.8.7"
}

// ============================================================
// FILE: android/app/src/main/AndroidManifest.xml
// ============================================================
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:theme="@style/Theme.HAMSignal"
        android:usesCleartextTraffic="false">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:screenOrientation="portrait"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
        <activity android:name=".CoinDetailActivity" android:exported="false" />
        <activity android:name=".WalletActivity" android:exported="false" />
        <activity android:name=".HistoryActivity" android:exported="false" />
        <activity android:name=".SettingsActivity" android:exported="false" />
    </application>
</manifest>

// ============================================================
// FILE: android/app/src/main/res/values/strings.xml
// ============================================================
<resources>
    <string name="app_name">HAM Crypto Signal</string>
</resources>

// ============================================================
// FILE: android/app/src/main/res/values/themes.xml
// ============================================================
<resources>
    <style name="Theme.HAMSignal" parent="Theme.Material3.Dark.NoActionBar">
        <item name="colorPrimary">#16C784</item>
        <item name="colorOnPrimary">#0B0F17</item>
        <item name="colorSecondary">#4882FF</item>
        <item name="android:statusBarColor">#0B0F17</item>
        <item name="android:navigationBarColor">#0B0F17</item>
        <item name="android:windowBackground">#0B0F17</item>
    </style>
</resources>

// ============================================================
// FILE: android/app/src/main/res/values/colors.xml
// ============================================================
<resources>
    <color name="bg">#0B0F17</color>
    <color name="panel">#141B27</color>
    <color name="panel2">#1B2433</color>
    <color name="white">#EEF3FA</color>
    <color name="muted">#91A0B5</color>
    <color name="green">#16C784</color>
    <color name="red">#F6465D</color>
    <color name="blue">#4882FF</color>
    <color name="yellow">#FFC857</color>
</resources>

// ============================================================
// FILE: android/app/src/main/res/drawable/bg_input.xml
// ============================================================
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="@color/panel" />
    <corners android:radius="12dp" />
    <stroke android:width="1dp" android:color="@color/panel2" />
</shape>

// ============================================================
// FILE: android/app/src/main/res/layout/activity_main.xml
// ============================================================
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="@color/bg">
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:padding="16dp"
        android:gravity="center_vertical">
        <TextView
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="HAM Crypto Signal"
            android:textColor="@color/white"
            android:textSize="18sp"
            android:textStyle="bold" />
        <TextView
            android:id="@+id/tvLastUpdate"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="--:--"
            android:textColor="@color/muted"
            android:textSize="12sp" />
    </LinearLayout>
    <EditText
        android:id="@+id/etSearch"
        android:layout_width="match_parent"
        android:layout_height="44dp"
        android:layout_marginHorizontal="16dp"
        android:background="@drawable/bg_input"
        android:hint="Cari koin..."
        android:paddingHorizontal="16dp"
        android:textColor="@color/white"
        android:textColorHint="@color/muted"
        android:textSize="14sp"
        android:imeOptions="actionSearch"
        android:inputType="text" />
    <FrameLayout
        android:id="@+id/contentFrame"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1" />
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="56dp"
        android:orientation="horizontal"
        android:background="@color/panel"
        android:gravity="center">
        <TextView
            android:id="@+id/navHome"
            android:layout_width="0dp"
            android:layout_height="match_parent"
            android:layout_weight="1"
            android:gravity="center"
            android:text="Home"
            android:textColor="@color/green"
            android:textSize="12sp"
            android:textStyle="bold" />
        <TextView
            android:id="@+id/navMarkets"
            android:layout_width="0dp"
            android:layout_height="match_parent"
            android:layout_weight="1"
            android:gravity="center"
            android:text="Markets"
            android:textColor="@color/muted"
            android:textSize="12sp" />
        <TextView
            android:id="@+id/navScanner"
            android:layout_width="0dp"
            android:layout_height="match_parent"
            android:layout_weight="1"
            android:gravity="center"
            android:text="Scanner"
            android:textColor="@color/muted"
            android:textSize="12sp" />
        <TextView
            android:id="@+id/navWallet"
            android:layout_width="0dp"
            android:layout_height="match_parent"
            android:layout_weight="1"
            android:gravity="center"
            android:text="Wallet"
            android:textColor="@color/muted"
            android:textSize="12sp" />
        <TextView
            android:id="@+id/navSettings"
            android:layout_width="0dp"
            android:layout_height="match_parent"
            android:layout_weight="1"
            android:gravity="center"
            android:text="Settings"
            android:textColor="@color/muted"
            android:textSize="12sp" />
    </LinearLayout>
</LinearLayout>

// ============================================================
// FILE: android/app/src/main/res/layout/item_coin.xml
// ============================================================
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="64dp"
    android:orientation="horizontal"
    android:paddingHorizontal="16dp"
    android:gravity="center_vertical"
    android:background="?android:attr/selectableItemBackground">
    <LinearLayout
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:orientation="vertical">
        <TextView
            android:id="@+id/tvSymbol"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textColor="@color/white"
            android:textSize="15sp"
            android:textStyle="bold" />
        <TextView
            android:id="@+id/tvName"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textColor="@color/muted"
            android:textSize="12sp" />
    </LinearLayout>
    <LinearLayout
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:gravity="end">
        <TextView
            android:id="@+id/tvPrice"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textColor="@color/white"
            android:textSize="15sp" />
        <TextView
            android:id="@+id/tvChange"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textSize="12sp" />
    </LinearLayout>
</LinearLayout>

// ============================================================
// FILE: android/app/src/main/res/layout/activity_coin_detail.xml
// ============================================================
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="@color/bg">
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:padding="16dp"
        android:gravity="center_vertical">
        <TextView
            android:id="@+id/btnBack"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="←"
            android:textColor="@color/white"
            android:textSize="24sp"
            android:paddingEnd="16dp" />
        <TextView
            android:id="@+id/tvDetailSymbol"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:textColor="@color/white"
            android:textSize="18sp"
            android:textStyle="bold" />
        <TextView
            android:id="@+id/tvDetailPrice"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textColor="@color/white"
            android:textSize="16sp" />
    </LinearLayout>
    <LinearLayout
        android:id="@+id/intervalRow"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:paddingHorizontal="16dp"
        android:paddingBottom="8dp" />
    <com.github.mikephil.charting.charts.CandleStickChart
        android:id="@+id/candleChart"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:layout_margin="8dp" />
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="16dp"
        android:background="@color/panel">
        <TextView
            android:id="@+id/tvIndicatorTitle"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Indikator"
            android:textColor="@color/white"
            android:textSize="14sp"
            android:textStyle="bold" />
        <TextView
            android:id="@+id/tvIndicators"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:textColor="@color/muted"
            android:textSize="13sp"
            android:lineSpacingExtra="4dp"
            android:paddingTop="8dp" />
    </LinearLayout>
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:padding="16dp"
        android:gravity="center">
        <Button
            android:id="@+id/btnBuy"
            android:layout_width="0dp"
            android:layout_height="48dp"
            android:layout_weight="1"
            android:layout_marginEnd="8dp"
            android:text="Beli"
            android:backgroundTint="@color/green"
            android:textColor="@color/bg" />
        <Button
            android:id="@+id/btnSell"
            android:layout_width="0dp"
            android:layout_height="48dp"
            android:layout_weight="1"
            android:layout_marginStart="8dp"
            android:text="Jual"
            android:backgroundTint="@color/red"
            android:textColor="@color/white" />
    </LinearLayout>
</LinearLayout>

// ============================================================
// FILE: android/app/src/main/res/layout/activity_wallet.xml
// ============================================================
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/bg" />

// ============================================================
// FILE: android/app/src/main/java/com/ham/signal/ApiClient.java
// ============================================================
package com.ham.signal;

import okhttp3.*;
import org.json.*;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class ApiClient {

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    public static JSONArray getBinance24hrTickers() throws IOException, JSONException {
        String url = "https://api.binance.com/api/v3/ticker/24hr";
        Request req = new Request.Builder().url(url).build();
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) throw new IOException("Binance HTTP " + resp.code());
            return new JSONArray(resp.body().string());
        }
    }

    public static JSONArray getBinanceKlines(String symbol, String interval, int limit) throws IOException, JSONException {
        String url = "https://api.binance.com/api/v3/klines?symbol=" + symbol
                + "&interval=" + interval + "&limit=" + limit;
        Request req = new Request.Builder().url(url).build();
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) throw new IOException("Binance klines HTTP " + resp.code());
            return new JSONArray(resp.body().string());
        }
    }

    public static JSONArray getCoinGeckoMarkets(int page, int perPage) throws IOException, JSONException {
        String url = "https://api.coingecko.com/api/v3/coins/markets"
                + "?vs_currency=usd&order=market_cap_desc"
                + "&per_page=" + perPage + "&page=" + page
                + "&sparkline=false&price_change_percentage=1h,24h,7d";
        Request req = new Request.Builder().url(url).build();
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) throw new IOException("CoinGecko HTTP " + resp.code());
            return new JSONArray(resp.body().string());
        }
    }

    public static JSONObject searchDexScreener(String query) throws IOException, JSONException {
        String url = "https://api.dexscreener.com/latest/dex/search?q=" + android.net.Uri.encode(query);
        Request req = new Request.Builder().url(url).build();
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) throw new IOException("DexScreener HTTP " + resp.code());
            return new JSONObject(resp.body().string());
        }
    }
}

// ============================================================
// FILE: android/app/src/main/java/com/ham/signal/SignalScanner.java
// ============================================================
package com.ham.signal;

import org.json.JSONArray;
import org.json.JSONException;
import java.util.*;

public class SignalScanner {

    public static class SignalResult {
        public String symbol;
        public double price;
        public int score;
        public String category;
        public List<String> reasons = new ArrayList<>();
        public List<String> risks = new ArrayList<>();
    }

    public static SignalResult analyze(String symbol, JSONArray klines) throws JSONException {
        int n = klines.length();
        if (n < 30) {
            SignalResult r = new SignalResult();
            r.symbol = symbol;
            r.category = "WAIT";
            r.reasons.add("Data historis tidak cukup (minimal 30 candle)");
            return r;
        }
        double[] close = new double[n];
        double[] high = new double[n];
        double[] low = new double[n];
        double[] vol = new double[n];
        for (int i = 0; i < n; i++) {
            JSONArray k = klines.getJSONArray(i);
            high[i] = Double.parseDouble(k.getString(2));
            low[i] = Double.parseDouble(k.getString(3));
            close[i] = Double.parseDouble(k.getString(4));
            vol[i] = Double.parseDouble(k.getString(5));
        }
        double lastClose = close[n - 1];
        double rsi = calculateRSI(close, 14);
        double rsiPrev = calculateRSI(close, 14, 1);
        double ema9 = calculateEMA(close, 9);
        double ema21 = calculateEMA(close, 21);
        double ema50 = calculateEMA(close, 50);
        double ema200 = n >= 200 ? calculateEMA(close, 200) : Double.NaN;
        double[] macd = calculateMACD(close);
        double macdLine = macd[0];
        double signalLine = macd[1];
        double histogram = macdLine - signalLine;
        double[] bb = calculateBollinger(close, 20, 2);
        double bbUpper = bb[0], bbLower = bb[2];
        double atr = calculateATR(high, low, close, 14);
        double avgVol = average(vol, 20);
        double volRatio = vol[n - 1] / avgVol;
        double resistance = max(high, n - 20, n);
        double support = min(low, n - 20, n);
        int score = 0;
        List<String> reasons = new ArrayList<>();
        List<String> risks = new ArrayList<>();
        if (rsi < 30) { score += 20; reasons.add("RSI oversold (" + fmt(rsi) + ")"); }
        else if (rsi > 70) { score += 20; reasons.add("RSI overbought (" + fmt(rsi) + ")"); }
        else if (rsi > 50) { score += 8; reasons.add("RSI di atas 50 (" + fmt(rsi) + ")"); }
        if (rsi - rsiPrev > 5) { score += 8; reasons.add("RSI naik tajam"); }
        if (rsi - rsiPrev < -5) { score += 8; reasons.add("RSI turun tajam"); }
        if (!Double.isNaN(ema200)) {
            if (lastClose > ema9 && ema9 > ema21 && ema21 > ema50 && ema50 > ema200) {
                score += 20; reasons.add("EMA bull stack (9>21>50>200)");
            } else if (lastClose < ema9 && ema9 < ema21 && ema21 < ema50 && ema50 < ema200) {
                score += 20; reasons.add("EMA bear stack (9<21<50<200)");
            } else {
                score += 5; reasons.add("EMA campuran");
            }
        } else {
            if (ema9 > ema21 && ema21 > ema50) { score += 12; reasons.add("EMA pendek bull"); }
            if (ema9 < ema21 && ema21 < ema50) { score += 12; reasons.add("EMA pendek bear"); }
        }
        if (histogram > 0 && macdLine > signalLine) { score += 10; reasons.add("MACD bullish"); }
        else if (histogram < 0 && macdLine < signalLine) { score += 10; reasons.add("MACD bearish"); }
        if (lastClose <= bbLower) { score += 15; reasons.add("Harga menyentuh lower Bollinger"); }
        else if (lastClose >= bbUpper) { score += 15; reasons.add("Harga menyentuh upper Bollinger"); }
        if (volRatio > 2.0) { score += 10; reasons.add("Volume spike (" + fmt(volRatio) + "x rata-rata)"); }
        else if (volRatio < 0.5) { risks.add("Volume rendah (" + fmt(volRatio) + "x)"); }
        if (lastClose > resistance * 0.99) { score += 5; reasons.add("Mendekati resistance"); }
        if (lastClose < support * 1.01) { score += 5; reasons.add("Mendekati support"); }
        double atrPercent = atr / lastClose * 100;
        if (atrPercent > 5) risks.add("Volatilitas tinggi (ATR " + fmt(atrPercent) + "%)");
        else if (atrPercent < 1) risks.add("Volatilitas rendah, potensi breakout");
        String category;
        if (score >= 70 && (rsi < 30 || (lastClose > ema9 && ema9 > ema21))) category = "BUY SETUP";
        else if (score >= 70 && (rsi > 70 || (lastClose < ema9 && ema9 < ema21))) category = "SELL SETUP";
        else if (score >= 40) category = "WATCH";
        else category = "WAIT";
        SignalResult r = new SignalResult();
        r.symbol = symbol;
        r.price = lastClose;
        r.score = Math.min(score, 100);
        r.category = category;
        r.reasons = reasons;
        r.risks = risks;
        return r;
    }

    static double calculateRSI(double[] close, int period) { return calculateRSI(close, period, 0); }

    static double calculateRSI(double[] close, int period, int offset) {
        int n = close.length;
        int start = n - period - 1 - offset;
        if (start < 0) return 50;
        double gain = 0, loss = 0;
        for (int i = start + 1; i <= start + period; i++) {
            double diff = close[i] - close[i - 1];
            if (diff > 0) gain += diff; else loss -= diff;
        }
        if (loss == 0) return 100;
        double rs = (gain / period) / (loss / period);
        return 100 - (100 / (1 + rs));
    }

    static double calculateEMA(double[] data, int period) {
        int n = data.length;
        if (n < period) return Double.NaN;
        double k = 2.0 / (period + 1);
        double ema = data[0];
        for (int i = 1; i < n; i++) ema = data[i] * k + ema * (1 - k);
        return ema;
    }

    static double[] calculateMACD(double[] close) {
        double ema12 = calculateEMA(close, 12);
        double ema26 = calculateEMA(close, 26);
        double macdLine = ema12 - ema26;
        double signalLine = calculateEMA(close, 9);
        return new double[]{macdLine, signalLine};
    }

    static double[] calculateBollinger(double[] close, int period, double mult) {
        int n = close.length;
        if (n < period) return new double[]{Double.NaN, Double.NaN, Double.NaN};
        double sum = 0;
        for (int i = n - period; i < n; i++) sum += close[i];
        double sma = sum / period;
        double variance = 0;
        for (int i = n - period; i < n; i++) variance += Math.pow(close[i] - sma, 2);
        double sd = Math.sqrt(variance / period);
        return new double[]{sma + mult * sd, sma, sma - mult * sd};
    }

    static double calculateATR(double[] high, double[] low, double[] close, int period) {
        int n = high.length;
        if (n < period + 1) return 0;
        double sum = 0;
        for (int i = n - period; i < n; i++) {
            double tr = Math.max(high[i] - low[i],
                    Math.max(Math.abs(high[i] - close[i - 1]), Math.abs(low[i] - close[i - 1])));
            sum += tr;
        }
        return sum / period;
    }

    static double average(double[] arr, int period) {
        int n = arr.length;
        if (n < period) period = n;
        double sum = 0;
        for (int i = n - period; i < n; i++) sum += arr[i];
        return sum / period;
    }

    static double max(double[] arr, int from, int to) {
        double m = Double.MIN_VALUE;
        for (int i = from; i < to && i < arr.length; i++) m = Math.max(m, arr[i]);
        return m;
    }

    static double min(double[] arr, int from, int to) {
        double m = Double.MAX_VALUE;
        for (int i = from; i < to && i < arr.length; i++) m = Math.min(m, arr[i]);
        return m;
    }

    static String fmt(double v) { return String.format(Locale.US, "%.2f", v); }
}

// ============================================================
// FILE: android/app/src/main/java/com/ham/signal/PortfolioManager.java
// ============================================================
package com.ham.signal;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

public class PortfolioManager {

    private static final String PREFS = "ham_account";
    private final SharedPreferences prefs;

    public PortfolioManager(Context ctx) {
        prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public double getCash() {
        return Double.longBitsToDouble(prefs.getLong("cash", Double.doubleToLongBits(150_000_000_000.0)));
    }

    public void setCash(double v) {
        prefs.edit().putLong("cash", Double.doubleToLongBits(v)).apply();
    }

    public double getRealizedPnL() {
        return Double.longBitsToDouble(prefs.getLong("realizedPnL", Double.doubleToLongBits(0.0)));
    }

    private void setRealizedPnL(double v) {
        prefs.edit().putLong("realizedPnL", Double.doubleToLongBits(v)).apply();
    }

    public Map<String, Double> getHoldings() {
        Map<String, Double> map = new HashMap<>();
        try {
            JSONObject o = new JSONObject(prefs.getString("holdings", "{}"));
            Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                map.put(k, o.getDouble(k));
            }
        } catch (Exception ignored) {}
        return map;
    }

    public Map<String, Double> getCostBasis() {
        Map<String, Double> map = new HashMap<>();
        try {
            JSONObject o = new JSONObject(prefs.getString("costBasis", "{}"));
            Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                map.put(k, o.getDouble(k));
            }
        } catch (Exception ignored) {}
        return map;
    }

    public List<Tx> getHistory() {
        List<Tx> list = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs.getString("history", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                list.add(new Tx(o.getString("symbol"), o.getString("side"),
                        o.getDouble("qty"), o.getDouble("price"),
                        o.getDouble("total"), o.getLong("time")));
            }
        } catch (Exception ignored) {}
        return list;
    }

    public static class Tx {
        public String symbol, side;
        public double qty, price, total;
        public long time;
        public Tx(String s, String a, double q, double p, double t, long tm) {
            symbol = s; side = a; qty = q; price = p; total = t; time = tm;
        }
    }

    public boolean buy(String symbol, double qty, double price, double feeRate) {
        double totalCost = qty * price * (1 + feeRate);
        if (totalCost > getCash()) return false;
        Map<String, Double> holdings = getHoldings();
        Map<String, Double> costBasis = getCostBasis();
        double oldQty = holdings.getOrDefault(symbol, 0.0);
        double oldCost = costBasis.getOrDefault(symbol, 0.0);
        double newQty = oldQty + qty;
        double newCost = oldCost + qty * price;
        holdings.put(symbol, newQty);
        costBasis.put(symbol, newCost);
        setCash(getCash() - totalCost);
        saveHoldings(holdings, costBasis);
        addTx(new Tx(symbol, "BUY", qty, price, totalCost, System.currentTimeMillis()));
        return true;
    }

    public boolean sell(String symbol, double qty, double price, double feeRate) {
        Map<String, Double> holdings = getHoldings();
        double held = holdings.getOrDefault(symbol, 0.0);
        if (qty > held) return false;
        double proceeds = qty * price * (1 - feeRate);
        Map<String, Double> costBasis = getCostBasis();
        double totalCostBasis = costBasis.getOrDefault(symbol, 0.0);
        double avgCost = held > 0 ? totalCostBasis / held : 0;
        double costOfSold = avgCost * qty;
        double profit = proceeds - costOfSold;
        holdings.put(symbol, held - qty);
        if (held - qty <= 0.00000001) {
            holdings.remove(symbol);
            costBasis.remove(symbol);
        } else {
            costBasis.put(symbol, totalCostBasis - costOfSold);
        }
        setCash(getCash() + proceeds);
        saveHoldings(holdings, costBasis);
        setRealizedPnL(getRealizedPnL() + profit);
        addTx(new Tx(symbol, "SELL", qty, price, proceeds, System.currentTimeMillis()));
        return true;
    }

    private void saveHoldings(Map<String, Double> holdings, Map<String, Double> costBasis) {
        try {
            JSONObject h = new JSONObject();
            for (Map.Entry<String, Double> e : holdings.entrySet()) h.put(e.getKey(), e.getValue());
            JSONObject cb = new JSONObject();
            for (Map.Entry<String, Double> e : costBasis.entrySet()) cb.put(e.getKey(), e.getValue());
            prefs.edit()
                    .putString("holdings", h.toString())
                    .putString("costBasis", cb.toString())
                    .apply();
        } catch (Exception ignored) {}
    }

    private void addTx(Tx tx) {
        try {
            JSONArray a = new JSONArray(prefs.getString("history", "[]"));
            JSONObject o = new JSONObject();
            o.put("symbol", tx.symbol);
            o.put("side", tx.side);
            o.put("qty", tx.qty);
            o.put("price", tx.price);
            o.put("total", tx.total);
            o.put("time", tx.time);
            a.put(o);
            prefs.edit().putString("history", a.toString()).apply();
        } catch (Exception ignored) {}
    }
}

// ============================================================
// FILE: android/app/src/main/java/com/ham/signal/MainActivity.java
// ============================================================
package com.ham.signal;

import android.content.Intent;
import android.os.*;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.DecimalFormat;
import java.util.*;
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
        String symbol = "", name = "";
        double price, change24h, volume, marketCap;
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
        tvDesc.setText("Analisis multi-indikator dari candle Binance. Skor 0–100. Bukan jaminan profit.");
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
                    tvSym.setText(r.symbol + "  •  " + r.category);
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
        if (now - lastFetchTime < 30_000) return;
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
                    tvLastUpdate.setText(new java.text.SimpleDateFormat("HH:mm:ss", Locale.US)
                            .format(new Date()) + " UTC");
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
                if (c.symbol.equals(e.getKey())) { total += c.price * e.getValue(); break; }
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

// ============================================================
// FILE: android/app/src/main/java/com/ham/signal/CoinDetailActivity.java
// ============================================================
package com.ham.signal;

import android.graphics.Color;
import android.os.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.github.mikephil.charting.charts.CandleStickChart;
import com.github.mikephil.charting.data.*;
import com.github.mikephil.charting.components.XAxis;
import org.json.JSONArray;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CoinDetailActivity extends AppCompatActivity {

    private CandleStickChart chart;
    private TextView tvSymbol, tvPrice, tvIndicators;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String symbol = "BTCUSDT";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_coin_detail);
        symbol = getIntent().getStringExtra("symbol");
        if (symbol == null) symbol = "BTCUSDT";
        if (!symbol.endsWith("USDT")) symbol += "USDT";
        tvSymbol = findViewById(R.id.tvDetailSymbol);
        tvPrice = findViewById(R.id.tvDetailPrice);
        tvIndicators = findViewById(R.id.tvIndicators);
        chart = findViewById(R.id.candleChart);
        tvSymbol.setText(symbol);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        setupIntervalButtons();
        loadChart("1h");
        findViewById(R.id.btnBuy).setOnClickListener(v -> showTradeDialog("BUY"));
        findViewById(R.id.btnSell).setOnClickListener(v -> showTradeDialog("SELL"));
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void setupIntervalButtons() {
        LinearLayout row = findViewById(R.id.intervalRow);
        String[] intervals = {"1m", "5m", "15m", "1h", "4h", "1d"};
        for (String iv : intervals) {
            Button btn = new Button(this);
            btn.setText(iv);
            btn.setTextSize(11);
            btn.setTextColor(getColor(R.color.white));
            btn.setBackgroundColor(getColor(R.color.panel));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(2, 0, 2, 0);
            btn.setLayoutParams(lp);
            btn.setOnClickListener(v -> loadChart(iv));
            row.addView(btn);
        }
    }

    private void loadChart(String interval) {
        executor.execute(() -> {
            try {
                JSONArray klines = ApiClient.getBinanceKlines(symbol, interval, 100);
                List<CandleEntry> entries = new ArrayList<>();
                for (int i = 0; i < klines.length(); i++) {
                    JSONArray k = klines.getJSONArray(i);
                    float high = Float.parseFloat(k.getString(2));
                    float low = Float.parseFloat(k.getString(3));
                    float open = Float.parseFloat(k.getString(1));
                    float close = Float.parseFloat(k.getString(4));
                    entries.add(new CandleEntry(i, high, low, open, close));
                }
                handler.post(() -> {
                    if (entries.isEmpty()) { tvIndicators.setText("Tidak ada data candle."); return; }
                    CandleDataSet set = new CandleDataSet(entries, symbol);
                    set.setDecreasingColor(Color.RED);
                    set.setIncreasingColor(Color.GREEN);
                    set.setNeutralColor(Color.GRAY);
                    set.setShadowColorSameAsCandle(true);
                    set.setDrawValues(false);
                    CandleData data = new CandleData(set);
                    chart.setData(data);
                    chart.getDescription().setEnabled(false);
                    chart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
                    chart.getXAxis().setTextColor(Color.LTGRAY);
                    chart.getAxisLeft().setTextColor(Color.LTGRAY);
                    chart.getAxisRight().setEnabled(false);
                    chart.getLegend().setTextColor(Color.WHITE);
                    chart.invalidate();
                    float last = entries.get(entries.size() - 1).getClose();
                    tvPrice.setText(MainActivity.formatPrice(last));
                    try {
                        SignalScanner.SignalResult sig = SignalScanner.analyze(symbol, klines);
                        StringBuilder sb = new StringBuilder();
                        sb.append("Kategori: ").append(sig.category).append("  |  Skor: ").append(sig.score).append("/100\n");
                        sb.append("Alasan: ").append(String.join(", ", sig.reasons)).append("\n");
                        if (!sig.risks.isEmpty()) sb.append("Risiko: ").append(String.join(", ", sig.risks));
                        tvIndicators.setText(sb.toString());
                    } catch (Exception ignored) {}
                });
            } catch (Exception e) {
                handler.post(() -> tvIndicators.setText("Gagal memuat chart: " + e.getMessage()));
            }
        });
    }

    private void showTradeDialog(String side) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 16, 32, 16);
        EditText etQty = new EditText(this);
        etQty.setHint("Jumlah koin");
        etQty.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        layout.addView(etQty);
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(side + " " + symbol)
                .setView(layout)
                .setPositiveButton("Konfirmasi", (d, w) -> {
                    try {
                        double qty = Double.parseDouble(etQty.getText().toString());
                        double price = Double.parseDouble(tvPrice.getText().toString().replace("$", "").replace(",", ""));
                        PortfolioManager pm = new PortfolioManager(this);
                        boolean ok = side.equals("BUY") ? pm.buy(symbol, qty, price, 0.001) : pm.sell(symbol, qty, price, 0.001);
                        Toast.makeText(this, ok ? "Transaksi berhasil" : "Saldo/jumlah tidak mencukupi", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(this, "Input tidak valid", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Batal", null)
                .show();
    }
}

// ============================================================
// FILE: android/app/src/main/java/com/ham/signal/WalletActivity.java
// ============================================================
package com.ham.signal;

import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.*;

public class WalletActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wallet);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 32, 32, 32);
        root.setBackgroundColor(getColor(R.color.bg));
        PortfolioManager pm = new PortfolioManager(this);
        TextView tv = new TextView(this);
        tv.setTextColor(getColor(R.color.white));
        tv.setTextSize(14);
        tv.setLineSpacing(6f, 1f);
        StringBuilder sb = new StringBuilder();
        sb.append("CASH: $").append(String.format(Locale.US, "%,.2f", pm.getCash())).append("\n\n");
        sb.append("REALIZED PnL: $").append(String.format(Locale.US, "%,.2f", pm.getRealizedPnL())).append("\n\n");
        sb.append("HOLDINGS:\n");
        for (Map.Entry<String, Double> e : pm.getHoldings().entrySet()) {
            sb.append(e.getKey()).append(": ").append(e.getValue()).append("\n");
        }
        tv.setText(sb.toString());
        root.addView(tv);
        setContentView(root);
    }
}

// ============================================================
// FILE: android/app/src/main/java/com/ham/signal/HistoryActivity.java
// ============================================================
package com.ham.signal;

import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.text.SimpleDateFormat;
import java.util.*;

public class HistoryActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 32, 32, 32);
        PortfolioManager pm = new PortfolioManager(this);
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM HH:mm", Locale.US);
        for (PortfolioManager.Tx tx : pm.getHistory()) {
            TextView tv = new TextView(this);
            tv.setTextColor(getColor(tx.side.equals("BUY") ? R.color.green : R.color.red));
            tv.setText(sdf.format(new Date(tx.time)) + "  " + tx.side + "  " + tx.symbol
                    + "  qty=" + tx.qty + "  @ " + tx.price + "  total=$"
                    + String.format(Locale.US, "%,.2f", tx.total));
            tv.setTextSize(13);
            tv.setPadding(0, 8, 0, 8);
            root.addView(tv);
        }
        sv.addView(root);
        setContentView(sv);
    }
}

// ============================================================
// FILE: android/app/src/main/java/com/ham/signal/SettingsActivity.java
// ============================================================
package com.ham.signal;

import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 32, 32, 32);
        root.setBackgroundColor(getColor(R.color.bg));
        TextView tv = new TextView(this);
        tv.setText("Pengaturan");
        tv.setTextColor(getColor(R.color.white));
        tv.setTextSize(20);
        root.addView(tv);
        TextView tvInfo = new TextView(this);
        tvInfo.setText("\nInterval pembaruan data: 30 detik (throttle)\n"
                + "Batas risiko per transaksi: 0.1% fee\n"
                + "Sumber data: Binance (harga/klines), CoinGecko (market cap/rank), DexScreener (DEX)\n"
                + "Transaksi disimpan lokal. Belum terhubung ke bursa.\n");
        tvInfo.setTextColor(getColor(R.color.muted));
        tvInfo.setTextSize(13);
        root.addView(tvInfo);
        setContentView(root);
    }
}

// ============================================================
// FILE: .github/workflows/build-apk.yml
// ============================================================
name: Build HAM Crypto Signal APK
on:
  push:
    branches: [ main ]
  workflow_dispatch:
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17
      - name: Setup Android SDK
        uses: android-actions/setup-android@v3
        with:
          api-level: 35
          build-tools: 35.0.0
      - name: Grant execute permission for gradlew
        run: chmod +x android/gradlew
      - name: Build Debug APK
        run: ./gradlew assembleDebug
        working-directory: android
      - name: Upload APK
        uses: actions/upload-artifact@v4
        with:
          name: HAM-Crypto-Signal-debug
          path: android/app/build/outputs/apk/debug/app-debug.apk
