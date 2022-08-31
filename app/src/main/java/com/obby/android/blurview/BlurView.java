package com.obby.android.blurview;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DrawFilter;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RecordingCanvas;
import android.graphics.Rect;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.os.Build;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.annotation.FloatRange;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;

/**
 * 模糊视图
 *
 * @author obby-xiang
 */
public class BlurView extends View {
    /**
     * 最大模糊半径
     */
    public static final float MAX_BLUR_RADIUS = 25f;

    /**
     * 默认模糊半径
     */
    public static final float DEFAULT_BLUR_RADIUS = 16f;

    /**
     * 默认采样大小
     */
    public static final int DEFAULT_IN_SAMPLE_SIZE = 4;

    private static final String TAG = "BlurView";

    /**
     * 模糊半径
     */
    @FloatRange(from = 0f, fromInclusive = false, to = MAX_BLUR_RADIUS)
    private float mBlurRadius;

    /**
     * 采样大小
     */
    @IntRange(from = 1)
    private int mInSampleSize;

    /**
     * 不模糊视图
     */
    @NonNull
    private final Set<View> mViewExcludes;

    @Nullable
    private View mDecorView;

    private boolean mIsUpdating;

    @Nullable
    private Canvas mCanvas;

    @Nullable
    private Paint mPaint;

    @Nullable
    private RenderScript mRenderScript;

    @Nullable
    private ScriptIntrinsicBlur mBlurScript;

    @Nullable
    private Bitmap mViewBitmap;

    @Nullable
    private Bitmap mBlurBitmap;

    @Nullable
    private RenderNode mBlurRenderNode;

    private final DrawFilter mDrawFilter = new PaintFlagsDrawFilter(Paint.DITHER_FLAG,
            Paint.FILTER_BITMAP_FLAG);

    private final ViewTreeObserver.OnPreDrawListener mOnPreDrawListener = () -> {
        update();
        return true;
    };

    /**
     * 构造模糊视图
     *
     * @param context 上下文
     */
    public BlurView(@NonNull Context context) {
        this(context, null);
    }

    /**
     * 构造模糊视图
     *
     * @param context 上下文
     * @param attrs   属性
     */
    public BlurView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        mViewExcludes = new CopyOnWriteArraySet<>();

        final TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.BlurView);
        final float blurRadius = typedArray.getFloat(R.styleable.BlurView_blurRadius,
                DEFAULT_BLUR_RADIUS);
        final int inSampleSize = typedArray.getInt(R.styleable.BlurView_inSampleSize,
                DEFAULT_IN_SAMPLE_SIZE);
        typedArray.recycle();

        mBlurRadius = validateBlurRadius(blurRadius) ? blurRadius : DEFAULT_BLUR_RADIUS;
        mInSampleSize = validateInSampleSize(inSampleSize) ? inSampleSize : DEFAULT_IN_SAMPLE_SIZE;
    }

    /**
     * 获取模糊半径
     *
     * @return 模糊半径
     */
    public float getBlurRadius() {
        return mBlurRadius;
    }

    /**
     * 设置模糊半径
     *
     * @param blurRadius 模糊半径
     */
    public void setBlurRadius(float blurRadius) {
        if (!validateBlurRadius(blurRadius) || blurRadius == mBlurRadius) {
            return;
        }

        mBlurRadius = blurRadius;
        post(() -> update(true));
    }

    /**
     * 获取采样大小
     *
     * @return 采样大小
     */
    public int getInSampleSize() {
        return mInSampleSize;
    }

    /**
     * 设置采样大小
     *
     * @param inSampleSize 采样大小
     */
    public void setInSampleSize(int inSampleSize) {
        if (!validateInSampleSize(inSampleSize) || inSampleSize == mInSampleSize) {
            return;
        }

        mInSampleSize = inSampleSize;
        post(() -> update(true));
    }

    /**
     * 获取不模糊视图
     *
     * @return 不模糊视图
     */
    @NonNull
    public Collection<View> getViewExcludes() {
        return Collections.unmodifiableCollection(mViewExcludes);
    }

    /**
     * 设置不模糊视图
     *
     * @param viewExcludes 不模糊视图
     */
    public void setViewExcludes(@Nullable Collection<View> viewExcludes) {
        final Set<View> excludes = viewExcludes == null ? Collections.emptySet()
                : viewExcludes.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (excludes.containsAll(mViewExcludes) && mViewExcludes.containsAll(excludes)) {
            return;
        }

        mViewExcludes.clear();
        mViewExcludes.addAll(excludes);
        post(() -> update(true));
    }

    private boolean validateBlurRadius(final float blurRadius) {
        return blurRadius > 0 && blurRadius <= MAX_BLUR_RADIUS;
    }

    private boolean validateInSampleSize(final int inSampleSize) {
        return inSampleSize > 0;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mDecorView = ViewUtils.getDecorView(getContext());
        if (mDecorView != null) {
            mDecorView.getViewTreeObserver().addOnPreDrawListener(mOnPreDrawListener);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mDecorView != null) {
            mDecorView.getViewTreeObserver().removeOnPreDrawListener(mOnPreDrawListener);
            mDecorView = null;
        }

        if (mBlurRenderNode != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                mBlurRenderNode.discardDisplayList();
            }
            mBlurRenderNode = null;
        }

        if (mBlurBitmap != null) {
            mBlurBitmap.recycle();
            mBlurBitmap = null;
        }

        if (mViewBitmap != null) {
            mViewBitmap.recycle();
            mViewBitmap = null;
        }

        if (mBlurScript != null) {
            mBlurScript.destroy();
            mBlurScript = null;
        }

        if (mRenderScript != null) {
            mRenderScript.destroy();
            mRenderScript = null;
        }
    }

    @Override
    public void draw(Canvas canvas) {
        // 更新模糊时不绘制
        if (mIsUpdating) {
            return;
        }

        super.draw(canvas);

        canvas.saveLayer(0, 0, getWidth(), getHeight(), requirePaint());

        // 开启双线性插值优化缩放效果
        canvas.setDrawFilter(mDrawFilter);

        if (mBlurRenderNode != null && canvas.isHardwareAccelerated()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                canvas.drawRenderNode(mBlurRenderNode);
            }
        } else {
            if (mBlurBitmap != null) {
                canvas.save();
                canvas.scale(mInSampleSize, mInSampleSize);
                canvas.drawBitmap(mBlurBitmap, 0, 0, null);
                canvas.restore();
            }
        }

        // 处理不模糊区域
        applyViewExcludes(canvas);

        canvas.restore();
    }

    /**
     * 更新模糊
     */
    private void update() {
        update(false);
    }

    /**
     * 更新模糊
     *
     * @param invalidate 是否重绘视图
     */
    private void update(boolean invalidate) {
        if (!isShown() || getWidth() <= 0 || getHeight() <= 0) {
            return;
        }

        mIsUpdating = true;

        final boolean shouldInvalidate;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isHardwareAccelerated()) {
            shouldInvalidate = blurApi31();
        } else {
            shouldInvalidate = blur();
        }

        mIsUpdating = false;

        if (invalidate || shouldInvalidate) {
            invalidate();
        }
    }

    /**
     * 模糊视图处理
     *
     * @return 是否重绘视图
     */
    private boolean blur() {
        if (mRenderScript == null) {
            mRenderScript = RenderScript.create(getContext());
        }

        final Bitmap viewBitmap = createViewBitmap();
        final Allocation allocIn = Allocation.createFromBitmap(mRenderScript, viewBitmap);
        final Allocation allocOut = Allocation.createTyped(mRenderScript, allocIn.getType());

        if (mBlurScript == null) {
            mBlurScript = ScriptIntrinsicBlur.create(mRenderScript, Element.U8_4(mRenderScript));
        }

        mBlurScript.setRadius(mBlurRadius);
        mBlurScript.setInput(allocIn);
        mBlurScript.forEach(allocOut);

        final Bitmap oldBlurBitmap = mBlurBitmap;
        mBlurBitmap = resetBitmap(mBlurBitmap, viewBitmap.getWidth(), viewBitmap.getHeight());
        allocOut.copyTo(mBlurBitmap);

        allocIn.destroy();
        allocOut.destroy();
        viewBitmap.recycle();

        return mBlurBitmap != oldBlurBitmap;
    }

    /**
     * 模糊视图处理
     *
     * @return 是否重绘视图
     */
    @RequiresApi(api = Build.VERSION_CODES.S)
    private boolean blurApi31() {
        if (mBlurRenderNode == null) {
            mBlurRenderNode = new RenderNode(TAG);
        } else {
            if (mBlurRenderNode.hasDisplayList()) {
                mBlurRenderNode.discardDisplayList();
            }
        }

        mBlurRenderNode.setPosition(0, 0, getWidth(), getHeight());

        final Bitmap oldViewBitmap = mViewBitmap;
        final Bitmap viewBitmap = createViewBitmap();
        final RecordingCanvas canvas = mBlurRenderNode.beginRecording();
        canvas.scale(mInSampleSize, mInSampleSize);
        canvas.drawBitmap(viewBitmap, 0, 0, null);
        mBlurRenderNode.endRecording();

        final float blurRadius = mBlurRadius * mInSampleSize;
        final RenderEffect blurEffect = RenderEffect.createBlurEffect(blurRadius, blurRadius,
                Shader.TileMode.CLAMP);
        mBlurRenderNode.setRenderEffect(blurEffect);

        return viewBitmap != oldViewBitmap;
    }

    /**
     * 创建视图覆盖区域的位图
     *
     * @return 视图覆盖区域的位图
     */
    @NonNull
    private Bitmap createViewBitmap() {
        final Rect viewRect = ViewUtils.getRectRelativeToTarget(this, mDecorView);
        final int bitmapWidth = (int) Math.ceil((float) viewRect.width() / mInSampleSize);
        final int bitmapHeight = (int) Math.ceil((float) viewRect.height() / mInSampleSize);
        final Canvas canvas = requireCanvas();

        mViewBitmap = resetBitmap(mViewBitmap, Math.max(bitmapWidth, 1), Math.max(bitmapHeight, 1));
        canvas.setBitmap(mViewBitmap);

        // 调整画布
        canvas.scale(1 / (float) mInSampleSize, 1 / (float) mInSampleSize);
        canvas.translate(-viewRect.left, -viewRect.top);

        // 绘制窗口视图
        if (mDecorView != null) {
            mDecorView.draw(canvas);
        }

        canvas.clipRect(viewRect);
        canvas.setBitmap(null);

        return mViewBitmap;
    }

    /**
     * 应用不模糊视图区域到画布
     *
     * @param canvas 画布
     */
    private void applyViewExcludes(@NonNull final Canvas canvas) {
        if (mViewExcludes.isEmpty()) {
            return;
        }

        final Paint paint = requirePaint();
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        for (final View view : mViewExcludes) {
            if (view != null) {
                canvas.drawRect(ViewUtils.getRectRelativeToTarget(view, this), paint);
            }
        }
    }

    /**
     * 获取画布
     *
     * @return 画布
     */
    @NonNull
    private Canvas requireCanvas() {
        if (mCanvas == null) {
            mCanvas = new Canvas();
        } else {
            mCanvas.setMatrix(null);
            mCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        }

        return mCanvas;
    }

    /**
     * 获取画笔
     *
     * @return 画笔
     */
    @NonNull
    private Paint requirePaint() {
        if (mPaint == null) {
            mPaint = new Paint();
        } else {
            mPaint.reset();
        }

        mPaint.setFlags(Paint.ANTI_ALIAS_FLAG);

        return mPaint;
    }

    /**
     * 重置位图
     *
     * @param bitmap 位图
     * @param width  宽度
     * @param height 高度
     * @return 重置的位图
     */
    @NonNull
    private Bitmap resetBitmap(@Nullable final Bitmap bitmap, @IntRange(from = 1) final int width,
                               @IntRange(from = 1) final int height) {
        if (bitmap == null || bitmap.isRecycled() || !bitmap.isMutable()
                || bitmap.getWidth() != width || bitmap.getHeight() != height) {
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
            return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        }

        bitmap.eraseColor(Color.TRANSPARENT);
        bitmap.setWidth(width);
        bitmap.setHeight(height);

        return bitmap;
    }
}
