package com.ishfanatic.quran;

import android.service.dreams.DreamService;
import android.view.KeyEvent;

/** The system screensaver: the Scene. Right shows the next ayah; any other key wakes the TV back to where it was. */
public class QuranDream extends DreamService {

    private Scene scene;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        // Interactive so Right reaches dispatchKeyEvent; everything else ends the dream there.
        setInteractive(true);
        setFullscreen(true);
        scene = new Scene(this);
        setContentView(scene.root);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && scene != null) scene.next();
            return true;
        }
        // The waking key is used up here, so it doesn't also act on the app below.
        if (event.getAction() == KeyEvent.ACTION_DOWN) finish();
        return true;
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        if (scene != null) scene.start();
    }

    @Override
    public void onDreamingStopped() {
        if (scene != null) scene.stop();
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        if (scene != null) scene.shutdown();
        super.onDetachedFromWindow();
    }
}
