package com.leve.teclado;

/**
 * Cores e proporções.
 * 0 = AMOLED preto, 1 = Escuro, 2 = Claro, 3 = Alto contraste,
 * 4 = Conforto: minimalista, teclas grandes e espaçadas para dedos grandes.
 */
final class Theme {
    static final String[] NAMES = {"AMOLED preto", "Escuro", "Claro", "Alto contraste",
            "Conforto — minimalista, para dedos grandes"};

    final int bg, key, keySpecial, keyPressed, text, textDim, accent, bar, barText, popup;

    /** Tema de dedos grandes: teclas mais altas, mais espaço entre elas, letras maiores, sem enfeites. */
    boolean big;
    float minKeyHeightDp = 0, gapDp = 3, radiusDp = 6, textMul = 1f, cellDp = 46;

    private Theme(int bg, int key, int keySpecial, int keyPressed, int text, int textDim,
                  int accent, int bar, int barText, int popup) {
        this.bg = bg; this.key = key; this.keySpecial = keySpecial; this.keyPressed = keyPressed;
        this.text = text; this.textDim = textDim; this.accent = accent;
        this.bar = bar; this.barText = barText; this.popup = popup;
    }

    static Theme of(int i) {
        switch (i) {
            case 1: return new Theme(0xFF1E1F24, 0xFF33353C, 0xFF26282E, 0xFF50535C,
                    0xFFF2F2F2, 0xFF9A9CA3, 0xFF3DDC97, 0xFF1E1F24, 0xFFE6E6E6, 0xFF45474F);
            case 2: return new Theme(0xFFD8DAE0, 0xFFFFFFFF, 0xFFBCC0C8, 0xFFA9AEB8,
                    0xFF111111, 0xFF6B6F78, 0xFF0B8457, 0xFFD8DAE0, 0xFF222222, 0xFFFFFFFF);
            case 3: return new Theme(0xFF000000, 0xFF000000, 0xFF000000, 0xFFFFE600,
                    0xFFFFFFFF, 0xFFFFE600, 0xFFFFE600, 0xFF000000, 0xFFFFFFFF, 0xFF222222);
            case 4: {
                // tons quentes e suaves, quase sem contraste entre teclas: descansa a vista
                Theme t = new Theme(0xFF141516, 0xFF1D1E20, 0xFF171819, 0xFF34363A,
                        0xFFE9E5DF, 0xFF6E7076, 0xFF8CCFB2, 0xFF141516, 0xFFB9B6B0, 0xFF26282B);
                t.big = true;
                t.minKeyHeightDp = 60;
                t.gapDp = 5;
                t.radiusDp = 14;
                t.textMul = 1.15f;
                t.cellDp = 54;
                return t;
            }
            default: return new Theme(0xFF000000, 0xFF1A1A1D, 0xFF101012, 0xFF3A3A40,
                    0xFFEDEDED, 0xFF85858C, 0xFF3DDC97, 0xFF000000, 0xFFE0E0E0, 0xFF2A2A2F);
        }
    }

    boolean outlined() { return key == bg; }
}
