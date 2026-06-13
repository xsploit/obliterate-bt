package com.obliterate.btspam;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

final class ObUi {
    static final int RED = 0xFFFF2222;
    static final int GREEN = 0xFF00FF41;
    static final int PANEL = 0xFF1A1A1A;
    static final int EDIT_TEXT = 0xFFE0E0E0;

    private ObUi() {}

    static int dp(Context context, int px) {
        return (int)(px * context.getResources().getDisplayMetrics().density);
    }

    static TextView label(Context context, String text, int color, int sizeSp) {
        return label(context, text, color, sizeSp, 0, 8, 0, 3);
    }

    static TextView tightLabel(Context context, String text, int color, int sizeSp) {
        return label(context, text, color, sizeSp, 0, 6, 0, 2);
    }

    private static TextView label(Context context, String text, int color, int sizeSp,
                                  int left, int top, int right, int bottom) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(sizeSp);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setPadding(left, top, right, bottom);
        return tv;
    }

    static EditText edit(Context context, String text) {
        EditText edit = new EditText(context);
        edit.setText(text);
        edit.setTextColor(EDIT_TEXT);
        edit.setBackgroundColor(PANEL);
        edit.setTypeface(Typeface.MONOSPACE);
        edit.setPadding(12, 10, 12, 10);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 4, 0, 8);
        edit.setLayoutParams(params);
        return edit;
    }

    static Button fullButton(Context context, String text) {
        Button button = baseButton(context, text, 11, 16, false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 6, 0, 6);
        button.setLayoutParams(params);
        return button;
    }

    static Button wideButton(Context context, String text) {
        return baseButton(context, text, 10, 8, true);
    }

    static Button weightedWideButton(Context context, String text) {
        return weightedWideButton(context, text, 6, 3, 5);
    }

    static Button weightedWideButton(Context context, String text, int horizontalPadding,
                                     int horizontalMargin, int verticalMargin) {
        Button button = baseButton(context, text, 10, horizontalPadding, true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(horizontalMargin, verticalMargin, horizontalMargin, verticalMargin);
        button.setLayoutParams(params);
        return button;
    }

    static void setButtonOn(Button button, String text) {
        if (button == null) return;
        button.setText(text);
        button.setTextColor(GREEN);
    }

    static void resetButton(Button button, String text) {
        if (button == null) return;
        button.setText(text);
        button.setTextColor(RED);
    }

    private static Button baseButton(Context context, String text, int sizeSp,
                                     int horizontalPadding, boolean singleLine) {
        Button button = new Button(context);
        button.setText(text);
        button.setTextColor(RED);
        button.setBackgroundColor(PANEL);
        button.setTypeface(Typeface.MONOSPACE);
        button.setTextSize(sizeSp);
        button.setAllCaps(true);
        button.setPadding(horizontalPadding, 14, horizontalPadding, 14);
        if (singleLine) {
            button.setSingleLine(true);
            button.setEllipsize(TextUtils.TruncateAt.END);
        }
        return button;
    }
}
