package com.ishfanatic.quran;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;

/** The screensaver's page as an app and as the Home card: Right or OK shows the next ayah, Back leaves. */
public class QuranActivity extends Activity {

    private Scene scene;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        scene = new Scene(this);
        setContentView(scene.root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        scene.start();
    }

    @Override
    protected void onPause() {
        scene.stop();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        scene.shutdown();
        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER) {
            scene.next();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
