package com.leve.teclado;

import java.util.ArrayList;
import java.util.List;

/** Layouts das teclas (ABNT2 simplificado, com acentos no toque longo). */
final class Layouts {
    static final int SHIFT = -1, SYM = -2, SYM2 = -3, ENTER = -4, DEL = -5, ABC = -6, GLOBE = -7, NUM = -8;
    static final int SPACE = 32;

    static final int LETTERS = 0, SYMBOLS = 1, SYMBOLS2 = 2, NUMPAD = 3;

    static final class Key {
        String label;
        int code;
        float weight;
        String alts;     // caracteres extras do toque longo
        boolean special;
        float x, y, w, h; // calculados pela view

        Key(String label, int code, float weight, String alts, boolean special) {
            this.label = label; this.code = code; this.weight = weight; this.alts = alts; this.special = special;
        }
    }

    private static Key c(String ch, String alts) { return new Key(ch, ch.charAt(0), 1f, alts, false); }
    private static Key c(String ch) { return c(ch, null); }
    private static Key s(String label, int code, float w) { return new Key(label, code, w, null, true); }

    private static List<Key> chars(String keys, String[] alts) {
        List<Key> r = new ArrayList<>();
        for (int i = 0; i < keys.length(); i++) {
            String a = alts != null && i < alts.length && alts[i] != null && !alts[i].isEmpty() ? alts[i] : null;
            r.add(c(String.valueOf(keys.charAt(i)), a));
        }
        return r;
    }

    private static List<Key> bottom(int switchCode, String switchLabel) {
        List<Key> r = new ArrayList<>();
        r.add(s(switchLabel, switchCode, 1.4f));
        r.add(c(",", ",;:'\"…"));
        r.add(s("🌐", GLOBE, 1f));
        r.add(s("espaço", SPACE, 4.0f));
        r.add(c(".", ".?!-_/@"));
        r.add(s("↵", ENTER, 1.6f));
        return r;
    }

    static List<List<Key>> build(int layer, boolean numberRow) {
        List<List<Key>> rows = new ArrayList<>();
        switch (layer) {
            case SYMBOLS: {
                rows.add(chars("1234567890", new String[]{"¹½⅓¼", "²", "³¾", "⁴", "", "", "", "", "", "ⁿ∅"}));
                rows.add(chars("@#$_&-+()/", new String[]{"", "№", "€£¥¢", "", "", "–—·", "±", "<[{", ">]}", "\\"}));
                List<Key> r3 = new ArrayList<>();
                r3.add(s("=\\<", SYM2, 1.5f));
                r3.addAll(chars("*\"':;!?", new String[]{"★†", "“”«»", "‘’", "", "", "¡", "¿"}));
                r3.add(s("⌫", DEL, 1.5f));
                rows.add(r3);
                rows.add(bottom(ABC, "ABC"));
                break;
            }
            case SYMBOLS2: {
                rows.add(chars("~`|•√π÷×¶∆", null));
                rows.add(chars("£€¥^°={}\\%", new String[]{"", "", "", "", "", "≠≈", "", "", "", "‰"}));
                List<Key> r3 = new ArrayList<>();
                r3.add(s("?123", SYM, 1.5f));
                r3.addAll(chars("©®™[]<>", null));
                r3.add(s("⌫", DEL, 1.5f));
                rows.add(r3);
                rows.add(bottom(ABC, "ABC"));
                break;
            }
            case NUMPAD: {
                List<Key> r;
                r = chars("123", null); r.add(s("−", '-', 1f)); rows.add(r);
                r = chars("456", null); r.add(c(",", ",:")); rows.add(r);
                r = chars("789", null); r.add(s("⌫", DEL, 1f)); rows.add(r);
                r = new ArrayList<>();
                r.add(s("ABC", ABC, 1f)); r.add(c("0", "+*#")); r.add(c(".", "./()")); r.add(s("↵", ENTER, 1f));
                rows.add(r);
                for (List<Key> row : rows) for (Key k : row) k.weight = 2.5f;
                break;
            }
            default: {
                if (numberRow) rows.add(chars("1234567890", new String[]{"!¹", "@²", "#³", "$", "%", "^", "&", "*", "(", ")"}));
                String[] topAlts = numberRow
                        ? new String[]{"", "", "éèêë", "", "", "ý", "úùûü", "íìîï", "óòôõö", ""}
                        : new String[]{"1", "2", "3éèêë", "4", "5", "6ý", "7úùûü", "8íìîï", "9óòôõö", "0"};
                rows.add(chars("qwertyuiop", topAlts));
                rows.add(chars("asdfghjklç", new String[]{"áàâãäª", "ß$", "", "", "", "", "", "", "", ""}));
                List<Key> r3 = new ArrayList<>();
                r3.add(s("⇧", SHIFT, 1.5f));
                r3.addAll(chars("zxcvbnm", new String[]{"", "", "ç©", "", "", "ñ", "µ"}));
                r3.add(s("⌫", DEL, 1.5f));
                rows.add(r3);
                rows.add(bottom(SYM, "?123"));
            }
        }
        return rows;
    }
}
