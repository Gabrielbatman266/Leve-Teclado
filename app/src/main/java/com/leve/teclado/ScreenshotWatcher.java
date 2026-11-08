package com.leve.teclado;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Vigia a galeria: quando você tira um print, ele entra sozinho na aba Imagens.
 * Não fica rodando nada: o Android avisa quando aparece foto nova e aí ele olha só as últimas.
 */
final class ScreenshotWatcher extends ContentObserver {

    private static final int MAX_PER_SCAN = 10;
    private static final long MAX_AGE_SEC = 24 * 60 * 60; // ao reabrir, pega prints de até 1 dia

    private final Context ctx;
    private final ClipStore store;
    private final SharedPreferences sp;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean registered, scanning;

    ScreenshotWatcher(Context c, ClipStore store) {
        super(new Handler(Looper.getMainLooper()));
        this.ctx = c.getApplicationContext();
        this.store = store;
        this.sp = Prefs.sp(c);
    }

    static String permission() {
        return Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_IMAGES
                : Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    /** Android 14: usuário escolheu "só algumas fotos" (aí os prints novos não aparecem). */
    static boolean partialOnly(Context c) {
        if (Build.VERSION.SDK_INT < 34) return false;
        return !hasPermission(c) && c.checkSelfPermission("android.permission.READ_MEDIA_VISUAL_USER_SELECTED")
                == PackageManager.PERMISSION_GRANTED;
    }

    interface Result { void onDone(int found); }

    /** Busca manual (botão "Buscar prints agora"): olha as últimas 24 h, ignora o ponto de partida. */
    void scanNow(final Result r) {
        new Thread(new Runnable() {
            @Override public void run() {
                final ArrayList<Uri> found = hasPermission(ctx) ? findNew(true) : new ArrayList<Uri>();
                main.post(new Runnable() {
                    @Override public void run() {
                        for (int i = found.size() - 1; i >= 0; i--) store.offerScreenshot(ctx.getContentResolver(), found.get(i));
                        if (r != null) r.onDone(found.size());
                    }
                });
            }
        }).start();
    }

    static boolean hasPermission(Context c) {
        if (Build.VERSION.SDK_INT < 23) return true;
        return c.checkSelfPermission(permission()) == PackageManager.PERMISSION_GRANTED;
    }

    /** Liga ou desliga conforme a configuração e a permissão, e procura prints novos. */
    void refresh(boolean enabled) {
        boolean can = enabled && hasPermission(ctx);
        if (can && !registered) {
            try {
                ctx.getContentResolver().registerContentObserver(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, this);
                registered = true;
            } catch (Exception ignored) { }
        } else if (!can && registered) {
            stop();
        }
        if (can) scanSoon(200);
    }

    void stop() {
        if (!registered) return;
        try { ctx.getContentResolver().unregisterContentObserver(this); } catch (Exception ignored) { }
        registered = false;
    }

    @Override
    public void onChange(boolean selfChange) {
        scanSoon(1200); // espera o print terminar de ser salvo
    }

    private void scanSoon(long delay) {
        main.removeCallbacks(scan);
        main.postDelayed(scan, delay);
    }

    private final Runnable scan = new Runnable() {
        @Override public void run() {
            if (scanning) { scanSoon(800); return; }
            scanning = true;
            new Thread(new Runnable() {
                @Override public void run() {
                    final ArrayList<Uri> found = findNew(false);
                    main.post(new Runnable() {
                        @Override public void run() {
                            scanning = false;
                            // do mais antigo para o mais novo: o print mais recente fica no topo
                            for (int i = found.size() - 1; i >= 0; i--) store.offerScreenshot(ctx.getContentResolver(), found.get(i));
                        }
                    });
                }
            }).start();
        }
    };

    private ArrayList<Uri> findNew(boolean manual) {
        ArrayList<Uri> out = new ArrayList<>();
        long now = System.currentTimeMillis() / 1000;
        long last = sp.getLong("shotLastSec", 0);
        if (last == 0) {
            // primeira vez: pega os prints das últimas 6 horas (não perde os tirados logo após liberar)
            last = now - 6 * 60 * 60;
            sp.edit().putLong("shotLastSec", last).apply();
        }
        long since = manual ? now - MAX_AGE_SEC : Math.max(last, now - MAX_AGE_SEC);

        ContentResolver cr = ctx.getContentResolver();
        boolean q = Build.VERSION.SDK_INT >= 29;
        String[] proj = q
                ? new String[]{MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED,
                        MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.RELATIVE_PATH,
                        MediaStore.Images.Media.BUCKET_DISPLAY_NAME, MediaStore.Images.Media.DATA}
                : new String[]{MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED,
                        MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.DATA,
                        MediaStore.Images.Media.BUCKET_DISPLAY_NAME};
        // sem filtro de data na consulta (alguns celulares gravam a data em milissegundos): filtra aqui
        String sel = q ? MediaStore.Images.Media.IS_PENDING + " = 0" : null;
        long newest = last;
        Cursor c = null;
        try {
            c = cr.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, proj, sel,
                    null, MediaStore.Images.Media._ID + " DESC");
            if (c == null) return out;
            int rows = 0;
            while (c.moveToNext() && rows++ < 40) {
                long id = c.getLong(0), added = c.getLong(1);
                if (added > 100000000000L) added /= 1000; // estava em milissegundos
                if (added <= since) continue;
                StringBuilder where = new StringBuilder();
                for (int i = 2; i < proj.length; i++) {
                    String v = c.getString(i);
                    if (v != null) where.append(v).append(' ');
                }
                String w = where.toString().toLowerCase(Locale.ROOT);
                // "Screenshots" (Xiaomi, Samsung, Google…), "Captura de tela", "Capturas"
                boolean shot = w.contains("screenshot") || w.contains("captura") || w.contains("screen_shot")
                        || w.contains("screencapture");
                if (!shot) continue;
                if (added > newest) newest = added;
                if (out.size() < MAX_PER_SCAN)
                    out.add(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id));
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        if (newest > last && !manual) sp.edit().putLong("shotLastSec", newest).apply();
        if (!out.isEmpty()) sp.edit().putLong("shotFoundAt", System.currentTimeMillis()).apply();
        return out;
    }
}
