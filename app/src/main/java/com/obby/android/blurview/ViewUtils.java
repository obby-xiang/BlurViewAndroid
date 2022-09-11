package com.obby.android.blurview;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Rect;
import android.view.View;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 视图工具
 *
 * @author obby-xiang
 */
public final class ViewUtils {
    private ViewUtils() {
    }

    /**
     * 获取视图相对目标视图的区域
     *
     * @param view       视图
     * @param targetView 目标视图
     * @return 视图相对目标视图的区域
     */
    @NonNull
    public static Rect getRectRelativeToTarget(@Nullable final View view,
                                               @Nullable final View targetView) {
        final Rect viewRect = getViewRectOnScreen(view);

        if (targetView != null) {
            final Rect targetRect = getViewRectOnScreen(targetView);
            viewRect.offset(-targetRect.left, -targetRect.top);
        }

        return viewRect;
    }

    /**
     * 获取视图在屏幕的区域
     *
     * @param view 视图
     * @return 视图区域
     */
    @NonNull
    public static Rect getViewRectOnScreen(@Nullable final View view) {
        final Rect rect = new Rect();

        if (view != null) {
            final int[] location = new int[2];
            view.getLocationOnScreen(location);
            rect.set(location[0], location[1], location[0] + view.getRight() - view.getLeft(),
                    location[1] + view.getBottom() - view.getTop());
        }

        return rect;
    }

    /**
     * 获取窗口内容视图
     *
     * @param context 上下文
     * @return 窗口内容视图
     */
    @Nullable
    public static View getWindowContentView(@Nullable final Context context) {
        final Window window = getWindow(context);
        return window == null ? null : window.findViewById(Window.ID_ANDROID_CONTENT);
    }

    /**
     * 获取窗口视图
     *
     * @param context 上下文
     * @return 窗口视图
     */
    @Nullable
    public static View getWindowDecorView(@Nullable final Context context) {
        final Window window = getWindow(context);
        return window == null ? null : window.getDecorView();
    }

    /**
     * 获取窗口
     *
     * @param context 上下文
     * @return 窗口
     */
    @Nullable
    public static Window getWindow(@Nullable final Context context) {
        final Activity activity = getActivity(context);
        return activity == null ? null : activity.getWindow();
    }

    /**
     * 获取界面
     *
     * @param context 上下文
     * @return 界面
     */
    @Nullable
    public static Activity getActivity(@Nullable final Context context) {
        if (context instanceof Activity) {
            return (Activity) context;
        }

        if (context instanceof ContextWrapper) {
            final Context baseContext = ((ContextWrapper) context).getBaseContext();
            return baseContext == context ? null : getActivity(baseContext);
        }

        return null;
    }
}
