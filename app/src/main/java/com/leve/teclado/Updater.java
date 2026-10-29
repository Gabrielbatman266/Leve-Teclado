package com.leve.teclado;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;

/** Atualização direto do GitHub (Releases): checa a versão, baixa o APK e abre o instalador. */
final class Updater {

    static final String REPO = "Gabrielbatman266/Leve-Teclado";

    interface Callback {
        /** latest = número da versão nova (ou -1 se falhou); url = link do APK. */
        void onResult(int current, int latest, String url, String error);
    }

    static int currentVersion(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    /** Pergunta ao GitHub qual é a última versão (em segundo plano). */
    static void check(final Activity a, final Callback cb) {
        final int current = currentVersion(a);
        new Thread(new Runnable() {
            @Override public void run() {
                int latest = -1;
                String url = null, error = null;
                HttpURLConnection con = null;
                try {
                    con = (HttpURLConnection) new URL("https://api.github.com/repos/" + REPO + "/releases/latest").openConnection();
                    con.setConnectTimeout(8000);
                    con.setReadTimeout(8000);
                    con.setRequestProperty("Accept", "application/vnd.github+json");
                    con.setRequestProperty("User-Agent", "LeveTeclado");
                    if (con.getResponseCode() != 200) throw new Exception("GitHub respondeu " + con.getResponseCode());
                    JSONObject o = new JSONObject(ClipStore.readAll(con.getInputStream()));
                    String tag = o.optString("tag_name", "");
                    latest = Integer.parseInt(tag.substring(tag.lastIndexOf('.') + 1).replaceAll("\\D", ""));
                    JSONArray assets = o.getJSONArray("assets");
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject as = assets.getJSONObject(i);
                        if (as.getString("name").endsWith(".apk")) { url = as.getString("browser_download_url"); break; }
                    }
                    if (url == null) throw new Exception("Release sem APK");
                } catch (Exception e) {
                    latest = -1;
                    error = "Sem conexão com o GitHub";
                } finally {
                    if (con != null) con.disconnect();
                }
                final int l = latest;
                final String u = url, err = error;
                a.runOnUiThread(new Runnable() {
                    @Override public void run() { if (!a.isFinishing()) cb.onResult(current, l, u, err); }
                });
            }
        }).start();
    }

    /** Precisa da permissão "instalar apps desconhecidos" (Android 8+). Retorna false se abriu a tela para liberar. */
    static boolean ensureInstallPermission(Activity a) {
        if (Build.VERSION.SDK_INT >= 26 && !a.getPackageManager().canRequestPackageInstalls()) {
            a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + a.getPackageName())));
            return false;
        }
        return true;
    }

    /** Baixa com o gerenciador de downloads do Android (mostra o progresso na notificação) e abre o instalador. */
    static void download(final Activity a, String url, int version) {
        final DownloadManager dm = (DownloadManager) a.getSystemService(Context.DOWNLOAD_SERVICE);
        if (dm == null) return;
        String name = "Leve-Teclado-" + version + ".apk";
        File old = new File(a.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), name);
        if (old.exists()) old.delete();

        DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
        r.setTitle("Leve Teclado — atualização");
        r.setDescription("Versão 1.0." + version);
        r.setMimeType("application/vnd.android.package-archive");
        r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
        r.setDestinationInExternalFilesDir(a, Environment.DIRECTORY_DOWNLOADS, name);
        final long id = dm.enqueue(r);

        final Context app = a.getApplicationContext();
        BroadcastReceiver done = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                if (i.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != id) return;
                try { app.unregisterReceiver(this); } catch (Exception ignored) { }
                Uri apk = dm.getUriForDownloadedFile(id);
                if (apk == null) return;
                Intent install = new Intent(Intent.ACTION_VIEW);
                install.setDataAndType(apk, "application/vnd.android.package-archive");
                install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try { app.startActivity(install); } catch (Exception ignored) { }
            }
        };
        IntentFilter f = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(done, f, Context.RECEIVER_EXPORTED);
        else app.registerReceiver(done, f);
    }
}
