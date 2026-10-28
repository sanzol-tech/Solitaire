package ar.sanzol.solitaire.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GameCodecTest {

    @Test
    public void roundTripPreservesBoardHistoryDrawModeAndClock() {
        Game g = new Game(11);
        g.setDrawCount(3);
        for (int i = 0; i < 30; i++) {
            if (!GameTest.playOneMove(g)) g.drawFromStock();
        }
        String text = GameCodec.encode(new SavedGame(g, 98_765));

        SavedGame restored = GameCodec.decode(text);
        assertEquals(98_765, restored.elapsedMs());
        assertEquals(3, restored.game().getDrawCount());
        assertEquals(g.getMoves(), restored.game().getMoves());
        assertEquals(text, GameCodec.encode(restored));

        // Undo behaves identically on the original and the restored game.
        Game r = restored.game();
        while (g.canUndo()) {
            assertTrue(r.undo());
            g.undo();
            assertEquals(GameTest.boardOf(g), GameTest.boardOf(r));
        }
        assertTrue(!r.canUndo());
    }

    @Test
    public void freshDealRoundTrips() {
        Game g = new Game(1);
        String text = GameCodec.encode(new SavedGame(g, 0));
        assertEquals(text, GameCodec.encode(GameCodec.decode(text)));
    }

    @Test
    public void overlongHistoryIsTrimmedToTheNewestEntries() {
        Game g = new Game(2);
        StringBuilder sb = new StringBuilder(GameCodec.encode(new SavedGame(g, 0)));
        String board = sb.toString().split("\n")[4].substring("board ".length());
        for (int i = 0; i < Game.MAX_UNDO + 20; i++) sb.append("undo 0 ").append(board).append('\n');
        Game restored = GameCodec.decode(sb.toString()).game();
        int n = 0;
        while (restored.undo()) n++;
        assertEquals(Game.MAX_UNDO, n);
    }

    @Test
    public void rejectsMalformedOrImpossibleData() {
        String good = GameCodec.encode(new SavedGame(new Game(4), 10));
        String[] lines = good.split("\n");
        String board = lines[4];

        assertRejected(null);
        assertRejected("");
        assertRejected(good.replace(GameCodec.HEADER, "solitaire-state 99"));
        assertRejected(good.replace("draw 1", "draw 2"));
        assertRejected(good.replace("moves 0", "moves -1"));
        assertRejected(good.replace("elapsed 10", "elapsed x"));
        assertRejected(good.replace("elapsed 10", "elapsed -5"));
        // 12 piles
        assertRejected(good.replace(board, board.substring(0, board.lastIndexOf('/'))));
        // duplicate card: replace the first stock card id with the second one
        String stock = board.substring("board ".length(), board.indexOf('/'));
        String[] stockCards = stock.split(",");
        assertRejected(good.replace(board, board.replaceFirst(stockCards[0] + ",", stockCards[1] + ",")));
        // face-up card in the stock
        assertRejected(good.replace(board, board.replaceFirst(stockCards[0] + ",",
                stockCards[0].replace('d', 'u') + ",")));
        // unknown card id and bad face marker
        assertRejected(good.replace(board, board.replaceFirst(stockCards[0] + ",", "99d,")));
        assertRejected(good.replace(board, board.replaceFirst(stockCards[0] + ",",
                stockCards[0].replace('d', 'x') + ",")));
        // malformed undo line
        assertRejected(good + "undo nonsense\n");
        assertRejected(good + "redo 1 " + board.substring(6) + "\n");
    }

    private static void assertRejected(String text) {
        assertThrows(IllegalArgumentException.class, () -> GameCodec.decode(text));
    }
}
