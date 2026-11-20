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
    /** Índice ordenado (sem acento): a sugestão só olha as palavras com o mesmo começo. */
    private final java.util.TreeMap<String, Object> byKey = new java.util.TreeMap<>();
    /** Limite de palavras aprendidas: as menos usadas saem quando passa disso (memória e velocidade). */
    static final int MAX_LEARNED = 40000;
    private int learnedCount;
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
        index(e);
        if (base == 0) learnedCount++;
        reindex(e);
        return e;
    }

    @SuppressWarnings("unchecked")
    private void index(Entry e) {
        Object cur = byKey.get(e.key);
        if (cur == null) byKey.put(e.key, e);
        else if (cur instanceof Entry) {
            ArrayList<Entry> l = new ArrayList<>(2);
            l.add((Entry) cur);
            l.add(e);
            byKey.put(e.key, l);
        } else ((ArrayList<Entry>) cur).add(e);
    }

    @SuppressWarnings("unchecked")
    private void unindex(Entry e) {
        Object cur = byKey.get(e.key);
        if (cur == e) byKey.remove(e.key);
        else if (cur instanceof ArrayList) {
            ArrayList<Entry> l = (ArrayList<Entry>) cur;
            l.remove(e);
            if (l.size() == 1) byKey.put(e.key, l.get(0));
            else if (l.isEmpty()) byKey.remove(e.key);
        }
    }

    /** Tira uma palavra aprendida de todos os lugares. */
    private void drop(Entry e) {
        byWord.remove(e.word);
        unindex(e);
        if (e.base == 0) learnedCount--;
        if (bestByKey.get(e.key) == e) {
            bestByKey.remove(e.key);
            Object cur = byKey.get(e.key);
            if (cur instanceof Entry) reindex((Entry) cur);
            else if (cur != null) for (Object o : (ArrayList<?>) cur) reindex((Entry) o);
        }
    }

    /** Passou do limite: esquece de uma vez as 10% menos usadas (raro, barato). */
    private void evictIfNeeded() {
        if (learnedCount <= MAX_LEARNED * 11 / 10) return;
        ArrayList<Entry> learned = new ArrayList<>();
        for (Entry e : entries) if (e.base == 0) learned.add(e);
        java.util.Collections.sort(learned, new java.util.Comparator<Entry>() {
            @Override public int compare(Entry a, Entry b) { return a.user - b.user; }
        });
        int remove = learned.size() - MAX_LEARNED;
        java.util.HashSet<Entry> gone = new java.util.HashSet<>();
        for (int i = 0; i < remove; i++) { gone.add(learned.get(i)); drop(learned.get(i)); }
        ArrayList<Entry> keep = new ArrayList<>(entries.size() - gone.size());
        for (Entry e : entries) if (!gone.contains(e)) keep.add(e);
        entries.clear();
        entries.addAll(keep);
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
        // só as palavras que começam igual (índice ordenado): rápido mesmo com dezenas de milhares
        ArrayList<Entry> cands = new ArrayList<>();
        for (Object o : byKey.subMap(key, true, key + Character.MAX_VALUE, false).values()) {
            if (o instanceof Entry) cands.add((Entry) o);
            else for (Object x : (ArrayList<?>) o) cands.add((Entry) x);
            if (cands.size() > 20000) break;
        }
        for (int i = 0, n = cands.size(); i < n; i++) {
            Entry e = cands.get(i);
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
        evictIfNeeded();
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
            drop(e);
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
                    // grava num arquivo temporário e troca: se o celular desligar no meio, nada se perde
                    File tmp = new File(userFile.getPath() + ".tmp");
                    try {
                        FileOutputStream out = new FileOutputStream(tmp);
                        out.write(o.toString().getBytes("UTF-8"));
                        out.getFD().sync();
                        out.close();
                        if (!tmp.renameTo(userFile)) { userFile.delete(); tmp.renameTo(userFile); }
                    } catch (Exception ignored) { }
                }
            });
        }
    };

    void clearLearned() {
        for (int i = entries.size() - 1; i >= 0; i--) {
            Entry e = entries.get(i);
            e.user = 0;
            if (e.base == 0) { entries.remove(i); byWord.remove(e.word); unindex(e); learnedCount--; }
        }
        bestByKey.clear();
        for (Entry e : entries) reindex(e);
        scheduleSave();
    }
}
