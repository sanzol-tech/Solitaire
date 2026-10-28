package ar.sanzol.solitaire.game;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds specific boards for tests. Cards are written as rank + suit letter
 * ({@code "AH"}, {@code "10D"}, {@code "KS"}); a leading {@code ~} marks a face-down card.
 * Cards not placed anywhere go face down into the stock unless {@link #noStock()} is used.
 */
final class TestBoards {

    private final List<List<Card>> piles = new ArrayList<>();
    private final Set<Integer> used = new HashSet<>();
    private boolean fillStock = true;
    private int drawCount = 1;

    TestBoards() {
        for (int i = 0; i < Game.PILE_COUNT; i++) piles.add(new ArrayList<>());
    }

    TestBoards pile(int id, String cards) {
        for (String token : cards.trim().split("\\s+")) {
            if (token.isEmpty()) continue;
            Card c = card(token);
            if (!used.add(c.id())) throw new IllegalArgumentException("card used twice: " + token);
            piles.get(id).add(c);
        }
        return this;
    }

    /** Fills foundation for {@code suit} (0..3) with ace..{@code upToRank}. */
    TestBoards foundation(int suit, int upToRank) {
        StringBuilder sb = new StringBuilder();
        for (int r = 1; r <= upToRank; r++) sb.append(label(r)).append("HDCS".charAt(suit)).append(' ');
        return pile(Game.FOUNDATION_0 + suit, sb.toString());
    }

    TestBoards noStock() {
        fillStock = false;
        return this;
    }

    TestBoards draw(int n) {
        drawCount = n;
        return this;
    }

    Game build() {
        List<List<Card>> copy = Game.copyPiles(piles);
        if (fillStock) {
            for (int id = 0; id < Card.DECK_SIZE; id++) {
                if (!used.contains(id)) copy.get(Game.STOCK).add(0, Card.fromId(id, false));
            }
        }
        return new Game(copy, drawCount, 0, new ArrayList<>());
    }

    static Card card(String token) {
        boolean faceUp = !token.startsWith("~");
        String t = faceUp ? token : token.substring(1);
        char suitChar = t.charAt(t.length() - 1);
        int suit = "HDCS".indexOf(suitChar);
        String r = t.substring(0, t.length() - 1);
        int rank = switch (r) {
            case "A" -> 1;
            case "J" -> 11;
            case "Q" -> 12;
            case "K" -> 13;
            default -> Integer.parseInt(r);
        };
        return new Card(suit, rank, faceUp);
    }

    private static String label(int rank) {
        return switch (rank) {
            case 1 -> "A";
            case 11 -> "J";
            case 12 -> "Q";
            case 13 -> "K";
            default -> String.valueOf(rank);
        };
    }
}
