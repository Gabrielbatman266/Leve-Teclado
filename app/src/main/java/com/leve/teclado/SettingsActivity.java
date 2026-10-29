package com.leve.teclado;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.util.Map;

/** Tela de configurações: ativação, opções, atalhos de texto e backup. */
public final class SettingsActivity extends Activity implements ClipStore.Listener {

    private static final int REQ_EXPORT = 1, REQ_IMPORT = 2;
    private static final int BG = 0xFF000000, CARD = 0xFF141417, TEXT = 0xFFEDEDED, DIM = 0xFF8C8C94, ACCENT = 0xFF3DDC97;

    private SharedPreferences sp;
    private LinearLayout content, shortcutList;
    private TextView status, clipCount;
    private float dp;
    private ClipStore store;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        dp = getResources().getDisplayMetrics().density;
        sp = Prefs.sp(this);
        store = ClipStore.get(this);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (16 * dp);
        content.setPadding(p, p, p, p * 3);
        scroll.addView(content);
        setContentView(scroll);

        TextView title = text("Leve Teclado", 28, TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        content.addView(title);
        content.addView(text("Rápido, leve, com área de transferência gigante.", 14, DIM));

        // ---- ativação
        section("Ativar");
        status = text("", 15, TEXT);
        content.addView(status);
        content.addView(button("1. Ligar o Leve Teclado", new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)); }
        }));
        content.addView(button("2. Escolher como teclado atual", new View.OnClickListener() {
            @Override public void onClick(View v) {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showInputMethodPicker();
            }
        }));
        EditText test = new EditText(this);
        test.setHint("Toque aqui para testar");
        test.setHintTextColor(DIM);
        test.setTextColor(TEXT);
        content.addView(test);

        // ---- opções
        section("Teclado");
        toggle("Linha de números fixa", "numberRow", true);
        toggle("Vibração ao tocar", "haptic", true);
        toggle("Prévia ampliada da tecla (acessibilidade)", "preview", true);
        toggle("Mostrar acento/símbolo escondido na tecla", "hints", true);
        toggle("Maiúscula automática no início da frase", "autoCap", true);
        toggle("Dois espaços = ponto final", "doubleSpace", true);
        toggle("Barra de ferramentas (copiar, colar, cursor…)", "showToolbar", true);
        slider("Altura das teclas", "keyHeightDp", 50, 36, 90, 1, "dp");
        slider("Tamanho das letras", "textScale", 100, 70, 180, 5, "%");
        slider("Tempo do toque longo", "longPressMs", 320, 150, 800, 10, "ms");

        section("Tema");
        RadioGroup rg = new RadioGroup(this);
        int cur = sp.getInt("theme", 0);
        for (int i = 0; i < Theme.NAMES.length; i++) {
            RadioButton r = new RadioButton(this);
            r.setText(Theme.NAMES[i]);
            r.setTextColor(TEXT);
            r.setId(1000 + i);
            rg.addView(r);
        }
        rg.check(1000 + cur);
        rg.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(RadioGroup g, int id) { sp.edit().putInt("theme", id - 1000).apply(); }
        });
        content.addView(rg);

        // ---- área de transferência
        section("Área de transferência");
        clipCount = text("", 14, DIM);
        content.addView(clipCount);
        slider("Guardar no histórico", "historyMax", 1000, 100, 10000, 100, " itens");
        toggle("Voltar ao teclado depois de colar", "backAfterPaste", true);
        toggle("Pausar histórico (não guardar nada)", "pauseHistory", false);
        content.addView(button("Exportar backup (.json)", new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("application/json");
                i.putExtra(Intent.EXTRA_TITLE, "leve-teclado-backup.json");
                startActivityForResult(i, REQ_EXPORT);
            }
        }));
        content.addView(button("Importar backup", new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                startActivityForResult(i, REQ_IMPORT);
            }
        }));
        content.addView(button("Apagar histórico (mantém os salvos ★)", new View.OnClickListener() {
            @Override public void onClick(View v) {
                confirm("Apagar todo o histórico? Os itens salvos ficam.", new Runnable() {
                    @Override public void run() { store.clearUnpinned(); }
                });
            }
        }));

        // ---- atalhos de texto
        section("Atalhos de texto");
        content.addView(text("Digite o atalho e aperte espaço: ele vira o texto completo. Ex.: @@ → seu e-mail, " +
                "end → seu endereço, obg → obrigado!", 14, DIM));
        final EditText key = new EditText(this);
        key.setHint("Atalho (ex.: @@)");
        key.setSingleLine(true);
        final EditText val = new EditText(this);
        val.setHint("Texto completo");
        for (EditText e : new EditText[]{key, val}) { e.setTextColor(TEXT); e.setHintTextColor(DIM); content.addView(e); }
        content.addView(button("Adicionar atalho", new View.OnClickListener() {
            @Override public void onClick(View v) {
                String k = key.getText().toString().trim();
                String s = val.getText().toString();
                if (k.isEmpty() || k.contains(" ") || s.isEmpty()) {
                    toast("Atalho sem espaços e texto não vazio");
                    return;
                }
                Map<String, String> m = Prefs.loadShortcuts(sp);
                m.put(k, s);
                Prefs.saveShortcuts(SettingsActivity.this, m);
                key.setText("");
                val.setText("");
                renderShortcuts();
            }
        }));
        shortcutList = new LinearLayout(this);
        shortcutList.setOrientation(LinearLayout.VERTICAL);
        content.addView(shortcutList);
        renderShortcuts();

        // ---- dicas
        section("Gestos e truques");
        content.addView(text(
                "• Deslize no ESPAÇO para mover o cursor\n" +
                "• Deslize o ⌫ para a esquerda para apagar palavras\n" +
                "• Segure ⌫: apaga rápido e depois palavra por palavra\n" +
                "• Segure uma letra para acentos (á ã ç é õ…) e números\n" +
                "• Toque duplo no ⇧ = CAPS LOCK (ou segure)\n" +
                "• Segure ↵ para pular linha mesmo onde Enter envia\n" +
                "• Segure ?123 para abrir a área de transferência\n" +
                "• Segure o espaço ou 🌐 para trocar de teclado (voz/emoji)\n" +
                "• 📎 na barra cola o último copiado; segure para ver tudo\n" +
                "• Copiar sem nada selecionado copia o campo inteiro\n" +
                "• No histórico: toque cola, segure copia, ☆ salva, ✕ apaga\n" +
                "• 🔎 busca no histórico digitando no próprio teclado\n" +
                "• Selecionar + ◀ ▶ ⇤ ⇥ seleciona texto com precisão", 14, TEXT));
    }

    @Override
    protected void onResume() {
        super.onResume();
        store.addListener(this);
        updateStatus();
        onClipsChanged();
    }

    @Override
    protected void onPause() {
        store.removeListener(this);
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) updateStatus();
    }

    @Override
    public void onClipsChanged() {
        int pinned = 0;
        for (ClipStore.Item it : store.items) if (it.pinned) pinned++;
        clipCount.setText(store.items.size() + " itens guardados · " + pinned + " salvos ★");
    }

    private void updateStatus() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        boolean enabled = false;
        if (imm != null) {
            for (InputMethodInfo i : imm.getEnabledInputMethodList()) {
                if (i.getPackageName().equals(getPackageName())) { enabled = true; break; }
            }
        }
        String def = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        boolean selected = def != null && def.startsWith(getPackageName() + "/");
        if (selected) { status.setText("✓ Leve Teclado ativo e em uso"); status.setTextColor(ACCENT); }
        else if (enabled) { status.setText("Ligado. Falta o passo 2: escolher como teclado atual."); status.setTextColor(0xFFFFC857); }
        else { status.setText("Ainda não ligado. Faça o passo 1."); status.setTextColor(0xFFFF6B6B); }
    }

    private void renderShortcuts() {
        shortcutList.removeAllViews();
        final Map<String, String> m = Prefs.loadShortcuts(sp);
        if (m.isEmpty()) { shortcutList.addView(text("Nenhum atalho ainda.", 14, DIM)); return; }
        for (final Map.Entry<String, String> e : m.entrySet()) {
            TextView t = text(e.getKey() + "  →  " + e.getValue() + "   (toque para apagar)", 15, TEXT);
            int p = (int) (8 * dp);
            t.setPadding(p, p, p, p);
            t.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    confirm("Apagar o atalho \"" + e.getKey() + "\"?", new Runnable() {
                        @Override public void run() {
                            m.remove(e.getKey());
                            Prefs.saveShortcuts(SettingsActivity.this, m);
                            renderShortcuts();
                        }
                    });
                }
            });
            shortcutList.addView(t);
        }
    }

    // ------------------------------------------------------------ backup

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (req == REQ_EXPORT) {
                OutputStream out = getContentResolver().openOutputStream(uri);
                if (out == null) throw new Exception();
                out.write(store.exportJson().getBytes("UTF-8"));
                out.close();
                toast("Backup salvo");
            } else if (req == REQ_IMPORT) {
                String json = ClipStore.readAll(getContentResolver().openInputStream(uri));
                toast(store.importJson(json) + " itens importados");
            }
        } catch (Exception e) {
            toast("Não foi possível ler/gravar o arquivo");
        }
    }

    // ------------------------------------------------------------ helpers de UI

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setLineSpacing(0, 1.15f);
        return t;
    }

    private void section(String name) {
        TextView t = text(name.toUpperCase(), 13, ACCENT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, (int) (26 * dp), 0, (int) (6 * dp));
        content.addView(t);
    }

    private Button button(String label, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(TEXT);
        GradientDrawable g = new GradientDrawable();
        g.setColor(CARD);
        g.setCornerRadius(12 * dp);
        g.setStroke((int) dp, 0xFF2A2A30);
        b.setBackground(g);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) (8 * dp);
        b.setLayoutParams(lp);
        return b;
    }

    private void toggle(String label, final String key, boolean def) {
        Switch s = new Switch(this);
        s.setText(label);
        s.setTextColor(TEXT);
        s.setTextSize(15);
        int p = (int) (10 * dp);
        s.setPadding(0, p, 0, p);
        s.setChecked(sp.getBoolean(key, def));
        s.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean on) { sp.edit().putBoolean(key, on).apply(); }
        });
        content.addView(s);
    }

    private void slider(final String label, final String key, int def, final int min, int max, final int step,
                        final String unit) {
        final TextView t = text("", 15, TEXT);
        t.setPadding(0, (int) (10 * dp), 0, 0);
        int value = sp.getInt(key, def);
        t.setText(label + ": " + value + unit);
        SeekBar bar = new SeekBar(this);
        bar.setMax((max - min) / step);
        bar.setProgress((value - min) / step);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int prog, boolean user) {
                int v = min + prog * step;
                t.setText(label + ": " + v + unit);
                if (user) sp.edit().putInt(key, v).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        content.addView(t);
        content.addView(bar);
    }

    private void confirm(String msg, final Runnable yes) {
        new AlertDialog.Builder(this)
                .setMessage(msg)
                .setPositiveButton("Sim", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { yes.run(); }
                })
                .setNegativeButton("Não", null)
                .show();
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
