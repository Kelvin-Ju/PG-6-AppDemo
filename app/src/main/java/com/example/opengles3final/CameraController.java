package com.example.opengles3final;

import org.andresoviedo.android_3d_model_engine.camera.DefaultCamera;
import org.andresoviedo.android_3d_model_engine.camera.IsometricCamera;
import org.andresoviedo.android_3d_model_engine.camera.OrthographicCamera;
import org.andresoviedo.android_3d_model_engine.camera.PointOfViewCamera;
import org.andresoviedo.android_3d_model_engine.controller.TouchEvent;
import org.andresoviedo.android_3d_model_engine.model.Camera;
import org.andresoviedo.android_3d_model_engine.model.Constants;
import org.andresoviedo.android_3d_model_engine.model.Projection;
import org.andresoviedo.android_3d_model_engine.view.ViewEvent;
import org.andresoviedo.util.event.EventListener;

import java.util.EventObject;

public final class CameraController implements EventListener {

    private final Camera handlerDefault;
    private final Camera handlerIsometric;
    private final Camera handlerOrtho;
    private final Camera handlerPOV;

    private final Camera camera;

    private Camera handler;
    private int width;
    private int height;

    public CameraController(Camera camera) {
        this.camera = camera;
        this.handlerDefault = new DefaultCamera(camera);
        this.handlerIsometric = new IsometricCamera(camera);
        this.handlerOrtho = new OrthographicCamera(camera);
        this.handlerPOV = new PointOfViewCamera(camera);
        this.handler = handlerDefault;
        this.handler.enable();
    }

    private void updateHandler(Projection projection) {
        switch (projection) {
            case PERSPECTIVE:
                this.handler = handlerDefault;
                break;
            case ISOMETRIC:
                this.handler = handlerIsometric;
                break;
            case ORTHOGRAPHIC:
                this.handler = handlerOrtho;
                break;
            case FREE:
                this.handler = handlerPOV;
                break;
            default:
                throw new UnsupportedOperationException("Unsupported projection: " + projection);
        }
        this.camera.setDelegate(this.handler);
        this.handler.enable();
    }

    @Override
    public boolean onEvent(EventObject event) {
        if (event instanceof ViewEvent) {
            final ViewEvent viewEvent = (ViewEvent) event;
            switch (viewEvent.getCode()) {
                case SURFACE_CREATED:
                case SURFACE_CHANGED:
                    this.width = ((ViewEvent) event).getWidth();
                    this.height = ((ViewEvent) event).getHeight();
                    break;
                case PROJECTION_CHANGED:
                    camera.setProjection(viewEvent.getProjection());
                    updateHandler(viewEvent.getProjection());
                    break;
            }
        } else if (event instanceof TouchEvent) {
            TouchEvent touchEvent = (TouchEvent) event;
            switch (touchEvent.getAction()) {
                case MOVE:
                    handleRotation(touchEvent.getdX(), touchEvent.getdY());
                    break;
                case PINCH:
                    final float zoomFactor = touchEvent.getZoom();
                    handler.MoveCameraZ((float) (-zoomFactor * Constants.near * Math.log(camera.getDistance())));
                    break;
            }
        }
        return true;
    }

    private void handleRotation(float dx, float dy) {
        float max = Math.max(width, height);
        dx = (float) (dx / max * Math.PI * 2);
        dy = (float) (dy / max * Math.PI * 2);

        // Clamping rotation angles
        float clampedDX = clamp(dx, (float) (-Math.PI / 4), (float) (Math.PI / 4)); // Clamp to +/- 45 degrees
        float clampedDY = clamp(dy, (float) (-Math.PI / 9), (float) (Math.PI / 9)); // Clamp to +/- 20 degrees

        handler.translateCamera(clampedDX, clampedDY);
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
