package ar.sanzol.solitaire.game;

/** A game together with the elapsed play time, as persisted between sessions. */
public record SavedGame(Game game, long elapsedMs) {
}
