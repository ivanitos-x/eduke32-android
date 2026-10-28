package com.codex.eduke32;

import android.graphics.Canvas;
import android.hardware.display.DisplayManager;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.WindowManager;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.content.Context;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
import org.libsdl.app.SDLActivity;

public final class GameActivity extends SDLActivity {
    private static final int ACTION_FIRE = 0;
    private static final int ACTION_JUMP = 1;
    private static final int ACTION_USE = 2;
    private static final int ACTION_CROUCH = 3;
    private static final int ACTION_INVENTORY = 4;
    private static final int ACTION_NEXT_WEAPON = 5;
    private static final int ACTION_KICK = 6;
    private static final int ACTION_MENU_SELECT = 7;
    private static final int ACTION_MENU_BACK = 8;
    private static final int ACTION_MENU_UP = 9;
    private static final int ACTION_MENU_DOWN = 10;
    private static final int ACTION_MENU_LEFT = 11;
    private static final int ACTION_MENU_RIGHT = 12;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private OnBackInvokedCallback systemBackCallback;

    private static native void nativeAction(int action, boolean down);
    private static native void nativeLook(float yaw, float pitch);
    private static native void nativePreferBigCores(boolean enabled);
    private static native void nativeMove(float strafe, float forward);
    private static native void nativeLookJoystick(float yaw, float pitch);
    private static native void nativeReset();
    private static native int nativeTouchMode();
    private boolean blankTapActive;
    private boolean volumeFireEnabled, volumeFireHeld, touchFireHeld, volumeKeyCaptured;
    private void setFireSource(boolean physical, boolean down) {
        boolean before=volumeFireHeld || touchFireHeld;
        if(physical) volumeFireHeld=down; else touchFireHeld=down;
        boolean after=volumeFireHeld || touchFireHeld;
        if(before!=after) nativeAction(ACTION_FIRE,after);
    }
    private void releaseFireSources() {
        boolean wasHeld=volumeFireHeld || touchFireHeld;
        volumeFireHeld=touchFireHeld=false;
        if(wasHeld) nativeAction(ACTION_FIRE,false);
    }
    @Override public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if(mBrokenLibraries)return super.dispatchKeyEvent(event);
        if(event.getAction()==android.view.KeyEvent.ACTION_DOWN &&
                (event.getKeyCode()==android.view.KeyEvent.KEYCODE_POWER || event.getKeyCode()==android.view.KeyEvent.KEYCODE_VOLUME_DOWN)) {
            // Release firing when a system screenshot/power chord is starting.
            setFireSource(true,false);volumeKeyCaptured=false;
            return super.dispatchKeyEvent(event);
        }
        if(event.getKeyCode()==android.view.KeyEvent.KEYCODE_VOLUME_UP) {
            if(event.getAction()==android.view.KeyEvent.ACTION_UP && (volumeKeyCaptured || event.isCanceled())) {
                setFireSource(true,false); volumeKeyCaptured=false; return true;
            }
            if(event.getAction()==android.view.KeyEvent.ACTION_DOWN) {
                if(volumeKeyCaptured) return true; // Ignore key repeat, including after a menu opens.
                if(volumeFireEnabled && nativeTouchMode()==5 && event.getRepeatCount()==0) {
                    volumeKeyCaptured=true; setFireSource(true,true); return true;
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if(mBrokenLibraries)return super.dispatchTouchEvent(event);
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN && nativeTouchMode() == 1) {
            if (touchControls != null) touchControls.cancelPointers();
            blankTapActive = true;
            nativeAction(7, true); // Enter for intros, cinematics and respawn.
            return true;
        }
        if (blankTapActive) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                nativeAction(7, false);
                blankTapActive = false;
            }
            return true;
        }
        return super.dispatchTouchEvent(event);
    }
    private TouchControls touchControls;
    private final Runnable refreshTouchVisibility = new Runnable() {
        @Override public void run() {
            if (touchControls != null) touchControls.updateInputMode();
            mainHandler.postDelayed(this, 50);
        }
    };

    @Override protected void onResume() {
        super.onResume();
        if(mBrokenLibraries) { DiagnosticApp.stage("Game resume: native libraries unavailable");return; }
        DiagnosticApp.stage("Game resumed");
        volumeKeyCaptured=false;
        volumeFireEnabled=getSharedPreferences("launcher_settings",Context.MODE_MULTI_PROCESS)
                .getBoolean("volume_up_fire",false);
        nativePreferBigCores(!getIntent().getBooleanExtra("emergency_start",false) && getSharedPreferences("launcher_settings",Context.MODE_MULTI_PROCESS).getBoolean("prefer_big_cores",false));
        if(touchControls!=null) touchControls.loadLayout();
        mainHandler.removeCallbacks(refreshTouchVisibility);
        mainHandler.post(refreshTouchVisibility);
    }

    @Override protected void onPause() {
        if(mBrokenLibraries) { super.onPause();return; }
        DiagnosticApp.stage("Game paused");
        if(!getIntent().getBooleanExtra("emergency_start",false))SaveFolderSync.schedule(this,getIntent().getStringExtra("game_preset"));
        releaseFireSources();volumeKeyCaptured=false;
        mainHandler.removeCallbacks(refreshTouchVisibility);
        blankTapActive = false;
        if (touchControls != null) touchControls.cancelPointers();
        super.onPause();
    }
    private static native void nativeDisplayRefreshRate(float rate);
    private DisplayManager displayManager;
    private float requestedRefreshRate;
    private float lastReportedRefreshRate;
    private final DisplayManager.DisplayListener refreshListener = new DisplayManager.DisplayListener() {
        @Override public void onDisplayAdded(int id) { }
        @Override public void onDisplayRemoved(int id) { }
        @Override public void onDisplayChanged(int id) { reportDisplayRefreshRate(); }
    };

    private void reportDisplayRefreshRate() {
        if(mBrokenLibraries)return;
        Display display = getWindowManager().getDefaultDisplay();
        if (display == null) return;
        float actual = display.getRefreshRate();
        nativeDisplayRefreshRate(actual);
        if (Math.abs(actual - lastReportedRefreshRate) > .5f) {
            android.util.Log.i("EDuke32", "Display refresh: " + actual + " Hz (requested " + requestedRefreshRate + ")");
            lastReportedRefreshRate = actual;
        }
    }

    private void requestSmoothDisplayMode() {
        if(mBrokenLibraries || getIntent().getBooleanExtra("emergency_start",false))return;
        Display display = getWindowManager().getDefaultDisplay();
        if (display == null) return;
        Display.Mode current = display.getMode();
        Display.Mode best = current;
        for (Display.Mode candidate : display.getSupportedModes()) {
            if (candidate.getPhysicalWidth() == current.getPhysicalWidth()
                    && candidate.getPhysicalHeight() == current.getPhysicalHeight()
                    && candidate.getRefreshRate() <= 120.5f
                    && candidate.getRefreshRate() > best.getRefreshRate()) best = candidate;
        }
        requestedRefreshRate = best.getRefreshRate();
        WindowManager.LayoutParams params = getWindow().getAttributes();
        if (params.preferredDisplayModeId != best.getModeId()
                || Math.abs(params.preferredRefreshRate - requestedRefreshRate) > .5f) {
            params.preferredDisplayModeId = best.getModeId();
            params.preferredRefreshRate = requestedRefreshRate;
            getWindow().setAttributes(params);
        }
        if (Build.VERSION.SDK_INT >= 30 && mSurface != null) {
            Surface surface = mSurface.getHolder().getSurface();
            if (surface.isValid()) {
                if (Build.VERSION.SDK_INT >= 31)
                    surface.setFrameRate(requestedRefreshRate, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT,
                            Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS);
                else surface.setFrameRate(requestedRefreshRate, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT);
            }
        }
        reportDisplayRefreshRate();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if(!hasFocus && !mBrokenLibraries) {
            releaseFireSources();volumeKeyCaptured=false;blankTapActive=false;
            if(touchControls!=null)touchControls.cancelPointers();
            nativeReset();
        }
        DiagnosticApp.stage("Window focus="+hasFocus+" orientation="+getRequestedOrientation());
        super.onWindowFocusChanged(hasFocus);
        if(mBrokenLibraries)return;
        if(hasFocus) {
            setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            mainHandler.postDelayed(()->{if(hasWindowFocus())requestSmoothDisplayMode();},200);
        }
    }

    @Override public void setOrientationBis(int w,int h,boolean resizable,String hint) {
        // Never derive game orientation from a transient assistant/power-menu surface.
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
    }

    @Override
    public void onBackPressed() {
        sendMenuBack();
    }

    private void sendMenuBack() {
        if(mBrokenLibraries) { finish();return; }
        android.util.Log.i("EDuke32", "Android Back requested");
        nativeAction(ACTION_MENU_BACK, true);
        mainHandler.postDelayed(() -> nativeAction(ACTION_MENU_BACK, false), 100);
    }

    @Override
    protected void onCreate(Bundle state) {
        if(!getIntent().getBooleanExtra("emergency_start",false))SaveFolderSync.start(this,getIntent().getStringExtra("game_preset"));
        DiagnosticApp.stage("GameActivity onCreate; emergency="+getIntent().getBooleanExtra("emergency_start",false));
        super.onCreate(state);
        if(mBrokenLibraries) { DiagnosticApp.stage("SDL initialization stopped: broken native libraries");return; }
        DiagnosticApp.stage("SDL Java initialization complete");
        displayManager = (DisplayManager)getSystemService(Context.DISPLAY_SERVICE);
        if (displayManager != null) displayManager.registerDisplayListener(refreshListener, mainHandler);
        requestSmoothDisplayMode();
        if (mSurface != null) mSurface.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override public void surfaceCreated(SurfaceHolder holder) { requestSmoothDisplayMode(); }
            @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                requestSmoothDisplayMode();
            }
            @Override public void surfaceDestroyed(SurfaceHolder holder) { }
        });
        if (Build.VERSION.SDK_INT >= 33) {
            systemBackCallback = this::sendMenuBack;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_OVERLAY, systemBackCallback);
        }
        if (mLayout != null) {
            touchControls = new TouchControls(this);
            mLayout.addView(touchControls,
                    new RelativeLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT));
        }
    }

    @Override
    protected void onDestroy() {
        if (Build.VERSION.SDK_INT >= 33 && systemBackCallback != null)
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(systemBackCallback);
        if (displayManager != null) displayManager.unregisterDisplayListener(refreshListener);
        mainHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override public void loadLibraries() {
        DiagnosticApp.stage("Loading SDL2 and main native libraries");
        try { super.loadLibraries();DiagnosticApp.stage("Native libraries loaded"); }
        catch(RuntimeException | LinkageError error) {
            DiagnosticApp.stage("Native load failure: "+android.util.Log.getStackTraceString(error));
            throw error;
        }
    }

    @Override protected String[] getArguments() {
        return getIntent().getBooleanExtra("emergency_start",false)
                ? new String[]{"-androidsafe"} : new String[0];
    }

    @Override
    protected String[] getLibraries() {
        return new String[] { "SDL2", "main" };
    }

    private static final class TouchControls extends View {
        private static final int LOOK=-2, IDLE=-3, EDGE_BACK=-4, MOVE=-5;
        private static final int BUTTON_COUNT=8;
        private final SparseArray<Pointer> pointers=new SparseArray<>();
        private final ControlArtwork artwork=new ControlArtwork();
        private final GameActivity activity;
        private android.content.SharedPreferences prefs;
        private final float[] xs=new float[BUTTON_COUNT], ys=new float[BUTTON_COUNT];
        private final float[] sizes=new float[BUTTON_COUNT];
        private float moveX,moveY,moveRadius,moveAxisX,moveAxisY;
        private float lookX,lookY,lookRadius,lookAxisX,lookAxisY;
        private boolean gameInputMode;

        private static final class Pointer {
            int role; final float x,y;
            Pointer(int role,float x,float y) { this.role=role; this.x=x; this.y=y; }
        }
        TouchControls(GameActivity context) {
            super(context); activity=context;
            prefs=context.getSharedPreferences("touch_controls",Context.MODE_PRIVATE);
            setVisibility(View.VISIBLE); setWillNotDraw(false); setClickable(true);
        }
        void updateInputMode() {
            boolean game=nativeTouchMode()==5;
            if (game!=gameInputMode) {
                cancelPointers(); activity.releaseFireSources(); gameInputMode=game;
                // Keep the overlay attached to preserve the software SurfaceView.
                invalidate();
            }
        }
        private float scale(String id) { return Math.max(.65f,Math.min(1.5f,prefs.getFloat("size_"+id,1f))); }
        private float buttonRadius(int i) {
            float r=Math.max(42f,Math.min(getWidth(),getHeight())*.078f);
            return r*sizes[i]*(i==ACTION_MENU_SELECT?.82f:1f);
        }
        private String buttonId(int i) { return i==ACTION_KICK?"kick":i==ACTION_MENU_SELECT?"6":Integer.toString(i); }
        private void loadLayout() {
            prefs=activity.getSharedPreferences("touch_controls",Context.MODE_MULTI_PROCESS);
            float[] defaultsX={.87f,.74f,.94f,.68f,.83f,.95f,.80f,.78f};
            float[] defaultsY={.73f,.84f,.53f,.60f,.42f,.34f,.63f,.15f};
            for(int i=0;i<BUTTON_COUNT;i++) {
                String id=buttonId(i);
                xs[i]=getWidth()*prefs.getFloat("x_"+id,defaultsX[i]);
                ys[i]=getHeight()*prefs.getFloat("y_"+id,defaultsY[i]); sizes[i]=scale(id);
            }
            moveRadius=Math.max(80f,Math.min(getWidth(),getHeight())*.175f)*scale("dpad");
            moveX=getWidth()*prefs.getFloat("x_dpad",.16f);
            moveY=getHeight()*prefs.getFloat("y_dpad",.72f);
            lookRadius=Math.max(64f,Math.min(getWidth(),getHeight())*.14f)*scale("look");
        }
        private int hitButton(float x,float y) {
            int best=-1; float nearest=Float.MAX_VALUE;
            for(int i=0;i<BUTTON_COUNT;i++) {
                float dx=x-xs[i],dy=y-ys[i],distance=dx*dx+dy*dy;
                float r=buttonRadius(i)*1.15f;
                if(distance<=r*r && distance<nearest) { best=i; nearest=distance; }
            }
            return best;
        }
        private boolean held(int role) {
            for(int i=0;i<pointers.size();i++) if(pointers.valueAt(i).role==role) return true;
            return false;
        }
        private void beginPointer(MotionEvent event,int index) {
            int id=event.getPointerId(index); float x=event.getX(index),y=event.getY(index);
            float edge=28f*getResources().getDisplayMetrics().density;
            if(x<edge || x>getWidth()-edge) { pointers.put(id,new Pointer(EDGE_BACK,x,y)); return; }
            int button=hitButton(x,y);
            if(button>=0) {
                boolean active=held(button); pointers.put(id,new Pointer(button,x,y));
                if(!active) { if(button==ACTION_FIRE) activity.setFireSource(false,true); else nativeAction(button,true); }
            } else if(x<getWidth()*.40f && !held(MOVE)) {
                pointers.put(id,new Pointer(MOVE,x,y)); moveX=x; moveY=y;
                moveAxisX=moveAxisY=0; nativeMove(0,0);
            } else if(x>=getWidth()*.40f && !held(LOOK)) {
                pointers.put(id,new Pointer(LOOK,x,y)); lookX=x; lookY=y;
                lookAxisX=lookAxisY=0; nativeLookJoystick(0,0);
            } else pointers.put(id,new Pointer(IDLE,x,y));
            invalidate();
        }
        private void movePointer(MotionEvent event,int index) {
            Pointer p=pointers.get(event.getPointerId(index)); if(p==null) return;
            float x=event.getX(index),y=event.getY(index);
            if(p.role==MOVE) updateMove(x,y);
            else if(p.role==LOOK) updateLook(x,y);
            else if(p.role==EDGE_BACK) {
                float density=getResources().getDisplayMetrics().density;
                if((p.x<28f*density && x-p.x>72f*density)
                    || (p.x>getWidth()-28f*density && p.x-x>72f*density)) {
                    activity.sendMenuBack(); p.role=IDLE;
                }
            }
            invalidate();
        }
        private void updateMove(float x,float y) {
            float dx=(x-moveX)/moveRadius,dy=(y-moveY)/moveRadius;
            float distance=(float)Math.sqrt(dx*dx+dy*dy);
            float limit=Math.max(1f,distance);
            moveAxisX=dx/limit; moveAxisY=dy/limit;
            // A radial dead zone preserves direction through diagonals.
            float magnitude=Math.min(1f,Math.max(0f,(distance-.12f)/.88f));
            if(distance>0) nativeMove(dx/distance*magnitude,-dy/distance*magnitude);
            else nativeMove(0,0);
        }
        private void updateLook(float x,float y) {
            float dx=(x-lookX)/lookRadius,dy=(y-lookY)/lookRadius;
            float limit=Math.max(1f,(float)Math.sqrt(dx*dx+dy*dy));
            lookAxisX=dx/limit; lookAxisY=dy/limit;
            nativeLookJoystick(-lookAxisX,lookAxisY);
        }
        private void endPointer(int id) {
            Pointer p=pointers.get(id); if(p==null) return;
            pointers.remove(id);
            if(p.role>=0 && !held(p.role)) { if(p.role==ACTION_FIRE) activity.setFireSource(false,false); else nativeAction(p.role,false); }
            if(p.role==MOVE) { moveAxisX=moveAxisY=0; nativeMove(0,0); }
            if(p.role==LOOK) { lookAxisX=lookAxisY=0; nativeLookJoystick(0,0); }
            invalidate();
        }
        private void cancelPointers() {
            for(int i=0;i<pointers.size();i++) {
                int role=pointers.valueAt(i).role; if(role>=0) { if(role==ACTION_FIRE) activity.setFireSource(false,false); else nativeAction(role,false); }
            }
            pointers.clear(); moveAxisX=moveAxisY=lookAxisX=lookAxisY=0;
            activity.releaseFireSources();
            nativeMove(0,0); nativeLookJoystick(0,0); nativeReset(); invalidate();
        }
        @Override protected void onSizeChanged(int w,int h,int oldw,int oldh) {
            cancelPointers(); loadLayout();
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas); if(!gameInputMode) return;
            if(held(MOVE)) artwork.stick(canvas,moveX,moveY,moveRadius,moveAxisX,moveAxisY,true);
            // Only an active touch displays the floating camera stick.
            if(held(LOOK)) artwork.stick(canvas,lookX,lookY,lookRadius,lookAxisX,lookAxisY,true);
            for(int i=0;i<BUTTON_COUNT;i++) artwork.button(canvas,xs[i],ys[i],buttonRadius(i),i,held(i));
        }
        @Override public boolean onTouchEvent(MotionEvent event) {
            if(nativeTouchMode()!=5) {
                updateInputMode(); return mSurface!=null && mSurface.dispatchTouchEvent(event);
            }
            int action=event.getActionMasked(),index=event.getActionIndex();
            if(action==MotionEvent.ACTION_DOWN || action==MotionEvent.ACTION_POINTER_DOWN) beginPointer(event,index);
            else if(action==MotionEvent.ACTION_MOVE) for(int i=0;i<event.getPointerCount();i++) movePointer(event,i);
            else if(action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_POINTER_UP) endPointer(event.getPointerId(index));
            else if(action==MotionEvent.ACTION_CANCEL) cancelPointers();
            return true;
        }
    }
}
