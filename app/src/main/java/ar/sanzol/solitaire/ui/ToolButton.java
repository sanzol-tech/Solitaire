package ar.sanzol.solitaire.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Bottom-bar button: icon above a short label, with a rounded ripple. */
public final class ToolButton extends LinearLayout {

    private final ImageView icon;
    private final TextView label;

    public ToolButton(Context context, int iconRes, int textColor) {
        super(context);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER);
        setPadding(0, dp(6), 0, dp(6));
        setClickable(true);
        setFocusable(true);

        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(dp(12));
        setBackground(new RippleDrawable(
                ColorStateList.valueOf(Color.argb(60, 255, 255, 255)), null, mask));

        icon = new ImageView(context);
        icon.setImageResource(iconRes);
        icon.setImageTintList(ColorStateList.valueOf(textColor));
        icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(icon, new LayoutParams(dp(26), dp(26)));

        label = new TextView(context);
        label.setTextColor(textColor);
        label.setTextSize(12);
        label.setGravity(Gravity.CENTER);
        LayoutParams lp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(2);
        addView(label, lp);
    }

    public void setIcon(int iconRes) {
        icon.setImageResource(iconRes);
    }

    public void setLabel(CharSequence text) {
        label.setText(text);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        setAlpha(enabled ? 1f : 0.35f);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
