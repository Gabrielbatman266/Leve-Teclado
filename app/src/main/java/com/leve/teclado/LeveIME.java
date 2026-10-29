package com.leve.teclado;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class LeveIME extends InputMethodService
        implements KeyboardView.Listener, ClipPanel.Host, ClipboardManager.OnPrimaryClipChangedListener,
        ClipStore.Listener {

    private static final int MODE_KEYS = 0, MODE_CLIP = 1, MODE_SEARCH = 2;

    private Prefs prefs;
    private Theme theme;
    private ClipStore store;
    private ClipboardManager clipboard;
    private float dp;

    private LinearLayout root, toolbarRow;
    private HorizontalScrollView toolbar;
    private KeyboardView keyboard;
    private ClipPanel panel;
    private TextView latestChip, selChip;

    private int mode = MODE_KEYS;
    private int layer = Layouts.LETTERS;
    private int shiftState;
    private long lastShiftTap, lastSpaceTime;
    private boolean selectMode, passwordField;
    private int selStart, selEnd;

    // ------------------------------------------------------------ ciclo de vida

    @Override
    public void onCreate() {
        super.onCreate();
        dp = getResources().getDisplayMetrics().density;
        prefs = Prefs.load(this);
        theme = Theme.of(prefs.theme);
        store = ClipStore.get(this);
        store.max = prefs.historyMax;
        store.addListener(this);
        clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) clipboard.addPrimaryClipChangedListener(this);
    }

    @Override
    public void onDestroy() {
        if (clipboard != null) clipboard.removePrimaryClipChangedListener(this);
        store.removeListener(this);
        super.onDestroy();
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        return false; // nunca a tela cheia de paisagem: mais leve e você vê o app
    }

    @Override
    public View onCreateInputView() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        toolbar = new HorizontalScrollView(this);
        toolbar.setHorizontalScrollBarEnabled(false);
        toolbarRow = new LinearLayout(this);
        toolbarRow.setOrientation(LinearLayout.HORIZONTAL);
        toolbarRow.setGravity(Gravity.CENTER_VERTICAL);
        int p = (int) (3 * dp);
        toolbarRow.setPadding(p, p, p, p);
        toolbar.addView(toolbarRow);
        buildToolbar();
        root.addView(toolbar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        panel = new ClipPanel(this, store, this);
        panel.setVisibility(View.GONE);
        root.addView(panel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0));

        keyboard = new KeyboardView(this);
        keyboard.setListener(this);
        root.addView(keyboard, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        applyPrefs();
        return root;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        prefs = Prefs.load(this);
        store.setMax(prefs.historyMax);
        int cls = info.inputType & InputType.TYPE_MASK_CLASS;
        int variation = info.inputType & InputType.TYPE_MASK_VARIATION;
        passwordField = variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
                || (cls == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        if (!restarting || layer == Layouts.NUMPAD) {
            layer = (cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE)
                    ? Layouts.NUMPAD : Layouts.LETTERS;
        }
        if (cls == InputType.TYPE_CLASS_DATETIME) layer = Layouts.SYMBOLS;
        shiftState = 0;
        selectMode = false;
        selStart = info.initialSelStart;
        selEnd = info.initialSelEnd;
        applyPrefs();
        setMode(MODE_KEYS);
        updateEnterLabel(info);
        autoShift();
        captureCurrentClip();
    }

    @Override
    public void onFinishInputView(boolean finishing) {
        super.onFinishInputView(finishing);
        if (keyboard != null) keyboard.cancelTouch();
    }

    private void applyPrefs() {
        if (root == null) return;
        theme = Theme.of(prefs.theme);
        root.setBackgroundColor(theme.bg);
        toolbar.setVisibility(prefs.showToolbar ? View.VISIBLE : View.GONE);
        styleToolbar();
        panel.applyTheme(theme);
        keyboard.configure(prefs, Layouts.build(layer, prefs.numberRow));
        keyboard.setShiftState(shiftState);
        refreshLatestChip();
    }

    // ------------------------------------------------------------ barra de ferramentas

    private void buildToolbar() {
        tool("📋", new Runnable() { public void run() { setMode(mode == MODE_KEYS ? MODE_CLIP : MODE_KEYS); } });
        latestChip = tool("📎", new Runnable() { public void run() { pasteLatest(); } });
        tool("Tudo", new Runnable() { public void run() { menu(android.R.id.selectAll); } });
        tool("Copiar", new Runnable() { public void run() { copy(); } });
        tool("Recortar", new Runnable() { public void run() { cut(); } });
        tool("Colar", new Runnable() { public void run() { pasteLatest(); } });
        tool("◀", new Runnable() { public void run() { onCursor(-1); } });
        tool("▶", new Runnable() { public void run() { onCursor(1); } });
        selChip = tool("Selecionar", new Runnable() {
            public void run() { selectMode = !selectMode; styleToolbar(); }
        });
        tool("⇤", new Runnable() { public void run() { key(KeyEvent.KEYCODE_MOVE_HOME, shiftMeta()); } });
        tool("⇥", new Runnable() { public void run() { key(KeyEvent.KEYCODE_MOVE_END, shiftMeta()); } });
        tool("↶", new Runnable() {
            public void run() { key(KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON); }
        });
        tool("↷", new Runnable() {
            public void run() {
                key(KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON
                        | KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON);
            }
        });
        tool("🌐", new Runnable() { public void run() { showPicker(); } });
        tool("⚙", new Runnable() {
            public void run() {
                Intent i = new Intent(LeveIME.this, SettingsActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            }
        });
        latestChip.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { setMode(MODE_CLIP); return true; }
        });
    }

    private TextView tool(String label, final Runnable action) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(14);
        t.setSingleLine(true);
        t.setGravity(Gravity.CENTER);
        int h = (int) (11 * dp), v = (int) (7 * dp);
        t.setPadding(h, v, h, v);
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                if (prefs.haptic) view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP,
                        android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                action.run();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins((int) (2 * dp), 0, (int) (2 * dp), 0);
        toolbarRow.addView(t, lp);
        return t;
    }

    private void styleToolbar() {
        if (toolbarRow == null) return;
        toolbar.setBackgroundColor(theme.bar);
        for (int i = 0; i < toolbarRow.getChildCount(); i++) {
            TextView t = (TextView) toolbarRow.getChildAt(i);
            boolean on = (t == selChip && selectMode) || (i == 0 && mode != MODE_KEYS);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(14 * dp);
            g.setColor(on ? theme.accent : theme.keySpecial);
            if (t == latestChip && !on) g.setStroke((int) dp, theme.accent);
            else if (theme.outlined()) g.setStroke((int) dp, theme.textDim);
            t.setBackground(g);
            t.setTextColor(on ? 0xFF000000 : theme.barText);
        }
    }

    private void refreshLatestChip() {
        if (latestChip == null) return;
        String s = store.latest();
        if (s == null) {
            latestChip.setText("📎 vazio");
            return;
        }
        s = s.replace('\n', ' ').trim();
        if (s.length() > 22) s = s.substring(0, 22) + "…";
        latestChip.setText("📎 " + s);
    }

    // ------------------------------------------------------------ modos

    private void setMode(int m) {
        mode = m;
        if (panel == null) return;
        int kbH = keyboard.computeHeight();
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) panel.getLayoutParams();
        if (m == MODE_KEYS) {
            panel.setVisibility(View.GONE);
            keyboard.setVisibility(View.VISIBLE);
            if (panel.getQuery().length() > 0) panel.setQuery("");
        } else if (m == MODE_CLIP) {
            lp.height = Math.max(kbH, (int) (220 * dp));
            panel.setVisibility(View.VISIBLE);
            keyboard.setVisibility(View.GONE);
            panel.showSearchCursor(false);
            panel.refresh();
        } else {
            lp.height = (int) (210 * dp);
            panel.setVisibility(View.VISIBLE);
            keyboard.setVisibility(View.VISIBLE);
            if (layer == Layouts.NUMPAD) { layer = Layouts.LETTERS; keyboard.configure(prefs, Layouts.build(layer, prefs.numberRow)); }
            panel.showSearchCursor(true);
        }
        panel.setLayoutParams(lp);
        styleToolbar();
    }

    // ClipPanel.Host
    @Override public void pasteClip(String text) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.commitText(text, 1);
        if (prefs.backAfterPaste) setMode(MODE_KEYS);
    }

    @Override public void copyToSystem(String text) {
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText("Leve Teclado", text));
        toast("Copiado");
    }

    @Override public void startSearch() { setMode(MODE_SEARCH); }

    @Override public void closePanel() { setMode(MODE_KEYS); }

    // ------------------------------------------------------------ área de transferência

    @Override
    public void onPrimaryClipChanged() {
        captureCurrentClip();
    }

    private void captureCurrentClip() {
        if (clipboard == null || prefs.pauseHistory) return;
        try {
            ClipData clip = clipboard.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) return;
            CharSequence t = clip.getItemAt(0).coerceToText(this);
            if (t != null && !(passwordField && isInputViewShown())) store.add(t.toString());
        } catch (Exception ignored) { } // alguns sistemas bloqueiam leitura em segundo plano
    }

    @Override public void onClipsChanged() { refreshLatestChip(); }

    private void pasteLatest() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        String text = null;
        try {
            ClipData clip = clipboard != null ? clipboard.getPrimaryClip() : null;
            if (clip != null && clip.getItemCount() > 0) {
                CharSequence t = clip.getItemAt(0).coerceToText(this);
                if (t != null) text = t.toString();
            }
        } catch (Exception ignored) { }
        if (text == null || text.isEmpty()) text = store.latest();
        if (text != null && !text.isEmpty()) ic.commitText(text, 1);
        else ic.performContextMenuAction(android.R.id.paste);
    }

    private void copy() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        if (selStart != selEnd) {
            ic.performContextMenuAction(android.R.id.copy);
            CharSequence sel = ic.getSelectedText(0);
            if (sel != null) store.add(sel.toString());
            toast("Copiado");
            return;
        }
        // nada selecionado: copia o campo inteiro sem mexer no cursor
        ExtractedText et = ic.getExtractedText(new ExtractedTextRequest(), 0);
        if (et != null && et.text != null && et.text.length() > 0 && clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("Leve Teclado", et.text));
            store.add(et.text.toString());
            toast("Campo inteiro copiado");
        } else {
            toast("Nada para copiar");
        }
    }

    private void cut() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        if (selStart == selEnd) { toast("Selecione um texto (botão Tudo ou Selecionar)"); return; }
        CharSequence sel = ic.getSelectedText(0);
        if (sel != null) store.add(sel.toString());
        ic.performContextMenuAction(android.R.id.cut);
    }

    // ------------------------------------------------------------ teclas

    @Override
    public void onText(String text) {
        if (mode == MODE_SEARCH) {
            panel.setQuery(panel.getQuery() + text);
            panel.showSearchCursor(true);
            afterChar();
            return;
        }
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        // pontuação logo após o espaço automático do ponto duplo: cola na palavra
        ic.commitText(text, 1);
        afterChar();
    }

    private void afterChar() {
        if (shiftState == 1) { shiftState = 0; keyboard.setShiftState(0); }
    }

    @Override
    public void onKey(int code) {
        if (mode == MODE_SEARCH && handleSearchKey(code)) return;
        InputConnection ic = getCurrentInputConnection();
        switch (code) {
            case Layouts.SHIFT: {
                long now = SystemClock.uptimeMillis();
                if (shiftState == 2) shiftState = 0;
                else if (shiftState == 1) shiftState = (now - lastShiftTap < 350) ? 2 : 0;
                else shiftState = 1;
                lastShiftTap = now;
                keyboard.setShiftState(shiftState);
                break;
            }
            case Layouts.DEL:
                deleteOne(ic);
                break;
            case Layouts.SPACE:
                space(ic);
                break;
            case Layouts.ENTER:
                enter(ic);
                break;
            case Layouts.SYM: switchLayer(Layouts.SYMBOLS); break;
            case Layouts.SYM2: switchLayer(Layouts.SYMBOLS2); break;
            case Layouts.ABC: switchLayer(Layouts.LETTERS); break;
            case Layouts.GLOBE: nextKeyboard(); break;
            default:
                if (code > 0) onText(new String(Character.toChars(code)));
        }
    }

    private boolean handleSearchKey(int code) {
        String q = panel.getQuery();
        switch (code) {
            case Layouts.DEL:
                if (!q.isEmpty()) panel.setQuery(q.substring(0, q.length() - 1));
                panel.showSearchCursor(true);
                return true;
            case Layouts.SPACE:
                panel.setQuery(q + " ");
                panel.showSearchCursor(true);
                return true;
            case Layouts.ENTER:
                setMode(MODE_CLIP); // fecha o teclado e mostra os resultados grandes
                return true;
        }
        return false;
    }

    @Override
    public void onLongPressSpecial(int code) {
        InputConnection ic = getCurrentInputConnection();
        switch (code) {
            case Layouts.SHIFT: shiftState = 2; keyboard.setShiftState(2); break;
            case Layouts.SPACE:
            case Layouts.GLOBE: showPicker(); break;
            case Layouts.SYM:
            case Layouts.ABC:
            case Layouts.SYM2: setMode(MODE_CLIP); break;
            case Layouts.ENTER: if (ic != null && mode != MODE_SEARCH) ic.commitText("\n", 1); break;
            default: onKey(code);
        }
    }

    private void switchLayer(int l) {
        layer = l;
        keyboard.configure(prefs, Layouts.build(layer, prefs.numberRow));
        if (l == Layouts.LETTERS) autoShift();
    }

    private void deleteOne(InputConnection ic) {
        if (ic == null) return;
        if (selStart != selEnd && selStart >= 0) { ic.commitText("", 1); return; }
        CharSequence b = ic.getTextBeforeCursor(2, 0);
        if (b == null || b.length() == 0) {
            key(KeyEvent.KEYCODE_DEL, 0);
        } else if (b.length() == 2 && Character.isSurrogatePair(b.charAt(0), b.charAt(1))) {
            ic.deleteSurroundingText(2, 0);
        } else {
            ic.deleteSurroundingText(1, 0);
        }
    }

    @Override
    public void onDeleteWords(int words) {
        if (mode == MODE_SEARCH) { panel.setQuery(""); panel.showSearchCursor(true); return; }
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        if (selStart != selEnd && selStart >= 0) { ic.commitText("", 1); words--; }
        if (words <= 0) return;
        CharSequence b = ic.getTextBeforeCursor(64 * words, 0);
        if (b == null || b.length() == 0) {
            for (int i = 0; i < words; i++) key(KeyEvent.KEYCODE_DEL, KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON);
            return;
        }
        int i = b.length();
        for (int w = 0; w < words && i > 0; w++) {
            while (i > 0 && Character.isWhitespace(b.charAt(i - 1))) i--;
            boolean word = i > 0 && Character.isLetterOrDigit(b.charAt(i - 1));
            if (word) while (i > 0 && Character.isLetterOrDigit(b.charAt(i - 1))) i--;
            else if (i > 0) i--; // símbolo isolado
        }
        ic.deleteSurroundingText(b.length() - i, 0);
    }

    private void space(InputConnection ic) {
        if (ic == null) return;
        long now = SystemClock.uptimeMillis();
        CharSequence b = ic.getTextBeforeCursor(48, 0);

        // ponto final com espaço duplo
        if (prefs.doubleSpace && now - lastSpaceTime < 600 && b != null && b.length() >= 2
                && b.charAt(b.length() - 1) == ' ' && Character.isLetterOrDigit(b.charAt(b.length() - 2))) {
            ic.beginBatchEdit();
            ic.deleteSurroundingText(1, 0);
            ic.commitText(". ", 1);
            ic.endBatchEdit();
            lastSpaceTime = 0;
            return;
        }

        // atalhos de texto: "@@" + espaço vira seu e-mail, etc.
        if (b != null && !prefs.shortcuts.isEmpty()) {
            int i = b.length();
            while (i > 0 && !Character.isWhitespace(b.charAt(i - 1))) i--;
            String word = b.subSequence(i, b.length()).toString();
            if (!word.isEmpty()) {
                String exp = prefs.shortcuts.get(word);
                if (exp == null) exp = prefs.shortcuts.get(word.toLowerCase());
                if (exp != null) {
                    ic.beginBatchEdit();
                    ic.deleteSurroundingText(word.length(), 0);
                    ic.commitText(exp + " ", 1);
                    ic.endBatchEdit();
                    lastSpaceTime = 0;
                    return;
                }
            }
        }
        ic.commitText(" ", 1);
        lastSpaceTime = now;
    }

    private void enter(InputConnection ic) {
        if (ic == null) return;
        EditorInfo ei = getCurrentInputEditorInfo();
        int action = ei == null ? EditorInfo.IME_ACTION_NONE : ei.imeOptions & EditorInfo.IME_MASK_ACTION;
        boolean noAction = ei == null || (ei.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
                || action == EditorInfo.IME_ACTION_NONE || action == EditorInfo.IME_ACTION_UNSPECIFIED
                || (ei.inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0;
        if (noAction) key(KeyEvent.KEYCODE_ENTER, 0);
        else ic.performEditorAction(action);
    }

    private void updateEnterLabel(EditorInfo ei) {
        String label = "↵";
        if (ei != null && (ei.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0
                && (ei.inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE) == 0) {
            switch (ei.imeOptions & EditorInfo.IME_MASK_ACTION) {
                case EditorInfo.IME_ACTION_GO: label = "Ir"; break;
                case EditorInfo.IME_ACTION_SEARCH: label = "🔍"; break;
                case EditorInfo.IME_ACTION_SEND: label = "➤"; break;
                case EditorInfo.IME_ACTION_NEXT: label = "Próx"; break;
                case EditorInfo.IME_ACTION_DONE: label = "✓"; break;
                case EditorInfo.IME_ACTION_PREVIOUS: label = "Ant"; break;
            }
        }
        if (keyboard != null) keyboard.setEnterLabel(label);
    }

    @Override
    public void onCursor(int steps) {
        if (mode == MODE_SEARCH) return;
        int code = steps < 0 ? KeyEvent.KEYCODE_DPAD_LEFT : KeyEvent.KEYCODE_DPAD_RIGHT;
        int meta = shiftMeta();
        for (int i = 0; i < Math.abs(steps); i++) key(code, meta);
    }

    private int shiftMeta() {
        return selectMode ? (KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON) : 0;
    }

    @Override
    public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd,
                                  int candStart, int candEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candStart, candEnd);
        selStart = newSelStart;
        selEnd = newSelEnd;
        if (newSelStart == newSelEnd) autoShift();
    }

    private void autoShift() {
        if (keyboard == null || !prefs.autoCap || layer != Layouts.LETTERS || shiftState == 2 || mode == MODE_SEARCH) return;
        InputConnection ic = getCurrentInputConnection();
        EditorInfo ei = getCurrentInputEditorInfo();
        if (ic == null || ei == null || ei.inputType == InputType.TYPE_NULL) return;
        int caps = ic.getCursorCapsMode(ei.inputType);
        shiftState = caps != 0 ? 1 : 0;
        keyboard.setShiftState(shiftState);
    }

    // ------------------------------------------------------------ utilidades

    private void menu(int id) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.performContextMenuAction(id);
    }

    private void key(int keyCode, int meta) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        long t = SystemClock.uptimeMillis();
        ic.sendKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, keyCode, 0, meta, -1, 0,
                KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE));
        ic.sendKeyEvent(new KeyEvent(t, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, keyCode, 0, meta, -1, 0,
                KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE));
    }

    private void showPicker() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showInputMethodPicker();
    }

    @SuppressWarnings("deprecation")
    private void nextKeyboard() {
        boolean ok = false;
        if (Build.VERSION.SDK_INT >= 28) {
            ok = switchToNextInputMethod(false);
        } else {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            IBinder token = getWindow().getWindow() != null ? getWindow().getWindow().getAttributes().token : null;
            if (imm != null && token != null) ok = imm.switchToNextInputMethod(token, false);
        }
        if (!ok) showPicker();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
