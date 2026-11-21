package pro.enriquez.vida;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    // Primero la dirección de Tailscale (funciona dentro y fuera de casa); si falla, la de la red local.
    private static final String[] URLS = {
        "http://100.82.42.25/local/vida/index.html",
        "http://192.168.1.20/local/vida/index.html"
    };
    private static final int MAX_TRIES = 14;

    private WebView web;
    private LinearLayout overlay;
    private TextView msg;
    private Button retry, openTs;
    private final Handler h = new Handler(Looper.getMainLooper());
    private int tries = 0;
    private boolean failed = false;
    private boolean weConnected = false;   // el VPN lo encendió esta app
    private boolean openingTailscale = false;
    private boolean started = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#06121F"));

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#06121F"));
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setMediaPlaybackRequiresUserGesture(false);
        web.setLongClickable(false);
        web.setOnLongClickListener(v -> true);
        web.setHapticFeedbackEnabled(false);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame()) failedLoad();
            }
            @Override
            public void onPageFinished(WebView v, String url) {
                if (!failed) { overlay.setVisibility(View.GONE); }
            }
        });
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));

        overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setGravity(Gravity.CENTER);
        overlay.setBackgroundColor(Color.parseColor("#06121F"));
        TextView t = new TextView(this);
        t.setText("Vida");
        t.setTextColor(Color.WHITE);
        t.setTextSize(34);
        t.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        overlay.addView(t);
        msg = new TextView(this);
        msg.setTextColor(Color.parseColor("#9FB3C8"));
        msg.setTextSize(15);
        msg.setGravity(Gravity.CENTER);
        msg.setPadding(48, 24, 48, 24);
        overlay.addView(msg);
        retry = new Button(this);
        retry.setText("Reintentar");
        retry.setVisibility(View.GONE);
        retry.setOnClickListener(v -> start());
        overlay.addView(retry);
        openTs = new Button(this);
        openTs.setText("Abrir Tailscale");
        openTs.setVisibility(View.GONE);
        openTs.setOnClickListener(v -> {
            openingTailscale = true;
            Intent i = getPackageManager().getLaunchIntentForPackage("com.tailscale.ipn");
            if (i != null) startActivity(i);
        });
        overlay.addView(openTs);
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));

        setContentView(root);
        immersive();
        started = true;
        start();
    }

    private boolean vpnActive() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            for (Network n : cm.getAllNetworks()) {
                NetworkCapabilities c = cm.getNetworkCapabilities(n);
                if (c != null && c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return true;
            }
        } catch (Exception ignored) { }
        return false;
    }

    private void sendTs(String action) {
        try {
            Intent i = new Intent(action);
            i.setPackage("com.tailscale.ipn");
            sendBroadcast(i);
        } catch (Exception ignored) { }
    }

    // Enciende Tailscale solo si estaba apagado; así sabemos si debemos apagarlo al salir.
    private void connectVpn() {
        if (vpnActive()) return;
        weConnected = true;
        sendTs("com.tailscale.ipn.CONNECT_VPN");
    }

    private void start() {
        tries = 0;
        failed = false;
        retry.setVisibility(View.GONE);
        openTs.setVisibility(View.GONE);
        overlay.setVisibility(View.VISIBLE);
        msg.setText("Conectando…");
        connectVpn();
        load();
    }

    private void load() {
        failed = false;
        web.loadUrl(URLS[tries % URLS.length]);
    }

    private void failedLoad() {
        failed = true;
        overlay.setVisibility(View.VISIBLE);
        tries++;
        if (tries >= MAX_TRIES) {
            msg.setText("No pude conectar con Vida.\nRevisa que Tailscale esté activo y que el portátil esté encendido.");
            retry.setVisibility(View.VISIBLE);
            openTs.setVisibility(View.VISIBLE);
            return;
        }
        msg.setText("Conectando… (" + tries + ")");
        if (tries == 3) connectVpn();
        h.postDelayed(this::load, 1500);
    }

    // Barras del sistema siempre visibles y fijas: el gesto de bajar abre las notificaciones
    // de una vez y la interfaz no cambia de tamaño.
    private void immersive() {
        Window w = getWindow();
        if (Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(true);
            WindowInsetsController c = w.getInsetsController();
            if (c != null) c.show(WindowInsets.Type.systemBars());
        } else {
            w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean f) {
        super.onWindowFocusChanged(f);
        if (f) immersive();
    }

    @Override
    protected void onStart() {
        super.onStart();
        openingTailscale = false;
        if (started && !weConnected) connectVpn();   // al volver a la app
        else if (started && weConnected && !vpnActive()) sendTs("com.tailscale.ipn.CONNECT_VPN");
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }

    // Al salir de la app, apaga Tailscale si lo encendió ella.
    @Override
    protected void onStop() {
        if (weConnected && !openingTailscale) {
            sendTs("com.tailscale.ipn.DISCONNECT_VPN");
        }
        super.onStop();
    }

    @Override
    protected void onPause() {
        web.onPause();
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("(function(){return !!(window.__vidaBack&&window.__vidaBack())})()", v -> {
            if (!"true".equals(v)) moveTaskToBack(true);
        });
    }
}
