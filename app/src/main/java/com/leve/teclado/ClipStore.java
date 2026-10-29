package com.leve.teclado;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Histórico da área de transferência + itens salvos (fixados).
 * Lista só é mexida na thread principal; leitura/escrita do arquivo é em segundo plano.
 */
final class ClipStore {
    static final class Item {
        String text;
        long time;
        boolean pinned;
        Item(String t, long time, boolean pinned) { this.text = t; this.time = time; this.pinned = pinned; }
    }

    interface Listener { void onClipsChanged(); }

    private static ClipStore instance;
    private static final int MAX_CHARS = 200_000;
    private static final Charset UTF8 = Charset.forName("UTF-8");

    final ArrayList<Item> items = new ArrayList<>();
    private final ArrayList<String> pending = new ArrayList<>();
    private final File file;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final List<Listener> listeners = new ArrayList<>();
    private boolean loaded;
    int max = 1000;

    static synchronized ClipStore get(Context c) {
        if (instance == null) instance = new ClipStore(c.getApplicationContext());
        return instance;
    }

    private ClipStore(Context c) {
        file = new File(c.getFilesDir(), "clips.json");
        io.execute(new Runnable() {
            @Override public void run() {
                final ArrayList<Item> list = readFile(file);
                main.post(new Runnable() {
                    @Override public void run() {
                        items.addAll(0, list);
                        loaded = true;
                        for (String p : pending) add(p);
                        pending.clear();
                        notifyChanged();
                    }
                });
            }
        });
    }

    boolean isLoaded() { return loaded; }

    void addListener(Listener l) { if (!listeners.contains(l)) listeners.add(l); }
    void removeListener(Listener l) { listeners.remove(l); }

    private void notifyChanged() {
        for (int i = 0; i < listeners.size(); i++) listeners.get(i).onClipsChanged();
    }

    /** Adiciona no topo. Se já existir, só sobe para o topo (mantém se estava salvo). */
    void add(String text) {
        if (text == null || text.trim().isEmpty()) return;
        if (text.length() > MAX_CHARS) text = text.substring(0, MAX_CHARS);
        if (!loaded) { pending.add(text); return; }
        if (!items.isEmpty() && items.get(0).text.equals(text)) return;
        boolean pinned = false;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).text.equals(text)) { pinned = items.remove(i).pinned; break; }
        }
        items.add(0, new Item(text, System.currentTimeMillis(), pinned));
        trim();
        changed();
    }

    String latest() {
        return items.isEmpty() ? null : items.get(0).text;
    }

    void togglePin(Item it) { it.pinned = !it.pinned; changed(); }

    void remove(Item it) { items.remove(it); changed(); }

    /** Apaga o histórico, mas mantém os salvos. */
    void clearUnpinned() {
        for (int i = items.size() - 1; i >= 0; i--) if (!items.get(i).pinned) items.remove(i);
        changed();
    }

    void clearAll() { items.clear(); changed(); }

    private void trim() {
        int count = 0;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).pinned) continue;
            count++;
            if (count > max) { items.remove(i); i--; }
        }
    }

    void setMax(int m) {
        if (m == max) return;
        max = m;
        if (loaded) { int before = items.size(); trim(); if (items.size() != before) changed(); }
    }

    private void changed() {
        notifyChanged();
        main.removeCallbacks(saveTask);
        main.postDelayed(saveTask, 700);
    }

    private final Runnable saveTask = new Runnable() {
        @Override public void run() {
            final String json = toJson(items).toString();
            io.execute(new Runnable() {
                @Override public void run() { writeAtomic(file, json); }
            });
        }
    };

    // ---------- backup ----------

    String exportJson() { return toJson(items).toString(); }

    /** Junta um backup com o que já existe. Retorna quantos itens novos entraram. */
    int importJson(String json) {
        ArrayList<Item> in = parse(json);
        int added = 0;
        for (int i = in.size() - 1; i >= 0; i--) {
            Item n = in.get(i);
            boolean exists = false;
            for (Item it : items) if (it.text.equals(n.text)) { exists = true; if (n.pinned) it.pinned = true; break; }
            if (!exists) { items.add(n); added++; }
        }
        java.util.Collections.sort(items, new java.util.Comparator<Item>() {
            @Override public int compare(Item a, Item b) { return Long.compare(b.time, a.time); }
        });
        changed();
        return added;
    }

    // ---------- arquivo ----------

    private static JSONArray toJson(List<Item> list) {
        JSONArray a = new JSONArray();
        try {
            for (Item it : list) {
                JSONObject o = new JSONObject();
                o.put("t", it.text);
                o.put("d", it.time);
                if (it.pinned) o.put("p", true);
                a.put(o);
            }
        } catch (Exception ignored) { }
        return a;
    }

    private static ArrayList<Item> parse(String s) {
        ArrayList<Item> list = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(s);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                list.add(new Item(o.getString("t"), o.optLong("d", 0), o.optBoolean("p", false)));
            }
        } catch (Exception ignored) { }
        return list;
    }

    private static ArrayList<Item> readFile(File f) {
        if (!f.exists()) return new ArrayList<>();
        try {
            return parse(readAll(new FileInputStream(f)));
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    static String readAll(InputStream in) throws java.io.IOException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), UTF8);
        } finally {
            in.close();
        }
    }

    private static void writeAtomic(File f, String data) {
        File tmp = new File(f.getPath() + ".tmp");
        try {
            FileOutputStream out = new FileOutputStream(tmp);
            out.write(data.getBytes(UTF8));
            out.getFD().sync();
            out.close();
            if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f); }
        } catch (Exception ignored) { }
    }
}
