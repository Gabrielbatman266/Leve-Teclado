package com.leve.teclado;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

/** Todas as opções do teclado num lugar só (lidas da memória, sem custo). */
final class Prefs {
    static final String FILE = "leve";

    boolean feedPostLeve, feedInsta, numberRow, haptic, preview, hints, autoCap, doubleSpace, backAfterPaste, pauseHistory, showToolbar, suggestions, autoCorrect, saveImages, saveShots;
    int postLeveTtl, keyHeightDp, textScale, longPressMs, historyMax, theme;
    final Map<String, String> shortcuts = new LinkedHashMap<>();

    static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static Prefs load(Context c) {
        SharedPreferences s = sp(c);
        Prefs p = new Prefs();
        p.feedPostLeve = s.getBoolean("feedPostLeve", true);
        p.feedInsta = s.getBoolean("feedInsta", false);
        p.postLeveTtl = s.getInt("postLeveTtl", 10);
        p.numberRow = s.getBoolean("numberRow", true);
        p.haptic = s.getBoolean("haptic", true);
        p.preview = s.getBoolean("preview", true);
        p.hints = s.getBoolean("hints", true);
        p.autoCap = s.getBoolean("autoCap", true);
        p.doubleSpace = s.getBoolean("doubleSpace", true);
        p.backAfterPaste = s.getBoolean("backAfterPaste", true);
        p.pauseHistory = s.getBoolean("pauseHistory", false);
        p.showToolbar = s.getBoolean("showToolbar", true);
        p.suggestions = s.getBoolean("suggestions", true);
        p.autoCorrect = s.getBoolean("autoCorrect", true);
        p.saveImages = s.getBoolean("saveImages", true);
        p.saveShots = s.getBoolean("saveShots", true);
        // valores sempre dentro de limites seguros (um backup ou ajuste estranho não quebra o teclado)
        p.keyHeightDp = clamp(s.getInt("keyHeightDp", 50), 30, 120);
        p.textScale = clamp(s.getInt("textScale", 100), 50, 250);
        p.longPressMs = clamp(s.getInt("longPressMs", 320), 100, 2000);
        p.historyMax = clamp(s.getInt("historyMax", 1000), 10, 10000);
        p.theme = clamp(s.getInt("theme", 0), 0, Theme.NAMES.length - 1);
        p.postLeveTtl = clamp(p.postLeveTtl, 0, 7 * 24 * 60);
        p.shortcuts.putAll(loadShortcuts(s));
        return p;
    }

    static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    static Map<String, String> loadShortcuts(SharedPreferences s) {
        Map<String, String> m = new LinkedHashMap<>();
        try {
            JSONArray a = new JSONArray(s.getString("shortcuts", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                m.put(o.getString("k"), o.getString("v"));
            }
        } catch (Exception ignored) { }
        return m;
    }

    static void saveShortcuts(Context c, Map<String, String> m) {
        JSONArray a = new JSONArray();
        try {
            for (Map.Entry<String, String> e : m.entrySet()) {
                JSONObject o = new JSONObject();
                o.put("k", e.getKey());
                o.put("v", e.getValue());
                a.put(o);
            }
        } catch (Exception ignored) { }
        sp(c).edit().putString("shortcuts", a.toString()).apply();
    }
}
