package com.codex.eduke32;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.CompoundButton;

/** Theme-independent graphical switch; labels belong to the surrounding card. */
final class ToggleSwitch extends CompoundButton {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private float position;
    private ValueAnimator animator;

    ToggleSwitch(Context context) {
        super(context);
        setButtonDrawable(null);
        setBackground(null);
        setText(null);
        setPadding(0,0,0,0);
        setMinWidth(0);setMinHeight(0);
        setMinimumWidth(0);setMinimumHeight(0);
        setClickable(true);setFocusable(true);
        position=isChecked()?1f:0f;
    }

    private float dp(float n) { return n*getResources().getDisplayMetrics().density; }
    @Override protected void onMeasure(int widthSpec,int heightSpec) {
        setMeasuredDimension(resolveSize((int)dp(64),widthSpec),resolveSize((int)dp(48),heightSpec));
    }

    @Override public void setChecked(boolean checked) {
        super.setChecked(checked);
        if(animator!=null) animator.cancel();
        float target=checked?1f:0f;
        if(!isLaidOut() || !isAttachedToWindow()) { position=target;invalidate();return; }
        animator=ValueAnimator.ofFloat(position,target);
        animator.setDuration(150);
        animator.addUpdateListener(value->{position=(Float)value.getAnimatedValue();invalidate();});
        animator.start();
        if(Build.VERSION.SDK_INT>=30) setStateDescription(checked?"On":"Off");
    }

    @Override protected void onDraw(Canvas canvas) {
        float cx=getWidth()*.5f,cy=getHeight()*.5f;
        float halfWidth=dp(26),halfHeight=dp(15);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(blend(0xff46515e,0xff0878ff,position));
        paint.setAlpha(isEnabled()?255:105);
        canvas.drawRoundRect(cx-halfWidth,cy-halfHeight,cx+halfWidth,cy+halfHeight,halfHeight,halfHeight,paint);
        paint.setColor(Color.WHITE);paint.setAlpha(isEnabled()?255:130);
        float direction=getLayoutDirection()==LAYOUT_DIRECTION_RTL?-1f:1f;
        canvas.drawCircle(cx+direction*(position*2f-1f)*dp(11),cy,dp(11),paint);
        if(isFocused()) {
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));
            paint.setColor(0xffa4cdff);
            canvas.drawRoundRect(cx-halfWidth-dp(3),cy-halfHeight-dp(3),cx+halfWidth+dp(3),cy+halfHeight+dp(3),dp(18),dp(18),paint);
        }
    }

    private static int blend(int from,int to,float t) {
        return Color.rgb((int)(Color.red(from)+(Color.red(to)-Color.red(from))*t),
                (int)(Color.green(from)+(Color.green(to)-Color.green(from))*t),
                (int)(Color.blue(from)+(Color.blue(to)-Color.blue(from))*t));
    }

    @Override public CharSequence getAccessibilityClassName() { return "android.widget.Switch"; }
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);info.setCheckable(true);info.setChecked(isChecked());
    }
    @Override protected void onDetachedFromWindow() {
        if(animator!=null) animator.cancel();
        super.onDetachedFromWindow();
    }
}
