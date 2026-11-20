package com.leve.teclado;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Patterns;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Histórico da área de transferência (textos e imagens) + itens salvos (fixados).
 * Lista só é mexida na thread principal; arquivos são lidos/gravados em segundo plano.
 */
final class ClipStore {

    static final int KIND_ALL = 0, KIND_TEXT = 1, KIND_LINK = 2, KIND_IMAGE = 3;

    static final class Item {
        String text;      // vazio nas imagens
        String img;       // nome do arquivo da imagem (null = texto)
        int w, h;
        boolean shot;     // print de tela (veio da galeria, não da área de transferência)
        long time;
        boolean pinned;
        private Boolean link;

        Item(String t, long time, boolean pinned) { this.text = t; this.time = time; this.pinned = pinned; }

        boolean isImage() { return img != null; }

        boolean isLink() {
            if (link == null) link = img == null && looksLikeLink(text);
            return link;
        }

        /**
         * Rápido: o "regex de site" do Android é lento em textos longos (abrir o painel com
         * 10 mil itens levava segundos). Só roda ele em textos curtos, sem espaço, com ponto.
         */
        static boolean looksLikeLink(String t) {
            if (t.length() >= 2000) return false;
            if (t.contains("://") || t.contains("www.")) return true;
            if (t.length() > 120 || t.indexOf('.') < 0 || t.trim().indexOf(' ') >= 0) return false;
            return Patterns.WEB_URL.matcher(t).find();
        }

        boolean matches(int kind) {
            switch (kind) {
                case KIND_TEXT: return !isImage();
                case KIND_LINK: return isLink();
                case KIND_IMAGE: return isImage();
                default: return true;
            }
        }
    }

    interface Listener { void onClipsChanged(); }

    private static ClipStore instance;
    private static final int MAX_CHARS = 200_000;
    /**
     * Teto de letras guardadas no total (~16 MB). Textos curtos: cabem os 10.000 itens.
     * Textos gigantes: os mais antigos saem antes de pesar no celular (antes podia chegar a 2 GB e fechar o teclado).
     */
    static final long MAX_TOTAL_CHARS = 8_000_000L;
    private static final int MAX_REMOVED_MEMO = 2000;
    private static final int MAX_IMAGES = 150;                 // imagens não salvas guardadas
    private static final long MAX_IMAGE_BYTES = 25L * 1024 * 1024;
    private static final Charset UTF8 = Charset.forName("UTF-8");

    final ArrayList<Item> items = new ArrayList<>();
    final File imgDir;
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
        imgDir = new File(c.getFilesDir(), "clipimg");
        imgDir.mkdirs();
        io.execute(new Runnable() {
            @Override public void run() {
                final ArrayList<Item> list = readFile(file);
                // apaga imagens que ficaram sem item (ex.: app fechado no meio de uma gravação)
                HashSet<String> used = new HashSet<>();
                for (Item it : list) if (it.img != null) used.add(it.img);
                File[] files = imgDir.listFiles();
                if (files != null) for (File f : files) if (!used.contains(f.getName())) f.delete();
                for (int i = list.size() - 1; i >= 0; i--) {
                    Item it = list.get(i);
                    if (it.img != null && !new File(imgDir, it.img).exists()) list.remove(i);
                }
                main.post(new Runnable() {
                    @Override public void run() {
                        items.addAll(0, list);
                        loaded = true;
                        trim();
                        for (String p : pending) add(p);
                        pending.clear();
                        version++;
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

    /** Adiciona texto no topo. Se já existir, só sobe para o topo (mantém se estava salvo). */
    void add(String text) {
        if (text == null || text.trim().isEmpty()) return;
        if (text.length() > MAX_CHARS) text = text.substring(0, MAX_CHARS);
        if (!loaded) { pending.add(text); return; }
        if (!items.isEmpty() && !items.get(0).isImage() && items.get(0).text.equals(text)) return;
        boolean pinned = false;
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            if (!it.isImage() && it.text.equals(text)) { pinned = items.remove(i).pinned; break; }
        }
        items.add(0, new Item(text, System.currentTimeMillis(), pinned));
        trim();
        changed();
    }

    /** Último texto copiado (imagens ficam de fora). */
    String latest() {
        for (Item it : items) if (!it.isImage()) return it.text;
        return null;
    }

    Item latestItem() { return items.isEmpty() ? null : items.get(0); }

    void togglePin(Item it) { it.pinned = !it.pinned; changed(); }

    /** Tira do histórico um texto (não mexe nos salvos ★). */
    boolean removeText(String t) {
        if (t == null || !loaded) return false;
        boolean any = false; String tt = t.trim();
        for (Item it : new ArrayList<>(items)) if (!it.isImage() && !it.pinned && it.text != null && it.text.trim().equals(tt)) { remove(it); any = true; }
        return any;
    }

    void remove(Item it) {
        items.remove(it);
        forget(it);
        changed();
    }

    // itens apagados de propósito: não voltam só porque o Android ainda os tem copiados
    private final HashMap<Integer, Integer> removed = new HashMap<>();
    private final HashSet<String> removedUris = new HashSet<>();
    private final HashMap<String, String> uriToImg = new HashMap<>();
    private int lastSeenHash, lastSeenLen = -1;
    private String lastSeenUri;

    private void forget(Item it) {
        if (it.isImage()) {
            for (java.util.Map.Entry<String, String> e : uriToImg.entrySet())
                if (it.img.equals(e.getValue())) removedUris.add(e.getKey());
            final File f = new File(imgDir, it.img);
            io.execute(new Runnable() { @Override public void run() { f.delete(); } });
        } else {
            if (removed.size() >= MAX_REMOVED_MEMO) removed.clear(); // só a memória de "apagados": não cresce sem fim
            removed.put(it.text.hashCode(), it.text.length());
        }
    }

    /** Captura de texto do sistema: só entra se for uma cópia nova (não a mesma que já vimos ou que você apagou). */
    void offerFromSystem(String text, boolean isNewCopy) {
        if (text == null) return;
        int h = text.hashCode(), len = text.length();
        boolean same = h == lastSeenHash && len == lastSeenLen;
        lastSeenHash = h; lastSeenLen = len;
        lastSeenUri = null;
        if (!isNewCopy) {
            if (same) return;
            Integer r = removed.get(h);
            if (r != null && r == len) return;
        }
        removed.remove(h);
        add(text);
    }

    /** Captura de imagem do sistema: copia o arquivo para o app (em segundo plano) e põe no topo. */
    void offerImage(final ContentResolver cr, final Uri uri, boolean isNewCopy) {
        final String key = uri.toString();
        boolean same = key.equals(lastSeenUri);
        lastSeenUri = key;
        lastSeenLen = -1;
        if (!isNewCopy && (same || removedUris.contains(key))) return;
        removedUris.remove(key);
        io.execute(new Runnable() {
            @Override public void run() {
                final Item it = copyImage(cr, uri);
                if (it == null) return;
                main.post(new Runnable() {
                    @Override public void run() {
                        uriToImg.put(key, it.img);
                        addImageItem(it);
                    }
                });
            }
        });
    }

    /** Print de tela novo: entra direto na aba Imagens (sem mexer no controle da área de transferência). */
    void offerScreenshot(final ContentResolver cr, final Uri uri) {
        if (removedUris.contains(uri.toString())) return;
        io.execute(new Runnable() {
            @Override public void run() {
                final Item it = copyImage(cr, uri);
                if (it == null) return;
                it.shot = true;
                main.post(new Runnable() {
                    @Override public void run() {
                        uriToImg.put(uri.toString(), it.img);
                        addImageItem(it);
                    }
                });
            }
        });
    }

    private void addImageItem(Item n) {
        if (!loaded) return;
        boolean pinned = false;
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            if (n.img.equals(it.img)) {
                if (i == 0) return;
                Item old = items.remove(i);
                pinned = old.pinned;
                n.shot = n.shot || old.shot;
                break;
            }
        }
        n.pinned = pinned;
        items.add(0, n);
        trim();
        changed();
    }

    /** Copia a imagem para a pasta do app. O nome é o "md5" do conteúdo: a mesma imagem nunca duplica. */
    private Item copyImage(ContentResolver cr, Uri uri) {
        File tmp = new File(imgDir, "tmp_" + System.nanoTime());
        try {
            String mime = null;
            try { mime = cr.getType(uri); } catch (Exception ignored) { }
            InputStream in = cr.openInputStream(uri);
            if (in == null) return null;
            MessageDigest md = MessageDigest.getInstance("MD5");
            FileOutputStream out = new FileOutputStream(tmp);
            byte[] buf = new byte[32768];
            long total = 0;
            int n;
            try {
                while ((n = in.read(buf)) > 0) {
                    total += n;
                    if (total > MAX_IMAGE_BYTES) throw new Exception("imagem grande demais");
                    md.update(buf, 0, n);
                    out.write(buf, 0, n);
                }
            } finally {
                in.close();
                out.close();
            }
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(tmp.getPath(), o);
            if (o.outWidth <= 0) { tmp.delete(); return null; } // não é imagem
            if (o.outMimeType != null) mime = o.outMimeType;
            StringBuilder hex = new StringBuilder();
            for (byte b : md.digest()) hex.append(String.format("%02x", b));
            String name = hex + ClipImageProvider.extFor(mime);
            File dst = new File(imgDir, name);
            if (dst.exists()) tmp.delete();
            else if (!tmp.renameTo(dst)) { tmp.delete(); return null; }
            Item it = new Item("", System.currentTimeMillis(), false);
            it.img = name;
            it.w = o.outWidth;
            it.h = o.outHeight;
            return it;
        } catch (Exception e) {
            tmp.delete();
            return null;
        }
    }

    /** Apaga o que não está salvo (só do tipo escolhido). */
    void clearUnpinned(int kind) {
        for (int i = items.size() - 1; i >= 0; i--) {
            Item it = items.get(i);
            if (it.pinned || !it.matches(kind)) continue;
            items.remove(i);
            forget(it);
        }
        changed();
    }

    void clearUnpinned() { clearUnpinned(KIND_ALL); }

    private void trim() {
        int texts = 0, images = 0;
        long chars = 0;
        for (Item it : items) if (it.pinned && it.text != null) chars += it.text.length();
        boolean any = false;
        // percorre do mais novo pro mais antigo, montando a lista que fica (sem remover no meio: rápido)
        ArrayList<Item> keep = null;
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            boolean over = false;
            if (!it.pinned) {
                int len = it.text == null ? 0 : it.text.length();
                over = it.isImage() ? ++images > MAX_IMAGES : (++texts > max || chars + len > MAX_TOTAL_CHARS);
                if (!over && !it.isImage()) chars += len;
            }
            if (over) {
                if (keep == null) { keep = new ArrayList<>(items.size()); keep.addAll(items.subList(0, i)); }
                if (it.isImage()) forget(it);
                any = true;
            } else if (keep != null) keep.add(it);
        }
        if (any) { items.clear(); items.addAll(keep); }
    }

    void setMax(int m) {
        if (m == max) return;
        max = m;
        if (loaded) { int before = items.size(); trim(); if (items.size() != before) changed(); }
    }

    int countImages() {
        int n = 0;
        for (Item it : items) if (it.isImage()) n++;
        return n;
    }

    /** Muda a cada alteração da lista (a busca usa para saber se pode reaproveitar o resultado). */
    int version;

    private void changed() {
        version++;
        notifyChanged();
        main.removeCallbacks(saveTask);
        main.postDelayed(saveTask, 700);
    }

    /**
     * Salvar: na tela principal só tira uma "foto" rápida da lista; montar e gravar o arquivo
     * acontece em segundo plano (antes 10 mil itens travavam o teclado por ~0,2 s a cada cópia).
     */
    private final Runnable saveTask = new Runnable() {
        @Override public void run() {
            final Item[] snap = new Item[items.size()];
            for (int i = 0; i < snap.length; i++) {
                Item it = items.get(i);
                Item c = new Item(it.text, it.time, it.pinned);
                c.img = it.img; c.w = it.w; c.h = it.h; c.shot = it.shot;
                snap[i] = c;
            }
            io.execute(new Runnable() {
                @Override public void run() { writeStream(file, java.util.Arrays.asList(snap)); }
            });
        }
    };

    /** Grava direto no arquivo, item por item (não monta um textão gigante na memória). */
    private static void writeStream(File f, List<Item> list) {
        File tmp = new File(f.getPath() + ".tmp");
        try {
            FileOutputStream out = new FileOutputStream(tmp);
            android.util.JsonWriter w = new android.util.JsonWriter(new java.io.BufferedWriter(
                    new java.io.OutputStreamWriter(out, UTF8), 65536));
            w.beginArray();
            for (Item it : list) {
                w.beginObject();
                w.name("t").value(it.text);
                w.name("d").value(it.time);
                if (it.pinned) w.name("p").value(true);
                if (it.isImage()) {
                    w.name("i").value(it.img); w.name("w").value(it.w); w.name("h").value(it.h);
                    if (it.shot) w.name("s").value(true);
                }
                w.endObject();
            }
            w.endArray();
            w.flush();
            out.getFD().sync();
            w.close();
            if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f); }
        } catch (Exception e) {
            tmp.delete();
        }
    }

    // ---------- backup (só textos) ----------

    String exportJson() { return toJson(items, false).toString(); }

    /** Junta um backup com o que já existe. Retorna quantos itens novos entraram. */
    int importJson(String json) {
        ArrayList<Item> in = parse(json);
        int added = 0;
        for (int i = in.size() - 1; i >= 0; i--) {
            Item n = in.get(i);
            if (n.isImage()) continue;
            boolean exists = false;
            for (Item it : items) if (!it.isImage() && it.text.equals(n.text)) { exists = true; if (n.pinned) it.pinned = true; break; }
            if (!exists) { items.add(n); added++; }
        }
        java.util.Collections.sort(items, new java.util.Comparator<Item>() {
            @Override public int compare(Item a, Item b) { return Long.compare(b.time, a.time); }
        });
        trim();
        changed();
        return added;
    }

    // ---------- arquivo ----------

    private static JSONArray toJson(List<Item> list, boolean withImages) {
        JSONArray a = new JSONArray();
        try {
            for (Item it : list) {
                if (it.isImage() && !withImages) continue;
                JSONObject o = new JSONObject();
                o.put("t", it.text);
                o.put("d", it.time);
                if (it.pinned) o.put("p", true);
                if (it.isImage()) { o.put("i", it.img); o.put("w", it.w); o.put("h", it.h); if (it.shot) o.put("s", true); }
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
                Item it = new Item(o.optString("t", ""), o.optLong("d", 0), o.optBoolean("p", false));
                String img = o.optString("i", "");
                if (!img.isEmpty() && img.indexOf('/') < 0) {
                    it.img = img;
                    it.w = o.optInt("w");
                    it.h = o.optInt("h");
                    it.shot = o.optBoolean("s", false);
                } else if (it.text.isEmpty()) {
                    continue;
                }
                list.add(it);
            }
        } catch (Exception ignored) { }
        return list;
    }

    /**
     * Lê o histórico aos poucos (sem carregar o arquivo inteiro de uma vez). Se o arquivo estiver
     * cortado no meio (celular desligou), aproveita tudo que dá para ler.
     */
    private static ArrayList<Item> readFile(File f) {
        ArrayList<Item> list = new ArrayList<>();
        if (!f.exists()) return list;
        android.util.JsonReader r = null;
        long total = 0;
        try {
            r = new android.util.JsonReader(new java.io.BufferedReader(
                    new java.io.InputStreamReader(new FileInputStream(f), UTF8), 65536));
            r.beginArray();
            while (r.hasNext()) {
                String t = "", img = null;
                long d = 0;
                boolean p = false, shot = false;
                int w = 0, h = 0;
                r.beginObject();
                while (r.hasNext()) {
                    String n = r.nextName();
                    if (r.peek() == android.util.JsonToken.NULL) { r.skipValue(); continue; }
                    switch (n) {
                        case "t": t = r.nextString(); break;
                        case "d": d = r.nextLong(); break;
                        case "p": p = r.nextBoolean(); break;
                        case "i": img = r.nextString(); break;
                        case "w": w = r.nextInt(); break;
                        case "h": h = r.nextInt(); break;
                        case "s": shot = r.nextBoolean(); break;
                        default: r.skipValue();
                    }
                }
                r.endObject();
                if (t.length() > MAX_CHARS) t = t.substring(0, MAX_CHARS);
                Item it = new Item(t, d, p);
                if (img != null && !img.isEmpty() && img.indexOf('/') < 0) {
                    it.img = img; it.w = w; it.h = h; it.shot = shot;
                } else if (t.isEmpty()) continue;
                if (!p && total + t.length() > MAX_TOTAL_CHARS) continue; // arquivo antigo enorme: fica só o que cabe
                total += t.length();
                list.add(it);
            }
        } catch (Throwable e) {
            // arquivo quebrado: fica com o que já leu
        } finally {
            if (r != null) try { r.close(); } catch (Exception ignored) { }
        }
        return list;
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

}
