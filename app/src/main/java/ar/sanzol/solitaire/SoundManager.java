package ar.sanzol.solitaire;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

/** Short, low-latency sound effects backed by {@link SoundPool}. */
final class SoundManager {

    static final int PLACE = 0;   // card placed
    static final int FLIP = 1;    // card drawn from the stock / undo
    static final int SHUFFLE = 2; // new game
    static final int WIN = 3;     // victory

    private final SoundPool pool;
    private final int[] ids = new int[4];
    private boolean enabled = true;

    SoundManager(Context context) {
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        pool = new SoundPool.Builder()
                .setMaxStreams(6)
                .setAudioAttributes(attrs)
                .build();
        ids[PLACE] = pool.load(context, R.raw.card_place, 1);
        ids[FLIP] = pool.load(context, R.raw.card_flip, 1);
        ids[SHUFFLE] = pool.load(context, R.raw.shuffle, 1);
        ids[WIN] = pool.load(context, R.raw.win, 1);
    }

    /** Plays a sound; silently ignored while muted or before the sample has loaded. */
    void play(int sound) {
        if (!enabled) return;
        float volume = (sound == WIN) ? 0.8f : 1f;
        // Slight pitch variation so repeated card placements don't sound identical.
        float rate = (sound == PLACE) ? 0.94f + (float) Math.random() * 0.12f : 1f;
        pool.play(ids[sound], volume, volume, 1, 0, rate);
    }

    boolean isEnabled() {
        return enabled;
    }

    void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    void release() {
        pool.release();
    }
}
