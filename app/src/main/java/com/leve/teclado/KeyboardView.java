package com.leve.teclado;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.List;

/**
 * Teclado desenhado direto no Canvas: nada de layouts pesados, uma única View.
 * Recursos: toque longo com acentos, deslizar no espaço move o cursor,
 * deslizar no apagar apaga palavras, apagar acelerado, digitação rápida com 2 dedos.
 */
final class KeyboardView extends View {

    interface Listener {
        void onKey(int code);
        void onText(String text);
        void onCursor(int steps);
        void onDeleteWords(int words);
        void onLongPressSpecial(int code);
    }

    private Listener listener;
    private List<List<Layouts.Key>> rows;
    private Theme theme = Theme.of(0);
    private Prefs prefs;
    private final float dp;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Paint keyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private int shiftState; // 0 normal, 1 maiúscula uma vez, 2 caps lock
    private String enterLabel = "↵";

    // estado do toque
    private Layouts.Key active;
    private int activePointer = -1;
    private float downX, downY, lastStepX;
    private boolean consumed;          // o toque já gerou ação
    private boolean spaceSwipe, delSwipe;
    private int delWords;
    private int repeatCount;
    private String popupChars;         // toque longo aberto
    private int popupIndex;
    private final RectF popupRect = new RectF();

    KeyboardView(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        textPaint.setTextAlign(Paint.Align.CENTER);
        hintPaint.setTextAlign(Paint.Align.RIGHT);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(1.5f * c.getResources().getDisplayMetrics().density);
        setHapticFeedbackEnabled(true);
    }

    void setListener(Listener l) { listener = l; }

    void configure(Prefs p, List<List<Layouts.Key>> layout) {
        prefs = p;
        theme = Theme.of(p.theme);
        rows = layout;
        cancelTouch();
        requestLayout();
        layoutKeys(getWidth());
        invalidate();
    }

    void setShiftState(int s) {
        if (s != shiftState) { shiftState = s; invalidate(); }
    }

    void setEnterLabel(String l) {
        if (!l.equals(enterLabel)) { enterLabel = l; invalidate(); }
    }

    int computeHeight() {
        if (rows == null || prefs == null) return 0;
        return (int) (rows.size() * prefs.keyHeightDp * dp + 8 * dp);
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        setMeasuredDimension(MeasureSpec.getSize(wSpec), computeHeight());
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        layoutKeys(w);
    }

    private void layoutKeys(int width) {
        if (rows == null || width <= 0) return;
        float kh = prefs.keyHeightDp * dp;
        float top = 4 * dp;
        for (List<Layouts.Key> row : rows) {
            float total = 0;
            for (Layouts.Key k : row) total += k.weight;
            float unit = width / Math.max(total, 10f);
            float x = (width - unit * total) / 2f;
            for (Layouts.Key k : row) {
                k.x = x; k.y = top; k.w = unit * k.weight; k.h = kh;
                x += k.w;
            }
            top += kh;
        }
    }

    // ----------------------------------------------------------------- desenho

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(theme.bg);
        if (rows == null) return;
        float gap = 3 * dp, radius = 6 * dp;
        float scale = prefs.textScale / 100f;
        boolean upper = shiftState > 0;
        strokePaint.setColor(theme.textDim);

        for (List<Layouts.Key> row : rows) {
            for (Layouts.Key k : row) {
                rect.set(k.x + gap, k.y + gap, k.x + k.w - gap, k.y + k.h - gap);
                boolean pressed = k == active && popupChars == null;
                keyPaint.setColor(pressed ? theme.keyPressed : (k.special ? theme.keySpecial : theme.key));
                canvas.drawRoundRect(rect, radius, radius, keyPaint);
                if (theme.outlined()) canvas.drawRoundRect(rect, radius, radius, strokePaint);

                String label = labelFor(k, upper);
                int color = theme.text;
                if (k.code == Layouts.SHIFT && shiftState > 0) color = theme.accent;
                if (k.code == Layouts.ENTER) color = theme.accent;
                if (pressed && theme.outlined()) color = 0xFF000000;
                textPaint.setColor(color);

                float size;
                if (k.code == Layouts.SPACE) {
                    size = 13 * dp * scale;
                    if (spaceSwipe && k == active) label = "◀  cursor  ▶";
                    textPaint.setColor(theme.textDim);
                } else if (k.code == Layouts.DEL && delSwipe && k == active) {
                    label = "−" + delWords;
                    size = 16 * dp * scale;
                } else if (label.length() > 1) {
                    size = (label.length() > 3 ? 13 : 15) * dp * scale;
                } else {
                    size = (k.special ? 20 : 22) * dp * scale;
                }
                textPaint.setTextSize(size);
                textPaint.setTypeface(k.special ? Typeface.DEFAULT : Typeface.DEFAULT);
                float cy = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f;
                canvas.drawText(label, rect.centerX(), cy, textPaint);

                if (prefs.hints && k.alts != null && !k.special) {
                    hintPaint.setColor(theme.textDim);
                    hintPaint.setTextSize(10 * dp * scale);
                    String h = k.alts.substring(0, Character.charCount(k.alts.codePointAt(0)));
                    canvas.drawText(h, rect.right - 3 * dp, rect.top + 11 * dp * scale, hintPaint);
                }
                if (k.code == Layouts.SHIFT && shiftState == 2) {
                    keyPaint.setColor(theme.accent);
                    canvas.drawRect(rect.centerX() - 6 * dp, rect.bottom - 7 * dp,
                            rect.centerX() + 6 * dp, rect.bottom - 5 * dp, keyPaint);
                }
            }
        }

        if (popupChars != null && active != null) drawPopup(canvas, upper, radius);
        else if (prefs.preview && active != null && !active.special && !consumed) drawPreview(canvas, upper, radius);
    }

    private String labelFor(Layouts.Key k, boolean upper) {
        if (k.code == Layouts.ENTER) return enterLabel;
        if (k.code == Layouts.SHIFT) return shiftState == 2 ? "⇪" : "⇧";
        if (!k.special && upper) return k.label.toUpperCase();
        return k.label;
    }

    private void drawPreview(Canvas canvas, boolean upper, float radius) {
        Layouts.Key k = active;
        float w = k.w * 1.25f, h = k.h * 1.15f;
        float left = Math.max(0, Math.min(getWidth() - w, k.x + k.w / 2f - w / 2f));
        float top = Math.max(0, k.y - h);
        rect.set(left, top, left + w, top + h);
        keyPaint.setColor(theme.popup);
        canvas.drawRoundRect(rect, radius, radius, keyPaint);
        strokePaint.setColor(theme.accent);
        canvas.drawRoundRect(rect, radius, radius, strokePaint);
        textPaint.setColor(theme.text);
        textPaint.setTextSize(30 * dp * prefs.textScale / 100f);
        float cy = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f;
        canvas.drawText(labelFor(k, upper), rect.centerX(), cy, textPaint);
    }

    private void drawPopup(Canvas canvas, boolean upper, float radius) {
        int n = popupChars.codePointCount(0, popupChars.length());
        float cell = popupRect.width() / n;
        keyPaint.setColor(theme.popup);
        canvas.drawRoundRect(popupRect, radius, radius, keyPaint);
        strokePaint.setColor(theme.accent);
        canvas.drawRoundRect(popupRect, radius, radius, strokePaint);
        textPaint.setTextSize(22 * dp * prefs.textScale / 100f);
        int idx = 0;
        for (int i = 0; i < popupChars.length(); ) {
            int cp = popupChars.codePointAt(i);
            String s = new String(Character.toChars(cp));
            if (upper) s = s.toUpperCase();
            float l = popupRect.left + idx * cell;
            if (idx == popupIndex) {
                keyPaint.setColor(theme.accent);
                rect.set(l + 2 * dp, popupRect.top + 2 * dp, l + cell - 2 * dp, popupRect.bottom - 2 * dp);
                canvas.drawRoundRect(rect, radius, radius, keyPaint);
                textPaint.setColor(0xFF000000);
            } else {
                textPaint.setColor(theme.text);
            }
            float cy = popupRect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f;
            canvas.drawText(s, l + cell / 2f, cy, textPaint);
            i += Character.charCount(cp);
            idx++;
        }
    }

    // ----------------------------------------------------------------- toque

    private Layouts.Key keyAt(float x, float y) {
        if (rows == null) return null;
        Layouts.Key best = null;
        float bestD = Float.MAX_VALUE;
        for (List<Layouts.Key> row : rows) {
            for (Layouts.Key k : row) {
                if (x >= k.x && x < k.x + k.w && y >= k.y && y < k.y + k.h) return k;
                float cx = Math.max(k.x, Math.min(x, k.x + k.w));
                float cy = Math.max(k.y, Math.min(y, k.y + k.h));
                float d = (cx - x) * (cx - x) + (cy - y) * (cy - y);
                if (d < bestD) { bestD = d; best = k; }
            }
        }
        return best;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int idx = e.getActionIndex();
                // digitação rápida: segundo dedo encosta antes do primeiro soltar
                if (active != null) finishActive(true);
                startKey(e.getPointerId(idx), e.getX(idx), e.getY(idx));
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (active == null) return true;
                int idx = e.findPointerIndex(activePointer);
                if (idx >= 0) moveKey(e.getX(idx), e.getY(idx));
                return true;
            }
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_UP: {
                int id = e.getPointerId(e.getActionIndex());
                if (active != null && id == activePointer) finishActive(true);
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                cancelTouch();
                return true;
        }
        return true;
    }

    private void startKey(int pointer, float x, float y) {
        Layouts.Key k = keyAt(x, y);
        if (k == null) return;
        active = k;
        activePointer = pointer;
        downX = lastStepX = x;
        downY = y;
        consumed = false;
        spaceSwipe = delSwipe = false;
        delWords = 0;
        repeatCount = 0;
        popupChars = null;
        if (prefs.haptic) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);

        if (k.code == Layouts.DEL) {
            fire(k);
            consumed = true;
            handler.postDelayed(repeatDelete, 380);
        } else if (k.alts != null || k.special) {
            handler.postDelayed(longPress, prefs.longPressMs);
        }
        invalidate();
    }

    private void moveKey(float x, float y) {
        float dx = x - downX;
        float step = 14 * dp;

        if (popupChars != null) {
            int n = popupChars.codePointCount(0, popupChars.length());
            int i = (int) ((x - popupRect.left) / (popupRect.width() / n));
            i = Math.max(0, Math.min(n - 1, i));
            if (i != popupIndex) { popupIndex = i; invalidate(); }
            return;
        }

        if (active.code == Layouts.SPACE) {
            if (!spaceSwipe && Math.abs(dx) > 18 * dp) {
                spaceSwipe = true;
                consumed = true;
                handler.removeCallbacks(longPress);
                lastStepX = x;
                invalidate();
            }
            if (spaceSwipe) {
                int steps = (int) ((x - lastStepX) / step);
                if (steps != 0) {
                    lastStepX += steps * step;
                    if (listener != null) listener.onCursor(steps);
                }
            }
            return;
        }

        if (active.code == Layouts.DEL) {
            if (!delSwipe && dx < -24 * dp) {
                delSwipe = true;
                handler.removeCallbacks(repeatDelete);
            }
            if (delSwipe) {
                int w = Math.max(0, (int) (-dx / (22 * dp)));
                if (w != delWords) { delWords = w; invalidate(); }
            }
            return;
        }

        // escorregou para outra tecla antes do toque longo: troca de tecla (corrige o dedo)
        if (!consumed && !active.special) {
            Layouts.Key k = keyAt(x, y);
            if (k != null && k != active && !k.special
                    && (Math.abs(x - downX) > active.w * 0.6f || Math.abs(y - downY) > active.h * 0.6f)) {
                handler.removeCallbacks(longPress);
                active = k;
                downX = x; downY = y;
                if (k.alts != null) handler.postDelayed(longPress, prefs.longPressMs);
                invalidate();
            }
        }
    }

    private void finishActive(boolean commit) {
        handler.removeCallbacks(longPress);
        handler.removeCallbacks(repeatDelete);
        Layouts.Key k = active;
        if (k != null && commit) {
            if (popupChars != null) {
                int cpIndex = popupChars.offsetByCodePoints(0, popupIndex);
                String s = new String(Character.toChars(popupChars.codePointAt(cpIndex)));
                if (listener != null) listener.onText(shiftState > 0 ? s.toUpperCase() : s);
            } else if (k.code == Layouts.DEL && delSwipe) {
                if (delWords > 0 && listener != null) listener.onDeleteWords(delWords);
            } else if (!consumed) {
                fire(k);
            }
        }
        active = null;
        activePointer = -1;
        popupChars = null;
        spaceSwipe = delSwipe = false;
        invalidate();
    }

    void cancelTouch() {
        handler.removeCallbacks(longPress);
        handler.removeCallbacks(repeatDelete);
        active = null;
        activePointer = -1;
        popupChars = null;
        spaceSwipe = delSwipe = false;
        invalidate();
    }

    private void fire(Layouts.Key k) {
        if (listener == null) return;
        if (k.special) listener.onKey(k.code);
        else listener.onText(shiftState > 0 ? k.label.toUpperCase() : k.label);
    }

    private final Runnable longPress = new Runnable() {
        @Override public void run() {
            Layouts.Key k = active;
            if (k == null || consumed) return;
            if (k.special) {
                consumed = true;
                if (listener != null) listener.onLongPressSpecial(k.code);
                invalidate();
                return;
            }
            if (prefs.haptic) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
            int n = k.alts.codePointCount(0, k.alts.length());
            if (n == 1) {
                consumed = true;
                if (listener != null) listener.onText(shiftState > 0 ? k.alts.toUpperCase() : k.alts);
                invalidate();
                return;
            }
            popupChars = k.alts;
            popupIndex = 0;
            float cell = Math.min(k.w, getWidth() / (float) n);
            float w = cell * n, h = k.h * 0.95f;
            float left = Math.max(0, Math.min(getWidth() - w, k.x));
            float top = Math.max(0, k.y - h);
            popupRect.set(left, top, left + w, top + h);
            invalidate();
        }
    };

    private final Runnable repeatDelete = new Runnable() {
        @Override public void run() {
            if (active == null || active.code != Layouts.DEL || delSwipe) return;
            repeatCount++;
            // acelera: depois de um tempo segurando, apaga palavra inteira
            if (repeatCount > 14 && listener != null) listener.onDeleteWords(1);
            else fire(active);
            handler.postDelayed(this, repeatCount > 14 ? 140 : 45);
        }
    };

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        cancelTouch();
    }
}
