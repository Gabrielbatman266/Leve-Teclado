package com.leve.teclado;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Locale;

/** Painel da área de transferência: histórico grande, salvos, busca. */
final class ClipPanel extends LinearLayout implements ClipStore.Listener {

    interface Host {
        void pasteClip(String text);
        void copyToSystem(String text);
        void startSearch();
        void closePanel();
        void onClipDeleted(String text);
    }

    private final ClipStore store;
    private final Host host;
    private final float dp;
    private Theme theme = Theme.of(0);

    private boolean savedTab;
    private String query = "";
    private final ArrayList<ClipStore.Item> shown = new ArrayList<>();

    private final TextView tabHistory, tabSaved, search, clear, close, empty;
    private final ListView list;
    private final Adapter adapter = new Adapter();
    private boolean confirmClear;

    ClipPanel(Context c, ClipStore store, Host host) {
        super(c);
        this.store = store;
        this.host = host;
        dp = c.getResources().getDisplayMetrics().density;
        setOrientation(VERTICAL);

        LinearLayout header = new LinearLayout(c);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        int pad = (int) (4 * dp);
        header.setPadding(pad, pad, pad, pad);

        tabHistory = chip("Histórico");
        tabSaved = chip("★ Salvos");
        search = chip("🔎 Buscar");
        clear = chip("Limpar");
        close = chip("⌨");
        search.setSingleLine(true);
        search.setEllipsize(TextUtils.TruncateAt.START);

        header.addView(tabHistory, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        header.addView(tabSaved, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        header.addView(search, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        header.addView(clear, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        header.addView(close, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        addView(header, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

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

        tabHistory.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { savedTab = false; refresh(); }
        });
        tabSaved.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { savedTab = true; refresh(); }
        });
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
                    clear.setText("Confirmar?");
                    postDelayed(new Runnable() {
                        @Override public void run() { confirmClear = false; clear.setText("Limpar"); }
                    }, 2500);
                } else {
                    confirmClear = false;
                    clear.setText("Limpar");
                    ClipPanel.this.store.clearUnpinned();
                    ClipPanel.this.host.onClipDeleted(null);
                }
            }
        });
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                ClipPanel.this.host.pasteClip(shown.get(pos).text);
            }
        });
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) {
                ClipPanel.this.host.copyToSystem(shown.get(pos).text);
                return true;
            }
        });
    }

    private TextView chip(String text) {
        TextView t = new TextView(getContext());
        t.setText(text);
        t.setTextSize(14);
        t.setGravity(Gravity.CENTER);
        int h = (int) (12 * dp), v = (int) (8 * dp);
        t.setPadding(h, v, h, v);
        LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        t.setLayoutParams(lp);
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
        TextView[] all = {tabHistory, tabSaved, search, clear, close};
        for (TextView t : all) {
            boolean on = (t == tabHistory && !savedTab) || (t == tabSaved && savedTab)
                    || (t == search && !query.isEmpty());
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(16 * dp);
            g.setColor(on ? theme.accent : theme.key);
            if (theme.outlined()) g.setStroke((int) dp, theme.textDim);
            t.setBackground(g);
            t.setTextColor(on ? 0xFF000000 : theme.text);
            MarginLayoutParams lp = (MarginLayoutParams) t.getLayoutParams();
            lp.setMargins((int) (3 * dp), 0, (int) (3 * dp), 0);
        }
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
        String q = query.toLowerCase(Locale.ROOT);
        for (ClipStore.Item it : store.items) {
            if (savedTab && !it.pinned) continue;
            if (!q.isEmpty() && !it.text.toLowerCase(Locale.ROOT).contains(q)) continue;
            shown.add(it);
        }
        if (shown.isEmpty()) {
            empty.setVisibility(VISIBLE);
            if (!store.isLoaded()) empty.setText("Carregando…");
            else if (!q.isEmpty()) empty.setText("Nada encontrado para \"" + query + "\"");
            else if (savedTab) empty.setText("Nenhum texto salvo.\nToque na ☆ de um item do histórico para salvar.");
            else empty.setText("Nada copiado ainda.\nTudo que você copiar aparece aqui.");
        } else {
            empty.setVisibility(GONE);
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
        super.onDetachedFromWindow();
    }

    // ------------------------------------------------------------- lista

    private static final class Holder {
        TextView text, meta, pin, del;
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

                LinearLayout col = new LinearLayout(c);
                col.setOrientation(VERTICAL);
                h = new Holder();
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
                    @Override public void onClick(View v) { store.togglePin(((Holder) ((View) v.getParent()).getTag()).item); }
                });
                h.del.setOnClickListener(new OnClickListener() {
                    @Override public void onClick(View v) {
                        ClipStore.Item it = ((Holder) ((View) v.getParent()).getTag()).item;
                        if (it == null) return;
                        store.remove(it);
                        host.onClipDeleted(it.text);
                    }
                });
                row.setTag(h);
                convert = row;
            } else {
                h = (Holder) convert.getTag();
            }
            ClipStore.Item it = shown.get(pos);
            h.item = it;
            String t = it.text;
            if (t.length() > 400) t = t.substring(0, 400);
            h.text.setText(t.replace('\n', '⏎'));
            h.text.setTextColor(theme.text);
            String when = it.time > 0 ? DateUtils.getRelativeTimeSpanString(it.time).toString() : "";
            h.meta.setText(when + " · " + it.text.length() + " caracteres");
            h.meta.setTextColor(theme.textDim);
            h.pin.setText(it.pinned ? "★" : "☆");
            h.pin.setTextColor(it.pinned ? theme.accent : theme.textDim);
            h.del.setTextColor(theme.textDim);
            h.text.setTypeface(it.pinned ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            return convert;
        }
    }
}
