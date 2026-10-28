package ar.sanzol.solitaire;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Random;

import ar.sanzol.solitaire.game.Game;
import ar.sanzol.solitaire.game.SavedGame;
import ar.sanzol.solitaire.ui.SolitaireView;
import ar.sanzol.solitaire.ui.ToolButton;

/**
 * Hosts the table, the info bar and the toolbar, and owns the game lifecycle.
 *
 * <p>Resume policy: the current game (board, undo history, draw mode, elapsed time) is
 * saved in {@link #onPause()}. On start, an unfinished saved game is always resumed. A
 * finished game is only restored when the activity is being recreated (configuration
 * change or process death while in the background), so the victory dialog can be shown
 * again; a fresh launch after a win deals a new game. Missing or unreadable saves also
 * deal a new game.
 */
public class MainActivity extends Activity implements SolitaireView.Listener {

    private static final int TABLE_DARK = Color.rgb(24, 80, 18);
    private static final int BAR_COLOR = Color.rgb(15, 52, 11);
    private static final int TEXT_COLOR = Color.argb(235, 255, 255, 255);

    private static final String PREFS = "solitaire";
    private static final String PREF_DRAW_COUNT = "drawCount";
    private static final String PREF_SOUND = "sound";

    private final Random random = new SecureRandom();

    private Game game;
    private GameStore store;
    private SolitaireView board;
    private SoundManager sounds;
    private SharedPreferences prefs;

    private LinearLayout toolbar;
    private TextView movesText, timeText;
    private ToolButton undoButton, drawButton, soundButton;
    private AlertDialog dialog;
    private boolean winShown;

    // Clock: starts with the first move, pauses while the app is in the background.
    private long elapsedMs;
    private long runningSince = -1;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateTime();
            handler.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        store = new GameStore(this);
        sounds = new SoundManager(this);
        sounds.setEnabled(prefs.getBoolean(PREF_SOUND, true));

        boolean recreating = savedInstanceState != null;
        SavedGame saved = store.load();
        if (saved != null && (!saved.game().isWon() || recreating)) {
            game = saved.game();
            elapsedMs = saved.elapsedMs();
        } else {
            game = new Game(random.nextLong());
            game.setDrawCount(prefs.getInt(PREF_DRAW_COUNT, 1));
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(TABLE_DARK);

        root.addView(buildInfoBar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        board = new SolitaireView(this, game);
        board.setListener(this);
        root.addView(board, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        toolbar = buildToolbar();
        root.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
        setupEdgeToEdge(root);
        onStateChanged();

        if (game.isWon()) {
            showWinDialog(false);
        } else {
            board.resumeAutoComplete();
        }
    }

    // ------------------------------------------------------------------- UI

    private View buildInfoBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), dp(10), dp(16), dp(2));

        movesText = makeInfoText();
        timeText = makeInfoText();
        timeText.setGravity(Gravity.END);

        bar.addView(movesText, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        bar.addView(timeText, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return bar;
    }

    private TextView makeInfoText() {
        TextView t = new TextView(this);
        t.setTextColor(TEXT_COLOR);
        t.setTextSize(15);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setShadowLayer(3, 0, 1, Color.argb(120, 0, 0, 0));
        return t;
    }

    private LinearLayout buildToolbar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(BAR_COLOR);
        bar.setPadding(dp(4), dp(4), dp(4), dp(4));

        ToolButton newButton = new ToolButton(this, R.drawable.ic_new, TEXT_COLOR);
        newButton.setLabel(getString(R.string.action_new));
        newButton.setOnClickListener(v -> confirm(getString(R.string.confirm_new_game), this::newGame));

        undoButton = new ToolButton(this, R.drawable.ic_undo, TEXT_COLOR);
        undoButton.setLabel(getString(R.string.action_undo));
        undoButton.setOnClickListener(v -> {
            if (game.undo()) {
                if (!game.isWon()) winShown = false;
                sounds.play(SoundManager.FLIP);
                board.refresh();
                onStateChanged();
            }
        });

        drawButton = new ToolButton(this, R.drawable.ic_cards, TEXT_COLOR);
        drawButton.setOnClickListener(v -> {
            int next = game.getDrawCount() == 1 ? 3 : 1;
            confirm(getString(R.string.confirm_draw_mode, next), () -> {
                prefs.edit().putInt(PREF_DRAW_COUNT, next).apply();
                game.setDrawCount(next);
                newGame();
            });
        });

        soundButton = new ToolButton(this, R.drawable.ic_sound_on, TEXT_COLOR);
        soundButton.setOnClickListener(v -> {
            sounds.setEnabled(!sounds.isEnabled());
            prefs.edit().putBoolean(PREF_SOUND, sounds.isEnabled()).apply();
            updateSoundButton();
            sounds.play(SoundManager.PLACE);
        });

        for (ToolButton b : new ToolButton[]{newButton, undoButton, drawButton, soundButton}) {
            bar.addView(b, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        updateSoundButton();
        return bar;
    }

    private void updateSoundButton() {
        boolean on = sounds.isEnabled();
        soundButton.setIcon(on ? R.drawable.ic_sound_on : R.drawable.ic_sound_off);
        soundButton.setLabel(getString(on ? R.string.action_sound_on : R.string.action_sound_off));
    }

    /** Draws behind the system bars and pads the content so nothing is covered. */
    private void setupEdgeToEdge(View root) {
        Window w = getWindow();
        w.setDecorFitsSystemWindows(false);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            // From API 35 edge-to-edge is enforced and these colours are ignored.
            w.setStatusBarColor(Color.TRANSPARENT);
            w.setNavigationBarColor(BAR_COLOR);
        }
        w.setNavigationBarContrastEnforced(false);

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets i = insets.getInsets(WindowInsets.Type.systemBars()
                    | WindowInsets.Type.displayCutout());
            v.setPadding(i.left, i.top, i.right, 0);
            // The toolbar paints the bottom inset so its colour runs to the screen edge.
            toolbar.setPadding(dp(4), dp(4), dp(4), dp(4) + i.bottom);
            return insets;
        });
        root.requestApplyInsets();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    // ----------------------------------------------------------------- game

    private void confirm(String message, Runnable action) {
        if (game.getMoves() == 0 || game.isWon()) {
            action.run();
            return;
        }
        dismissDialog();
        dialog = new AlertDialog.Builder(this)
                .setMessage(message)
                .setPositiveButton(R.string.dialog_yes, (d, which) -> action.run())
                .setNegativeButton(R.string.dialog_no, null)
                .show();
    }

    private void newGame() {
        game.newGame(random.nextLong());
        winShown = false;
        elapsedMs = 0;
        runningSince = -1;
        sounds.play(SoundManager.SHUFFLE);
        board.refresh();
        onStateChanged();
    }

    @Override
    public void onCardsPlaced() {
        sounds.play(SoundManager.PLACE);
    }

    @Override
    public void onStockDrawn() {
        sounds.play(SoundManager.FLIP);
    }

    @Override
    public void onStateChanged() {
        movesText.setText(getString(R.string.moves_format, game.getMoves()));
        undoButton.setEnabled(game.canUndo());
        drawButton.setLabel(getString(R.string.action_draw_format, game.getDrawCount()));
        if (game.getMoves() > 0 && runningSince < 0 && !game.isWon()) {
            runningSince = SystemClock.elapsedRealtime();
        }
        updateTime();
    }

    @Override
    public void onWin() {
        showWinDialog(true);
    }

    private void showWinDialog(boolean celebrate) {
        if (winShown) return;
        winShown = true;
        stopClock();
        if (celebrate) sounds.play(SoundManager.WIN);
        dismissDialog();
        dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.win_title)
                .setMessage(getResources().getQuantityString(R.plurals.win_message,
                        game.getMoves(), game.getMoves(), formatTime(elapsedMs)))
                .setPositiveButton(R.string.win_new_game, (d, w) -> newGame())
                .setNegativeButton(R.string.win_close, null)
                .show();
    }

    private void dismissDialog() {
        if (dialog != null && dialog.isShowing()) dialog.dismiss();
        dialog = null;
    }

    // ---------------------------------------------------------------- clock

    private long currentElapsed() {
        return elapsedMs + (runningSince >= 0 ? SystemClock.elapsedRealtime() - runningSince : 0);
    }

    private void stopClock() {
        elapsedMs = currentElapsed();
        runningSince = -1;
        updateTime();
    }

    private void updateTime() {
        timeText.setText(getString(R.string.time_format, formatTime(currentElapsed())));
    }

    private static String formatTime(long ms) {
        long s = ms / 1000;
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    // ------------------------------------------------------------ lifecycle

    @Override
    protected void onResume() {
        super.onResume();
        if (game.getMoves() > 0 && !game.isWon() && runningSince < 0) {
            runningSince = SystemClock.elapsedRealtime();
        }
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
        stopClock();
        store.save(new SavedGame(game, elapsedMs));
    }

    @Override
    protected void onDestroy() {
        dismissDialog();
        sounds.release();
        super.onDestroy();
    }
}
