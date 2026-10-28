package com.codex.eduke32;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.content.Context;

public final class ControlLayoutActivity extends Activity {
    private static final String PREFS = "touch_controls";
    private static final String[] NAMES = { "FIRE", "JUMP", "USE", "DUCK", "INV", "NEXT", "KICK", "ENTER", "MOVE", "LOOK" };
    private static final String[] PREF_IDS = { "0", "1", "2", "3", "4", "5", "kick", "6", "dpad", "look" };
    private static final float[] DEFAULT_X = { .87f, .74f, .94f, .68f, .83f, .95f, .80f, .78f, .16f, .70f };
    private static final float[] DEFAULT_Y = { .73f, .84f, .53f, .60f, .42f, .34f, .63f, .15f, .72f, .78f };
    private final float[] x = DEFAULT_X.clone();
    private final float[] y = DEFAULT_Y.clone();
    private Preview preview;
    private final float[] sizes={1,1,1,1,1,1,1,1,1,1};
    private android.widget.SeekBar sizeSlider;
    private TextView help;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        loadPositions();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(22, 25, 32));
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(8), dp(4), dp(8), dp(4));

        help = new TextView(this);
        help.setText("Drag to move • Select to resize");
        help.setTextColor(Color.WHITE);
        help.setTextSize(16);
        toolbar.addView(help, new LinearLayout.LayoutParams(0, -2, 1));

        Button reset = new Button(this);
        reset.setText("Reset");
        reset.setOnClickListener(v -> {
            System.arraycopy(DEFAULT_X, 0, x, 0, x.length);
            System.arraycopy(DEFAULT_Y, 0, y, 0, y.length);
            java.util.Arrays.fill(sizes,1f);
            preview.selected=-1; sizeSlider.setEnabled(false);
            savePositions();
            preview.invalidate();
        });
        toolbar.addView(reset);
        Button done = new Button(this);
        done.setText("Done");
        done.setOnClickListener(v -> finish());
        toolbar.addView(done);
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(56)));

        sizeSlider=new android.widget.SeekBar(this);
        sizeSlider.setMax(85); sizeSlider.setProgress(35); sizeSlider.setEnabled(false);
        sizeSlider.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.rgb(230,197,125)));
        sizeSlider.setThumbTintList(android.content.res.ColorStateList.valueOf(Color.rgb(240,220,170)));
        sizeSlider.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(android.widget.SeekBar bar,int value,boolean fromUser) {
                if(fromUser && preview!=null && preview.selected>=0) {
                    sizes[preview.selected]=.65f+value/100f;
                    help.setText(NAMES[preview.selected]+" • "+Math.round(sizes[preview.selected]*100)+"%");
                    preview.invalidate(); savePositions();
                }
            }
            public void onStartTrackingTouch(android.widget.SeekBar bar) { }
            public void onStopTrackingTouch(android.widget.SeekBar bar) { }
        });
        toolbar.addView(sizeSlider,new LinearLayout.LayoutParams(dp(150),-2));
        preview = new Preview(this);
        root.addView(preview, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private int dp(float value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }

    private void loadPositions() {
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        for (int i = 0; i < NAMES.length; i++) {
            String id = PREF_IDS[i];
            x[i] = prefs.getFloat("x_" + id, DEFAULT_X[i]);
            y[i] = prefs.getFloat("y_" + id, DEFAULT_Y[i]);
            sizes[i]=Math.max(.65f,Math.min(1.5f,prefs.getFloat("size_"+id,1f)));
        }
    }

    private void savePositions() {
        android.content.SharedPreferences.Editor editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        for (int i = 0; i < NAMES.length; i++) {
            String id = PREF_IDS[i];
            editor.putFloat("x_" + id, x[i]).putFloat("y_" + id, y[i]).putFloat("size_"+id,sizes[i]);
        }
        editor.apply();
    }

    private final class Preview extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int selected = -1;
        private boolean dragging;
        private float offsetX,offsetY;
        private final ControlArtwork artwork=new ControlArtwork();

        Preview(Context context) { super(context); }

        private float radius() { return Math.max(42f, Math.min(getWidth(), getHeight()) * .078f); }
        private float cx(int i) { return x[i] * getWidth(); }
        private float cy(int i) { return y[i] * getHeight(); }

        private float elementRadius(int i) {
            float min=Math.min(getWidth(),getHeight());
            return sizes[i]*(i==8?Math.max(80f,min*.175f):i==9?Math.max(64f,min*.14f):radius()*(i==7?.82f:1f));
        }
        @Override protected void onDraw(Canvas canvas) {
            canvas.drawColor(Color.rgb(9,11,15));
            for(int i=0;i<NAMES.length;i++) {
                float px=cx(i),py=cy(i),r=elementRadius(i);
                if(i>=8) artwork.stick(canvas,px,py,r,0,0,i==selected);
                else artwork.button(canvas,px,py,r,i,i==selected);
                paint.setColor(i==selected?0xffffdfa0:0xffaab0b7);
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setTextSize(Math.max(12f,radius()*.24f));
                canvas.drawText(NAMES[i]+(i>=8?" (floating)":""),px,py+r+paint.getTextSize()*1.3f,paint);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float px = event.getX(), py = event.getY();
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                float nearest = Float.MAX_VALUE;
                selected = -1;
                for (int i = 0; i < NAMES.length; i++) {
                    float dx = px - cx(i), dy = py - cy(i);
                    float distance = dx * dx + dy * dy;
                    float limit = elementRadius(i)*1.15f;
                    if (distance <= limit * limit && distance < nearest) { nearest = distance; selected = i; }
                }
                dragging=selected>=0;
                sizeSlider.setEnabled(dragging);
                if(dragging) {
                    offsetX=px-cx(selected); offsetY=py-cy(selected);
                    sizeSlider.setProgress(Math.round((sizes[selected]-.65f)*100));
                    help.setText(NAMES[selected]+" • "+Math.round(sizes[selected]*100)+"%");
                }
                invalidate();
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE && selected >= 0 && dragging) {
                float marginX = Math.min(.08f, radius() / Math.max(1f, getWidth()));
                float marginY = Math.min(.10f, radius() / Math.max(1f, getHeight()));
                x[selected] = Math.max(marginX, Math.min(1f - marginX, (px-offsetX) / Math.max(1f, getWidth())));
                y[selected] = Math.max(marginY, Math.min(1f - marginY, (py-offsetY) / Math.max(1f, getHeight())));
                invalidate();
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                if (selected >= 0) savePositions();
                dragging=false;
                invalidate();
                return true;
            }
            return true;
        }
    }
}
