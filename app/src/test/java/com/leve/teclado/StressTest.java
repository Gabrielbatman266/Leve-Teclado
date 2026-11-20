package com.leve.teclado;

import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowToast;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Testes extremamente estressantes do Leve Teclado, do fácil (N1) ao impossível (N10). */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w393dp-h851dp-xxhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class StressTest {

    LeveIME ime;
    ServiceController<LeveIME> sc;
    EditText field;
    View inputView;

    @Before public void reset() {
        Report.set(ClipStore.class, "instance", null);
        Report.set(Dictionary.class, "instance", null);
        ShadowToast.reset();
    }

    @After public void tearDown() {
        try { if (sc != null) sc.destroy(); } catch (Throwable ignored) { }
    }

    // ------------------------------------------------------------------ ajudantes

    static Context app() { return RuntimeEnvironment.getApplication(); }

    static void idle() { shadowOf(Looper.getMainLooper()).idle(); }

    static double ms(long n) { return n / 1e6; }

    /** Liga o teclado num campo de texto de verdade (dentro de uma tela). */
    void boot(int inputType, int imeOptions) throws Exception {
        sc = Robolectric.buildService(LeveIME.class).create();
        ime = sc.get();
        inputView = ime.onCreateInputView();
        layoutInput();
        focus(inputType, imeOptions);
        waitLoaded();
    }

    void layoutInput() {
        DisplayMetrics dm = app().getResources().getDisplayMetrics();
        inputView.measure(View.MeasureSpec.makeMeasureSpec(dm.widthPixels, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(dm.heightPixels, View.MeasureSpec.AT_MOST));
        inputView.layout(0, 0, inputView.getMeasuredWidth(), inputView.getMeasuredHeight());
    }

    void focus(int inputType, int imeOptions) {
        Activity act = Robolectric.buildActivity(Activity.class).setup().get();
        field = new EditText(act);
        field.setInputType(inputType);
        field.setImeOptions(imeOptions);
        act.setContentView(field);
        field.requestFocus();
        EditorInfo ei = new EditorInfo();
        InputConnection ic = field.onCreateInputConnection(ei);
        ei.packageName = "com.teste.app";
        Report.set(ime, "mStartedInputConnection", ic);
        Report.set(ime, "mInputEditorInfo", ei);
        ime.onStartInput(ei, false);
        ime.onStartInputView(ei, false);
        idle();
    }

    void waitLoaded() throws Exception {
        long end = System.currentTimeMillis() + 30000;
        while (System.currentTimeMillis() < end) {
            idle();
            if (Dictionary.get(app()).isLoaded() && ClipStore.get(app()).isLoaded()) return;
            Thread.sleep(3);
        }
    }

    KeyboardView kb() { return Report.get(ime, "keyboard"); }

    Layouts.Key key(String label) {
        List<List<Layouts.Key>> rows = Report.get(kb(), "rows");
        for (List<Layouts.Key> r : rows) for (Layouts.Key k : r) if (k.label.equals(label)) return k;
        throw new AssertionError("tecla não existe: " + label);
    }

    Layouts.Key keyCode(int code) {
        List<List<Layouts.Key>> rows = Report.get(kb(), "rows");
        for (List<Layouts.Key> r : rows) for (Layouts.Key k : r) if (k.code == code) return k;
        throw new AssertionError("tecla não existe: " + code);
    }

    void touch(Layouts.Key k, long holdMs) {
        long t = SystemClock.uptimeMillis();
        float x = k.x + k.w / 2, y = k.y + k.h / 2;
        MotionEvent d = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0);
        kb().onTouchEvent(d);
        if (holdMs > 0) shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(holdMs));
        MotionEvent u = MotionEvent.obtain(t, t + Math.max(30, holdMs), MotionEvent.ACTION_UP, x, y, 0);
        kb().onTouchEvent(u);
        d.recycle(); u.recycle();
        idle();
    }

    void tap(Layouts.Key k) { touch(k, 0); }

    /** Digita como um dedo de verdade, tecla por tecla (letras minúsculas, espaço e pontuação). */
    void typeKeys(String s) {
        for (char c : s.toCharArray()) {
            if (c == ' ') tap(keyCode(Layouts.SPACE));
            else if (c == '\b') tap(keyCode(Layouts.DEL));
            else tap(key(String.valueOf(c)));
        }
        runSuggestions();
    }

    void runSuggestions() { shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(40)); }

    String text() { idle(); return field.getText().toString(); }

    void setText(String s) {
        field.setText(s);
        field.setSelection(s.length());
        ime.onUpdateSelection(0, 0, s.length(), s.length(), -1, -1);
        idle();
    }

    void copy(String s) {
        ClipboardManager cm = (ClipboardManager) app().getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("t", s));
        idle();
    }

    static String bigText(int chars, int seed) {
        Random r = new Random(seed);
        StringBuilder sb = new StringBuilder(chars);
        while (sb.length() < chars) sb.append((char) ('a' + r.nextInt(26))).append(r.nextInt(9) == 0 ? " " : "");
        return sb.substring(0, chars);
    }

    // ================================================================== N1 FÁCIL
    @Test public void n01_facil() throws Exception {
        // campo de mensagem comum (como WhatsApp/Instagram): pede maiúscula no começo das frases
        boot(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, EditorInfo.IME_ACTION_SEND);
        Report.run(1, "digitar 'oi tudo bem'", () -> {
            typeKeys("oi tudo bem");
            String t = text();
            return t.equals("Oi tudo bem") ? "\"" + t + "\" (maiúscula automática)" : Report.fail("\"" + t + "\"");
        });
        Report.run(1, "corrige acento: 'voce nao' → 'você não'", () -> {
            setText("");
            typeKeys("voce nao ");
            String t = text();
            return t.equals("Você não ") ? "ok" : Report.fail("\"" + t + "\"");
        });
        Report.run(1, "⌫ logo após corrigir desfaz a correção", () -> {
            setText("");
            typeKeys("nao \b");
            String t = text();
            return t.equals("Nao") ? "ok" : Report.fail("\"" + t + "\"");
        });
        Report.run(1, "apagar letra", () -> {
            setText("casa");
            tap(keyCode(Layouts.DEL));
            return text().equals("cas") ? "ok" : Report.fail(text());
        });
        Report.run(1, "dois espaços = ponto final", () -> {
            setText("");
            typeKeys("ok  ");
            String t = text();
            return t.equals("Ok. ") ? "ok" : Report.fail("\"" + t + "\"");
        });
        Report.run(1, "sugestões aparecem", () -> {
            setText("");
            typeKeys("obrig");
            String[] w = Report.get(ime, "sugWords");
            return w[1] != null && w[1].toLowerCase().startsWith("obrig") ? "sugeriu \"" + w[1] + "\"" : Report.fail(java.util.Arrays.toString(w));
        });
    }

    // ================================================================== N2 emojis e gestos
    @Test public void n02_emojis_e_gestos() throws Exception {
        boot(InputType.TYPE_CLASS_TEXT, 0);
        String[][] cases = {{"oi 👍🏽", "oi "}, {"família 👨‍👩‍👧", "família "}, {"Brasil 🇧🇷", "Brasil "},
                {"coração ❤️", "coração "}, {"café", "caf"}, {"ok 🫶", "ok "}};
        for (final String[] c : cases) {
            Report.run(2, "⌫ apaga emoji/símbolo inteiro: " + c[0], () -> {
                setText(c[0]);
                tap(keyCode(Layouts.DEL));
                String t = text();
                return t.equals(c[1]) ? "ok" : Report.fail("ficou \"" + t + "\" (" + t.length() + " chars)");
            });
        }
        Report.run(2, "deslizar ⌫ apaga palavras", () -> {
            setText("uma frase de teste");
            ime.onDeleteWords(2);
            return text().equals("uma frase ") ? "ok" : Report.fail("\"" + text() + "\"");
        });
        Report.run(2, "atalho @@ vira e-mail", () -> {
            Prefs.sp(app()).edit().putString("shortcuts", "[{\"k\":\"@@\",\"v\":\"eu@exemplo.com\"}]").commit();
            ime.onStartInputView((EditorInfo) Report.get(ime, "mInputEditorInfo"), true);
            setText("meu email é @@");
            tap(keyCode(Layouts.SPACE));
            return text().equals("meu email é eu@exemplo.com ") ? "ok" : Report.fail("\"" + text() + "\"");
        });
        Report.run(2, "segurar letra = acento", () -> {
            setText("");
            touch(key("a"), 600);
            String t = text();
            return !t.isEmpty() && "áàâãäªÁÀÂÃÄ".indexOf(t.charAt(0)) >= 0 ? "\"" + t + "\"" : Report.fail("\"" + t + "\"");
        });
        Report.run(2, "deslizar no espaço move o cursor", () -> {
            setText("abcdef");
            Layouts.Key sp = keyCode(Layouts.SPACE);
            long t = SystemClock.uptimeMillis();
            float x = sp.x + sp.w / 2, y = sp.y + sp.h / 2;
            kb().onTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0));
            for (int i = 1; i <= 10; i++) kb().onTouchEvent(MotionEvent.obtain(t, t + i * 10, MotionEvent.ACTION_MOVE, x - i * 20, y, 0));
            kb().onTouchEvent(MotionEvent.obtain(t, t + 200, MotionEvent.ACTION_UP, x - 200, y, 0));
            idle();
            int sel = field.getSelectionStart();
            return sel < 6 && text().equals("abcdef") ? "cursor foi para " + sel : Report.fail("sel=" + sel + " texto=" + text());
        });
    }

    // ================================================================== N3 área de transferência
    @Test public void n03_area_de_transferencia() throws Exception {
        boot(InputType.TYPE_CLASS_TEXT, 0);
        final ClipStore store = ClipStore.get(app());
        Report.run(3, "copiar 1.000 textos diferentes", () -> {
            long t = System.nanoTime();
            for (int i = 0; i < 1000; i++) copy("texto copiado número " + i);
            double each = ms(System.nanoTime() - t) / 1000;
            if (store.items.size() != 1000) return Report.fail("guardou " + store.items.size());
            return Report.budget("por cópia", each, 4, "ms");
        });
        Report.run(3, "📎 cola o último copiado", () -> {
            setText("");
            copy("colar isto aqui");
            View chip = Report.get(ime, "latestChip");
            chip.performClick();
            idle();
            return text().equals("colar isto aqui") ? "ok" : Report.fail("\"" + text() + "\"");
        });
        Report.run(3, "senha não vai pro histórico", () -> {
            sc.get();
            focus(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD, 0);
            copy("minhaSenhaSecreta123");
            boolean saved = false;
            for (ClipStore.Item it : store.items) if ("minhaSenhaSecreta123".equals(it.text)) saved = true;
            focus(InputType.TYPE_CLASS_TEXT, 0);
            return !saved ? "não guardou" : Report.fail("guardou a senha no histórico");
        });
        Report.run(3, "senha copiada do gerenciador (marcada como sensível) não vai pro histórico", () -> {
            ClipboardManager cm = (ClipboardManager) app().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData cd = ClipData.newPlainText("senha", "S3nh@DoBanco!");
            android.os.PersistableBundle extras = new android.os.PersistableBundle();
            extras.putBoolean("android.content.extra.IS_SENSITIVE", true);
            cd.getDescription().setExtras(extras);
            cm.setPrimaryClip(cd);
            idle();
            for (ClipStore.Item it : store.items) if ("S3nh@DoBanco!".equals(it.text)) return Report.fail("guardou a senha");
            return "não guardou";
        });
        Report.run(3, "botão 📋 fica aceso no modo área de transferência", () -> {
            Report.call(ime, "setMode", 1);
            TextView clip = toolbarChip("📋");
            int on = bgColor(clip);
            Report.call(ime, "setMode", 3);
            TextView emo = toolbarChip("😊");
            int onE = bgColor(emo);
            Report.call(ime, "setMode", 0);
            Theme th = Report.get(ime, "theme");
            boolean ok = on == th.accent && onE == th.accent;
            return ok ? "ok" : Report.fail("destaque no botão errado");
        });
        Report.run(3, "busca no histórico (1.000 itens) por letra", () -> {
            ClipPanel p = Report.get(ime, "panel");
            long t = System.nanoTime();
            String q = "número 99";
            for (int i = 1; i <= q.length(); i++) p.setQuery(q.substring(0, i));
            double each = ms(System.nanoTime() - t) / q.length();
            int shown = ((List<?>) Report.get(p, "shown")).size();
            p.setQuery("");
            return shown == 11 ? Report.budget("por letra", each, 16, "ms") : Report.fail("achou " + shown + " (esperado 11)");
        });
    }

    private TextView toolbarChip(String start) {
        LinearLayout row = Report.get(ime, "toolbarRow");
        for (int i = 0; i < row.getChildCount(); i++) {
            TextView t = (TextView) row.getChildAt(i);
            if (t.getText().toString().startsWith(start)) return t;
        }
        throw new AssertionError("sem botão " + start);
    }

    private static int bgColor(View v) {
        android.graphics.drawable.GradientDrawable g = (android.graphics.drawable.GradientDrawable) v.getBackground();
        return g.getColor() == null ? 0 : g.getColor().getDefaultColor();
    }

    // ================================================================== N4 velocidade de digitação
    @Test public void n04_velocidade() throws Exception {
        boot(InputType.TYPE_CLASS_TEXT, 0);
        Report.run(4, "3.000 toques seguidos", () -> {
            String s = "o rato roeu a roupa do rei de roma ";
            long t = System.nanoTime();
            int n = 0;
            while (n < 3000) { for (char c : s.toCharArray()) { typeOne(c); n++; } }
            double each = ms(System.nanoTime() - t) / n;
            return Report.budget("por toque", each, 3, "ms");
        });
        Report.run(4, "sugestões com 50 mil palavras aprendidas", () -> {
            Dictionary d = Dictionary.get(app());
            Random r = new Random(1);
            for (int i = 0; i < 50000; i++) d.learn(randomWord(r));
            setText("");
            typeKeys("pro");
            long t = System.nanoTime();
            for (int i = 0; i < 200; i++) Report.call(ime, "computeSuggestions");
            return Report.budget("por tecla", ms(System.nanoTime() - t) / 200, 1.5, "ms");
        });
        Report.run(4, "dicionário não cresce sem limite", () -> {
            Dictionary d = Dictionary.get(app());
            Random r = new Random(2);
            for (int i = 0; i < 150000; i++) d.learn(randomWord(r));
            int n = ((List<?>) Report.get(d, "entries")).size();
            return n <= 60000 ? n + " palavras guardadas" : Report.fail(n + " palavras na memória");
        });
    }

    private void typeOne(char c) {
        if (c == ' ') ime.onKey(Layouts.SPACE); else ime.onText(String.valueOf(c));
    }

    static String randomWord(Random r) {
        int len = 3 + r.nextInt(9);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) sb.append((char) ('a' + r.nextInt(26)));
        return sb.toString();
    }

    // ================================================================== N5 entradas estranhas
    @Test public void n05_entradas_estranhas() throws Exception {
        boot(InputType.TYPE_CLASS_TEXT, 0);
        Report.run(5, "campo sem informações (EditorInfo nulo)", () -> { ime.onStartInputView(null, false); return "ok"; });
        Report.run(5, "app fechou o campo no meio (sem conexão)", () -> {
            Report.set(ime, "mStartedInputConnection", null);
            Report.set(ime, "mInputConnection", null);
            for (int c : new int[]{Layouts.DEL, Layouts.SPACE, Layouts.ENTER, Layouts.SHIFT, 'a'}) ime.onKey(c);
            ime.onText("x"); ime.onDeleteWords(3); ime.onCursor(5); ime.onEmoji("😀"); ime.onEmojiBackspace();
            for (String tool : new String[]{"Tudo", "Copiar", "Recortar", "Colar", "◀", "▶", "↶", "↷", "📎"}) toolbarChip(tool).performClick();
            Report.call(ime, "computeSuggestions");
            focus(InputType.TYPE_CLASS_TEXT, 0);
            return "ok";
        });
        Report.run(5, "texto vazio vindo da tecla", () -> { ime.onText(""); return "ok"; });
        Report.run(5, "campo de número abre o teclado numérico", () -> {
            focus(InputType.TYPE_CLASS_NUMBER, 0);
            int layer = Report.get(ime, "layer");
            focus(InputType.TYPE_CLASS_TEXT, 0);
            return layer == Layouts.NUMPAD ? "ok" : Report.fail("camada " + layer);
        });
        Report.run(5, "configurações absurdas", () -> {
            Prefs.sp(app()).edit().putInt("keyHeightDp", -40).putInt("textScale", 0).putInt("longPressMs", -5)
                    .putInt("historyMax", -1).putInt("theme", 77).putString("shortcuts", "{lixo").commit();
            focus(InputType.TYPE_CLASS_TEXT, 0);
            layoutInput();
            Bitmap_draw();
            typeKeys("teste");
            Prefs.sp(app()).edit().clear().commit();
            focus(InputType.TYPE_CLASS_TEXT, 0);
            layoutInput();
            return "ok";
        });
        Report.run(5, "cursor no meio de seleção + emoji ⌫", () -> {
            setText("seleção 😀");
            ime.onUpdateSelection(0, 0, 2, 9, -1, -1);
            ime.onEmojiBackspace();
            ime.onUpdateSelection(0, 0, 0, 0, -1, -1);
            ime.onEmojiBackspace();
            return "ok";
        });
        Report.run(5, "5.000 atalhos de texto", () -> {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < 5000; i++) sb.append(i > 0 ? "," : "").append("{\"k\":\"a").append(i).append("\",\"v\":\"valor ").append(i).append("\"}");
            Prefs.sp(app()).edit().putString("shortcuts", sb.append("]").toString()).commit();
            focus(InputType.TYPE_CLASS_TEXT, 0);
            setText("a4999");
            long t = System.nanoTime();
            tap(keyCode(Layouts.SPACE));
            Prefs.sp(app()).edit().remove("shortcuts").commit();
            String txt = text();
            return txt.equals("valor 4999 ") ? Report.budget("espaço", ms(System.nanoTime() - t), 16, "ms") : Report.fail(txt);
        });
    }

    private void Bitmap_draw() {
        android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(Math.max(1, inputView.getWidth()),
                Math.max(1, inputView.getHeight()), android.graphics.Bitmap.Config.ARGB_8888);
        inputView.draw(new android.graphics.Canvas(b));
    }

    // ================================================================== N7 histórico gigante
    @Test public void n07_historico_10_mil() throws Exception {
        Prefs.sp(app()).edit().putInt("historyMax", 10000).commit();
        boot(InputType.TYPE_CLASS_TEXT, 0);
        final ClipStore store = ClipStore.get(app());
        for (int i = 0; i < 10000; i++) store.add("item " + i + " " + bigText(700, i));
        Report.run(7, "10.000 itens guardados (700 letras cada)", () ->
                store.items.size() == 10000 ? "todos guardados" : Report.fail("só " + store.items.size()));
        Report.run(7, "10.000 itens: salvar sem travar a tela", () -> {
            Runnable save = Report.get(store, "saveTask");
            long t = System.nanoTime();
            save.run();
            return Report.budget("tela parada", ms(System.nanoTime() - t), 16, "ms");
        });
        Report.run(7, "copiar algo novo com 10.000 no histórico", () -> {
            long t = System.nanoTime();
            for (int i = 0; i < 20; i++) copy("novo " + i);
            return Report.budget("por cópia", ms(System.nanoTime() - t) / 20, 8, "ms");
        });
        Report.run(7, "abrir o painel com 10.000 itens", () -> {
            long t = System.nanoTime();
            Report.call(ime, "setMode", 1);
            double d = ms(System.nanoTime() - t);
            Report.call(ime, "setMode", 0);
            return Report.budget("abrir", d, 30, "ms");
        });
        Report.run(7, "buscar no histórico de 7 milhões de letras", () -> {
            ClipPanel p = Report.get(ime, "panel");
            long t = System.nanoTime();
            String q = "zzqx";
            for (int i = 1; i <= q.length(); i++) p.setQuery(q.substring(0, i));
            double each = ms(System.nanoTime() - t) / q.length();
            p.setQuery("");
            return Report.budget("por letra", each, 40, "ms");
        });
    }

    // ================================================================== N6 layout
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    @Test public void n06a_layout_samsung_fraco_teclas_maximas() throws Exception {
        Prefs.sp(app()).edit().putInt("keyHeightDp", 90).putInt("textScale", 180).putInt("theme", 4).commit();
        boot(InputType.TYPE_CLASS_TEXT, 0);
        layoutChecks("Samsung fraco, teclas 90dp + letra 180% + Conforto");
    }

    @Config(qualifiers = "w640dp-h360dp-land-xhdpi")
    @Test public void n06b_layout_deitado() throws Exception {
        boot(InputType.TYPE_CLASS_TEXT, 0);
        layoutChecks("celular deitado");
    }

    @Config(qualifiers = "w320dp-h480dp-mdpi")
    @Test public void n06c_layout_tela_minuscula() throws Exception {
        Prefs.sp(app()).edit().putInt("textScale", 150).commit();
        boot(InputType.TYPE_CLASS_TEXT, 0);
        layoutChecks("tela 320x480 + letra 150%");
    }

    @Config(qualifiers = "w393dp-h851dp-xxhdpi")
    @Test public void n06d_layout_normal() throws Exception {
        boot(InputType.TYPE_CLASS_TEXT, 0);
        layoutChecks("Xiaomi normal");
    }

    private void layoutChecks(final String tag) {
        final DisplayMetrics dm = app().getResources().getDisplayMetrics();
        final float d = dm.density;
        layoutInput();
        Report.run(6, tag + ": teclado não cobre a tela toda", () -> {
            float h = inputView.getMeasuredHeight() / d, screen = dm.heightPixels / d;
            float pct = h / screen * 100;
            return pct <= 58 ? String.format("ocupa %.0f%% (%.0fdp)", pct, h) : Report.fail(String.format("ocupa %.0f%% da tela (%.0fdp)", pct, h));
        });
        Report.run(6, tag + ": teclas grandes o bastante pro dedo", () -> {
            List<List<Layouts.Key>> rows = Report.get(kb(), "rows");
            float minW = 999, minH = 999;
            for (List<Layouts.Key> r : rows) for (Layouts.Key k : r) { minW = Math.min(minW, k.w / d); minH = Math.min(minH, k.h / d); }
            return minW >= 28 && minH >= 34 ? String.format("menor tecla %.0fx%.0fdp", minW, minH)
                    : Report.fail(String.format("tecla de %.0fx%.0fdp", minW, minH));
        });
        Report.run(6, tag + ": nomes das teclas cabem nas teclas", () -> {
            List<List<Layouts.Key>> rows = Report.get(kb(), "rows");
            android.graphics.Paint p = Report.get(kb(), "textPaint");
            Prefs prefs = Report.get(kb(), "prefs");
            Theme th = Report.get(kb(), "theme");
            float scale = prefs.textScale / 100f * th.textMul;
            StringBuilder bad = new StringBuilder();
            Bitmap_draw();
            for (List<Layouts.Key> r : rows) for (Layouts.Key k : r) {
                String label = k.label;
                float size = Report.call(kb(), "labelSize", k, label) instanceof Float ? (Float) Report.call(kb(), "labelSize", k, label) : 0;
                p.setTextSize(size);
                float w = p.measureText(label);
                if (w > k.w - 2 * th.gapDp * d) bad.append(label).append(' ');
            }
            return bad.length() == 0 ? "ok" : Report.fail("cortados: " + bad);
        });
        Report.run(6, tag + ": barra de cima com botões tocáveis", () -> {
            LinearLayout row = Report.get(ime, "toolbarRow");
            float minH = 999;
            for (int i = 0; i < row.getChildCount(); i++) {
                View c = row.getChildAt(i);
                if (c.getVisibility() == View.VISIBLE) minH = Math.min(minH, c.getHeight() / d);
            }
            return minH >= 32 ? String.format("altura %.0fdp", minH) : Report.fail(String.format("botões com %.0fdp", minH));
        });
    }

    // ================================================================== N8 memória
    @Test public void n08_memoria_e_vazamentos() throws Exception {
        boot(InputType.TYPE_CLASS_TEXT, 0);
        final ClipStore store = ClipStore.get(app());
        Report.run(8, "copiar 300 textos de 200 mil letras", () -> {
            long m0 = Report.usedMb();
            for (int i = 0; i < 300; i++) store.add(bigText(200_000, i));
            idle();
            long grow = Report.usedMb() - m0;
            return Report.budget("RAM a mais", grow, 48, "MB");
        });
        Report.run(8, "abrir e fechar o teclado 100x", () -> {
            List<WeakReference<View>> views = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                View v = ime.onCreateInputView();
                views.add(new WeakReference<>(v));
                focus(InputType.TYPE_CLASS_TEXT, 0);
                Report.call(ime, "setMode", i % 4);
                ime.onFinishInputView(true);
            }
            inputView = ime.onCreateInputView();
            Report.usedMb();
            int alive = 0;
            for (WeakReference<View> w : views) if (w.get() != null) alive++;
            return alive <= 2 ? alive + " telas presas" : Report.fail(alive + " telas antigas presas na memória");
        });
        Report.run(8, "ligar/desligar o serviço 30x", () -> {
            List<WeakReference<LeveIME>> refs = new ArrayList<>();
            for (int i = 0; i < 30; i++) {
                ServiceController<LeveIME> c = Robolectric.buildService(LeveIME.class).create();
                c.get().onCreateInputView();
                refs.add(new WeakReference<>(c.get()));
                c.destroy();
            }
            ShadowToast.reset();
            Report.usedMb();
            int alive = 0;
            for (WeakReference<LeveIME> w : refs) if (w.get() != null) alive++;
            return alive <= 1 ? alive + " presos" : Report.fail(alive + " teclados velhos presos");
        });
    }

    // ================================================================== N9 EXTREMO
    @Test public void n09_extremo_macaco() throws Exception {
        boot(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE, 0);
        Report.run(9, "macaco: 30.000 toques com vários dedos", () -> {
            Random r = new Random(9);
            KeyboardView k = kb();
            int w = Math.max(1, k.getWidth()), h = Math.max(1, k.getHeight());
            int crashes = 0;
            String first = null;
            long t0 = SystemClock.uptimeMillis();
            for (int i = 0; i < 30000; i++) {
                try {
                    int a = r.nextInt(10);
                    long t = t0 + i * 7;
                    float x = r.nextFloat() * w * 1.2f - w * 0.1f, y = r.nextFloat() * h * 1.2f - h * 0.1f;
                    MotionEvent e;
                    if (a < 3) e = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0);
                    else if (a < 6) e = MotionEvent.obtain(t, t, MotionEvent.ACTION_MOVE, x, y, 0);
                    else if (a < 8) e = MotionEvent.obtain(t, t, MotionEvent.ACTION_UP, x, y, 0);
                    else if (a == 8) e = MotionEvent.obtain(t, t, MotionEvent.ACTION_CANCEL, x, y, 0);
                    else {
                        MotionEvent.PointerProperties[] pp = {new MotionEvent.PointerProperties(), new MotionEvent.PointerProperties()};
                        pp[0].id = 0; pp[1].id = 1;
                        MotionEvent.PointerCoords[] pc = {new MotionEvent.PointerCoords(), new MotionEvent.PointerCoords()};
                        pc[0].x = x; pc[0].y = y; pc[1].x = r.nextFloat() * w; pc[1].y = r.nextFloat() * h;
                        int act = r.nextBoolean() ? MotionEvent.ACTION_POINTER_DOWN : MotionEvent.ACTION_POINTER_UP;
                        e = MotionEvent.obtain(t, t, act | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2, pp, pc, 0, 0, 1, 1, 0, 0, 0, 0);
                    }
                    k.onTouchEvent(e);
                    e.recycle();
                    if (i % 37 == 0) shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(r.nextInt(700)));
                    if (i % 500 == 0) { ShadowToast.reset(); if (field.length() > 5000) setText(""); }
                } catch (Throwable t) {
                    crashes++;
                    if (first == null) first = Report.where(t);
                }
            }
            return crashes == 0 ? "0 crashes, " + field.length() + " letras digitadas" : Report.fail(crashes + " crashes; 1º: " + first);
        });
        Report.run(9, "macaco: 5.000 botões/modos aleatórios", () -> {
            Random r = new Random(10);
            LinearLayout row = Report.get(ime, "toolbarRow");
            int crashes = 0;
            String first = null;
            for (int i = 0; i < 5000; i++) {
                try {
                    switch (r.nextInt(8)) {
                        case 0: { View c = row.getChildAt(r.nextInt(row.getChildCount())); if (!((TextView) c).getText().toString().startsWith("⚙") && !((TextView) c).getText().toString().startsWith("🌐")) c.performClick(); break; }
                        case 1: Report.call(ime, "setMode", r.nextInt(4)); break;
                        case 2: ime.onKey(new int[]{Layouts.SHIFT, Layouts.SYM, Layouts.SYM2, Layouts.ABC, Layouts.DEL, Layouts.SPACE, Layouts.ENTER, Layouts.EMOJI}[r.nextInt(8)]); break;
                        case 3: ime.onText(new String[]{"a", "😀", "ç", ".", "\n", "👨‍👩‍👧"}[r.nextInt(6)]); break;
                        case 4: ime.onUpdateSelection(0, 0, r.nextInt(30), r.nextInt(30), -1, -1); break;
                        case 5: copy("clip " + r.nextInt(100)); break;
                        case 6: ime.onDeleteWords(r.nextInt(5)); break;
                        default: shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50));
                    }
                    if (i % 300 == 0) { ShadowToast.reset(); setText(""); }
                } catch (Throwable t) {
                    crashes++;
                    if (first == null) first = Report.where(t);
                }
            }
            return crashes == 0 ? "0 crashes" : Report.fail(crashes + " crashes; 1º: " + first);
        });
    }

    // ================================================================== N10 IMPOSSÍVEL
    @Test public void n10_impossivel() throws Exception {
        File dir = app().getFilesDir();
        dir.mkdirs();
        try (FileOutputStream f = new FileOutputStream(new File(dir, "clips.json"))) {
            f.write("[{\"t\":\"ok\"},{\"t\":".getBytes()); // arquivo cortado no meio (celular desligou)
        }
        try (FileOutputStream f = new FileOutputStream(new File(dir, "palavras_usuario.json"))) {
            f.write(new byte[]{(byte) 0xff, 0, 1, 2, '{', '{'});
        }
        Report.run(10, "arquivos corrompidos ao ligar", () -> { boot(InputType.TYPE_CLASS_TEXT, 0); return "abriu"; });
        if (ime == null) return;
        final ClipStore store = ClipStore.get(app());
        Report.run(10, "10.000 textos de 200 mil letras (2 GB) sem estourar", () -> {
            store.setMax(10000);
            long m0 = Report.usedMb();
            String base = bigText(200_000, 3);
            for (int i = 0; i < 10000; i++) store.add(i + base.substring(0, 199_990));
            long grow = Report.usedMb() - m0;
            return Report.budget("RAM a mais", grow, 64, "MB");
        });
        Report.run(10, "colar um texto de 5 milhões de letras", () -> {
            String huge = bigText(5_000_000, 4);
            copy(huge);
            setText("");
            long t = System.nanoTime();
            View chip = Report.get(ime, "latestChip");
            chip.performClick();
            idle();
            return Report.budget("colar", ms(System.nanoTime() - t), 300, "ms");
        });
        Report.run(10, "200 mil palavras aprendidas + sugestão", () -> {
            Dictionary d = Dictionary.get(app());
            Random r = new Random(5);
            for (int i = 0; i < 200000; i++) d.learn(randomWord(r));
            setText("");
            typeKeys("ca");
            long t = System.nanoTime();
            for (int i = 0; i < 100; i++) Report.call(ime, "computeSuggestions");
            return Report.budget("por tecla", ms(System.nanoTime() - t) / 100, 2, "ms");
        });
    }
}
