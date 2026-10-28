package ar.sanzol.solitaire.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import java.util.List;

import ar.sanzol.solitaire.game.Card;
import ar.sanzol.solitaire.game.Game;

/**
 * Draws the table and turns touches into game actions. All cards are drawn on a Canvas.
 * The view reports what happened through {@link Listener}; it does not play sounds or
 * show dialogs itself.
 */
public class SolitaireView extends View {

    public interface Listener {
        /** Cards were moved onto a foundation or tableau column. */
        void onCardsPlaced();

        /** The stock was tapped (draw or recycle). */
        void onStockDrawn();

        /** Any change of game state; called after the two events above. */
        void onStateChanged();

        void onWin();
    }

    private static final float CARD_RATIO = 1.42f;
    private static final long AUTOCOMPLETE_START_MS = 250;
    private static final long AUTOCOMPLETE_STEP_MS = 90;

    private final Game game;
    private Listener listener;

    // Geometry
    private float originX, cw, ch, gap, radius, topY, tabY;
    private float downOffset, upOffset;

    // Paints
    private final Paint bgPaint = new Paint();
    private final Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint backPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint backLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint slotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint slotTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tmp = new RectF();

    // Drag state
    private int dragPile = -1, dragIndex = -1;
    private float downX, downY, touchX, touchY, grabDx, grabDy;
    private boolean dragging;
    private final int touchSlop;

    private final Runnable autoCompleteRunnable = new Runnable() {
        @Override
        public void run() {
            if (game.autoCompleteStep()) {
                if (listener != null) listener.onCardsPlaced();
                notifyChanged();
                postDelayed(this, AUTOCOMPLETE_STEP_MS);
            }
        }
    };

    public SolitaireView(Context context, Game game) {
        super(context);
        this.game = game;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        cardPaint.setColor(Color.rgb(250, 250, 246));
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setColor(Color.rgb(170, 170, 170));
        borderPaint.setStrokeWidth(2f);

        backPaint.setColor(Color.rgb(32, 64, 160));
        backLinePaint.setStyle(Paint.Style.STROKE);
        backLinePaint.setColor(Color.argb(110, 255, 255, 255));
        backLinePaint.setStrokeWidth(2f);

        slotPaint.setStyle(Paint.Style.STROKE);
        slotPaint.setStrokeWidth(3f);
        slotPaint.setColor(Color.argb(90, 255, 255, 255));
        slotTextPaint.setColor(Color.argb(70, 255, 255, 255));
        slotTextPaint.setTextAlign(Paint.Align.CENTER);
        slotTextPaint.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));

        textPaint.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        shadowPaint.setColor(Color.argb(70, 0, 0, 0));
    }

    public void setListener(Listener l) {
        listener = l;
    }

    /** Call after changing the game from outside (new game, undo, restore). */
    public void refresh() {
        removeCallbacks(autoCompleteRunnable);
        dragPile = -1;
        dragging = false;
        invalidate();
    }

    /** Restarts autocomplete if the game is in a finishable state (e.g. after restoring). */
    public void resumeAutoComplete() {
        if (game.canAutoComplete()) {
            removeCallbacks(autoCompleteRunnable);
            postDelayed(autoCompleteRunnable, AUTOCOMPLETE_START_MS);
        }
    }

    private void notifyChanged() {
        invalidate();
        if (listener != null) listener.onStateChanged();
        if (game.isWon()) {
            if (listener != null) listener.onWin();
        } else if (game.canAutoComplete()) {
            removeCallbacks(autoCompleteRunnable);
            postDelayed(autoCompleteRunnable, AUTOCOMPLETE_START_MS);
        }
    }

    // ---------------------------------------------------------------- layout

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        gap = w * 0.018f;
        float byWidth = (w - gap * 8) / 7f;
        // On wide or short screens, limit card size so the stock row plus a tall column fit.
        float byHeight = h / (CARD_RATIO * 3.2f);
        cw = Math.min(byWidth, byHeight);
        originX = (w - (cw * 7 + gap * 6)) / 2f;
        ch = cw * CARD_RATIO;
        radius = cw * 0.09f;
        topY = gap * 2;
        tabY = topY + ch + gap * 3;
        downOffset = ch * 0.11f;
        upOffset = ch * 0.27f;
        slotTextPaint.setTextSize(cw * 0.6f);
        bgPaint.setShader(new RadialGradient(w / 2f, h / 2.5f, Math.max(w, h) * 0.8f,
                Color.rgb(64, 150, 40), Color.rgb(24, 80, 18), Shader.TileMode.CLAMP));
    }

    private float colX(int col) {
        return originX + col * (cw + gap);
    }

    /** Shrinks the overlaps of a column so it fits on screen. */
    private float columnScale(List<Card> col) {
        float total = 0;
        for (int i = 0; i < col.size() - 1; i++) total += col.get(i).faceUp ? upOffset : downOffset;
        float available = getHeight() - tabY - ch - gap;
        return (total > available && total > 0) ? available / total : 1f;
    }

    private float cardY(List<Card> col, int index, float scale) {
        float y = tabY;
        for (int i = 0; i < index; i++) y += (col.get(i).faceUp ? upOffset : downOffset) * scale;
        return y;
    }

    /** X position of waste card {@code k}; in draw-3 mode the last three are fanned. */
    private float wasteX(int k, int size) {
        int visible = Math.min(size, game.getDrawCount() == 3 ? 3 : 1);
        int fromTop = size - 1 - k;
        if (fromTop >= visible) return colX(5) - (visible - 1) * cw * 0.28f;
        return colX(5) - fromTop * cw * 0.28f;
    }

    // --------------------------------------------------------------- drawing

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawRect(0, 0, getWidth(), getHeight(), bgPaint);
        if (cw <= 0) return;

        // Foundations
        for (int f = 0; f < 4; f++) {
            List<Card> p = game.pile(Game.FOUNDATION_0 + f);
            float x = colX(f);
            int visibleTop = topVisibleIndex(Game.FOUNDATION_0 + f, p.size());
            if (visibleTop < 0) {
                drawSlot(canvas, x, topY, "A");
            } else {
                if (visibleTop > 0) drawCard(canvas, p.get(visibleTop - 1), x, topY);
                drawCard(canvas, p.get(visibleTop), x, topY);
            }
        }

        // Waste
        List<Card> waste = game.pile(Game.WASTE);
        int wasteTop = topVisibleIndex(Game.WASTE, waste.size());
        for (int k = Math.max(0, wasteTop - 2); k <= wasteTop; k++) {
            drawCard(canvas, waste.get(k), wasteX(k, wasteTop + 1), topY);
        }

        // Stock
        List<Card> stock = game.pile(Game.STOCK);
        if (stock.isEmpty()) {
            drawSlot(canvas, colX(6), topY, waste.isEmpty() ? "" : "↻");
        } else {
            drawCard(canvas, stock.get(stock.size() - 1), colX(6), topY);
        }

        // Tableau
        for (int c = 0; c < 7; c++) {
            int id = Game.TABLEAU_0 + c;
            List<Card> col = game.pile(id);
            float x = colX(c);
            if (col.isEmpty() || (dragging && dragPile == id && dragIndex == 0)) {
                drawSlot(canvas, x, tabY, "");
            }
            float scale = columnScale(col);
            int end = (dragging && dragPile == id) ? dragIndex : col.size();
            for (int i = 0; i < end; i++) {
                drawCard(canvas, col.get(i), x, cardY(col, i, scale));
            }
        }

        // Dragged cards on top of everything
        if (dragging && dragPile >= 0) {
            List<Card> src = game.pile(dragPile);
            float x = touchX - grabDx;
            float y = touchY - grabDy;
            for (int i = dragIndex; i < src.size(); i++) {
                tmp.set(x + 6, y + 8, x + cw + 6, y + ch + 8);
                canvas.drawRoundRect(tmp, radius, radius, shadowPaint);
                drawCard(canvas, src.get(i), x, y);
                y += upOffset;
            }
        }
    }

    /** Top visible index of a pile, hiding the cards currently being dragged. */
    private int topVisibleIndex(int pileId, int size) {
        if (dragging && dragPile == pileId) return dragIndex - 1;
        return size - 1;
    }

    private void drawSlot(Canvas canvas, float x, float y, String label) {
        tmp.set(x, y, x + cw, y + ch);
        canvas.drawRoundRect(tmp, radius, radius, slotPaint);
        if (!label.isEmpty()) {
            Paint.FontMetrics fm = slotTextPaint.getFontMetrics();
            canvas.drawText(label, x + cw / 2, y + ch / 2 - (fm.ascent + fm.descent) / 2, slotTextPaint);
        }
    }

    private void drawCard(Canvas canvas, Card card, float x, float y) {
        tmp.set(x, y, x + cw, y + ch);
        if (!card.faceUp) {
            canvas.drawRoundRect(tmp, radius, radius, cardPaint);
            float inset = cw * 0.07f;
            tmp.set(x + inset, y + inset, x + cw - inset, y + ch - inset);
            canvas.drawRoundRect(tmp, radius * 0.6f, radius * 0.6f, backPaint);
            canvas.save();
            canvas.clipRect(tmp);
            float step = cw * 0.16f;
            for (float d = -ch; d < cw + ch; d += step) {
                canvas.drawLine(x + d, y, x + d + ch, y + ch, backLinePaint);
                canvas.drawLine(x + d + ch, y, x + d, y + ch, backLinePaint);
            }
            canvas.restore();
            tmp.set(x, y, x + cw, y + ch);
            canvas.drawRoundRect(tmp, radius, radius, borderPaint);
            return;
        }

        canvas.drawRoundRect(tmp, radius, radius, cardPaint);
        canvas.drawRoundRect(tmp, radius, radius, borderPaint);

        textPaint.setColor(card.isRed() ? Color.rgb(200, 20, 30) : Color.rgb(20, 20, 20));

        // Corner: rank on the left, small suit on the right
        float pad = cw * 0.07f;
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setTextSize(cw * 0.30f);
        canvas.drawText(card.rankLabel(), x + pad, y + pad + cw * 0.26f, textPaint);
        textPaint.setTextAlign(Paint.Align.RIGHT);
        textPaint.setTextSize(cw * 0.26f);
        canvas.drawText(card.suitSymbol(), x + cw - pad, y + pad + cw * 0.24f, textPaint);

        // Centre: big suit, or the letter for face cards
        textPaint.setTextAlign(Paint.Align.CENTER);
        if (card.rank > 10) {
            textPaint.setTextSize(cw * 0.55f);
            canvas.drawText(card.rankLabel(), x + cw / 2, y + ch * 0.68f, textPaint);
            textPaint.setTextSize(cw * 0.30f);
            canvas.drawText(card.suitSymbol(), x + cw / 2, y + ch * 0.92f, textPaint);
        } else {
            textPaint.setTextSize(cw * 0.62f);
            canvas.drawText(card.suitSymbol(), x + cw / 2, y + ch * 0.78f, textPaint);
        }
    }

    // ----------------------------------------------------------------- input

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                removeCallbacks(autoCompleteRunnable);
                downX = touchX = x;
                downY = touchY = y;
                dragging = false;
                dragPile = -1;
                if (hit(x, y, colX(6), topY)) {
                    if (game.drawFromStock()) {
                        if (listener != null) listener.onStockDrawn();
                        notifyChanged();
                    }
                    return true;
                }
                pickUp(x, y);
                return true;

            case MotionEvent.ACTION_MOVE:
                touchX = x;
                touchY = y;
                if (dragPile >= 0 && !dragging && Math.hypot(x - downX, y - downY) > touchSlop) {
                    dragging = true;
                }
                if (dragging) invalidate();
                return true;

            case MotionEvent.ACTION_UP:
                if (dragPile >= 0) {
                    boolean changed;
                    if (dragging) {
                        int target = dropTarget();
                        changed = target >= 0 && game.move(dragPile, dragIndex, target);
                    } else {
                        performClick();
                        changed = game.autoMove(dragPile, dragIndex);
                    }
                    dragging = false;
                    dragPile = -1;
                    if (changed) {
                        if (listener != null) listener.onCardsPlaced();
                        notifyChanged();
                    } else {
                        invalidate();
                    }
                }
                return true;

            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                dragPile = -1;
                invalidate();
                return true;

            default:
                return super.onTouchEvent(e);
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private boolean hit(float px, float py, float x, float y) {
        return px >= x && px <= x + cw && py >= y && py <= y + ch;
    }

    /** Finds the touched card and records where it was grabbed. */
    private void pickUp(float px, float py) {
        List<Card> waste = game.pile(Game.WASTE);
        if (!waste.isEmpty()) {
            int top = waste.size() - 1;
            float wx = wasteX(top, waste.size());
            if (hit(px, py, wx, topY)) {
                setGrab(Game.WASTE, top, wx, topY, px, py);
                return;
            }
        }
        for (int f = 0; f < 4; f++) {
            List<Card> p = game.pile(Game.FOUNDATION_0 + f);
            if (!p.isEmpty() && hit(px, py, colX(f), topY)) {
                setGrab(Game.FOUNDATION_0 + f, p.size() - 1, colX(f), topY, px, py);
                return;
            }
        }
        for (int c = 0; c < 7; c++) {
            float x = colX(c);
            if (px < x || px > x + cw) continue;
            List<Card> col = game.pile(Game.TABLEAU_0 + c);
            float scale = columnScale(col);
            for (int i = col.size() - 1; i >= 0; i--) {
                float cy = cardY(col, i, scale);
                if (py >= cy && py <= cy + ch) {
                    if (col.get(i).faceUp) setGrab(Game.TABLEAU_0 + c, i, x, cy, px, py);
                    return;
                }
            }
        }
    }

    private void setGrab(int pile, int index, float cardX, float cardY, float px, float py) {
        dragPile = pile;
        dragIndex = index;
        grabDx = px - cardX;
        grabDy = py - cardY;
    }

    /** Target pile under the centre of the dragged card, or -1. */
    private int dropTarget() {
        float cx = touchX - grabDx + cw / 2;
        float cy = touchY - grabDy + ch / 2;
        int col = (int) Math.floor((cx - originX + gap / 2) / (cw + gap));
        if (col < 0 || col > 6) return -1;
        if (cy < tabY - gap) {
            return col < 4 ? Game.FOUNDATION_0 + col : -1;
        }
        return Game.TABLEAU_0 + col;
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(autoCompleteRunnable);
        super.onDetachedFromWindow();
    }
}
