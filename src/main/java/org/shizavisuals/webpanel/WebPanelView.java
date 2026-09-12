package org.shizavisuals.webpanel;

import net.minecraft.client.Camera;
import org.joml.Matrix4f;

final class WebPanelView {
    static final Matrix4f ROTATION = new Matrix4f();

    private WebPanelView() {
    }

    static void update(Camera camera) {
        if (camera != null) {
            camera.getViewRotationMatrix(ROTATION);
        } else {
            ROTATION.identity();
        }
    }

    static Matrix4f rotation() {
        return ROTATION;
    }
}
