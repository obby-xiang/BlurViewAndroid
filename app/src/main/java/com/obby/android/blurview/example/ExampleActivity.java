package com.obby.android.blurview.example;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.slider.Slider;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.obby.android.blurview.BlurView;
import com.obby.android.blurview.R;

import java.util.Collections;
import java.util.Locale;

/**
 * 模糊视图示例
 *
 * @author obby-xiang
 */
@SuppressWarnings("FieldCanBeLocal")
public class ExampleActivity extends AppCompatActivity {
    private MaterialCardView mImageContainerView;

    private TextView mTitleView;

    private BlurView mBlurView;

    private TextView mBlurRadiusTextView;

    private Slider mBlurRadiusView;

    private TextView mSampleSizeTextView;

    private Slider mSampleSizeView;

    private SwitchMaterial mExcludeTitleView;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_example);

        mImageContainerView = findViewById(R.id.image_container);
        mTitleView = findViewById(R.id.title);
        mBlurView = findViewById(R.id.blur);
        mBlurRadiusTextView = findViewById(R.id.blur_radius_text);
        mBlurRadiusView = findViewById(R.id.blur_radius);
        mSampleSizeTextView = findViewById(R.id.sample_size_text);
        mSampleSizeView = findViewById(R.id.sample_size);
        mExcludeTitleView = findViewById(R.id.exclude_title);

        mBlurRadiusTextView.setText(String.format(Locale.ROOT, "%1.0f", mBlurView.getBlurRadius()));
        mBlurRadiusView.setValue(mBlurView.getBlurRadius());
        mSampleSizeTextView.setText(String.format(Locale.ROOT, "%d", mBlurView.getInSampleSize()));
        mSampleSizeView.setValue(mBlurView.getInSampleSize());

        mImageContainerView.setOnTouchListener((view, event) -> {
            if (event != null) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        mBlurView.setVisibility(View.GONE);
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        mBlurView.setVisibility(View.VISIBLE);
                        break;
                }
            }
            return true;
        });

        mBlurRadiusView.addOnChangeListener((slider, value, fromUser) ->
                mBlurRadiusTextView.setText(String.format(Locale.ROOT, "%1.0f", value)));
        mBlurRadiusView.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @SuppressLint("RestrictedApi")
            @Override
            public void onStartTrackingTouch(@NonNull Slider slider) {
            }

            @SuppressLint("RestrictedApi")
            @Override
            public void onStopTrackingTouch(@NonNull Slider slider) {
                mBlurView.setBlurRadius(slider.getValue());
            }
        });

        mSampleSizeView.addOnChangeListener((slider, value, fromUser) ->
                mSampleSizeTextView.setText(String.format(Locale.ROOT, "%1.0f", value)));
        mSampleSizeView.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @SuppressLint("RestrictedApi")
            @Override
            public void onStartTrackingTouch(@NonNull Slider slider) {
            }

            @SuppressLint("RestrictedApi")
            @Override
            public void onStopTrackingTouch(@NonNull Slider slider) {
                mBlurView.setInSampleSize((int) slider.getValue());
            }
        });

        mExcludeTitleView.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                mBlurView.setViewExcludes(Collections.singletonList(mTitleView));
            } else {
                mBlurView.setViewExcludes(null);
            }
        });

        mExcludeTitleView.setChecked(true);
    }
}