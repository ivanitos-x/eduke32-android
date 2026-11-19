package com.codex.eduke32;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.content.Context;

final class ControlLayoutView extends FrameLayout {
    private static final int FIRST_EXTRA = 10;
    static final int[] EXTRA_ICONS = {ControlArtwork.INVENTORY_PREVIOUS,
            ControlArtwork.INVENTORY_NEXT, ControlArtwork.MAP, ControlArtwork.CONSOLE};
    private static final String PREFS = "touch_controls";
    private static final String[] NAMES = { "FIRE", "JUMP", "USE", "DUCK", "INV", "NEXT", "KICK", "ENTER", "MOVE", "LOOK",
            "Inventory [", "Inventory ]", "Map", "Console" };
    private static final String[] PREF_IDS = { "0", "1", "2", "3", "4", "5", "kick", "6", "dpad", "look",
            "inv_prev", "inv_next", "map", "console" };
    private static final float[] DEFAULT_X = { .87f, .74f, .94f, .68f, .83f, .95f, .80f, .78f, .16f, .70f, .16f, .265f, .37f, .475f };
    private static final float[] DEFAULT_Y = { .73f, .84f, .53f, .60f, .42f, .34f, .63f, .15f, .72f, .78f, .13f, .13f, .13f, .13f };
    static String extraId(int index) { return PREF_IDS[FIRST_EXTRA + index]; }
    static float extraX(int index) { return DEFAULT_X[FIRST_EXTRA + index]; }
    static float extraY(int index) { return DEFAULT_Y[FIRST_EXTRA + index]; }
    private final float[] x = DEFAULT_X.clone();
    private final float[] y = DEFAULT_Y.clone();
    private Preview preview;
    private final float[] sizes = new float[NAMES.length];
    private android.widget.SeekBar sizeSlider;
    private TextView help;
    private final java.util.ArrayList<CustomTouchButtons.Button> custom = new java.util.ArrayList<>();
    private Button editBinding;

    ControlLayoutView(Context context, Runnable close) {
        super(context);
        loadPositions();
        custom.addAll(CustomTouchButtons.load(context));
        setClickable(true);

        FrameLayout root = this;
        root.setBackgroundColor(Color.rgb(22, 25, 32));
        LinearLayout toolbar = new LinearLayout(context);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(8), dp(4), dp(8), dp(4));

        help = new TextView(context);
        help.setText("Drag to move • Select to resize");
        help.setTextColor(Color.WHITE);
        help.setTextSize(16);
        toolbar.addView(help, new LinearLayout.LayoutParams(0, -2, 1));

        Button add = new Button(context);
        add.setText("Add button");
        add.setOnClickListener(view -> chooseBinding(-1));
        toolbar.addView(add);
        editBinding = new Button(context);
        editBinding.setText("Edit button");
        editBinding.setVisibility(View.GONE);
        editBinding.setOnClickListener(view -> chooseBinding(preview.selected - NAMES.length));
        toolbar.addView(editBinding);

        Button reset = new Button(context);
        reset.setText("Reset");
        reset.setOnClickListener(v -> {
            System.arraycopy(DEFAULT_X, 0, x, 0, x.length);
            System.arraycopy(DEFAULT_Y, 0, y, 0, y.length);
            java.util.Arrays.fill(sizes,1f);
            preview.selected=-1; sizeSlider.setEnabled(false);
            editBinding.setVisibility(View.GONE);
            savePositions();
            preview.invalidate();
        });
        toolbar.addView(reset);
        Button done = new Button(context);
        done.setText("Done");
        done.setOnClickListener(v -> close.run());
        toolbar.addView(done);

        sizeSlider=new android.widget.SeekBar(context);
        sizeSlider.setMax(85); sizeSlider.setProgress(35); sizeSlider.setEnabled(false);
        sizeSlider.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.rgb(230,197,125)));
        sizeSlider.setThumbTintList(android.content.res.ColorStateList.valueOf(Color.rgb(240,220,170)));
        sizeSlider.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(android.widget.SeekBar bar,int value,boolean fromUser) {
                if(fromUser && preview!=null && preview.selected>=0) {
                    int index = preview.selected;
                    if (index < NAMES.length) sizes[index]=.65f+value/100f;
                    else custom.get(index - NAMES.length).size = .65f+value/100f;
                    preview.describeSelection();
                    preview.invalidate();
                }
            }
            public void onStartTrackingTouch(android.widget.SeekBar bar) { }
            public void onStopTrackingTouch(android.widget.SeekBar bar) {
                if (preview.selected >= NAMES.length) saveCustom();
                else if (preview.selected >= 0) savePositions();
            }
        });
        toolbar.addView(sizeSlider,new LinearLayout.LayoutParams(dp(150),-2));
        preview = new Preview(context);
        root.addView(preview, new FrameLayout.LayoutParams(-1, -1));

        toolbar.setBackgroundColor(Color.rgb(27, 35, 46));
        toolbar.setClickable(true);
        toolbar.setVisibility(View.GONE);
        FrameLayout.LayoutParams panel = new FrameLayout.LayoutParams(-1, dp(56), Gravity.TOP);
        panel.topMargin = dp(44);
        root.addView(toolbar, panel);

        TextView toggle = new TextView(context);
        toggle.setText("▾");
        toggle.setTextSize(22);
        toggle.setTextColor(Color.WHITE);
        toggle.setGravity(Gravity.CENTER);
        toggle.setContentDescription("Show editor tools");
        GradientDrawable toggleBackground = new GradientDrawable();
        toggleBackground.setColor(Color.rgb(27, 35, 46));
        toggleBackground.setCornerRadius(dp(10));
        toggle.setBackground(toggleBackground);
        toggle.setOnClickListener(v -> {
            boolean show = toolbar.getVisibility() != View.VISIBLE;
            toolbar.setVisibility(show ? View.VISIBLE : View.GONE);
            toggle.setText(show ? "▴" : "▾");
            toggle.setContentDescription(show ? "Hide editor tools" : "Show editor tools");
        });
        root.addView(toggle, new FrameLayout.LayoutParams(dp(48), dp(44), Gravity.TOP | Gravity.CENTER_HORIZONTAL));
    }

    private int dp(float value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }

    private void saveCustom() {
        if (!CustomTouchButtons.save(getContext(), custom))
            android.widget.Toast.makeText(getContext(), "Could not save touch buttons", android.widget.Toast.LENGTH_LONG).show();
    }

    private void chooseBinding(int index) {
        if (index < 0 && custom.size() >= CustomTouchButtons.LIMIT) {
            android.widget.Toast.makeText(getContext(), "Custom button limit reached", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        boolean constEdit = index >= 0 && index < custom.size();
        java.util.ArrayList<String> labels = new java.util.ArrayList<>(
                java.util.Arrays.asList(CustomTouchButtons.LABELS));
        java.util.ArrayList<String> bindings = new java.util.ArrayList<>();
        String[] functions = GameActivity.nativeGameFunctions();
        if (functions != null) {
            for (String name : functions) {
                if (name == null) continue;
                String label = name.replace('_', ' ');
                if (labels.stream().anyMatch(label::equalsIgnoreCase)) continue;
                labels.add(label);
                bindings.add(name);
            }
        }
        android.app.AlertDialog.Builder dialog = new android.app.AlertDialog.Builder(getContext())
                .setTitle(constEdit ? "Change binding" : "Add button")
                .setItems(labels.toArray(new String[0]), (choice, action) -> {
                    CustomTouchButtons.Button button = constEdit ? custom.get(index) : new CustomTouchButtons.Button(action);
                    int builtInCount = CustomTouchButtons.LABELS.length;
                    button.action = action < builtInCount ? action : -1;
                    button.binding = action < builtInCount ? null : bindings.get(action - builtInCount);
                    if (!constEdit) custom.add(button);
                    saveCustom();
                    preview.select(NAMES.length + (constEdit ? index : custom.size() - 1));
                })
                .setNegativeButton("Cancel", null);
        if (constEdit) dialog.setNeutralButton("Remove", (choice, which) -> {
            custom.remove(index);
            saveCustom();
            preview.select(-1);
        });
        dialog.show();
    }

    private void loadPositions() {
        android.content.SharedPreferences prefs = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        for (int i = 0; i < NAMES.length; i++) {
            if (i == GameActivity.LEGACY_SATCHEL_SLOT) continue;
            String id = PREF_IDS[i];
            x[i] = prefs.getFloat("x_" + id, DEFAULT_X[i]);
            y[i] = prefs.getFloat("y_" + id, DEFAULT_Y[i]);
            sizes[i]=Math.max(.65f,Math.min(1.5f,prefs.getFloat("size_"+id,1f)));
        }
    }

    private void savePositions() {
        android.content.SharedPreferences.Editor editor = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        for (int i = 0; i < NAMES.length; i++) {
            if (i == GameActivity.LEGACY_SATCHEL_SLOT) continue;
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
        private float cx(int i) { return (i < NAMES.length ? x[i] : custom.get(i - NAMES.length).x) * getWidth(); }
        private float cy(int i) { return (i < NAMES.length ? y[i] : custom.get(i - NAMES.length).y) * getHeight(); }

        private float scale(int i) {
            return i < NAMES.length ? sizes[i] : custom.get(i - NAMES.length).size;
        }
        private void describeSelection() {
            if (selected < 0) {
                help.setText("Drag to move • Select to resize");
                return;
            }
            String label = selected < NAMES.length ? NAMES[selected]
                    : custom.get(selected - NAMES.length).label();
            help.setText(label + " • " + Math.round(scale(selected) * 100) + "%");
        }
        private void select(int index) {
            selected = index;
            sizeSlider.setEnabled(index >= 0);
            editBinding.setVisibility(index >= NAMES.length ? View.VISIBLE : View.GONE);
            if (index >= 0) sizeSlider.setProgress(Math.round((scale(index) - .65f) * 100));
            describeSelection();
            invalidate();
        }

        private float elementRadius(int i) {
            float min=Math.min(getWidth(),getHeight());
            if (i >= FIRST_EXTRA && i < NAMES.length) return scale(i) * min * .048f;
            return scale(i)*(i==8?Math.max(80f,min*.175f):i==9?Math.max(64f,min*.14f):radius()*(i==7?.82f:1f));
        }
        @Override protected void onDraw(Canvas canvas) {
            canvas.drawColor(Color.rgb(9,11,15));
            for(int i=0;i<NAMES.length;i++) {
                if (i == GameActivity.LEGACY_SATCHEL_SLOT) continue;
                float px=cx(i),py=cy(i),r=elementRadius(i);
                boolean stick = i == 8 || i == 9;
                if (stick) artwork.stick(canvas,px,py,r,0,0,i==selected);
                else artwork.button(canvas,px,py,r,i >= FIRST_EXTRA ? EXTRA_ICONS[i - FIRST_EXTRA] : i,i==selected);
                paint.setColor(i==selected?0xffffdfa0:0xffaab0b7);
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setTextSize(Math.max(12f,radius()*.24f));
                canvas.drawText(NAMES[i]+(stick?" (floating)":""),px,py+r+paint.getTextSize()*1.3f,paint);
            }
            for (int i = NAMES.length; i < NAMES.length + custom.size(); i++) {
                CustomTouchButtons.draw(canvas, paint, artwork, custom.get(i - NAMES.length),
                        cx(i), cy(i), elementRadius(i), i == selected);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float px = event.getX(), py = event.getY();
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                float nearest = Float.MAX_VALUE;
                selected = -1;
                for (int i = 0; i < NAMES.length + custom.size(); i++) {
                    if (i == GameActivity.LEGACY_SATCHEL_SLOT) continue;
                    float dx = px - cx(i), dy = py - cy(i);
                    float distance = dx * dx + dy * dy;
                    float limit = elementRadius(i)*1.15f;
                    if (distance <= limit * limit && distance < nearest) { nearest = distance; selected = i; }
                }
                dragging=selected>=0;
                select(selected);
                if(dragging) {
                    offsetX=px-cx(selected); offsetY=py-cy(selected);
                }
                invalidate();
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE && selected >= 0 && dragging) {
                float marginX = Math.min(.08f, radius() / Math.max(1f, getWidth()));
                float marginY = Math.min(.10f, radius() / Math.max(1f, getHeight()));
                float nextX = Math.max(marginX, Math.min(1f - marginX, (px-offsetX) / Math.max(1f, getWidth())));
                float nextY = Math.max(marginY, Math.min(1f - marginY, (py-offsetY) / Math.max(1f, getHeight())));
                if (selected < NAMES.length) {
                    x[selected] = nextX;
                    y[selected] = nextY;
                } else {
                    CustomTouchButtons.Button button = custom.get(selected - NAMES.length);
                    button.x = nextX;
                    button.y = nextY;
                }
                invalidate();
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                if (selected >= NAMES.length) saveCustom();
                else if (selected >= 0) savePositions();
                dragging=false;
                invalidate();
                return true;
            }
            return true;
        }
    }
}
