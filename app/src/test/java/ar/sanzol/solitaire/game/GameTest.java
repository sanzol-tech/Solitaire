package ar.sanzol.solitaire.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

public class GameTest {

    private static final int T0 = Game.TABLEAU_0;
    private static final int F0 = Game.FOUNDATION_0;

    // ------------------------------------------------------------------ deal

    @Test
    public void dealHas52UniqueCardsInKlondikeLayout() {
        for (long seed = 0; seed < 50; seed++) {
            Game g = new Game(seed);
            Set<Integer> ids = new HashSet<>();
            for (int p = 0; p < Game.PILE_COUNT; p++) for (Card c : g.pile(p)) ids.add(c.id());
            assertEquals(52, ids.size());

            assertEquals(24, g.pile(Game.STOCK).size());
            assertTrue(g.pile(Game.WASTE).isEmpty());
            for (int f = F0; f < T0; f++) assertTrue(g.pile(f).isEmpty());
            for (int col = 0; col < 7; col++) {
                List<Card> column = g.pile(T0 + col);
                assertEquals(col + 1, column.size());
                for (int i = 0; i < column.size(); i++) {
                    assertEquals(i == col, column.get(i).faceUp);
                }
            }
            Game.validateLayout(g.rawPiles());
            assertEquals(0, g.getMoves());
            assertFalse(g.canUndo());
        }
    }

    @Test
    public void sameSeedGivesSameDeal() {
        assertEquals(boardOf(new Game(42)), boardOf(new Game(42)));
        assertNotEquals(boardOf(new Game(42)), boardOf(new Game(43)));
    }

    // ------------------------------------------------------------ legality

    @Test
    public void onlyAceStartsAFoundationAndSameSuitContinuesIt() {
        Game g = new TestBoards().pile(T0, "AH").pile(T0 + 1, "2H").pile(T0 + 2, "2D").build();
        assertFalse(g.canMove(T0 + 1, 0, F0));
        assertTrue(g.move(T0, 0, F0));
        assertFalse(g.canMove(T0 + 2, 0, F0)); // wrong suit
        assertTrue(g.move(T0 + 1, 0, F0));
        assertEquals(2, g.pile(F0).size());
    }

    @Test
    public void onlyKingGoesToEmptyColumn() {
        Game g = new TestBoards().pile(T0, "~3C KS").pile(T0 + 1, "~4C QH").build();
        assertFalse(g.canMove(T0 + 1, 1, T0 + 2));
        assertTrue(g.canMove(T0, 1, T0 + 2));
    }

    @Test
    public void tableauNeedsAlternatingColourAndDescendingRank() {
        Game g = new TestBoards()
                .pile(T0, "8S").pile(T0 + 1, "7H").pile(T0 + 2, "7C").pile(T0 + 3, "6D").build();
        assertTrue(g.canMove(T0 + 1, 0, T0));      // red 7 on black 8
        assertFalse(g.canMove(T0 + 2, 0, T0));     // black 7 on black 8
        assertFalse(g.canMove(T0 + 3, 0, T0));     // 6 on 8
        assertFalse(g.canMove(T0, 0, T0 + 1));     // 8 on 7
    }

    @Test
    public void movingAStackFlipsTheUncoveredCard() {
        Game g = new TestBoards().pile(T0, "~2C 9H 8S").pile(T0 + 1, "10C").build();
        assertTrue(g.move(T0, 1, T0 + 1));
        assertEquals(3, g.pile(T0 + 1).size());
        assertEquals(1, g.pile(T0).size());
        assertTrue(g.pile(T0).get(0).faceUp);
        assertEquals(1, g.getMoves());
    }

    @Test
    public void cannotPickUpFaceDownCards() {
        Game g = new TestBoards().pile(T0, "~2C 9H").pile(T0 + 1, "3D").build();
        assertFalse(g.canMove(T0, 0, T0 + 1));
    }

    @Test
    public void runValidationRejectsBrokenSequences() {
        List<Card> good = cards("9H 8S 7D");
        assertTrue(Game.isValidRun(good, 0));
        assertFalse(Game.isValidRun(cards("9H 8D"), 0));   // same colour
        assertFalse(Game.isValidRun(cards("9H 7S"), 0));   // gap
        assertFalse(Game.isValidRun(cards("~9H 8S"), 0));  // face down
    }

    @Test
    public void wasteAndFoundationReleaseOnlyTheirTopCard() {
        Game g = new TestBoards().pile(Game.WASTE, "5D 4S").foundation(0, 3)
                .pile(T0, "6C").pile(T0 + 1, "4C").pile(T0 + 2, "5H").build();
        assertFalse(g.canMove(Game.WASTE, 0, T0));    // 5D fits on 6C but is under 4S
        assertTrue(g.canMove(Game.WASTE, 1, T0 + 2)); // the top card (4S on 5H) may move
        assertTrue(g.canMove(F0, 2, T0 + 1));         // 3H on 4C
        assertFalse(g.canMove(F0, 1, T0 + 1));        // 2H is not the foundation top
    }

    @Test
    public void rejectsStockWasteAndFoundationToFoundationTargets() {
        Game g = new TestBoards().foundation(0, 1).pile(T0, "KS").build();
        assertFalse(g.canMove(T0, 0, Game.STOCK));
        assertFalse(g.canMove(T0, 0, Game.WASTE));
        assertFalse(g.canMove(F0, 0, F0 + 1));
        assertFalse(g.canMove(T0, 0, T0));
        assertFalse(g.canMove(-1, 0, T0));
        assertFalse(g.canMove(T0, 5, T0 + 1));
    }

    @Test
    public void autoMovePrefersFoundationThenNonEmptyColumn() {
        Game g = new TestBoards().foundation(1, 1).pile(T0, "2D").pile(T0 + 1, "3S").build();
        assertTrue(g.autoMove(T0, 0));
        assertEquals(2, g.pile(F0 + 1).size());

        Game h = new TestBoards().pile(T0, "~4C 6H").pile(T0 + 2, "7S").build();
        assertTrue(h.autoMove(T0, 1));
        assertEquals(2, h.pile(T0 + 2).size());
    }

    @Test
    public void autoMoveDoesNotShuffleBottomKingsBetweenEmptyColumns() {
        Game g = new TestBoards().pile(T0, "KS").build();
        assertFalse(g.autoMove(T0, 0));
    }

    // --------------------------------------------------------- draw / recycle

    @Test
    public void drawOneMovesOneCardFaceUp() {
        Game g = new Game(1);
        Card top = g.pile(Game.STOCK).get(23);
        assertTrue(g.drawFromStock());
        assertEquals(23, g.pile(Game.STOCK).size());
        assertEquals(1, g.pile(Game.WASTE).size());
        assertEquals(top.id(), g.pile(Game.WASTE).get(0).id());
        assertTrue(g.pile(Game.WASTE).get(0).faceUp);
        assertEquals(1, g.getMoves());
    }

    @Test
    public void drawThreeMovesThreeOrWhatIsLeft() {
        Game g = new Game(1);
        g.setDrawCount(3);
        assertTrue(g.drawFromStock());
        assertEquals(3, g.pile(Game.WASTE).size());
        for (int i = 0; i < 7; i++) g.drawFromStock(); // 24 = 8 * 3
        assertEquals(0, g.pile(Game.STOCK).size());

        Game h = new TestBoards().draw(3).noStock().foundation(0, 13).foundation(1, 13)
                .foundation(2, 13).foundation(3, 11).pile(Game.STOCK, "~QS ~KS").build();
        assertTrue(h.drawFromStock());
        assertEquals(2, h.pile(Game.WASTE).size());
    }

    @Test
    public void recyclingTheWasteRestoresTheOriginalStockOrder() {
        Game g = new Game(7);
        List<Integer> before = ids(g.pile(Game.STOCK));
        for (int i = 0; i < 24; i++) g.drawFromStock();
        assertTrue(g.pile(Game.STOCK).isEmpty());
        assertTrue(g.drawFromStock()); // recycle
        assertEquals(before, ids(g.pile(Game.STOCK)));
        assertTrue(g.pile(Game.WASTE).isEmpty());
        for (Card c : g.pile(Game.STOCK)) assertFalse(c.faceUp);
        assertEquals(25, g.getMoves());
    }

    @Test
    public void drawingWithNothingLeftDoesNothing() {
        Game g = new TestBoards().noStock().foundation(0, 13).foundation(1, 13)
                .foundation(2, 13).foundation(3, 12).pile(T0, "KS").build();
        assertFalse(g.drawFromStock());
        assertEquals(0, g.getMoves());
        assertFalse(g.canUndo());
    }

    // ------------------------------------------------------------------ undo

    @Test
    public void undoRestoresEveryPreviousState() {
        Game g = new Game(3);
        List<String> states = new ArrayList<>();
        states.add(boardOf(g));
        for (int i = 0; i < 40; i++) {
            if (!playOneMove(g)) g.drawFromStock();
            states.add(boardOf(g));
        }
        for (int i = states.size() - 2; i >= 0; i--) {
            assertTrue(g.undo());
            assertEquals(states.get(i), boardOf(g));
        }
        assertFalse(g.undo());
        assertEquals(0, g.getMoves());
    }

    @Test
    public void undoHistoryIsBounded() {
        Game g = new Game(5);
        for (int i = 0; i < Game.MAX_UNDO + 50; i++) g.drawFromStock();
        int undone = 0;
        while (g.undo()) undone++;
        assertEquals(Game.MAX_UNDO, undone);
    }

    @Test
    public void newGameClearsHistoryAndMoves() {
        Game g = new Game(5);
        g.drawFromStock();
        g.newGame(6);
        assertEquals(0, g.getMoves());
        assertFalse(g.canUndo());
    }

    // ---------------------------------------------------- victory / autoplay

    @Test
    public void autocompleteFinishesASolvedBoard() {
        Game g = new TestBoards().noStock()
                .foundation(0, 10).foundation(1, 10).foundation(2, 10).foundation(3, 10)
                .pile(T0, "KS QH JS").pile(T0 + 1, "KH QS JH").pile(T0 + 2, "KC QD JC")
                .pile(T0 + 3, "KD QC JD").build();
        assertFalse(g.isWon());
        assertTrue(g.canAutoComplete());
        int steps = 0;
        while (g.autoCompleteStep()) steps++;
        assertEquals(12, steps);
        assertTrue(g.isWon());
        assertFalse(g.canAutoComplete());
    }

    @Test
    public void autocompleteIsUnavailableWithHiddenCardsOrStock() {
        Game hidden = new TestBoards().noStock()
                .foundation(0, 11).foundation(1, 12).foundation(2, 12).foundation(3, 12)
                .pile(T0, "~KH QH").pile(T0 + 1, "KD").pile(T0 + 2, "KC").pile(T0 + 3, "KS").build();
        assertFalse(hidden.canAutoComplete());

        Game withStock = new TestBoards()
                .foundation(0, 12).foundation(1, 12).foundation(2, 12).foundation(3, 11).build();
        assertFalse(withStock.canAutoComplete());
    }

    // ------------------------------------------------------------- misc

    @Test
    public void pileViewsAreReadOnly() {
        Game g = new Game(1);
        assertThrows(UnsupportedOperationException.class, () -> g.pile(T0).clear());
    }

    @Test
    public void layoutValidationRejectsImpossibleBoards() {
        List<List<Card>> piles = new Game(1).rawPiles();
        List<List<Card>> faceUpStock = Game.copyPiles(piles);
        faceUpStock.get(Game.STOCK).get(0).faceUp = true;
        assertThrows(IllegalArgumentException.class, () -> Game.validateLayout(faceUpStock));

        List<List<Card>> hiddenTop = Game.copyPiles(piles);
        hiddenTop.get(T0).get(0).faceUp = false;
        assertThrows(IllegalArgumentException.class, () -> Game.validateLayout(hiddenTop));

        List<List<Card>> missing = Game.copyPiles(piles);
        missing.get(Game.STOCK).remove(0);
        assertThrows(IllegalArgumentException.class, () -> Game.validateLayout(missing));
    }

    // ------------------------------------------------------------ helpers

    /** Plays the first legal non-stock move found; returns false if none exists. */
    static boolean playOneMove(Game g) {
        for (int p = Game.WASTE; p < Game.PILE_COUNT; p++) {
            List<Card> pile = g.pile(p);
            for (int i = 0; i < pile.size(); i++) {
                if (!pile.get(i).faceUp) continue;
                if (Game.isTableau(p) && i == 0 && pile.get(0).rank == 13) continue;
                for (int t = F0; t < Game.PILE_COUNT; t++) {
                    if (Game.isFoundation(p) && Game.isTableau(t)) continue;
                    if (g.move(p, i, t)) return true;
                }
            }
        }
        return false;
    }

    static String boardOf(Game g) {
        return GameCodec.encode(new SavedGame(g, 0)).split("\n")[4] + " m" + g.getMoves();
    }

    private static List<Card> cards(String s) {
        List<Card> list = new ArrayList<>();
        for (String t : s.split(" ")) list.add(TestBoards.card(t));
        return list;
    }

    private static List<Integer> ids(List<Card> cards) {
        List<Integer> list = new ArrayList<>();
        for (Card c : cards) list.add(c.id());
        return list;
    }
}
