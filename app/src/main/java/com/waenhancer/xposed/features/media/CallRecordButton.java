package com.waenhancer.xposed.features.media;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.Locale;

/**
 * A small record button laid over WhatsApp's call screen: a translucent dark circle with a
 * thin white ring, showing a red dot (start) or a red square and the elapsed time (stop). It is
 * added on top of the screen's content, so WhatsApp's own layout is never changed.
 */
@SuppressLint("ViewConstructor")
final class CallRecordButton extends View {

    interface Listener { void onToggle(); }

    private static final String TAG_OVERLAY = "waex_call_record_button";

    private final Paint background = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mark = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF square = new RectF();
    private boolean recording;
    private long recordingSince;

    private CallRecordButton(Context context, Listener listener) {
        super(context);
        background.setColor(0x66000000);
        ring.setStyle(Paint.Style.STROKE);
        ring.setColor(Color.WHITE);
        ring.setStrokeWidth(dp(2));
        mark.setColor(0xFFFF3B30);
        label.setColor(Color.WHITE);
        label.setTextAlign(Paint.Align.CENTER);
        label.setTextSize(dp(11));
        label.setFakeBoldText(true);
        setOnClickListener(v -> listener.onToggle());
        setContentDescription("Record call");
    }

    /** Shows the button on this Activity (once) and returns it; null if the screen has no content view. */
    @Nullable
    static CallRecordButton attach(@NonNull Activity activity, @NonNull Listener listener) {
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null) return null;
        View existing = content.findViewWithTag(TAG_OVERLAY);
        if (existing instanceof CallRecordButton) return (CallRecordButton) existing;
        CallRecordButton button = new CallRecordButton(activity, listener);
        button.setTag(TAG_OVERLAY);
        int size = (int) button.dp(52);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(size, size + (int) button.dp(18),
                Gravity.TOP | Gravity.END);
        params.topMargin = (int) button.dp(96);
        params.rightMargin = (int) button.dp(16);
        content.addView(button, params);
        return button;
    }

    /** Call screens by Activity name; the call list and call history are excluded. */
    static boolean isCallScreenName(@NonNull String simpleName) {
        String name = simpleName.toLowerCase(Locale.ROOT);
        if (name.contains("calllog") || name.contains("callslist") || name.contains("callhistory")) return false;
        return name.contains("voip") || name.endsWith("callactivity") || name.contains("callscreen")
                || name.contains("incall") || name.contains("ongoingcall");
    }

    static void detach(@Nullable WeakReference<CallRecordButton> reference) {
        CallRecordButton button = reference == null ? null : reference.get();
        if (button != null && button.getParent() instanceof ViewGroup) {
            ((ViewGroup) button.getParent()).removeView(button);
        }
    }

    void setRecording(boolean value) {
        if (recording == value) return;
        recording = value;
        recordingSince = SystemClock.elapsedRealtime();
        setContentDescription(value ? "Stop recording" : "Record call");
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        float size = Math.min(getWidth(), getHeight() - dp(18));
        float cx = getWidth() / 2f;
        float cy = size / 2f;
        float radius = size / 2f - dp(2);
        canvas.drawCircle(cx, cy, radius, background);
        canvas.drawCircle(cx, cy, radius - dp(1), ring);
        if (recording) {
            float half = radius * 0.32f;
            square.set(cx - half, cy - half, cx + half, cy + half);
            canvas.drawRoundRect(square, dp(3), dp(3), mark);
            long seconds = (SystemClock.elapsedRealtime() - recordingSince) / 1000;
            canvas.drawText(String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60),
                    cx, size + dp(14), label);
            postInvalidateDelayed(1000); // Advance the timer while recording.
        } else {
            canvas.drawCircle(cx, cy, radius * 0.42f, mark);
        }
    }

    private float dp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }
}
