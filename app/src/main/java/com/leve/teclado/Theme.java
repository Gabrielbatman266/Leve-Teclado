package com.leve.teclado;

/** Cores. 0 = AMOLED preto (economiza bateria), 1 = Escuro, 2 = Claro, 3 = Alto contraste. */
final class Theme {
    static final String[] NAMES = {"AMOLED preto", "Escuro", "Claro", "Alto contraste"};

    final int bg, key, keySpecial, keyPressed, text, textDim, accent, bar, barText, popup;

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
            default: return new Theme(0xFF000000, 0xFF1A1A1D, 0xFF101012, 0xFF3A3A40,
                    0xFFEDEDED, 0xFF85858C, 0xFF3DDC97, 0xFF000000, 0xFFE0E0E0, 0xFF2A2A2F);
        }
    }

    boolean outlined() { return key == bg; }
}
