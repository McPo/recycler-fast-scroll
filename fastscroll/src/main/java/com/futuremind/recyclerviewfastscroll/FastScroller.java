package com.futuremind.recyclerviewfastscroll;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v4.widget.TextViewCompat;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Created by mklimczak on 28/07/15.
 */
public class FastScroller extends LinearLayout {
    private final int handleColor;
    private final int bubbleColor;
    private final int textAppearance;

    private FastScrollBubble bubble;
    private ImageView handle;
    private int bubbleOffset;

    private int scrollerOrientation;

    private RecyclerView recyclerView;

    private final ScrollListener scrollListener = new ScrollListener();

    private boolean manuallyChangingPosition;

    private SectionTitleProvider titleProvider;

    public FastScroller(Context context) {
        this(context, null);
    }

    public FastScroller(Context context, AttributeSet attrs) {
        super(context, attrs);
        setClipChildren(false);
        LayoutInflater inflater = LayoutInflater.from(getContext());
        inflater.inflate(R.layout.fastscroller, this);

        TypedArray style = context.obtainStyledAttributes(attrs, R.styleable.FastScroller, R.attr.fastScrollerTheme, 0);
        try {
            bubbleColor = style.getColor(R.styleable.FastScroller_bubbleColor, ContextCompat.getColor(context, android.R.color.white));
            handleColor = style.getColor(R.styleable.FastScroller_handleColor, ContextCompat.getColor(context, android.R.color.darker_gray));
            textAppearance = style.getResourceId(R.styleable.FastScroller_textAppearance, android.R.style.TextAppearance);
        }
        finally {
            style.recycle();
        }
    }

    @Override //TODO should probably use some custom orientation instead of linear layout one
    public void setOrientation(int orientation) {
        scrollerOrientation = orientation;
        //switching orientation, because orientation in linear layout
        //is something different than orientation of fast scroller
        super.setOrientation(orientation == HORIZONTAL ? VERTICAL : HORIZONTAL);
    }

    /**
     * Attach the FastScroller to RecyclerView. Should be used after the Adapter is set
     * to the RecyclerView. If the adapter implements SectionTitleProvider, the FastScroller
     * will show a bubble with title.
     * @param recyclerView A RecyclerView to attach the FastScroller to
     */
    public void setRecyclerView(RecyclerView recyclerView) {
        this.recyclerView = recyclerView;
        if(recyclerView.getAdapter() instanceof SectionTitleProvider) titleProvider = (SectionTitleProvider) recyclerView.getAdapter();
        recyclerView.addOnScrollListener(scrollListener);
        invalidateVisibility();
        recyclerView.setOnHierarchyChangeListener(new OnHierarchyChangeListener() {
            @Override
            public void onChildViewAdded(View parent, View child) {
                invalidateVisibility();
            }

            @Override
            public void onChildViewRemoved(View parent, View child) {
                invalidateVisibility();
            }
        });
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        bubble = (FastScrollBubble) findViewById(R.id.fastscroller_bubble);
        handle = (ImageView) findViewById(R.id.fastscroller_handle);
        TextView defaultBubble = (TextView) bubble.getChildAt(0);

        bubbleOffset = (int) (isVertical() ? ((float)handle.getHeight()/2f)-bubble.getHeight() : ((float)handle.getWidth()/2f)-bubble.getWidth());
        initHandleBackground();
        initHandleMovement();

        setBackgroundTint(defaultBubble, bubbleColor);
        setImageTint(handle, handleColor);
        TextViewCompat.setTextAppearance(defaultBubble, textAppearance);
    }

    private void setBackgroundTint(View view, int color) {
        final Drawable background = DrawableCompat.wrap(view.getBackground());
        DrawableCompat.setTint(background, color);
        view.setBackground(background);
    }

    private void setImageTint(ImageView view, int color) {
        final Drawable image = DrawableCompat.wrap(view.getDrawable());
        DrawableCompat.setTint(image, color);
        view.setImageDrawable(image);
    }

    private void initHandleBackground() {
        handle.setImageDrawable(ContextCompat.getDrawable(getContext(),
                isVertical() ? R.drawable.fastscroller_handle_vertical : R.drawable.fastscroller_handle_horizontal));
    }

    private void initHandleMovement() {
        handle.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {

                    if(titleProvider!=null) bubble.show();
                    manuallyChangingPosition = true;

                    float relativePos = getRelativeTouchPosition(event);
                    setHandlePosition(relativePos);
                    setRecyclerViewPosition(relativePos);

                    return true;

                } else if (event.getAction() == MotionEvent.ACTION_UP) {

                    manuallyChangingPosition = false;
                    if(titleProvider!=null) bubble.hide();
                    return true;

                }

                return false;

            }
        });
    }

    private float getRelativeTouchPosition(MotionEvent event){
        if(isVertical()){
            float yInParent = event.getRawY() - Utils.getViewRawY(handle);
            return yInParent / (getHeight() - handle.getHeight());
        } else {
            float xInParent = event.getRawX() - Utils.getViewRawX(handle);
            return xInParent / (getWidth() - handle.getWidth());
        }
    }

    private void invalidateVisibility() {
        if(
                recyclerView.getAdapter()==null ||
                recyclerView.getAdapter().getItemCount()==0 ||
                recyclerView.getChildAt(0)==null ||
                isRecyclerViewScrollable()
                ){
            setVisibility(GONE);
        } else {
            setVisibility(VISIBLE);
        }
    }

    private boolean isRecyclerViewScrollable() {
        if(isVertical()) {
            return recyclerView.getChildAt(0).getHeight() * recyclerView.getAdapter().getItemCount() <= getHeight();
        } else {
            return recyclerView.getChildAt(0).getWidth() * recyclerView.getAdapter().getItemCount() <= getWidth();
        }
    }

    private void setRecyclerViewPosition(float relativePos) {
        if (recyclerView != null) {
            int itemCount = recyclerView.getAdapter().getItemCount();
            int targetPos = (int) Utils.getValueInRange(0, itemCount - 1, (int) (relativePos * (float) itemCount));
            recyclerView.scrollToPosition(targetPos);
            if(titleProvider!=null) bubble.setText(titleProvider.getSectionTitle(targetPos));
        }
    }

    private void setHandlePosition(float relativePos) {
        if(isVertical()) {
            bubble.setY(Utils.getValueInRange(
                    0,
                    getHeight() - bubble.getHeight(),
                    relativePos * (getHeight() - handle.getHeight()) + bubbleOffset)
            );
            handle.setY(Utils.getValueInRange(
                    0,
                    getHeight() - handle.getHeight(),
                    relativePos * (getHeight() - handle.getHeight()))
            );
        } else {
            bubble.setX(Utils.getValueInRange(
                    0,
                    getWidth() - bubble.getWidth(),
                    relativePos * (getWidth() - handle.getWidth()) + bubbleOffset)
            );
            handle.setX(Utils.getValueInRange(
                    0,
                    getWidth() - handle.getWidth(),
                    relativePos * (getWidth() - handle.getWidth()))
            );
        }
    }

    private class ScrollListener extends RecyclerView.OnScrollListener {
        @Override
        public void onScrolled(RecyclerView rv, int dx, int dy) {
            if(handle!=null && !manuallyChangingPosition && recyclerView.getChildCount() > 0) {
                View firstVisibleView = recyclerView.getChildAt(0);
                float recyclerViewOversize; //how much is recyclerView bigger than fastScroller
                int recyclerViewAbsoluteScroll;
                if(isVertical()) {
                    recyclerViewOversize = firstVisibleView.getHeight() * rv.getAdapter().getItemCount() - getHeight();
                    recyclerViewAbsoluteScroll = recyclerView.getChildLayoutPosition(firstVisibleView) * firstVisibleView.getHeight() - firstVisibleView.getTop();
                } else {
                    recyclerViewOversize = firstVisibleView.getWidth() * rv.getAdapter().getItemCount() - getWidth();
                    recyclerViewAbsoluteScroll = recyclerView.getChildLayoutPosition(firstVisibleView) * firstVisibleView.getWidth() - firstVisibleView.getLeft();
                }
                setHandlePosition(recyclerViewAbsoluteScroll / recyclerViewOversize);
            }
        }
    }

    private boolean isVertical(){
        return scrollerOrientation == VERTICAL;
    }

}
