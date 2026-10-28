package ar.sanzol.solitaire.game;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Klondike solitaire rules and state. Pure Java, no Android dependencies.
 *
 * <p>Pile ids:
 * <ul>
 *   <li>0 = stock</li>
 *   <li>1 = waste</li>
 *   <li>2..5 = foundations</li>
 *   <li>6..12 = tableau columns</li>
 * </ul>
 */
public final class Game {

    public static final int STOCK = 0;
    public static final int WASTE = 1;
    public static final int FOUNDATION_0 = 2;
    public static final int TABLEAU_0 = 6;
    public static final int PILE_COUNT = 13;

    public static final int MAX_UNDO = 300;

    private List<List<Card>> piles;
    private final Deque<Snapshot> undoStack = new ArrayDeque<>(); // newest first
    private int drawCount = 1;
    private int moves = 0;

    /** Immutable copy of the board used for undo history. */
    static final class Snapshot {
        final List<List<Card>> piles;
        final int moves;

        Snapshot(List<List<Card>> piles, int moves) {
            this.piles = piles;
            this.moves = moves;
        }
    }

    /** Creates a game and deals it with the given seed. */
    public Game(long seed) {
        newGame(seed);
    }

    /** Restores a game; used by {@link GameCodec}. History is ordered oldest first. */
    Game(List<List<Card>> piles, int drawCount, int moves, List<Snapshot> historyOldestFirst) {
        validateLayout(piles);
        if (drawCount != 1 && drawCount != 3) throw new IllegalArgumentException("drawCount " + drawCount);
        if (moves < 0) throw new IllegalArgumentException("moves " + moves);
        this.piles = piles;
        this.drawCount = drawCount;
        this.moves = moves;
        for (Snapshot s : historyOldestFirst) {
            validateLayout(s.piles);
            undoStack.push(s);
        }
        trimUndo();
    }

    // ---------------------------------------------------------------- game

    public void newGame(long seed) {
        List<List<Card>> fresh = emptyPiles();

        List<Card> deck = new ArrayList<>(Card.DECK_SIZE);
        for (int id = 0; id < Card.DECK_SIZE; id++) deck.add(Card.fromId(id, false));
        Collections.shuffle(deck, new Random(seed));

        int idx = 0;
        for (int col = 0; col < 7; col++) {
            List<Card> column = fresh.get(TABLEAU_0 + col);
            for (int k = 0; k <= col; k++) {
                Card c = deck.get(idx++);
                c.faceUp = (k == col);
                column.add(c);
            }
        }
        List<Card> stock = fresh.get(STOCK);
        while (idx < deck.size()) stock.add(deck.get(idx++));

        piles = fresh;
        undoStack.clear();
        moves = 0;
    }

    /** Read-only view of a pile. Index 0 is the bottom card. */
    public List<Card> pile(int id) {
        return Collections.unmodifiableList(piles.get(id));
    }

    public int getDrawCount() {
        return drawCount;
    }

    /** Changes the draw mode. Callers normally deal a new game right after. */
    public void setDrawCount(int n) {
        drawCount = (n == 3) ? 3 : 1;
    }

    public int getMoves() {
        return moves;
    }

    public static boolean isFoundation(int id) {
        return id >= FOUNDATION_0 && id < TABLEAU_0;
    }

    public static boolean isTableau(int id) {
        return id >= TABLEAU_0 && id < PILE_COUNT;
    }

    // --------------------------------------------------------------- stock

    /** Draws 1 or 3 cards to the waste, or recycles the waste when the stock is empty. */
    public boolean drawFromStock() {
        List<Card> stock = piles.get(STOCK);
        List<Card> waste = piles.get(WASTE);
        if (stock.isEmpty() && waste.isEmpty()) return false;

        saveState();
        if (stock.isEmpty()) {
            for (int i = waste.size() - 1; i >= 0; i--) {
                Card c = waste.get(i);
                c.faceUp = false;
                stock.add(c);
            }
            waste.clear();
        } else {
            for (int i = 0; i < drawCount && !stock.isEmpty(); i++) {
                Card c = stock.remove(stock.size() - 1);
                c.faceUp = true;
                waste.add(c);
            }
        }
        moves++;
        return true;
    }

    // --------------------------------------------------------------- moves

    /** Can the cards from {@code fromIndex} to the top of pile {@code from} go onto pile {@code to}? */
    public boolean canMove(int from, int fromIndex, int to) {
        if (!isValidPile(from) || !isValidPile(to)) return false;
        if (from == to || from == STOCK || to == STOCK || to == WASTE) return false;
        if (isFoundation(from) && isFoundation(to)) return false;

        List<Card> src = piles.get(from);
        if (fromIndex < 0 || fromIndex >= src.size()) return false;
        int count = src.size() - fromIndex;

        // Only the top card may leave the waste or a foundation.
        if ((from == WASTE || isFoundation(from)) && count != 1) return false;
        // A moved stack must itself be a face-up, descending, alternating-colour run.
        if (!isValidRun(src, fromIndex)) return false;

        Card moving = src.get(fromIndex);
        List<Card> dst = piles.get(to);
        if (isFoundation(to)) {
            if (count != 1) return false;
            if (dst.isEmpty()) return moving.rank == 1;
            Card top = dst.get(dst.size() - 1);
            return top.suit == moving.suit && moving.rank == top.rank + 1;
        }
        // Tableau destination
        if (dst.isEmpty()) return moving.rank == 13;
        Card top = dst.get(dst.size() - 1);
        return top.faceUp && top.isRed() != moving.isRed() && moving.rank == top.rank - 1;
    }

    public boolean move(int from, int fromIndex, int to) {
        if (!canMove(from, fromIndex, to)) return false;
        saveState();
        List<Card> src = piles.get(from);
        List<Card> dst = piles.get(to);
        List<Card> moving = new ArrayList<>(src.subList(fromIndex, src.size()));
        src.subList(fromIndex, src.size()).clear();
        dst.addAll(moving);
        if (isTableau(from) && !src.isEmpty()) {
            src.get(src.size() - 1).faceUp = true;
        }
        moves++;
        return true;
    }

    /**
     * Tap-to-move: tries a foundation first, then a non-empty tableau column, then an
     * empty column (except for a king that already sits at the bottom of a column).
     */
    public boolean autoMove(int from, int fromIndex) {
        List<Card> src = piles.get(from);
        if (fromIndex == src.size() - 1) {
            for (int f = FOUNDATION_0; f < TABLEAU_0; f++) {
                if (move(from, fromIndex, f)) return true;
            }
        }
        for (int pass = 0; pass < 2; pass++) {
            boolean wantEmpty = pass == 1;
            for (int t = TABLEAU_0; t < PILE_COUNT; t++) {
                if (t == from || piles.get(t).isEmpty() != wantEmpty) continue;
                if (wantEmpty && isTableau(from) && fromIndex == 0) continue;
                if (move(from, fromIndex, t)) return true;
            }
        }
        return false;
    }

    // -------------------------------------------------- autocomplete / end

    public boolean isWon() {
        for (int f = FOUNDATION_0; f < TABLEAU_0; f++) {
            if (piles.get(f).size() != Card.RANKS) return false;
        }
        return true;
    }

    /** Every card is visible and stock/waste are empty: the game can finish itself. */
    public boolean canAutoComplete() {
        if (isWon()) return false;
        if (!piles.get(STOCK).isEmpty() || !piles.get(WASTE).isEmpty()) return false;
        for (int t = TABLEAU_0; t < PILE_COUNT; t++) {
            for (Card c : piles.get(t)) if (!c.faceUp) return false;
        }
        return true;
    }

    /** Performs a single tableau-to-foundation move. Returns false when none is possible. */
    public boolean autoCompleteStep() {
        for (int t = TABLEAU_0; t < PILE_COUNT; t++) {
            List<Card> col = piles.get(t);
            if (col.isEmpty()) continue;
            for (int f = FOUNDATION_0; f < TABLEAU_0; f++) {
                if (move(t, col.size() - 1, f)) return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- undo

    private void saveState() {
        undoStack.push(new Snapshot(copyPiles(piles), moves));
        trimUndo();
    }

    private void trimUndo() {
        while (undoStack.size() > MAX_UNDO) undoStack.removeLast();
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean undo() {
        if (undoStack.isEmpty()) return false;
        Snapshot s = undoStack.pop();
        piles = copyPiles(s.piles);
        moves = s.moves;
        return true;
    }

    // ------------------------------------------------------- package access

    List<List<Card>> rawPiles() {
        return piles;
    }

    /** Undo history ordered oldest first. */
    List<Snapshot> historyOldestFirst() {
        List<Snapshot> list = new ArrayList<>(undoStack);
        Collections.reverse(list);
        return list;
    }

    // ----------------------------------------------------------- invariants

    private static boolean isValidPile(int id) {
        return id >= 0 && id < PILE_COUNT;
    }

    /** True if every card from {@code fromIndex} is face up and forms a descending alternating run. */
    static boolean isValidRun(List<Card> pile, int fromIndex) {
        for (int i = fromIndex; i < pile.size(); i++) {
            Card c = pile.get(i);
            if (!c.faceUp) return false;
            if (i > fromIndex) {
                Card below = pile.get(i - 1);
                if (c.rank != below.rank - 1 || c.isRed() == below.isRed()) return false;
            }
        }
        return true;
    }

    /**
     * Checks that a board is one Klondike can actually reach: 13 piles, 52 unique cards,
     * face-down stock, face-up waste, ordered single-suit foundations, and tableau columns
     * made of face-down cards followed by a valid face-up run with a face-up top card.
     *
     * @throws IllegalArgumentException describing the first violation found
     */
    static void validateLayout(List<List<Card>> piles) {
        if (piles == null || piles.size() != PILE_COUNT) {
            throw new IllegalArgumentException("expected " + PILE_COUNT + " piles");
        }
        Set<Integer> seen = new HashSet<>();
        for (List<Card> p : piles) {
            for (Card c : p) {
                if (!seen.add(c.id())) throw new IllegalArgumentException("duplicate card " + c);
            }
        }
        if (seen.size() != Card.DECK_SIZE) {
            throw new IllegalArgumentException("expected 52 cards, found " + seen.size());
        }
        for (Card c : piles.get(STOCK)) {
            if (c.faceUp) throw new IllegalArgumentException("face-up card in stock");
        }
        for (Card c : piles.get(WASTE)) {
            if (!c.faceUp) throw new IllegalArgumentException("face-down card in waste");
        }
        for (int f = FOUNDATION_0; f < TABLEAU_0; f++) {
            List<Card> p = piles.get(f);
            for (int i = 0; i < p.size(); i++) {
                Card c = p.get(i);
                if (!c.faceUp || c.rank != i + 1 || c.suit != p.get(0).suit) {
                    throw new IllegalArgumentException("invalid foundation " + f);
                }
            }
        }
        for (int t = TABLEAU_0; t < PILE_COUNT; t++) {
            List<Card> p = piles.get(t);
            if (p.isEmpty()) continue;
            int firstUp = 0;
            while (firstUp < p.size() && !p.get(firstUp).faceUp) firstUp++;
            if (firstUp == p.size()) throw new IllegalArgumentException("column " + t + " top is face down");
            if (!isValidRun(p, firstUp)) throw new IllegalArgumentException("invalid run in column " + t);
        }
    }

    private static List<List<Card>> emptyPiles() {
        List<List<Card>> list = new ArrayList<>(PILE_COUNT);
        for (int i = 0; i < PILE_COUNT; i++) list.add(new ArrayList<>());
        return list;
    }

    static List<List<Card>> copyPiles(List<List<Card>> src) {
        List<List<Card>> copy = new ArrayList<>(src.size());
        for (List<Card> p : src) {
            List<Card> pc = new ArrayList<>(p.size());
            for (Card c : p) pc.add(c.copy());
            copy.add(pc);
        }
        return copy;
    }
}
