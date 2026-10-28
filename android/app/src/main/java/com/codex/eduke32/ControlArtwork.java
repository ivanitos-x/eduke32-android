package com.codex.eduke32;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;

/** Shared vector artwork for the live overlay and layout editor. */
final class ControlArtwork {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Path path;
    private final Path[] icons=new Path[8];

    void stick(Canvas c, float x, float y, float r, float ax, float ay, boolean active) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb(active ? 98 : 65, 12, 16, 20));
        c.drawCircle(x, y, r, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1.5f, r * .018f));
        p.setColor(Color.argb(active ? 170 : 100, 245, 245, 238));
        c.drawCircle(x, y, r * .96f, p);
        p.setColor(Color.argb(38, 255, 255, 255));
        c.drawCircle(x, y, r * .77f, p);
        float kx = x + ax * r * .57f, ky = y + ay * r * .57f;
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb(active ? 145 : 95, 225, 228, 221));
        c.drawCircle(kx, ky, r * .38f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setColor(Color.argb(active ? 225 : 155, 250, 250, 240));
        c.drawCircle(kx, ky, r * .38f, p);
        p.setColor(Color.argb(35, 255, 255, 255));
        c.drawCircle(kx, ky, r * .29f, p);
    }

    void button(Canvas c, float x, float y, float r, int action, boolean active) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(active ? Color.argb(145, 178, 142, 64) : Color.argb(90, 12, 16, 20));
        c.drawCircle(x, y, r, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1.5f, r * .025f));
        p.setColor(active ? Color.argb(235, 255, 218, 130) : Color.argb(155, 245, 245, 235));
        c.drawCircle(x, y, r * .96f, p);
        p.setColor(Color.argb(30, 255, 255, 255));
        c.drawCircle(x, y, r * .84f, p);
        c.save();
        c.translate(x, y);
        c.scale(r, r);
        p.setColor(active ? 0xffffe7a8 : 0xeff8f8ed);
        p.setStrokeWidth(.065f);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        int icon=Math.max(0,Math.min(7,action));
        if(icons[icon]==null) {
        path=new Path();
        switch (icon) {
            case 0: // Fire: sight.
                path.addCircle(0,0,.32f,Path.Direction.CW);
                line(c, -.54f, 0, -.19f, 0); line(c, .19f, 0, .54f, 0);
                line(c, 0, -.54f, 0, -.19f); line(c, 0, .19f, 0, .54f);
                break;
            case 1: // Jumping figure and upward arrow.
                path.addCircle(-.13f,-.38f,.085f,Path.Direction.CW);
                poly(c, -.13f,-.23f, -.06f,.08f, -.30f,.41f);
                poly(c, -.06f,.08f, .22f,.24f, .38f,.05f);
                poly(c, -.42f,-.08f, -.16f,-.20f, .13f,-.09f);
                poly(c, .25f,-.23f, .25f,-.52f, .12f,-.39f);
                line(c, .25f,-.52f, .39f,-.39f);
                break;
            case 2: // Open hand.
                poly(c, -.33f,.07f, -.47f,-.06f, -.39f,-.19f, -.23f,-.05f,
                     -.23f,-.40f, -.12f,-.43f, -.10f,-.13f, -.08f,-.53f,
                     .04f,-.53f, .07f,-.13f, .12f,-.44f, .23f,-.42f,
                     .24f,-.10f, .31f,-.31f, .42f,-.26f, .32f,.27f,
                     .17f,.43f, -.12f,.43f, -.33f,.07f);
                break;
            case 3: // Crouching figure.
                path.addCircle(-.10f,-.36f,.095f,Path.Direction.CW);
                poly(c, -.07f,-.19f, .19f,.04f, -.12f,.17f, -.24f,.43f, .11f,.43f);
                poly(c, .19f,.04f, .40f,.31f, .43f,.43f);
                poly(c, -.02f,-.10f, -.28f,.06f, -.43f,.01f);
                break;
            case 4: // Inventory satchel.
                path.addRoundRect(-.42f,-.23f,.42f,.38f,.07f,.07f,Path.Direction.CW);
                poly(c, -.18f,-.23f, -.18f,-.43f, .18f,-.43f, .18f,-.23f);
                line(c, -.41f,.02f,.41f,.02f); line(c, 0,-.06f,0,.12f);
                break;
            case 5: // Next weapon: pistol and change arrow.
                poly(c, -.47f,-.30f, .31f,-.30f, .31f,-.10f, -.04f,-.10f,
                     -.13f,.25f, -.36f,.25f, -.28f,-.10f, -.47f,-.10f, -.47f,-.30f);
                poly(c, -.03f,.42f, .46f,.42f, .31f,.27f);
                line(c, .46f,.42f,.31f,.55f);
                break;
            case 6: // Quick kick: boot.
                poly(c, -.32f,-.47f, .03f,-.47f, .06f,-.02f, .42f,.17f,
                     .47f,.39f, -.37f,.39f, -.37f,.13f, -.32f,-.47f);
                line(c,-.35f,.27f,.44f,.27f); line(c,.02f,-.02f,-.14f,.03f);
                break;
            default: // Enter/confirm.
                poly(c, .36f,-.36f, .36f,.06f, -.38f,.06f, -.13f,-.19f);
                line(c,-.38f,.06f,-.13f,.31f);
        }
        icons[icon]=path;
        }
        c.drawPath(icons[icon],p);
        c.restore();
        p.setStrokeCap(Paint.Cap.BUTT);
    }

    private void line(Canvas c, float x, float y, float x2, float y2) { path.moveTo(x,y); path.lineTo(x2,y2); }
    private void poly(Canvas c, float... points) {
        path.moveTo(points[0],points[1]);
        for (int i=2; i<points.length; i+=2) path.lineTo(points[i],points[i+1]);

    }
}
