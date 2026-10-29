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

    boolean numberRow, haptic, preview, hints, autoCap, doubleSpace, backAfterPaste, pauseHistory, showToolbar, suggestions, autoCorrect;
    int keyHeightDp, textScale, longPressMs, historyMax, theme;
    final Map<String, String> shortcuts = new LinkedHashMap<>();

    static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static Prefs load(Context c) {
        SharedPreferences s = sp(c);
        Prefs p = new Prefs();
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
        p.keyHeightDp = s.getInt("keyHeightDp", 50);
        p.textScale = s.getInt("textScale", 100);
        p.longPressMs = s.getInt("longPressMs", 320);
        p.historyMax = s.getInt("historyMax", 1000);
        p.theme = s.getInt("theme", 0);
        p.shortcuts.putAll(loadShortcuts(s));
        return p;
    }

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
