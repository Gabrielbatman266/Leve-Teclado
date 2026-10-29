package com.leve.teclado;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Painel de emojis leve: categorias + recentes, grade simples que recicla as células. */
final class EmojiPanel extends LinearLayout {

    interface Host {
        void onEmoji(String emoji);
        void onEmojiBackspace();
        void onEmojiSpace();
        void closeEmoji();
    }

    private static final String[] TABS = {"🕘", "😀", "👋", "❤️", "🐶", "🍔", "⚽", "🚗", "💡", "🔣", "🏳️"};

    private static final String[] DATA = {
        null, // recentes
        // carinhas
        "😀 😃 😄 😁 😆 😅 🤣 😂 🙂 🙃 😉 😊 😇 🥰 😍 🤩 😘 😗 😚 😙 😋 😛 😜 🤪 😝 🤑 🤗 🤭 🤫 🤔 🤐 🤨 😐 😑 😶 😏 😒 🙄 😬 😮‍💨 🤥 😌 😔 😪 🤤 😴 😷 🤒 🤕 🤢 🤮 🥵 🥶 🥴 😵 🤯 🤠 🥳 🥸 😎 🤓 🧐 😕 😟 🙁 😮 😯 😲 😳 🥺 🥹 😦 😧 😨 😰 😥 😢 😭 😱 😖 😣 😞 😓 😩 😫 🥱 😤 😡 😠 🤬 😈 👿 💀 ☠️ 💩 🤡 👹 👺 👻 👽 👾 🤖 😺 😸 😹 😻 😼 😽 🙀 😿 😾 🙈 🙉 🙊",
        // gestos e pessoas
        "👋 🤚 🖐️ ✋ 🖖 👌 🤌 🤏 ✌️ 🤞 🫰 🤟 🤘 🤙 👈 👉 👆 🖕 👇 ☝️ 🫵 👍 👎 ✊ 👊 🤛 🤜 👏 🙌 🫶 👐 🤲 🤝 🙏 ✍️ 💅 🤳 💪 🦾 🦵 🦶 👂 👃 🧠 🫀 👀 👁️ 👅 👄 💋 👶 🧒 👦 👧 🧑 👱 👨 🧔 👩 🧓 👴 👵 🙍 🙎 🙅 🙆 💁 🙋 🧏 🙇 🤦 🤷 👮 🕵️ 💂 👷 🤴 👸 👳 🤵 👰 🤰 🤱 👼 🎅 🤶 🦸 🦹 🧙 🧚 🧛 🧜 🧝 🧞 🧟 💆 💇 🚶 🧍 🧎 🏃 💃 🕺 👯 🧘 🛀 🛌 👭 👫 👬 💏 💑 👪",
        // corações e símbolos de sentimento
        "❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 ❤️‍🔥 ❤️‍🩹 ❣️ 💕 💞 💓 💗 💖 💘 💝 💟 ♥️ 💌 💯 💢 💥 💫 💦 💨 🕳️ 💬 👁️‍🗨️ 🗨️ 🗯️ 💭 💤 ✨ 🌟 ⭐ 🔥 🎉 🎊 🎁 🎈 🏆 🥇 🥈 🥉 🎖️ 👑 💎",
        // animais e natureza
        "🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🐔 🐧 🐦 🐤 🦆 🦅 🦉 🦇 🐺 🐗 🐴 🦄 🐝 🐛 🦋 🐌 🐞 🐜 🕷️ 🦂 🐢 🐍 🦎 🦖 🦕 🐙 🦑 🦐 🦞 🦀 🐡 🐠 🐟 🐬 🐳 🐋 🦈 🐊 🐅 🐆 🦓 🦍 🐘 🦛 🦏 🐪 🦒 🦘 🐃 🐂 🐄 🐎 🐖 🐏 🐑 🦙 🐐 🦌 🐕 🐩 🐈 🐓 🦃 🦚 🦜 🦢 🕊️ 🐇 🦝 🦨 🦡 🦦 🦥 🐁 🐀 🐿️ 🦔 🌵 🎄 🌲 🌳 🌴 🌱 🌿 ☘️ 🍀 🍃 🍂 🍁 🍄 🌾 💐 🌷 🌹 🥀 🌺 🌸 🌼 🌻 🌞 🌝 🌚 🌙 🌎 🪐 ⚡ ☄️ 🌈 ☀️ ⛅ ☁️ 🌧️ ⛈️ 🌩️ ❄️ ☃️ ⛄ 🌊 💧",
        // comida
        "🍏 🍎 🍐 🍊 🍋 🍌 🍉 🍇 🍓 🫐 🍈 🍒 🍑 🥭 🍍 🥥 🥝 🍅 🍆 🥑 🥦 🥬 🥒 🌶️ 🌽 🥕 🧄 🧅 🥔 🍠 🥐 🍞 🥖 🧀 🥚 🍳 🧈 🥞 🧇 🥓 🥩 🍗 🍖 🌭 🍔 🍟 🍕 🥪 🌮 🌯 🥗 🍝 🍜 🍲 🍛 🍣 🍱 🥟 🍤 🍙 🍚 🍘 🍥 🥠 🍢 🍡 🍧 🍨 🍦 🥧 🧁 🍰 🎂 🍮 🍭 🍬 🍫 🍿 🍩 🍪 🌰 🥜 🍯 🥛 ☕ 🍵 🧃 🥤 🧋 🍶 🍺 🍻 🥂 🍷 🥃 🍸 🍹 🧉 🍾 🧊 🥄 🍴 🍽️",
        // atividades
        "⚽ 🏀 🏈 ⚾ 🥎 🎾 🏐 🏉 🥏 🎱 🏓 🏸 🏒 🏑 🥍 🏏 🥅 ⛳ 🏹 🎣 🤿 🥊 🥋 🎽 🛹 🛼 🛷 ⛸️ 🥌 🎿 ⛷️ 🏂 🏋️ 🤼 🤸 ⛹️ 🤺 🤾 🏌️ 🏇 🧘 🏄 🏊 🤽 🚣 🧗 🚵 🚴 🎮 🕹️ 🎲 ♟️ 🧩 🎯 🎳 🎤 🎧 🎼 🎹 🥁 🎷 🎺 🎸 🪕 🎻 🎬 🎨 🎭 🎪 🎟️ 🎫",
        // viagem e lugares
        "🚗 🚕 🚙 🚌 🚎 🏎️ 🚓 🚑 🚒 🚐 🛻 🚚 🚛 🚜 🏍️ 🛵 🚲 🛴 🚨 🚔 🚍 🚘 🚖 🚡 🚠 🚟 🚃 🚋 🚞 🚝 🚄 🚅 🚈 🚂 🚆 🚇 🚊 🚉 ✈️ 🛫 🛬 🛩️ 💺 🛰️ 🚀 🛸 🚁 🛶 ⛵ 🚤 🛥️ 🛳️ ⛴️ 🚢 ⚓ ⛽ 🚧 🚦 🚥 🗺️ 🗿 🗽 🗼 🏰 🏯 🏟️ 🎡 🎢 🎠 ⛲ ⛱️ 🏖️ 🏝️ 🏜️ 🌋 ⛰️ 🏔️ 🗻 🏕️ ⛺ 🏠 🏡 🏘️ 🏗️ 🏭 🏢 🏬 🏣 🏤 🏥 🏦 🏨 🏪 🏫 🏩 💒 🏛️ ⛪ 🕌 🕍 🌃 🌆 🌇 🌉",
        // objetos
        "⌚ 📱 💻 ⌨️ 🖥️ 🖨️ 🖱️ 💽 💾 💿 📀 📷 📸 📹 🎥 📞 ☎️ 📺 📻 🎙️ ⏰ ⏳ ⌛ 🔋 🔌 💡 🔦 🕯️ 🧯 💸 💵 💰 💳 🧾 ⚖️ 🔧 🔨 🛠️ ⛏️ 🔩 ⚙️ 🧲 🔫 💣 🔪 🗡️ ⚔️ 🛡️ 🔮 💊 💉 🩹 🧬 🦠 🧪 🌡️ 🧹 🧺 🧻 🚽 🚿 🛁 🧼 🪥 🔑 🗝️ 🚪 🛋️ 🛏️ 🧸 🖼️ 🛍️ 🛒 🎀 ✉️ 📩 📦 📫 📮 📝 📄 📃 📑 📊 📈 📉 📅 📆 📇 📋 📁 📂 🗂️ 📰 📓 📔 📒 📕 📗 📘 📙 📚 📖 🔖 🔗 📎 📐 📏 📌 📍 ✂️ 🖊️ 🖋️ ✒️ 🖌️ 🖍️ ✏️ 🔍 🔎 🔒 🔓",
        // símbolos
        "✅ ☑️ ✔️ ❌ ❎ ➕ ➖ ➗ ✖️ ♾️ ‼️ ⁉️ ❓ ❔ ❕ ❗ 〰️ ⚠️ 🚸 ⛔ 🚫 🔞 📵 🚭 ♻️ 🔰 🔱 ⚜️ 💲 💱 ©️ ®️ ™️ 🔝 🔜 🔛 🔙 🔚 ⬆️ ↗️ ➡️ ↘️ ⬇️ ↙️ ⬅️ ↖️ ↕️ ↔️ ↩️ ↪️ ⤴️ ⤵️ 🔀 🔁 🔂 🔄 🔃 ▶️ ⏸️ ⏹️ ⏺️ ⏭️ ⏮️ ⏩ ⏪ 🔼 🔽 🎦 🔅 🔆 📶 📳 📴 🆗 🆕 🆓 🆒 🆙 🆘 🅰️ 🅱️ 🆎 🅾️ 🔴 🟠 🟡 🟢 🔵 🟣 ⚫ ⚪ 🟤 🟥 🟧 🟨 🟩 🟦 🟪 ⬛ ⬜ 🔶 🔷 🔸 🔹 🔺 🔻 💠 🔘 🔳 🔲 0️⃣ 1️⃣ 2️⃣ 3️⃣ 4️⃣ 5️⃣ 6️⃣ 7️⃣ 8️⃣ 9️⃣ 🔟 #️⃣ *️⃣",
        // bandeiras
        "🏳️ 🏴 🏁 🚩 🏳️‍🌈 🇧🇷 🇵🇹 🇺🇸 🇦🇷 🇺🇾 🇵🇾 🇨🇱 🇧🇴 🇵🇪 🇨🇴 🇻🇪 🇲🇽 🇨🇦 🇬🇧 🇮🇪 🇫🇷 🇪🇸 🇮🇹 🇩🇪 🇳🇱 🇧🇪 🇨🇭 🇦🇹 🇸🇪 🇳🇴 🇩🇰 🇫🇮 🇵🇱 🇺🇦 🇷🇺 🇹🇷 🇬🇷 🇯🇵 🇰🇷 🇨🇳 🇮🇳 🇦🇴 🇲🇿 🇨🇻 🇳🇬 🇿🇦 🇪🇬 🇲🇦 🇦🇺 🇳🇿 🇮🇱 🇸🇦 🇦🇪",
    };

    private static final int RECENT_MAX = 40;

    private final Host host;
    private final float dp;
    private final SharedPreferences sp;
    private final LinearLayout tabRow, bottomRow;
    private final GridView grid;
    private final Adapter adapter = new Adapter();
    private final List<String> shown = new ArrayList<>();
    private final ArrayList<String> recents = new ArrayList<>();
    private int tab = 1;
    private Theme theme = Theme.of(0);

    EmojiPanel(Context c, Host host) {
        super(c);
        this.host = host;
        dp = c.getResources().getDisplayMetrics().density;
        sp = Prefs.sp(c);
        setOrientation(VERTICAL);

        for (String e : sp.getString("emojiRecents", "").split(" ")) if (!e.isEmpty()) recents.add(e);
        if (!recents.isEmpty()) tab = 0;

        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        tabRow = new LinearLayout(c);
        tabRow.setOrientation(HORIZONTAL);
        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            TextView t = new TextView(c);
            t.setText(TABS[i]);
            t.setTextSize(20);
            t.setGravity(Gravity.CENTER);
            int p = (int) (10 * dp);
            t.setPadding(p, (int) (6 * dp), p, (int) (6 * dp));
            t.setOnClickListener(new OnClickListener() {
                @Override public void onClick(View v) { selectTab(idx); }
            });
            tabRow.addView(t);
        }
        hs.addView(tabRow);
        addView(hs, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        grid = new GridView(c);
        grid.setAdapter(adapter);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setSelector(new android.graphics.drawable.ColorDrawable(0));
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                String e = shown.get(pos);
                addRecent(e);
                EmojiPanel.this.host.onEmoji(e);
            }
        });
        addView(grid, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));

        bottomRow = new LinearLayout(c);
        bottomRow.setOrientation(HORIZONTAL);
        addView(bottomRow, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        bottomKey("ABC", 1.3f, new Runnable() { public void run() { EmojiPanel.this.host.closeEmoji(); } }, false);
        bottomKey("espaço", 4f, new Runnable() { public void run() { EmojiPanel.this.host.onEmojiSpace(); } }, false);
        bottomKey("⌫", 1.3f, new Runnable() { public void run() { EmojiPanel.this.host.onEmojiBackspace(); } }, true);

        selectTab(tab);
    }

    private void bottomKey(String label, float w, final Runnable r, boolean repeat) {
        final TextView t = new TextView(getContext());
        t.setText(label);
        t.setTextSize(16);
        t.setGravity(Gravity.CENTER);
        int p = (int) (12 * dp);
        t.setPadding(0, p, 0, p);
        LayoutParams lp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, w);
        int m = (int) (3 * dp);
        lp.setMargins(m, m, m, m);
        bottomRow.addView(t, lp);
        if (!repeat) {
            t.setOnClickListener(new OnClickListener() { @Override public void onClick(View v) { r.run(); } });
            return;
        }
        // ⌫ segurado apaga repetido
        final Runnable[] loop = new Runnable[1];
        loop[0] = new Runnable() { @Override public void run() { r.run(); t.postDelayed(loop[0], 60); } };
        t.setOnTouchListener(new OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        r.run();
                        t.postDelayed(loop[0], 400);
                        t.setPressed(true);
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        t.removeCallbacks(loop[0]);
                        t.setPressed(false);
                        return true;
                }
                return true;
            }
        });
    }

    void applyTheme(Theme t) {
        theme = t;
        setBackgroundColor(t.bg);
        grid.setColumnWidth((int) (t.cellDp * dp));
        grid.setNumColumns(GridView.AUTO_FIT);
        for (int i = 0; i < bottomRow.getChildCount(); i++) {
            TextView v = (TextView) bottomRow.getChildAt(i);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(t.radiusDp * dp);
            g.setColor(t.keySpecial);
            if (t.outlined()) g.setStroke((int) dp, t.textDim);
            v.setBackground(g);
            v.setTextColor(t.text);
            v.setMinHeight((int) (Math.max(44, t.minKeyHeightDp - 6) * dp));
        }
        styleTabs();
        adapter.notifyDataSetChanged();
    }

    private void styleTabs() {
        for (int i = 0; i < tabRow.getChildCount(); i++) {
            View v = tabRow.getChildAt(i);
            if (i == tab) {
                GradientDrawable g = new GradientDrawable();
                g.setCornerRadius(12 * dp);
                g.setColor(theme.keyPressed);
                v.setBackground(g);
            } else {
                v.setBackground(null);
            }
        }
    }

    private void selectTab(int i) {
        tab = i;
        shown.clear();
        if (i == 0) shown.addAll(recents);
        else shown.addAll(Arrays.asList(DATA[i].split(" ")));
        styleTabs();
        adapter.notifyDataSetChanged();
        grid.setSelection(0);
    }

    private void addRecent(String e) {
        recents.remove(e);
        recents.add(0, e);
        while (recents.size() > RECENT_MAX) recents.remove(recents.size() - 1);
        StringBuilder b = new StringBuilder();
        for (String r : recents) { if (b.length() > 0) b.append(' '); b.append(r); }
        sp.edit().putString("emojiRecents", b.toString()).apply();
    }

    private final class Adapter extends BaseAdapter {
        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int i) { return shown.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int pos, View convert, ViewGroup parent) {
            TextView t = (TextView) convert;
            if (t == null) {
                t = new TextView(getContext());
                t.setGravity(Gravity.CENTER);
            }
            t.setLayoutParams(new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    (int) (theme.cellDp * dp)));
            t.setTextSize(theme.big ? 30 : 26);
            t.setText(shown.get(pos));
            return t;
        }
    }
}
