package com.variometerpro;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;

public class Compass {
    private ImageView compass;
    private float azimuthFrom;
    private float azimuthTo;
    private RotateAnimation anim;
    // record the compass picture angle turned
    private float currentDegree = 0f;

    public Compass(ImageView compass) {
        this.compass = compass;
    }

    public void rotate(float azimuthTo) {

        float degree = azimuthTo;

        anim = new RotateAnimation(360-currentDegree, 360-degree, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);

        // how long the animation will take place
        anim.setDuration(100);

        // set the animation after the end of the reservation status
        anim.setFillAfter(true);

        compass.startAnimation(anim);
        currentDegree = degree;
    }

    public float getAzimuthTo() {
        return azimuthTo;
    }

    public void setAzimuthTo(float azimuthTo) {
        this.azimuthTo = azimuthTo;
    }

    public float getAzimuthFrom() {
        return azimuthFrom;
    }

    public void setAzimuthFrom(float azimuthFrom) {
        this.azimuthFrom = azimuthFrom;
    }

}