package com.leve.teclado;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

/**
 * O Post Leve avisa o teclado:
 *  - EXPIRE: "copiei esta legenda; apague do histórico daqui a X min" (funciona mesmo se o teclado não viu a cópia);
 *  - FORGET: "apague esta legenda do histórico agora".
 */
public class PostLeveReceiver extends BroadcastReceiver {
    static final String EXPIRE = "com.leve.teclado.EXPIRE", FORGET = "com.leve.teclado.FORGET";

    @Override
    public void onReceive(final Context c, Intent i) {
        final String t = i.getStringExtra("texto");
        if (t == null || t.trim().isEmpty()) return;
        Prefs p = Prefs.load(c);
        long at = FORGET.equals(i.getAction()) ? 0 : System.currentTimeMillis() + Math.max(1, p.postLeveTtl) * 60_000L;
        if (p.postLeveTtl <= 0 && !FORGET.equals(i.getAction())) return;
        try {
            org.json.JSONArray a = new org.json.JSONArray(Prefs.sp(c).getString("plExpire", "[]"));
            org.json.JSONArray out = new org.json.JSONArray();
            for (int k = 0; k < a.length(); k++) if (!t.equals(a.getJSONObject(k).optString("t"))) out.put(a.getJSONObject(k));
            out.put(new org.json.JSONObject().put("t", t).put("at", at));
            Prefs.sp(c).edit().putString("plExpire", out.toString()).apply();
        } catch (Exception ignored) { }
        if (!FORGET.equals(i.getAction())) return;
        // apaga agora (o histórico carrega em segundo plano: tenta algumas vezes)
        final PendingResult pr = goAsync();
        final Handler h = new Handler(Looper.getMainLooper());
        final ClipStore s = ClipStore.get(c);
        final int[] tries = {0};
        h.postDelayed(new Runnable() {
            @Override public void run() {
                s.removeText(t);
                if (++tries[0] < 4) h.postDelayed(this, 1500); else pr.finish();
            }
        }, 800);
    }
}
