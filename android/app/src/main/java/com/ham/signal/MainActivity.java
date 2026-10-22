package com.ham.signal;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import android.text.*;
import org.json.*;
import java.net.*;
import java.io.*;
import java.text.DecimalFormat;
import java.util.*;

public class MainActivity extends Activity {
    final int BG=0xff0b0f17, PANEL=0xff141b27, PANEL2=0xff1b2433;
    final int WHITE=0xffeef3fa, MUTED=0xff91a0b5, GREEN=0xff16c784, RED=0xfff6465d, BLUE=0xff4882ff;
    LinearLayout root,body,bottom;
    TextView status;
    Handler handler=new Handler(Looper.getMainLooper());
    ArrayList<Coin> coins=new ArrayList<>();
    ArrayList<Tx> history=new ArrayList<>();
    HashMap<String,Double> holdings=new HashMap<>();
    SharedPreferences prefs;
    double cash=150000000000.0;
    String page="Home";
    boolean loading=false;

    static class Coin {
        String id,name,symbol;
        double price,change,change1h,change7d,cap,volume,high,low;
        ArrayList<Double> spark=new ArrayList<>();
    }

    static class Tx {
        String symbol,side;
        double qty,price,total;
        long time;
        Tx(String s,String a,double q,double p,double t,long tm){
            symbol=s;side=a;qty=q;price=p;total=t;time=tm;
        }
    }

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        prefs=getSharedPreferences("ham_account",MODE_PRIVATE);
        cash=Double.longBitsToDouble(prefs.getLong("cash",Double.doubleToLongBits(150000000000.0)));
        loadAccount();
        buildShell();
        fetchMarkets();
        handler.postDelayed(refresh,60000);
    }
       Runnable refresh=()->{
        if(!isFinishing())fetchMarkets();
        handler.postDelayed(refresh,60000);
    
    };

    @Override protected void onDestroy(){
        handler.removeCallbacks(refresh);
        super.onDestroy();
    }

    void saveAccount(){
        JSONObject h=new JSONObject();
        try{
            for(String k:holdings.keySet())h.put(k,holdings.get(k));
        }catch(Exception ignored){}
        JSONArray a=new JSONArray();
        for(Tx t:history){
            JSONObject o=new JSONObject();
            try{
                o.put("s",t.symbol);o.put("a",t.side);o.put("q",t.qty);
                o.put("p",t.price);o.put("v",t.total);o.put("t",t.time);
                a.put(o);
            }catch(Exception ignored){}
        }
        prefs.edit().putLong("cash",Double.doubleToLongBits(cash))
            .putString("holdings",h.toString())
            .putString("history",a.toString()).apply();
    }

    void loadAccount(){
        try{
            JSONObject h=new JSONObject(prefs.getString("holdings","{}"));
            Iterator<String> it=h.keys();
            while(it.hasNext()){
                String k=it.next();holdings.put(k,h.getDouble(k));
            }
        }catch(Exception ignored){}
        try{
            JSONArray a=new JSONArray(prefs.getString("history","[]"));
            for(int i=0;i<a.length();i++){
                JSONObject o=a.getJSONObject(i);
                history.add(new Tx(o.getString("s"),o.getString("a"),
                    o.getDouble("q"),o.getDouble("p"),o.getDouble("v"),o.getLong("t")));
            }
        }catch(Exception ignored){}
    }

    void buildShell(){
        root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(BG);
        setContentView(root);
        LinearLayout top=new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(16),dp(12),dp(16),dp(8));
        top.addView(text("HAM",25,GREEN,true));
        top.addView(text(" CRYPTO SIGNAL",14,WHITE,true));
        View spacer=new View(this);
        top.addView(spacer,new LinearLayout.LayoutParams(0,1,1));
        Button refreshButton=button("↻",PANEL2);
        refreshButton.setOnClickListener(v->fetchMarkets());
        top.addView(refreshButton);
        root.addView(top);
        status=text("Menghubungkan ke pasar...",11,MUTED,false);
        status.setPadding(dp(16),0,dp(16),dp(8));
        root.addView(status);
        FrameLayout frame=new FrameLayout(this);
        root.addView(frame,new LinearLayout.LayoutParams(-1,0,1));
        body=new LinearLayout(this);body.setOrientation(1);
        frame.addView(body,new FrameLayout.LayoutParams(-1,-1));
        bottom=new LinearLayout(this);bottom.setBackgroundColor(PANEL);
        root.addView(bottom);
        showPage("Home");
    }

    void showPage(String p){
        page=p;body.removeAllViews();
        if(p.equals("Home"))home();
        else if(p.equals("Wallet"))wallet();
        else if(p.equals("Markets"))marketPage(false);
        else marketPage(true);
        bottom.removeAllViews();
        String[] names={"Home","Markets","Scanner","Wallet"};
        String[] icons={"⌂","▥","⌕","◉"};
        for(int i=0;i<names.length;i++){
            final String n=names[i];
            LinearLayout item=column();item.setGravity(Gravity.CENTER);
            item.addView(text(icons[i],20,n.equals(page)?GREEN:MUTED,true));
            item.addView(text(n,10,n.equals(page)?GREEN:MUTED,true));
            item.setPadding(0,dp(8),0,dp(8));
            item.setOnClickListener(v->showPage(n));
            bottom.addView(item,new LinearLayout.LayoutParams(0,-2,1));
        }
    }

    void home(){
        ScrollView s=scroll();LinearLayout c=column();s.addView(c);body.addView(s);
        c.addView(title("HAM Trading Dashboard","Harga pasar dan analisis momentum"));
        c.addView(balanceCard());
        LinearLayout actions=new LinearLayout(this);
        Button buy=button("＋ Beli",GREEN),sell=button("－ Jual",PANEL2);
        buy.setOnClickListener(v->chooseTrade(true));
        sell.setOnClickListener(v->chooseTrade(false));
        actions.addView(buy,new LinearLayout.LayoutParams(0,dp(46),1));
        actions.addView(space(8));
        actions.addView(sell,new LinearLayout.LayoutParams(0,dp(46),1));
        c.addView(actions);
        c.addView(section("Sinyal teratas"));
        ArrayList<Coin> rank=new ArrayList<>(coins);
        rank.sort((a,b)->Integer.compare(Math.abs(score(b)),Math.abs(score(a))));
        for(int i=0;i<Math.min(12,rank.size());i++)c.addView(coinRow(rank.get(i),true));
        c.addView(section("Transaksi terakhir"));
        addHistory(c,5);
    }

    View balanceCard(){
        LinearLayout card=card();
        card.addView(text("TOTAL NILAI ASET",12,MUTED,true));
        double total=cash;
        for(String sym:holdings.keySet()){
            Coin coin=findSymbol(sym);
            if(coin!=null)total+=holdings.get(sym)*coin.price;
        }
        TextView val=text("$"+fmt(total),28,WHITE,true);
        val.setPadding(0,dp(8),0,dp(5));card.addView(val);
        card.addView(text("Saldo tersedia  $"+fmt(cash),14,GREEN,true));
        card.addView(text("Saldo trading virtual",10,MUTED,false));
        return card;
    }

    void wallet(){
        ScrollView s=scroll();LinearLayout c=column();s.addView(c);body.addView(s);
        c.addView(title("Wallet","Saldo, kepemilikan, dan transaksi"));
        c.addView(balanceCard());
        LinearLayout actions=new LinearLayout(this);
        Button b=button("Beli",GREEN),v=button("Jual",PANEL2);
        b.setOnClickListener(x->chooseTrade(true));
        v.setOnClickListener(x->chooseTrade(false));
        actions.addView(b,new LinearLayout.LayoutParams(0,dp(46),1));
        actions.addView(space(8));
        actions.addView(v,new LinearLayout.LayoutParams(0,dp(46),1));
        c.addView(actions);
        c.addView(section("Aset yang dimiliki"));
        boolean any=false;
        for(String sym:new ArrayList<>(holdings.keySet())){
            double qty=holdings.get(sym);
            if(qty<=0)continue;
            any=true;
            Coin coin=findSymbol(sym);
            double p=coin==null?0:coin.price;
            c.addView(note(sym.toUpperCase()+" • "+fmt(qty),
                "Harga: $"+fmt(p)+" | Nilai: $"+fmt(qty*p)));
        }
        if(!any)c.addView(note("Belum ada aset","Pilih Beli untuk memulai."));
        c.addView(section("Riwayat transaksi"));
        addHistory(c,100);
    }

    void chooseTrade(boolean isBuy){
        if(coins.isEmpty()){toast("Tunggu harga pasar dimuat.");return;}
        final EditText amount=new EditText(this);
        amount.setSingleLine(true);amount.setInputType(8194);
        amount.setHint(isBuy?"Nominal USD untuk membeli":"Jumlah koin untuk dijual");
        amount.setTextColor(WHITE);amount.setHintTextColor(MUTED);
        Spinner spinner=new Spinner(this);
        ArrayList<String> names=new ArrayList<>();
        for(Coin coin:coins)names.add(coin.symbol.toUpperCase()+" — "+coin.name);
        spinner.setAdapter(new ArrayAdapter<String>(this,
            android.R.layout.simple_spinner_dropdown_item,names));
        LinearLayout box=column();box.setPadding(dp(12),dp(8),dp(12),dp(8));
        box.addView(text(isBuy?"Beli dengan saldo USD":"Jual aset yang dimiliki",13,WHITE,true));
        box.addView(spinner);box.addView(amount);
        new AlertDialog.Builder(this).setTitle(isBuy?"Beli aset":"Jual aset")
            .setView(box).setNegativeButton("Batal",null)
            .setPositiveButton("Konfirmasi",(d,w)->{
                try{
                    double n=Double.parseDouble(amount.getText().toString().trim().replace(",","."));
                    Coin coin=coins.get(spinner.getSelectedItemPosition());
                    if(n<=0||coin.price<=0)throw new Exception();
                    if(isBuy){
                        if(n>cash){toast("Saldo tidak cukup.");return;}
                        double qty=n/coin.price;cash-=n;
                        holdings.put(coin.symbol,holdings.getOrDefault(coin.symbol,0.0)+qty);
                        history.add(0,new Tx(coin.symbol,"BELI",qty,coin.price,n,System.currentTimeMillis()));
                    }else{
                        double owned=holdings.getOrDefault(coin.symbol,0.0);
                        if(n>owned){toast("Jumlah koin tidak cukup.");return;}
                        double value=n*coin.price;cash+=value;
                        holdings.put(coin.symbol,owned-n);
                        history.add(0,new Tx(coin.symbol,"JUAL",n,coin.price,value,System.currentTimeMillis()));
                    }
                    saveAccount();showPage(page);toast("Transaksi berhasil dicatat.");
                }catch(Exception e){toast("Masukkan angka yang valid.");}
            }).show();
    }

    void marketPage(boolean scanner){
        ScrollView s=scroll();LinearLayout c=column();s.addView(c);body.addView(s);
        c.addView(title(scanner?"Coin Scanner":"Pasar Kripto",
            scanner?"Peringkat momentum dan risiko":"Harga dan perubahan pasar"));
        EditText search=new EditText(this);
        search.setSingleLine(true);search.setTextColor(WHITE);
        search.setHintTextColor(MUTED);search.setHint("Cari nama atau simbol koin");
        search.setBackground(bg(PANEL2,10));
        c.addView(search,new LinearLayout.LayoutParams(-1,dp(46)));
        LinearLayout list=column();c.addView(list);
        Runnable update=()->{
            list.removeAllViews();
            ArrayList<Coin> found=new ArrayList<>();
            String q=search.getText().toString().toLowerCase(Locale.US);
            for(Coin coin:coins){
                if(coin.name.toLowerCase(Locale.US).contains(q)||
                   coin.symbol.toLowerCase(Locale.US).contains(q))found.add(coin);
            }
            if(scanner)found.sort((a,b)->Integer.compare(Math.abs(score(b)),Math.abs(score(a))));
            for(Coin coin:found)list.addView(coinRow(coin,scanner));
        };
        search.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int st,int count,int after){}
            public void onTextChanged(CharSequence s,int st,int before,int count){update.run();}
            public void afterTextChanged(Editable e){}
        });
        update.run();
    }

    View coinRow(Coin coin,boolean scanner){
        LinearLayout row=card();
        LinearLayout line=new LinearLayout(this);
        line.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout left=column();
        left.addView(text(coin.symbol.toUpperCase()+" • "+coin.name,14,WHITE,true));
        left.addView(text("$"+price(coin.price),12,MUTED,false));
        line.addView(left,new LinearLayout.LayoutParams(0,-2,1));
        line.addView(text((coin.change>=0?"+":"")+fmt(coin.change)+"%",
            13,coin.change>=0?GREEN:RED,true));
        row.addView(line);
        if(scanner)row.addView(text(signal(coin)+" • skor "+score(coin),
            11,score(coin)>=0?GREEN:RED,true));
        row.setOnClickListener(v->detail(coin));
        return row;
    }

    void detail(Coin coin){
        LinearLayout box=column();box.setPadding(dp(18),dp(12),dp(18),dp(12));
        box.addView(text(coin.name+" ("+coin.symbol.toUpperCase()+")",20,WHITE,true));
        box.addView(text("$"+price(coin.price),27,WHITE,true));
        box.addView(text("24 jam: "+fmt(coin.change)+"%",
            13,coin.change>=0?GREEN:RED,true));
        box.addView(new SparkChart(coin.spark),new LinearLayout.LayoutParams(-1,dp(150)));
        box.addView(note("Sinyal HAM",signal(coin)+" • skor "+score(coin)));
        box.addView(note("Market cap","$"+compact(coin.cap)));
        box.addView(note("Volume 24 jam","$"+compact(coin.volume)));
        new AlertDialog.Builder(this).setView(box)
            .setPositiveButton("Beli",(d,w)->tradeOne(coin,true))
            .setNeutralButton("Jual",(d,w)->tradeOne(coin,false))
            .setNegativeButton("Tutup",null).show();
    }

    void tradeOne(Coin coin,boolean buy){
        EditText input=new EditText(this);
        input.setInputType(8194);input.setHint(buy?"Nominal USD":"Jumlah koin");
        new AlertDialog.Builder(this).setTitle((buy?"Beli ":"Jual ")+coin.symbol.toUpperCase())
            .setView(input).setNegativeButton("Batal",null)
            .setPositiveButton("Konfirmasi",(d,w)->{
                try{
                    double n=Double.parseDouble(input.getText().toString().trim().replace(",","."));
                    if(n<=0||coin.price<=0)throw new Exception();
                    double owned=holdings.getOrDefault(coin.symbol,0.0);
                    double qty=buy?n/coin.price:n;
                    double value=qty*coin.price;
                    if(buy){
                        if(value>cash){toast("Saldo tidak cukup.");return;}
                        cash-=value;holdings.put(coin.symbol,owned+qty);
                    }else{
                        if(qty>owned){toast("Koin tidak cukup.");return;}
                        cash+=value;holdings.put(coin.symbol,owned-qty);
                    }
                    history.add(0,new Tx(coin.symbol,buy?"BELI":"JUAL",qty,coin.price,value,System.currentTimeMillis()));
                    saveAccount();showPage(page);toast("Transaksi dicatat.");
                }catch(Exception e){toast("Masukkan angka yang valid.");}
            }).show();
    }

    void addHistory(LinearLayout target,int limit){
        if(history.isEmpty()){
            target.addView(note("Belum ada transaksi","Transaksi beli dan jual akan muncul di sini."));
            return;
        }
        for(int i=0;i<Math.min(limit,history.size());i++){
            Tx t=history.get(i);
            String date=new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.getDefault()).format(new Date(t.time));
            target.addView(note(t.side+" "+t.symbol.toUpperCase(),
                fmt(t.qty)+" koin × $"+price(t.price)+" = $"+fmt(t.total)+"\n"+date));
        }
    }

    void fetchMarkets(){
        if(loading)return;
        loading=true;status.setText("Memuat harga pasar...");
        new Thread(()->{
            ArrayList<Coin> result=new ArrayList<>();String error=null;
            HttpURLConnection conn=null;
            try{
                URL url=new URL("https://api.coingecko.com/api/v3/coins/markets?vs_currency=usd&order=market_cap_desc&per_page=100&page=1&sparkline=true&price_change_percentage=1h%2C24h%2C7d");
                conn=(HttpURLConnection)url.openConnection();
                conn.setConnectTimeout(12000);conn.setReadTimeout(15000);
                conn.setRequestProperty("User-Agent","HAM-Crypto-Signal/1.0");
                int code=conn.getResponseCode();
                if(code!=200)throw new Exception("HTTP "+code);
                BufferedReader br=new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb=new StringBuilder();String line;
                while((line=br.readLine())!=null)sb.append(line);
                br.close();
                JSONArray arr=new JSONArray(sb.toString());
                for(int i=0;i<arr.length();i++){
                    JSONObject o=arr.getJSONObject(i);Coin c=new Coin();
                    c.id=o.optString("id");c.name=o.optString("name");
                    c.symbol=o.optString("symbol","?");
                    c.price=o.optDouble("current_price");
                    c.change=o.optDouble("price_change_percentage_24h");
                    c.change1h=o.optDouble("price_change_percentage_1h_in_currency");
                    c.change7d=o.optDouble("price_change_percentage_7d_in_currency");
                    c.cap=o.optDouble("market_cap");c.volume=o.optDouble("total_volume");
                    c.high=o.optDouble("high_24h",c.price);c.low=o.optDouble("low_24h",c.price);
                    JSONObject sp=o.optJSONObject("sparkline_in_7d");
                    JSONArray pts=sp==null?null:sp.optJSONArray("price");
                    if(pts!=null){
                        int step=Math.max(1,pts.length()/60);
                        for(int j=0;j<pts.length();j+=step)c.spark.add(pts.optDouble(j,c.price));
                    }
                    result.add(c);
                }
            }catch(Exception e){error=e.getMessage();}
            finally{if(conn!=null)conn.disconnect();}
            final String err=error;
            handler.post(()->{
                loading=false;
                if(!result.isEmpty()){
                    coins.clear();coins.addAll(result);
                    status.setText("LIVE • CoinGecko • "+coins.size()+" aset • refresh 60 detik");
                    showPage(page);
                }else{
                    status.setText("Gagal memuat harga • "+err);
                    toast("Periksa internet lalu tekan ↻.");
                }
            });
        }).start();
    }

    Coin findSymbol(String symbol){
        for(Coin c:coins)if(c.symbol.equalsIgnoreCase(symbol))return c;
        return null;
    }

    int score(Coin c){
        int s=0;
        if(c.change1h>1)s+=20;else if(c.change1h>0)s+=8;
        if(c.change1h< -1)s-=20;else if(c.change1h<0)s-=8;
        if(c.change>5)s+=15;else if(c.change>1)s+=7;
        if(c.change< -5)s-=15;else if(c.change< -1)s-=7;
        if(c.change7d>8)s+=12;else if(c.change7d< -8)s-=12;
        double r=rsi(c.spark);
        if(r>75)s-=10;
        if(r<35&&c.change>0)s+=8;
        if(c.cap>0&&c.volume/c.cap>.1)s+=c.change>=0?8:-8;
        return Math.max(-100,Math.min(100,s));
    }

    String signal(Coin c){
        int s=score(c);
        if(s>=50)return "MOMENTUM KUAT";
        if(s>=25)return "POTENSI BREAKOUT";
        if(s<=-50)return "RISIKO DUMP TINGGI";
        if(s<=-25)return "TEKANAN JUAL";
        if(rsi(c.spark)<35)return "RSI RENDAH • PANTAU";
        if(rsi(c.spark)>70)return "RSI TINGGI • WASPADA";
        return "MENUNGGU KONFIRMASI";
    }

    double rsi(ArrayList<Double> p){
        if(p.size()<3)return 50;
        double gain=0,loss=0;int n=0;
        for(int i=Math.max(1,p.size()-15);i<p.size();i++){
            double d=p.get(i)-p.get(i-1);
            if(d>0)gain+=d;else loss-=d;n++;
        }
        if(n==0)return 50;
        if(loss==0)return gain==0?50:100;
        double rs=(gain/n)/(loss/n);
        return 100-100/(1+rs);
    }

    View title(String title,String sub){
        LinearLayout l=column();l.setPadding(0,dp(8),0,dp(14));
        l.addView(text(title,23,WHITE,true));
        l.addView(text(sub,12,MUTED,false));return l;
    }

    View section(String title){
        TextView t=text(title,16,WHITE,true);
        t.setPadding(0,dp(18),0,dp(9));return t;
    }

    LinearLayout card(){
        LinearLayout l=column();l.setPadding(dp(14),dp(13),dp(14),dp(13));
        l.setBackground(bg(PANEL,13));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin=dp(8);l.setLayoutParams(lp);return l;
    }

    View note(String title,String sub){
        LinearLayout l=card();l.addView(text(title,13,WHITE,true));
        TextView t=text(sub,11,MUTED,false);t.setPadding(0,dp(5),0,0);
        l.addView(t);return l;
    }

    LinearLayout column(){
        LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;
    }

    ScrollView scroll(){
        ScrollView s=new ScrollView(this);s.setClipToPadding(false);
        s.setPadding(dp(15),dp(4),dp(15),dp(16));return s;
    }

    TextView text(String value,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);
        t.setTextColor(color);if(bold)t.setTypeface(null,1);return t;
    }

    Button button(String label,int color){
        Button b=new Button(this);b.setText(label);b.setTextSize(12);b.setAllCaps(false);
        b.setTextColor(color==GREEN?0xff071915:WHITE);
        b.setBackground(bg(color,10));return b;
    }

    View space(int width){
        View v=new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(width),1));return v;
    }

    GradientDrawable bg(int color,int radius){
        GradientDrawable d=new GradientDrawable();
        d.setColor(color);d.setCornerRadius(dp(radius));return d;
    }

    int dp(float n){
        return (int)(getResources().getDisplayMetrics().density*n+0.5f);
    }

    String fmt(double n){
        if(Double.isNaN(n)||Double.isInfinite(n))return "0";
        return new DecimalFormat("#,##0.00").format(n);
    }

    String price(double n){
        if(n>=1000)return new DecimalFormat("#,##0.00").format(n);
        if(n>=1)return new DecimalFormat("0.0000").format(n);
        if(n>=0.01)return new DecimalFormat("0.00000").format(n);
        return new DecimalFormat("0.########").format(n);
    }

    String compact(double n){
        if(n>=1e12)return fmt(n/1e12)+"T";
        if(n>=1e9)return fmt(n/1e9)+"B";
        if(n>=1e6)return fmt(n/1e6)+"M";
        if(n>=1e3)return fmt(n/1e3)+"K";
        return fmt(n);
    }

    void toast(String message){
        Toast.makeText(this,message,Toast.LENGTH_SHORT).show();
    }

    class SparkChart extends View{
        ArrayList<Double> points;Paint paint=new Paint(3);
        SparkChart(ArrayList<Double> data){
            super(MainActivity.this);points=new ArrayList<>(data);
            setMinimumHeight(dp(150));
        }
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            if(points.size()<2){
                paint.setColor(MUTED);paint.setTextSize(dp(12));
                canvas.drawText("Grafik belum tersedia",dp(8),getHeight()/2f,paint);return;
            }
            double min=Collections.min(points),max=Collections.max(points);
            if(max<=min)max=min+1;
            float pad=dp(8),w=getWidth()-2*pad,h=getHeight()-2*pad;
            Path path=new Path();
            for(int i=0;i<points.size();i++){
                float x=pad+w*i/(points.size()-1);
                float y=pad+h-(float)((points.get(i)-min)/(max-min))*h;
                if(i==0)path.moveTo(x,y);else path.lineTo(x,y);
            }
            paint.setColor(points.get(points.size()-1)>=points.get(0)?GREEN:RED);
            paint.setStrokeWidth(dp(2));paint.setStyle(Paint.Style.STROKE);
            canvas.drawPath(path,paint);paint.setStyle(Paint.Style.FILL);
        }
    }
}
