package com.ghosted907.clusterdiag;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;

import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ClusterPreviewTest {
    @Test
    public void renders1920x720Preview() throws Exception {
        SaudiClusterView view = new SaudiClusterView(RuntimeEnvironment.getApplication());
        view.showDesignSample();
        view.snapNeedles();
        int width = 1920;
        int height = 720;
        view.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        view.draw(canvas);
        File[] outputs = new File[] {
                new File("/workspace/artifacts/saudi-cluster-preview.png"),
                new File("/opt/cursor/artifacts/saudi-cluster-preview.png")
        };
        for (File output : outputs) {
            File parent = output.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            FileOutputStream stream = new FileOutputStream(output);
            try {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
            } finally {
                stream.close();
            }
            assertTrue(output.length() > 10000L);
        }
        int sky = bitmap.getPixel(960, 40);
        int dune = bitmap.getPixel(200, 400);
        assertTrue(sky != dune);
    }
}
