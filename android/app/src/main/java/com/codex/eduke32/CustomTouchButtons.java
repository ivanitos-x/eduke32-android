package com.codex.eduke32;

import android.content.Context;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class CustomTouchButtons {
    static final int LIMIT = 12;

    static void draw(android.graphics.Canvas canvas, android.graphics.Paint paint, ControlArtwork artwork, Button button,
                     float x, float y, float radius, boolean pressed) {
        if (button.binding == null && button.action == 1
                || "Alt_Fire".equalsIgnoreCase(button.binding)) {
            artwork.button(canvas, x, y, radius, ControlArtwork.ALTERNATE_FIRE, pressed);
            return;
        }
        int oldColor = paint.getColor();
        float oldSize = paint.getTextSize();
        android.graphics.Paint.Style oldStyle = paint.getStyle();
        android.graphics.Paint.Align oldAlign = paint.getTextAlign();
        paint.setStyle(android.graphics.Paint.Style.FILL);
        paint.setColor(pressed ? 0xCC2967A0 : 0x990D1520);
        canvas.drawCircle(x, y, radius, paint);
        paint.setColor(0xFFF4F6F8);
        paint.setTextAlign(android.graphics.Paint.Align.CENTER);
        paint.setTextSize(radius * .48f);
        String label = button.label();
        float width = paint.measureText(label);
        if (width > radius * 1.7f) paint.setTextSize(paint.getTextSize() * radius * 1.7f / width);
        canvas.drawText(label, x, y - (paint.ascent() + paint.descent()) * .5f, paint);
        paint.setColor(oldColor);
        paint.setTextSize(oldSize);
        paint.setStyle(oldStyle);
        paint.setTextAlign(oldAlign);
    }
    // Order must match the nativeCustomAction table.
    static final String[] LABELS = {
        "Fire", "Alternate fire", "Jump", "Crouch", "Use", "Run", "Kick",
        "Next weapon", "Previous weapon", "Last weapon", "Inventory", "Previous item", "Next item",
        "Medkit", "Jetpack", "Night vision", "Steroids", "Holo Duke", "Map", "Quick save", "Quick load"
    };

    static final class Button {
        int action;
        String binding;
        float x = .5f, y = .5f, size = 1f;
        Button(int action) { this.action = action; }
        String label() { return binding == null ? LABELS[action] : binding.replace('_', ' '); }
        int resolve(String[] functions) {
            if (binding == null) return action;
            if (functions != null)
                for (int i = 0; i < functions.length; i++)
                    if (binding.equalsIgnoreCase(functions[i])) return 256 + i;
            return -1;
        }
    }

    private static AtomicFile file(Context context) {
        return new AtomicFile(new File(context.getFilesDir(), "custom-touch-buttons.json"));
    }

    static ArrayList<Button> load(Context context) {
        ArrayList<Button> result = new ArrayList<>();
        try {
            JSONArray data = new JSONArray(new String(file(context).readFully(), StandardCharsets.UTF_8));
            for (int i = 0; i < data.length() && result.size() < LIMIT; i++) {
                JSONObject item = data.getJSONObject(i);
                int action = item.optInt("action", -1);
                String binding = item.optString("binding", "");
                if (binding.isEmpty() && (action < 0 || action >= LABELS.length)) continue;
                Button button = new Button(action);
                button.binding = binding.isEmpty() ? null : binding;
                button.x = clamp(item.optDouble("x", .5), 0f, 1f, .5f);
                button.y = clamp(item.optDouble("y", .5), 0f, 1f, .5f);
                button.size = clamp(item.optDouble("size", 1), .65f, 1.5f, 1f);
                result.add(button);
            }
        } catch (FileNotFoundException ignored) {
            // No custom buttons have been saved yet.
        } catch (IOException | JSONException error) {
            android.util.Log.w("EDuke32", "Could not read custom touch buttons", error);
        }
        return result;
    }

    private static float clamp(double value, float min, float max, float fallback) {
        return Double.isFinite(value) ? (float)Math.max(min, Math.min(max, value)) : fallback;
    }

    static boolean save(Context context, ArrayList<Button> buttons) {
        AtomicFile file = file(context);
        FileOutputStream output = null;
        try {
            JSONArray data = new JSONArray();
            for (Button button : buttons) {
                data.put(new JSONObject().put("action", button.action).put("binding", button.binding)
                        .put("x", button.x).put("y", button.y).put("size", button.size));
            }
            output = file.startWrite();
            output.write(data.toString().getBytes(StandardCharsets.UTF_8));
            file.finishWrite(output);
            return true;
        } catch (IOException | JSONException error) {
            if (output != null) file.failWrite(output);
            android.util.Log.w("EDuke32", "Could not save custom touch buttons", error);
            return false;
        }
    }
}
