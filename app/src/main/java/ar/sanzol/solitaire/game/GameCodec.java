package ar.sanzol.solitaire.game;

import java.util.ArrayList;
import java.util.List;

/**
 * Plain-text serialization of a {@link SavedGame}, including the undo history.
 *
 * <pre>
 * solitaire-state 1
 * draw 1
 * moves 8
 * elapsed 61234
 * board  &lt;piles&gt;
 * undo 7 &lt;piles&gt;        (zero or more, oldest first)
 * </pre>
 *
 * {@code <piles>} is 13 piles separated by {@code /}; each pile is a comma-separated list of
 * cards from bottom to top, written as the card id (0..51) followed by {@code u} (face up) or
 * {@code d} (face down). Decoding validates everything and throws
 * {@link IllegalArgumentException} on any malformed or unreachable state, so a corrupt file
 * can never produce a broken game.
 */
public final class GameCodec {

    static final String HEADER = "solitaire-state 1";

    private GameCodec() {
    }

    public static String encode(SavedGame saved) {
        Game g = saved.game();
        StringBuilder sb = new StringBuilder(4096);
        sb.append(HEADER).append('\n');
        sb.append("draw ").append(g.getDrawCount()).append('\n');
        sb.append("moves ").append(g.getMoves()).append('\n');
        sb.append("elapsed ").append(saved.elapsedMs()).append('\n');
        sb.append("board ");
        appendPiles(sb, g.rawPiles());
        sb.append('\n');
        for (Game.Snapshot s : g.historyOldestFirst()) {
            sb.append("undo ").append(s.moves).append(' ');
            appendPiles(sb, s.piles);
            sb.append('\n');
        }
        return sb.toString();
    }

    public static SavedGame decode(String text) {
        if (text == null) throw new IllegalArgumentException("no data");
        String[] lines = text.split("\n");
        if (lines.length < 5 || !HEADER.equals(lines[0].trim())) {
            throw new IllegalArgumentException("unknown format");
        }
        int draw = parseInt(field(lines[1], "draw"));
        int moves = parseInt(field(lines[2], "moves"));
        long elapsed = parseLong(field(lines[3], "elapsed"));
        if (elapsed < 0) throw new IllegalArgumentException("negative elapsed time");
        List<List<Card>> board = parsePiles(field(lines[4], "board"));

        List<Game.Snapshot> history = new ArrayList<>();
        for (int i = 5; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            String rest = field(line, "undo");
            int space = rest.indexOf(' ');
            if (space < 0) throw new IllegalArgumentException("bad undo line " + i);
            int snapMoves = parseInt(rest.substring(0, space));
            if (snapMoves < 0) throw new IllegalArgumentException("bad undo moves");
            history.add(new Game.Snapshot(parsePiles(rest.substring(space + 1)), snapMoves));
        }
        if (history.size() > Game.MAX_UNDO) {
            history = new ArrayList<>(history.subList(history.size() - Game.MAX_UNDO, history.size()));
        }
        return new SavedGame(new Game(board, draw, moves, history), elapsed);
    }

    // -------------------------------------------------------------- helpers

    private static void appendPiles(StringBuilder sb, List<List<Card>> piles) {
        for (int p = 0; p < piles.size(); p++) {
            if (p > 0) sb.append('/');
            List<Card> pile = piles.get(p);
            for (int i = 0; i < pile.size(); i++) {
                if (i > 0) sb.append(',');
                Card c = pile.get(i);
                sb.append(c.id()).append(c.faceUp ? 'u' : 'd');
            }
        }
    }

    private static List<List<Card>> parsePiles(String s) {
        String[] parts = s.trim().split("/", -1);
        if (parts.length != Game.PILE_COUNT) throw new IllegalArgumentException("expected 13 piles");
        List<List<Card>> piles = new ArrayList<>(Game.PILE_COUNT);
        for (String part : parts) {
            List<Card> pile = new ArrayList<>();
            if (!part.isEmpty()) {
                for (String token : part.split(",", -1)) {
                    if (token.length() < 2) throw new IllegalArgumentException("bad card '" + token + "'");
                    char face = token.charAt(token.length() - 1);
                    if (face != 'u' && face != 'd') throw new IllegalArgumentException("bad face '" + token + "'");
                    int id = parseInt(token.substring(0, token.length() - 1));
                    pile.add(Card.fromId(id, face == 'u'));
                }
            }
            piles.add(pile);
        }
        return piles;
    }

    private static String field(String line, String name) {
        String prefix = name + " ";
        if (!line.startsWith(prefix)) throw new IllegalArgumentException("expected '" + name + "'");
        return line.substring(prefix.length()).trim();
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("bad number '" + s + "'", e);
        }
    }

    private static long parseLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("bad number '" + s + "'", e);
        }
    }
}
