package com.ham.signal;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import android.text.Editable;
import android.text.TextWatcher;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    final int BG = 0xFF0B0E11, PANEL = 0xFF181A20;
    final int WHITE = 0xFFEAECEF, MUTED = 0xFF848E9C;
    final int GREEN = 0xFF0ECB81, RED = 0xFFF6465D;
    final int YELLOW = 0xFFF0B90B;

    LinearLayout root, content, top;
    TextView status;
    EditText search;
    final ArrayList<Coin> coins = new ArrayList<>();
    final ExecutorService executor = Executors.newSingleThreadExecutor();
    android.content.SharedPreferences prefs;
    String page = "Home";
    Coin selected;
    boolean loading = false;

    android.content.SharedPreferences nullSafePrefs() {
        return getSharedPreferences("ham_sim", MODE_PRIVATE);
    }

    static class Coin {
        String id, name, symbol;
        double price, change, cap, volume;
        int rank;
        Coin(JSONObject o) {
            id = o.optString("id");
            name = o.optString("name");
            symbol = o.optString("symbol").toUpperCase(Locale.ROOT);
            price = o.optDouble("current_price");
            change = o.optDouble("price_change_percentage_24h");
            cap = o.optDouble("market_cap");
            volume = o.optDouble("total_volume");
            rank = o.optInt("market_cap_rank", 999999);
        }
        double score() {
            double ratio = cap > 0 ? volume / cap * 100 : 0;
            return 50 + (change > 0 && change < 4 ? 15 : 0)
                + (ratio >= 10 ? 15 : ratio >= 5 ? 8 : 0)
                + (rank <= 300 ? 5 : 0);
        }
    }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("ham_sim", MODE_PRIVATE);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        buildShell();
        loadMarkets();
    }

    GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
        return t;
    }

    LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    void pad(View v, int a, int b, int c, int d) {
        v.setPadding(a,b,c,d);
    }

    void buildShell() {
        root = column();
        root.setBackgroundColor(BG);

        top = row();
        pad(top, 18, 14, 18, 10);
        TextView brand = text("HAM", 25, YELLOW, true);
        top.addView(brand);
        TextView sub = text("  CRYPTO", 16, WHITE, true);
        top.addView(sub);
        View spacer = new View(this);
        top.addView(spacer, new LinearLayout.LayoutParams(0,1,1));
        TextView refresh = text("↻", 28, WHITE, true);
        refresh.setOnClickListener(v -> loadMarkets());
        top.addView(refresh);
        root.addView(top);

        FrameLayout frame = new FrameLayout(this);
        content = column();
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(content);
        frame.addView(scroll);
        root.addView(frame, new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout nav = row();
        nav.setBackgroundColor(PANEL);
        String[] labels = {"Home", "Markets", "Scanner", "Trade", "Assets"};
        String[] icons = {"⌂", "▤", "⌕", "⇄", "◉"};
        for (int i=0;i<labels.length;i++) {
            final String dest = labels[i];
            LinearLayout item = column();
            item.setGravity(Gravity.CENTER);
            pad(item, 1, 9, 1, 8);
            TextView icon = text(icons[i], 21,
                page.equals(dest) ? YELLOW : MUTED, true);
            TextView label = text(dest, 10,
                page.equals(dest) ? YELLOW : MUTED, false);
            item.addView(icon);
            item.addView(label);
            item.setOnClickListener(v -> showPage(dest));
            nav.addView(item, new LinearLayout.LayoutParams(0,-2,1));
        }
        root.addView(nav);
        setContentView(root);
    }

    void showPage(String dest) {
        page = dest;
        buildShell();
        if (dest.equals("Home")) home();
        else if (dest.equals("Markets")) markets();
        else if (dest.equals("Scanner")) scanner();
        else if (dest.equals("Trade")) tradePage();
        else if (dest.equals("Assets")) assets();
    }

    void heading(String title, String subtitle) {
        content.addView(text(title, 24, WHITE, true));
        if (subtitle != null) {
            TextView s = text(subtitle, 12, MUTED, false);
            pad(s,0,5,0,12);
            content.addView(s);
        }
    }

    void card(View v) {
        LinearLayout wrap = column();
        wrap.setBackground(shape(PANEL, 18));
        pad(wrap, 16, 15, 16, 15);
        wrap.addView(v);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(0,6,0,10);
        content.addView(wrap,lp);
    }

    void loadMarkets() {
        if (loading) return;
        loading = true;
        if (status != null) status.setText("Memuat data pasar...");
        executor.execute(() -> {
            try {
                URL url = new URL("https://api.coingecko.com/api/v3/coins/markets"
                    + "?vs_currency=usd&order=market_cap_desc"
                    + "&per_page=100&page=1&sparkline=false"
                    + "&price_change_percentage=24h");
                HttpURLConnection c = (HttpURLConnection)url.openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(20000);
                c.setRequestProperty("User-Agent","HAM-Crypto-Signal/2.0");
                int code = c.getResponseCode();
                if (code != 200) throw new Exception("HTTP " + code);
                StringBuilder body = new StringBuilder();
                try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(c.getInputStream()))) {
                    String line;
                    while ((line=r.readLine())!=null) body.append(line);
                }
                c.disconnect();
                JSONArray arr = new JSONArray(body.toString());
                ArrayList<Coin> result = new ArrayList<>();
                for (int i=0;i<arr.length();i++)
                    result.add(new Coin(arr.getJSONObject(i)));
                runOnUiThread(() -> {
                    coins.clear();
                    coins.addAll(result);
                    loading = false;
                    if (page.equals("Home")) home();
                    else if (page.equals("Markets")) markets();
                    else if (page.equals("Scanner")) scanner();
                    else if (page.equals("Trade")) tradePage();
                    else if (page.equals("Assets")) assets();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    loading = false;
                    content.removeAllViews();
                    heading("Tidak dapat memuat pasar", "Periksa koneksi internet lalu coba lagi.");
                    TextView err = text(e.getMessage(),14,RED,false);
                    content.addView(err);
                    Button retry = new Button(this);
                    retry.setText("Coba lagi");
                    retry.setOnClickListener(v -> loadMarkets());
                    content.addView(retry);
                });
            }
        });
    }

    String money(double n) {
        if (n >= 1000) return String.format(Locale.US,"$%,.2f",n);
        if (n >= 1) return String.format(Locale.US,"$%.4f",n);
        return String.format(Locale.US,"$%.8f",n);
    }

    String compact(double n) {
        if (n >= 1e12) return String.format(Locale.US,"$%.2fT",n/1e12);
        if (n >= 1e9) return String.format(Locale.US,"$%.2fB",n/1e9);
        if (n >= 1e6) return String.format(Locale.US,"$%.2fM",n/1e6);
        if (n >= 1e3) return String.format(Locale.US,"$%.2fK",n/1e3);
        return money(n);
    }

    TextView changeText(double change) {
        return text(String.format(Locale.US,"%+.2f%%",change),
            13,change>=0?GREEN:RED,true);
    }

    Coin find(String symbol) {
        for (Coin c:coins) if (c.symbol.equalsIgnoreCase(symbol)) return c;
        return null;
    }

    void home() {
        content.removeAllViews();
        heading("Pasar kripto", "Harga publik • USD • Data dapat tertunda");
        LinearLayout balance = column();
        balance.addView(text("SALDO SIMULASI",12,MUTED,true));
        balance.addView(text("$"+String.format(Locale.US,"%.2f",
            prefs.getFloat("cash",10000f)),30,WHITE,true));
        balance.addView(text("Dana virtual — bukan uang sungguhan",12,MUTED,false));
        card(balance);

        LinearLayout stats = row();
        stats.addView(stat("KOIN DIMUAT",String.valueOf(coins.size())),new LinearLayout.LayoutParams(0,-2,1));
        stats.addView(stat("MODE","SIMULASI"),new LinearLayout.LayoutParams(0,-2,1));
        card(stats);

        content.addView(text("Koin utama",19,WHITE,true));
        for (String sym:new String[]{"BTC","ETH","BNB","SOL"}) {
            Coin c=find(sym);
            if (c!=null) coinRow(c);
        }
        TextView all=text("Lihat semua pasar  →",14,YELLOW,true);
        pad(all,0,15,0,20);
        all.setOnClickListener(v -> showPage("Markets"));
        content.addView(all);
        TextView note=text("HAM Signal adalah alat informasi. Skor scanner bukan jaminan keuntungan.",12,MUTED,false);
        pad(note,0,10,0,20);
        content.addView(note);
    }

    LinearLayout stat(String title,String value) {
        LinearLayout box=column();
        box.setBackground(shape(PANEL,14));
        pad(box,12,14,8,14);
        box.addView(text(title,10,MUTED,true));
        box.addView(text(value,17,WHITE,true));
        return box;
    }

    void markets() {
        content.removeAllViews();
        heading("Markets", "Harga dan perubahan pasar");
        search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Cari nama atau simbol koin");
        search.setHintTextColor(MUTED);
        search.setTextColor(WHITE);
        search.setBackground(shape(PANEL,12));
        pad(search,14,5,14,5);
        content.addView(search);
        LinearLayout list=column();
        content.addView(list);
        Runnable render=() -> {
            list.removeAllViews();
            String q=search.getText().toString().toLowerCase(Locale.ROOT);
            for (Coin c:coins) {
                if (!c.name.toLowerCase(Locale.ROOT).contains(q)
                    && !c.symbol.toLowerCase(Locale.ROOT).contains(q)) continue;
                addCoinRow(list,c);
            }
        };
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int st,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){render.run();}
            public void afterTextChanged(Editable e){}
        });
        render.run();
    }

    void coinRow(Coin c) { addCoinRow(content,c); }

    void addCoinRow(LinearLayout target,Coin c) {
        LinearLayout r=row();
        pad(r,8,13,8,13);
        LinearLayout name=column();
        name.addView(text(c.name,15,WHITE,true));
        name.addView(text(c.symbol+"  ·  #"+c.rank,11,MUTED,false));
        r.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout right=column();
        right.setGravity(Gravity.RIGHT);
        right.addView(text(money(c.price),14,WHITE,true));
        right.addView(changeText(c.change));
        r.addView(right);
        r.setOnClickListener(v -> detail(c));
        target.addView(r);
        View line=new View(this);
        line.setBackgroundColor(0xFF292D35);
        target.addView(line,new LinearLayout.LayoutParams(-1,1));
    }

    void scanner() {
        content.removeAllViews();
        heading("HAM Scanner", "Penyaringan awal berdasarkan volume, tren dan kapitalisasi");
        Button refresh=new Button(this);
        refresh.setText("PERBARUI DATA PASAR");
        refresh.setOnClickListener(v -> loadMarkets());
        content.addView(refresh);
        ArrayList<Coin> ranked=new ArrayList<>(coins);
        ranked.removeIf(c -> c.price<=0 || c.cap<=0 || c.volume<25000
            || c.volume/c.cap<0.02 || c.change < -5 || c.change > 5);
        ranked.sort((a,b)->Double.compare(b.score(),a.score()));
        content.addView(text("Kandidat: "+ranked.size(),14,MUTED,false));
        for (int i=0;i<Math.min(50,ranked.size());i++) {
            Coin c=ranked.get(i);
            LinearLayout box=column();
            box.setBackground(shape(PANEL,14));
            pad(box,14,12,14,12);
            box.addView(text((i+1)+". "+c.name+" ("+c.symbol+")",
                15,WHITE,true));
            LinearLayout rr=row();
            rr.addView(text("Skor "+(int)c.score()+"/85",12,YELLOW,true),
                new LinearLayout.LayoutParams(0,-2,1));
            rr.addView(changeText(c.change));
            box.addView(rr);
            box.addView(text("Harga "+money(c.price)+"  ·  Vol/MC "
                +String.format(Locale.US,"%.2f%%",c.volume/c.cap*100),
                12,MUTED,false));
            box.setOnClickListener(v -> detail(c));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
            lp.setMargins(0,6,0,6);
            content.addView(box,lp);
        }
        content.addView(text("Skor adalah filter heuristik, bukan prediksi atau rekomendasi investasi.",11,MUTED,false));
    }

    void detail(Coin c) {
        selected=c;
        page="Detail";
        buildShell();
        content.removeAllViews();
        TextView back=text("‹  Kembali",15,YELLOW,true);
        back.setOnClickListener(v -> showPage("Markets"));
        content.addView(back);
        heading(c.name+" ("+c.symbol+")","Peringkat kapitalisasi #"+c.rank);
        LinearLayout price=column();
        price.addView(text(money(c.price),30,WHITE,true));
        price.addView(changeText(c.change));
        card(price);

        content.addView(text("Grafik harga 7 hari",17,WHITE,true));
        TextView graphStatus=text("Mengambil data grafik...",12,MUTED,false);
        content.addView(graphStatus);
        PriceChart chart=new PriceChart(this);
        content.addView(chart,new LinearLayout.LayoutParams(-1,230));
        executor.execute(() -> {
            try {
                URL u=new URL("https://api.coingecko.com/api/v3/coins/"
                    +c.id+"/market_chart?vs_currency=usd&days=7");
                HttpURLConnection conn=(HttpURLConnection)u.openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(20000);
                conn.setRequestProperty("User-Agent","HAM-Crypto-Signal/2.0");
                if(conn.getResponseCode()!=200) throw new Exception("Grafik tidak tersedia");
                StringBuilder sb=new StringBuilder();
                try(BufferedReader br=new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    String line; while((line=br.readLine())!=null) sb.append(line);
                }
                conn.disconnect();
                JSONArray arr=new JSONObject(sb.toString()).getJSONArray("prices");
                ArrayList<Double> values=new ArrayList<>();
                for(int i=0;i<arr.length();i++) values.add(arr.getJSONArray(i).getDouble(1));
                runOnUiThread(() -> {chart.setValues(values);graphStatus.setText("Sumber: CoinGecko · 7 hari");});
            } catch(Exception e) {
                runOnUiThread(() -> graphStatus.setText("Grafik tidak tersedia saat ini. Harga terkini tetap ditampilkan."));
            }
        });

        LinearLayout info=column();
        info.addView(text("Kapitalisasi pasar  "+compact(c.cap),14,WHITE,false));
        info.addView(text("Volume 24 jam  "+compact(c.volume),14,WHITE,false));
        info.addView(text("Volume / kapitalisasi  "+String.format(Locale.US,"%.2f%%",c.cap>0?c.volume/c.cap*100:0),14,WHITE,false));
        card(info);

        LinearLayout buttons=row();
        Button buy=new Button(this); buy.setText("BELI SIMULASI");
        Button sell=new Button(this); sell.setText("JUAL SIMULASI");
        buy.setOnClickListener(v -> tradeDialog(c,true));
        sell.setOnClickListener(v -> tradeDialog(c,false));
        buttons.addView(buy,new LinearLayout.LayoutParams(0,-2,1));
        buttons.addView(sell,new LinearLayout.LayoutParams(0,-2,1));
        content.addView(buttons);
    }

    class PriceChart extends View {
        Paint paint=new Paint(3);
        ArrayList<Double> values=new ArrayList<>();
        PriceChart(Activity a){super(a);}
        void setValues(ArrayList<Double> v){values=v;invalidate();}
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(BG);
            if(values.size()<2) return;
            double min=Collections.min(values), max=Collections.max(values);
            if(max==min) max=min+1;
            float left=12, right=getWidth()-12, top=18, bottom=getHeight()-20;
            paint.setColor(0xFF29313B); paint.setStrokeWidth(1);
            for(int i=0;i<4;i++){
                float y=top+(bottom-top)*i/3f;
                canvas.drawLine(left,y,right,y,paint);
            }
            paint.setColor(GREEN); paint.setStrokeWidth(3);
            paint.setStyle(Paint.Style.STROKE);
            Path path=new Path();
            for(int i=0;i<values.size();i++){
                float x=left+(right-left)*i/(values.size()-1f);
                float y=bottom-(float)((values.get(i)-min)/(max-min))*(bottom-top);
                if(i==0)path.moveTo(x,y); else path.lineTo(x,y);
            }
            canvas.drawPath(path,paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    void tradePage() {
        content.removeAllViews();
        heading("Trade simulasi", "Tidak terhubung ke bursa dan tidak menggunakan uang sungguhan");
        content.addView(stat("SALDO USD VIRTUAL",
            String.format(Locale.US,"$%.2f",prefs.getFloat("cash",10000f))));
        for(Coin c:coins) {
            LinearLayout r=row();
            TextView name=text(c.symbol+"  "+money(c.price),14,WHITE,true);
            r.addView(name,new LinearLayout.LayoutParams(0,-2,1));
            Button buy=new Button(this);buy.setText("Beli");
            Button sell=new Button(this);sell.setText("Jual");
            buy.setOnClickListener(v->tradeDialog(c,true));
            sell.setOnClickListener(v->tradeDialog(c,false));
            r.addView(buy);r.addView(sell);
            content.addView(r);
        }
    }

    void tradeDialog(Coin c, boolean buy) {
        EditText amount=new EditText(this);
        amount.setInputType(8194);
        amount.setSingleLine(true);
        amount.setHint("Jumlah USD virtual");
        amount.setTextColor(WHITE);
        amount.setHintTextColor(MUTED);
        LinearLayout box=column();pad(box,18,8,18,0);box.addView(amount);
        new AlertDialog.Builder(this)
            .setTitle((buy?"Beli ":"Jual ")+c.symbol+" (simulasi)")
            .setMessage("Harga acuan: "+money(c.price)+"\nMasukkan nilai transaksi dalam USD.")
            .setView(box)
            .setNegativeButton("Batal",null)
            .setPositiveButton(buy?"Beli":"Jual",(d,w)->{
                try {
                    double usd=Double.parseDouble(amount.getText().toString().trim());
                    if(usd<=0 || c.price<=0) throw new Exception();
                    double cash=prefs.getFloat("cash",10000f);
                    String key="hold_"+c.symbol;
                    double held=Double.longBitsToDouble(prefs.getLong(key,Double.doubleToLongBits(0)));
                    if(buy) {
                        if(usd>cash) {toast("Saldo virtual tidak cukup");return;}
                        held+=usd/c.price;cash-=usd;
                    } else {
                        double qty=usd/c.price;
                        if(qty>held) {toast("Kepemilikan simulasi tidak cukup");return;}
                        held-=qty;cash+=usd;
                    }
                    prefs.edit().putFloat("cash",(float)cash)
                        .putLong(key,Double.doubleToLongBits(held)).apply();
                    toast("Transaksi simulasi berhasil dicatat");
                    if(page.equals("Assets"))assets();
                    else if(page.equals("Trade"))tradePage();
                } catch(Exception ex) {toast("Masukkan jumlah USD yang valid");}
            }).show();
    }

    void assets() {
        content.removeAllViews();
        heading("Assets", "Portofolio simulasi di perangkat ini");
        double cash=prefs.getFloat("cash",10000f);
        double total=cash;
        for(Coin c:coins) {
            double qty=Double.longBitsToDouble(prefs.getLong("hold_"+c.symbol,
                Double.doubleToLongBits(0)));
            total+=qty*c.price;
        }
        content.addView(stat("TOTAL ESTIMASI",String.format(Locale.US,"$%.2f",total)));
        content.addView(stat("SALDO USD VIRTUAL",String.format(Locale.US,"$%.2f",cash)));
        content.addView(text("Kepemilikan",18,WHITE,true));
        boolean any=false;
        for(Coin c:coins) {
            double qty=Double.longBitsToDouble(prefs.getLong("hold_"+c.symbol,
                Double.doubleToLongBits(0)));
            if(qty<=0)continue;
            any=true;
            LinearLayout r=row();
            LinearLayout left=column();
            left.addView(text(c.name+" ("+c.symbol+")",14,WHITE,true));
            left.addView(text(String.format(Locale.US,"%.8f koin",qty),12,MUTED,false));
            r.addView(left,new LinearLayout.LayoutParams(0,-2,1));
            r.addView(text(money(qty*c.price),14,WHITE,true));
            content.addView(r);
        }
        if(!any)content.addView(text("Belum ada aset. Buka Trade untuk mencoba transaksi simulasi.",13,MUTED,false));
        Button reset=new Button(this);
        reset.setText("RESET SALDO SIMULASI");
        reset.setOnClickListener(v -> new AlertDialog.Builder(this)
            .setTitle("Reset portofolio?")
            .setMessage("Saldo virtual akan kembali ke $10.000 dan semua kepemilikan simulasi dihapus.")
            .setNegativeButton("Batal",null)
            .setPositiveButton("Reset",(d,w)->{
                android.content.SharedPreferences.Editor e=prefs.edit().clear();
                e.putFloat("cash",10000f).apply();
                assets();
            }).show());
        content.addView(reset);
    }

    void toast(String s) {
        Toast.makeText(this,s,Toast.LENGTH_SHORT).show();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
