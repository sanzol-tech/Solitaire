package ar.sanzol.solitaire.game;

/** A French-deck playing card. Only {@link #faceUp} is mutable. */
public final class Card {

    // Suits: 0 = hearts, 1 = diamonds (red), 2 = clubs, 3 = spades (black)
    public static final int SUITS = 4;
    public static final int RANKS = 13;
    public static final int DECK_SIZE = SUITS * RANKS;

    private static final String[] SUIT_SYMBOLS = {"♥", "♦", "♣", "♠"};
    private static final String[] RANK_LABELS =
            {"", "A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K"};

    public final int suit;
    public final int rank; // 1 (ace) .. 13 (king)
    public boolean faceUp;

    public Card(int suit, int rank, boolean faceUp) {
        if (suit < 0 || suit >= SUITS) throw new IllegalArgumentException("suit " + suit);
        if (rank < 1 || rank > RANKS) throw new IllegalArgumentException("rank " + rank);
        this.suit = suit;
        this.rank = rank;
        this.faceUp = faceUp;
    }

    public boolean isRed() {
        return suit < 2;
    }

    /** Unique index in 0..51, used for serialization and duplicate checks. */
    public int id() {
        return suit * RANKS + (rank - 1);
    }

    public static Card fromId(int id, boolean faceUp) {
        if (id < 0 || id >= DECK_SIZE) throw new IllegalArgumentException("card id " + id);
        return new Card(id / RANKS, id % RANKS + 1, faceUp);
    }

    public String rankLabel() {
        return RANK_LABELS[rank];
    }

    public String suitSymbol() {
        return SUIT_SYMBOLS[suit];
    }

    public Card copy() {
        return new Card(suit, rank, faceUp);
    }

    @Override
    public String toString() {
        return rankLabel() + suitSymbol() + (faceUp ? "" : "(down)");
    }
}
