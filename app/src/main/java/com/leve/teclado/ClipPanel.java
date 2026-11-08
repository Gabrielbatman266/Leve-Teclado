package com.leve.teclado;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Painel da área de transferência: abas (Tudo, Textos, Links, Imagens, Salvos), busca, miniaturas. */
final class ClipPanel extends LinearLayout implements ClipStore.Listener {

    interface Host {
        void pasteItem(ClipStore.Item item);
        void copyToSystem(ClipStore.Item item);
        void startSearch();
        void closePanel();
        void onClipDeleted(ClipStore.Item item);
        void askScreenshotPermission();
    }

    private static final int TAB_ALL = 0, TAB_TEXT = 1, TAB_LINK = 2, TAB_IMAGE = 3, TAB_SAVED = 4;
    private static final String[] TAB_NAMES = {"Tudo", "Textos", "🔗 Links", "🖼 Imagens", "★ Salvos"};

    private final ClipStore store;
    private final Host host;
    private final float dp;
    private Theme theme = Theme.of(0);

    private int tab = TAB_ALL;
    private String query = "";
    private final ArrayList<ClipStore.Item> shown = new ArrayList<>();

    private final TextView[] tabs = new TextView[TAB_NAMES.length];
    private final int[] counts = new int[TAB_NAMES.length];
    private final TextView search, clear, close, empty, permHint;
    private final ListView list;
    private final Adapter adapter = new Adapter();
    private boolean confirmClear;

    // miniaturas: decodificadas em segundo plano, pequenas, com cache limitado
    private final LruCache<String, Bitmap> thumbs = new LruCache<String, Bitmap>(6 * 1024 * 1024) {
        @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
    };
    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    ClipPanel(Context c, ClipStore store, Host host) {
        super(c);
        this.store = store;
        this.host = host;
        dp = c.getResources().getDisplayMetrics().density;
        setOrientation(VERTICAL);
        int pad = (int) (4 * dp);

        // linha 1: abas (rolam para o lado se não couberem)
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout tabRow = new LinearLayout(c);
        tabRow.setOrientation(HORIZONTAL);
        tabRow.setPadding(pad, pad, pad, 0);
        for (int i = 0; i < TAB_NAMES.length; i++) {
            final int idx = i;
            tabs[i] = chip(TAB_NAMES[i]);
            tabs[i].setOnClickListener(new OnClickListener() {
                @Override public void onClick(View v) { tab = idx; cancelClear(); refresh(); list.setSelection(0); }
            });
            tabRow.addView(tabs[i]);
        }
        hs.addView(tabRow);
        addView(hs, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        // linha 2: busca, limpar, voltar ao teclado
        LinearLayout header = new LinearLayout(c);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(pad, pad, pad, pad);
        search = chip("🔎 Buscar");
        clear = chip("Limpar");
        close = chip("⌨");
        search.setSingleLine(true);
        search.setEllipsize(TextUtils.TruncateAt.START);
        search.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        header.addView(search, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        header.addView(clear, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        header.addView(close, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        addView(header, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        permHint = new TextView(c);
        permHint.setText("📸 Toque aqui para os prints de tela aparecerem nesta aba");
        permHint.setGravity(Gravity.CENTER);
        permHint.setTextSize(15);
        permHint.setPadding(pad * 3, pad * 3, pad * 3, pad * 3);
        permHint.setVisibility(GONE);
        permHint.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { ClipPanel.this.host.askScreenshotPermission(); }
        });
        LayoutParams ph = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        ph.setMargins(pad * 2, pad, pad * 2, pad);
        addView(permHint, ph);

        empty = new TextView(c);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(pad * 4, pad * 4, pad * 4, pad * 4);
        empty.setVisibility(GONE);
        addView(empty, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        list = new ListView(c);
        list.setAdapter(adapter);
        list.setDividerHeight((int) dp);
        // sem barrinha de rolagem rápida: ela ficava em cima do ✕ e roubava o toque
        list.setFastScrollEnabled(false);
        list.setVerticalScrollBarEnabled(false);
        addView(list, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));

        search.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { ClipPanel.this.host.startSearch(); }
        });
        search.setOnLongClickListener(new OnLongClickListener() {
            @Override public boolean onLongClick(View v) { setQuery(""); return true; }
        });
        close.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { ClipPanel.this.host.closePanel(); }
        });
        clear.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                if (!confirmClear) {
                    confirmClear = true;
                    clear.setText(tab == TAB_ALL ? "Limpar tudo?" : "Limpar aba?");
                    postDelayed(clearTimeout, 2500);
                } else {
                    cancelClear();
                    ClipPanel.this.store.clearUnpinned(kindOf(tab));
                    ClipPanel.this.host.onClipDeleted(null);
                }
            }
        });
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                ClipPanel.this.host.pasteItem(shown.get(pos));
            }
        });
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) {
                ClipPanel.this.host.copyToSystem(shown.get(pos));
                return true;
            }
        });
    }

    private final Runnable clearTimeout = new Runnable() {
        @Override public void run() { confirmClear = false; clear.setText("Limpar"); }
    };

    private void cancelClear() {
        removeCallbacks(clearTimeout);
        confirmClear = false;
        clear.setText("Limpar");
    }

    private static int kindOf(int tab) {
        switch (tab) {
            case TAB_TEXT: return ClipStore.KIND_TEXT;
            case TAB_LINK: return ClipStore.KIND_LINK;
            case TAB_IMAGE: return ClipStore.KIND_IMAGE;
            default: return ClipStore.KIND_ALL;
        }
    }

    private TextView chip(String text) {
        TextView t = new TextView(getContext());
        t.setText(text);
        t.setTextSize(14);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        int h = (int) (12 * dp), v = (int) (8 * dp);
        t.setPadding(h, v, h, v);
        t.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        return t;
    }

    void applyTheme(Theme t) {
        theme = t;
        setBackgroundColor(t.bg);
        list.setDivider(new android.graphics.drawable.ColorDrawable(t.keySpecial));
        list.setDividerHeight((int) dp);
        empty.setTextColor(t.textDim);
        styleChips();
        adapter.notifyDataSetChanged();
    }

    private void styleChips() {
        for (int i = 0; i < tabs.length; i++) {
            String label = TAB_NAMES[i] + (counts[i] > 0 && i != TAB_ALL ? "  " + counts[i] : "");
            tabs[i].setText(label);
            style(tabs[i], i == tab);
        }
        style(search, !query.isEmpty());
        style(clear, confirmClear);
        style(close, false);
        clear.setVisibility(tab == TAB_SAVED ? GONE : VISIBLE);
    }

    private void style(TextView t, boolean on) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(16 * dp);
        g.setColor(on ? theme.accent : (theme.big ? theme.keySpecial : theme.key));
        if (theme.outlined()) g.setStroke((int) dp, theme.textDim);
        t.setBackground(g);
        t.setTextColor(on ? 0xFF000000 : theme.text);
        t.setTextSize(theme.big ? 16 : 14);
        MarginLayoutParams lp = (MarginLayoutParams) t.getLayoutParams();
        lp.setMargins((int) (3 * dp), 0, (int) (3 * dp), 0);
    }

    void setQuery(String q) {
        query = q == null ? "" : q;
        search.setText(query.isEmpty() ? "🔎 Buscar" : "🔎 " + query + "▏");
        refresh();
    }

    String getQuery() { return query; }

    void showSearchCursor(boolean typing) {
        search.setText(query.isEmpty() ? (typing ? "🔎 digite…▏" : "🔎 Buscar") : "🔎 " + query + (typing ? "▏" : ""));
    }

    @Override public void onClipsChanged() { refresh(); }

    void refresh() {
        shown.clear();
        for (int i = 0; i < counts.length; i++) counts[i] = 0;
        String q = query.toLowerCase(Locale.ROOT);
        for (ClipStore.Item it : store.items) {
            counts[TAB_ALL]++;
            if (it.isImage()) counts[TAB_IMAGE]++; else counts[TAB_TEXT]++;
            if (it.isLink()) counts[TAB_LINK]++;
            if (it.pinned) counts[TAB_SAVED]++;

            boolean inTab = tab == TAB_SAVED ? it.pinned : it.matches(kindOf(tab));
            if (!inTab) continue;
            if (!q.isEmpty() && (it.isImage() || !it.text.toLowerCase(Locale.ROOT).contains(q))) continue;
            shown.add(it);
        }
        if (shown.isEmpty()) {
            empty.setVisibility(VISIBLE);
            if (!store.isLoaded()) empty.setText("Carregando…");
            else if (!q.isEmpty()) empty.setText("Nada encontrado para \"" + query + "\"");
            else if (tab == TAB_SAVED) empty.setText("Nada salvo ainda.\nToque na ☆ de um item para salvar.");
            else if (tab == TAB_IMAGE) empty.setText("Nenhuma imagem ainda.\nCopie uma imagem ou tire um print e ela aparece aqui.");
            else if (tab == TAB_LINK) empty.setText("Nenhum link copiado ainda.");
            else empty.setText("Nada copiado ainda.\nTudo que você copiar aparece aqui.");
        } else {
            empty.setVisibility(GONE);
        }
        // aviso clicável quando os prints não estão liberados
        boolean needPerm = tab == TAB_IMAGE && !ScreenshotWatcher.hasPermission(getContext());
        permHint.setVisibility(needPerm ? VISIBLE : GONE);
        if (needPerm) {
            permHint.setTextColor(0xFF000000);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(12 * dp);
            g.setColor(theme.accent);
            permHint.setBackground(g);
        }
        styleChips();
        adapter.notifyDataSetChanged();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        store.addListener(this);
        refresh();
    }

    @Override
    protected void onDetachedFromWindow() {
        store.removeListener(this);
        thumbs.evictAll(); // libera memória quando o painel some
        super.onDetachedFromWindow();
    }

    // ------------------------------------------------------------- miniaturas

    private void loadThumb(final ImageView iv, final String name, final int sizePx) {
        iv.setTag(name);
        Bitmap b = thumbs.get(name);
        if (b != null) { iv.setImageBitmap(b); return; }
        iv.setImageDrawable(null);
        final File f = new File(store.imgDir, name);
        decoder.execute(new Runnable() {
            @Override public void run() {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(f.getPath(), o);
                int s = 1;
                while (o.outWidth / (s * 2) >= sizePx && o.outHeight / (s * 2) >= sizePx) s *= 2;
                o = new BitmapFactory.Options();
                o.inSampleSize = s;
                o.inPreferredConfig = Bitmap.Config.RGB_565;
                final Bitmap bm = BitmapFactory.decodeFile(f.getPath(), o);
                if (bm == null) return;
                main.post(new Runnable() {
                    @Override public void run() {
                        thumbs.put(name, bm);
                        if (name.equals(iv.getTag())) iv.setImageBitmap(bm);
                    }
                });
            }
        });
    }

    // ------------------------------------------------------------- lista

    private static final class Holder {
        TextView text, meta, pin, del;
        ImageView thumb;
        ClipStore.Item item;
    }

    private final class Adapter extends BaseAdapter {
        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int i) { return shown.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int pos, View convert, ViewGroup parent) {
            final Holder h;
            if (convert == null) {
                Context c = getContext();
                LinearLayout row = new LinearLayout(c);
                row.setOrientation(HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                int p = (int) (8 * dp);
                row.setPadding(p + p / 2, p, p + p / 2, p); // margem à direita, longe da borda

                h = new Holder();
                h.thumb = new ImageView(c);
                h.thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
                h.thumb.setClipToOutline(true);
                LayoutParams tl = new LayoutParams(0, 0);
                tl.rightMargin = (int) (10 * dp);
                row.addView(h.thumb, tl);

                LinearLayout col = new LinearLayout(c);
                col.setOrientation(VERTICAL);
                h.text = new TextView(c);
                h.text.setMaxLines(3);
                h.text.setEllipsize(TextUtils.TruncateAt.END);
                h.text.setTextSize(15);
                h.meta = new TextView(c);
                h.meta.setTextSize(11);
                col.addView(h.text);
                col.addView(h.meta);
                row.addView(col, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

                h.pin = new TextView(c);
                h.pin.setTextSize(20);
                h.pin.setPadding(p + p / 2, p, p + p / 2, p);
                h.del = new TextView(c);
                h.del.setTextSize(20);
                h.del.setGravity(Gravity.CENTER);
                h.del.setMinWidth((int) (48 * dp));
                h.del.setMinHeight((int) (48 * dp));
                h.del.setPadding(p, p, p, p);
                h.del.setText("✕");
                row.addView(h.pin);
                row.addView(h.del);

                h.pin.setOnClickListener(new OnClickListener() {
                    @Override public void onClick(View v) {
                        ClipStore.Item it = ((Holder) ((View) v.getParent()).getTag()).item;
                        if (it != null) store.togglePin(it);
                    }
                });
                h.del.setOnClickListener(new OnClickListener() {
                    @Override public void onClick(View v) {
                        ClipStore.Item it = ((Holder) ((View) v.getParent()).getTag()).item;
                        if (it == null) return;
                        store.remove(it);
                        host.onClipDeleted(it);
                    }
                });
                row.setTag(h);
                convert = row;
            } else {
                h = (Holder) convert.getTag();
            }
            ClipStore.Item it = shown.get(pos);
            h.item = it;
            String when = it.time > 0 ? DateUtils.getRelativeTimeSpanString(it.time).toString() : "";

            if (it.isImage()) {
                // imagens: miniatura grande na aba Imagens, média nas outras
                int size = (int) ((tab == TAB_IMAGE ? 96 : 64) * dp);
                LayoutParams tl = (LayoutParams) h.thumb.getLayoutParams();
                tl.width = size;
                tl.height = size;
                h.thumb.setLayoutParams(tl);
                GradientDrawable g = new GradientDrawable();
                g.setCornerRadius(8 * dp);
                g.setColor(theme.keySpecial);
                h.thumb.setBackground(g);
                h.thumb.setVisibility(VISIBLE);
                loadThumb(h.thumb, it.img, size);
                h.text.setText(it.shot ? "📸 Print de tela" : "Imagem");
                h.meta.setText(when + (it.w > 0 ? " · " + it.w + "×" + it.h : ""));
            } else {
                h.thumb.setVisibility(GONE);
                h.thumb.setTag(null);
                String t = it.text;
                if (t.length() > 400) t = t.substring(0, 400);
                h.text.setText((it.isLink() ? "🔗 " : "") + t.replace('\n', '⏎'));
                h.meta.setText(when + " · " + it.text.length() + " caracteres");
            }
            h.text.setTextColor(theme.text);
            h.meta.setTextColor(theme.textDim);
            h.pin.setText(it.pinned ? "★" : "☆");
            h.pin.setTextColor(it.pinned ? theme.accent : theme.textDim);
            h.del.setTextColor(theme.textDim);
            h.text.setTypeface(it.pinned ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            return convert;
        }
    }
}
