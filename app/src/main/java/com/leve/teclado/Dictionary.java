package com.leve.teclado;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * Dicionário leve para sugestões: lista base em português + palavras que você digita (aprende sozinho).
 * Busca ignora acentos: "voce" acha "você", "nao" acha "não".
 */
final class Dictionary {

    static final class Entry {
        final String word, key;
        int base, user;
        Entry(String w, int base) { word = w; key = strip(w); this.base = base; }
        int score() { return base + user * 120; }
    }

    private static Dictionary instance;
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");

    private final ArrayList<Entry> entries = new ArrayList<>();
    private final HashMap<String, Entry> byWord = new HashMap<>();
    private final HashMap<String, Entry> bestByKey = new HashMap<>(); // sem acento -> forma mais usada
    private final File userFile;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private volatile boolean loaded;

    static synchronized Dictionary get(Context c) {
        if (instance == null) instance = new Dictionary(c.getApplicationContext());
        return instance;
    }

    static String strip(String s) {
        String n = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return MARKS.matcher(n).replaceAll("");
    }

    private Dictionary(final Context c) {
        userFile = new File(c.getFilesDir(), "palavras_usuario.json");
        io.execute(new Runnable() {
            @Override public void run() {
                final ArrayList<String> base = new ArrayList<>();
                try {
                    BufferedReader r = new BufferedReader(new InputStreamReader(c.getAssets().open("palavras.txt"), "UTF-8"));
                    String line;
                    while ((line = r.readLine()) != null) { line = line.trim(); if (!line.isEmpty()) base.add(line); }
                    r.close();
                } catch (Exception ignored) { }
                final HashMap<String, Integer> user = new HashMap<>();
                try {
                    if (userFile.exists()) {
                        JSONObject o = new JSONObject(ClipStore.readAll(new FileInputStream(userFile)));
                        Iterator<String> it = o.keys();
                        while (it.hasNext()) { String k = it.next(); user.put(k, o.getInt(k)); }
                    }
                } catch (Exception ignored) { }
                main.post(new Runnable() {
                    @Override public void run() {
                        int n = base.size();
                        for (int i = 0; i < n; i++) put(base.get(i), 1000 - (i * 900 / Math.max(1, n)));
                        for (java.util.Map.Entry<String, Integer> e : user.entrySet()) {
                            Entry en = put(e.getKey(), 0);
                            en.user = e.getValue();
                            reindex(en);
                        }
                        loaded = true;
                    }
                });
            }
        });
    }

    private Entry put(String w, int base) {
        Entry e = byWord.get(w);
        if (e != null) return e;
        e = new Entry(w, base);
        entries.add(e);
        byWord.put(w, e);
        reindex(e);
        return e;
    }

    private void reindex(Entry e) {
        Entry b = bestByKey.get(e.key);
        if (b == null || e.score() > b.score()) bestByKey.put(e.key, e);
    }

    boolean isLoaded() { return loaded; }

    boolean known(String w) { return byWord.containsKey(w.toLowerCase(Locale.ROOT)); }

    /** Até {@code max} palavras que começam com o prefixo (sem contar acentos), mais usadas primeiro. */
    ArrayList<String> suggest(String typed, int max) {
        ArrayList<String> out = new ArrayList<>();
        if (!loaded || typed.isEmpty()) return out;
        String lower = typed.toLowerCase(Locale.ROOT);
        String key = strip(typed);
        Entry[] top = new Entry[max];
        int[] sc = new int[max];
        for (int i = 0, n = entries.size(); i < n; i++) {
            Entry e = entries.get(i);
            if (!e.key.startsWith(key) || e.word.equals(lower)) continue;
            int s = e.score();
            if (e.word.startsWith(lower)) s += 150;           // respeita o acento que você já digitou
            if (e.key.length() == key.length()) s += 400;     // mesma palavra, só faltou acento
            s -= (e.key.length() - key.length()) * 15;        // prefere completar curto
            for (int j = 0; j < max; j++) {
                if (top[j] == null || s > sc[j]) {
                    for (int k = max - 1; k > j; k--) { top[k] = top[k - 1]; sc[k] = sc[k - 1]; }
                    top[j] = e; sc[j] = s;
                    break;
                }
            }
        }
        for (Entry e : top) if (e != null) out.add(matchCase(typed, e.word));
        return out;
    }

    /** Correção só de acento, bem conservadora: "nao" -> "não", "voce" -> "você". */
    String autoAccent(String typed) {
        if (!loaded || typed.length() < 2) return null;
        String lower = typed.toLowerCase(Locale.ROOT);
        if (!lower.equals(strip(lower))) return null; // já tem acento
        if (byWord.containsKey(lower)) return null;    // palavra existe assim
        Entry b = bestByKey.get(lower);
        if (b == null || b.word.equals(lower)) return null;
        return matchCase(typed, b.word);
    }

    static String matchCase(String typed, String word) {
        if (typed.isEmpty()) return word;
        boolean firstUp = Character.isUpperCase(typed.charAt(0));
        boolean allUp = typed.length() > 1 && typed.equals(typed.toUpperCase(Locale.ROOT));
        if (allUp) return word.toUpperCase(Locale.ROOT);
        if (firstUp) return word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1);
        return word;
    }

    /** Aprende uma palavra que você terminou de digitar. */
    void learn(String word) {
        if (!loaded || word.length() < 2 || word.length() > 30) return;
        for (int i = 0; i < word.length(); i++) if (!Character.isLetter(word.charAt(i))) return;
        String w = word;
        // nomes próprios ficam com maiúscula; o resto vai em minúscula
        if (!(Character.isUpperCase(w.charAt(0)) && !byWord.containsKey(w.toLowerCase(Locale.ROOT))))
            w = w.toLowerCase(Locale.ROOT);
        if (w.equals(w.toUpperCase(Locale.ROOT)) && w.length() > 1) w = w.toLowerCase(Locale.ROOT);
        Entry e = put(w, 0);
        e.user++;
        reindex(e);
        scheduleSave();
    }

    /** Esquece uma palavra aprendida (segurar a sugestão). */
    void forget(String word) {
        Entry e = byWord.get(word);
        if (e == null) e = byWord.get(word.toLowerCase(Locale.ROOT));
        if (e == null) return;
        e.user = 0;
        if (e.base == 0) {
            entries.remove(e);
            byWord.remove(e.word);
            if (bestByKey.get(e.key) == e) {
                bestByKey.remove(e.key);
                for (Entry o : entries) if (o.key.equals(e.key)) reindex(o);
            }
        }
        scheduleSave();
    }

    private void scheduleSave() {
        main.removeCallbacks(save);
        main.postDelayed(save, 3000);
    }

    private final Runnable save = new Runnable() {
        @Override public void run() {
            final JSONObject o = new JSONObject();
            try {
                for (Entry e : entries) if (e.user > 0) o.put(e.word, e.user);
            } catch (Exception ignored) { }
            io.execute(new Runnable() {
                @Override public void run() {
                    try {
                        FileOutputStream out = new FileOutputStream(userFile);
                        out.write(o.toString().getBytes("UTF-8"));
                        out.close();
                    } catch (Exception ignored) { }
                }
            });
        }
    };

    void clearLearned() {
        for (int i = entries.size() - 1; i >= 0; i--) {
            Entry e = entries.get(i);
            e.user = 0;
            if (e.base == 0) { entries.remove(i); byWord.remove(e.word); }
        }
        bestByKey.clear();
        for (Entry e : entries) reindex(e);
        scheduleSave();
    }
}
