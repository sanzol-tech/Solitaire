package ar.sanzol.solitaire;

import android.content.Context;
import android.util.AtomicFile;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import ar.sanzol.solitaire.game.GameCodec;
import ar.sanzol.solitaire.game.SavedGame;

/**
 * Persists the current game in app-private storage. Writes are atomic, so a crash or
 * process kill mid-write leaves the previous save intact. Unreadable saves are discarded.
 */
final class GameStore {

    private static final String TAG = "GameStore";
    private final AtomicFile file;

    GameStore(Context context) {
        file = new AtomicFile(new File(context.getFilesDir(), "game_state.txt"));
    }

    /** Returns the saved game, or {@code null} if there is none or it cannot be used. */
    SavedGame load() {
        try {
            byte[] data = file.readFully();
            return GameCodec.decode(new String(data, StandardCharsets.UTF_8));
        } catch (FileNotFoundException e) {
            return null;
        } catch (IOException | IllegalArgumentException e) {
            Log.w(TAG, "Discarding unreadable saved game", e);
            file.delete();
            return null;
        }
    }

    void save(SavedGame saved) {
        FileOutputStream out = null;
        try {
            out = file.startWrite();
            out.write(GameCodec.encode(saved).getBytes(StandardCharsets.UTF_8));
            file.finishWrite(out);
        } catch (IOException e) {
            if (out != null) file.failWrite(out);
            Log.w(TAG, "Could not save game", e);
        }
    }
}
