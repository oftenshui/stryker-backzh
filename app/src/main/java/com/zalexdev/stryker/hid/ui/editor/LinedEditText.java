package com.zalexdev.stryker.hid.ui.editor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.Layout;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatEditText;

public class LinedEditText extends AppCompatEditText {

    private static final int COLOR_GUTTER_BG     = 0xFFF4F6FA;
    private static final int COLOR_GUTTER_BORDER = 0xFFE3E7EE;
    private static final int COLOR_GUTTER_TEXT   = 0xFF94A3B8;
    private static final int COLOR_GUTTER_ACTIVE = 0xFF1565C0;
    private static final int COLOR_CURRENT_LINE  = 0x141565C0;
    private static final int COLOR_ERROR_LINE    = 0x1FC62828;
    private static final int COLOR_ERROR_STRIPE  = 0xFFC62828;

    private final Paint gutterBgPaint = new Paint();
    private final Paint gutterBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gutterTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gutterActivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint currentLinePaint = new Paint();
    private final Paint errorLinePaint = new Paint();
    private final Paint errorStripePaint = new Paint();
    private final Rect rowRect = new Rect();

    private int gutterWidth;
    private int gutterTextPad;
    private int errorLine = -1;
    private int userPaddingTop;
    private int userPaddingRight;
    private int userPaddingBottom;

    public LinedEditText(@NonNull Context context) {
        this(context, null);
    }

    public LinedEditText(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, android.R.attr.editTextStyle);
    }

    public LinedEditText(@NonNull Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        setTypeface(Typeface.MONOSPACE);
        setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        setBackground(null);
        setInputType(getInputType()
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        setHorizontallyScrolling(false);
        setIncludeFontPadding(false);

        float density = getResources().getDisplayMetrics().density;
        gutterBgPaint.setColor(COLOR_GUTTER_BG);
        gutterBgPaint.setStyle(Paint.Style.FILL);
        gutterBorderPaint.setColor(COLOR_GUTTER_BORDER);
        gutterBorderPaint.setStrokeWidth(1f);
        gutterTextPaint.setColor(COLOR_GUTTER_TEXT);
        gutterTextPaint.setTypeface(Typeface.MONOSPACE);
        gutterTextPaint.setTextSize(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, 11, getResources().getDisplayMetrics()));
        gutterTextPaint.setTextAlign(Paint.Align.RIGHT);
        gutterActivePaint.setColor(COLOR_GUTTER_ACTIVE);
        gutterActivePaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        gutterActivePaint.setTextSize(gutterTextPaint.getTextSize());
        gutterActivePaint.setTextAlign(Paint.Align.RIGHT);
        currentLinePaint.setColor(COLOR_CURRENT_LINE);
        currentLinePaint.setStyle(Paint.Style.FILL);
        errorLinePaint.setColor(COLOR_ERROR_LINE);
        errorLinePaint.setStyle(Paint.Style.FILL);
        errorStripePaint.setColor(COLOR_ERROR_STRIPE);
        errorStripePaint.setStyle(Paint.Style.FILL);

        userPaddingTop = getPaddingTop();
        userPaddingRight = getPaddingRight();
        userPaddingBottom = getPaddingBottom();
        gutterWidth = (int) (42 * density);
        gutterTextPad = (int) (8 * density);
        super.setPadding(gutterWidth + (int) (10 * density),
                userPaddingTop, userPaddingRight, userPaddingBottom);
    }

    public void setErrorLine(int line) {
        if (this.errorLine == line) return;
        this.errorLine = line;
        invalidate();
    }

    public int getErrorLine() {
        return errorLine;
    }

    public void scrollToLine(int line) {
        Layout layout = getLayout();
        if (layout == null || line < 1) return;
        int lineIndex = Math.min(line - 1, layout.getLineCount() - 1);
        int start = layout.getLineStart(lineIndex);
        int end = layout.getLineEnd(lineIndex);
        setSelection(Math.min(start, length()), Math.min(end, length()));
        int top = layout.getLineTop(lineIndex) + getExtendedPaddingTop();
        int bottom = layout.getLineBottom(lineIndex) + getExtendedPaddingTop();
        int viewportCentreY = (top + bottom) / 2;
        requestRectangleOnScreen(new Rect(0, top, getWidth(), bottom));
        scrollTo(0, Math.max(0, viewportCentreY - getHeight() / 2));
    }

    @Override
    public void setPadding(int left, int top, int right, int bottom) {
        userPaddingTop = top;
        userPaddingRight = right;
        userPaddingBottom = bottom;
        super.setPadding(gutterWidth + (int) (10 * getResources().getDisplayMetrics().density),
                top, right, bottom);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Layout layout = getLayout();
        if (layout == null) {
            super.onDraw(canvas);
            return;
        }

        final int scrollY = getScrollY();
        final int paddingTop = getExtendedPaddingTop();
        final int width = getWidth();
        final int height = getHeight();

        canvas.drawRect(0, scrollY, gutterWidth, scrollY + height, gutterBgPaint);
        canvas.drawLine(gutterWidth + 0.5f, scrollY, gutterWidth + 0.5f, scrollY + height, gutterBorderPaint);

        int visibleTopLayout = Math.max(0, scrollY - paddingTop);
        int visibleBottomLayout = Math.max(visibleTopLayout, scrollY + height - paddingTop);
        int firstLine = layout.getLineForVertical(visibleTopLayout);
        int lastLine = layout.getLineForVertical(visibleBottomLayout);
        int currentLine = currentLineIndex(layout);

        for (int i = firstLine; i <= lastLine; i++) {
            int top = layout.getLineTop(i) + paddingTop;
            int bottom = layout.getLineBottom(i) + paddingTop;
            int lineNumber = i + 1;
            if (lineNumber == errorLine) {
                rowRect.set(gutterWidth + 1, top, width, bottom);
                canvas.drawRect(rowRect, errorLinePaint);
                canvas.drawRect(gutterWidth + 1, top, gutterWidth + 4, bottom, errorStripePaint);
            } else if (i == currentLine && hasFocus()) {
                rowRect.set(gutterWidth + 1, top, width, bottom);
                canvas.drawRect(rowRect, currentLinePaint);
            }
        }

        super.onDraw(canvas);

        Paint.FontMetrics fm = gutterTextPaint.getFontMetrics();
        float numberX = gutterWidth - gutterTextPad;
        for (int i = firstLine; i <= lastLine; i++) {
            int top = layout.getLineTop(i) + paddingTop;
            int bottom = layout.getLineBottom(i) + paddingTop;
            float centreY = (top + bottom) / 2f - (fm.ascent + fm.descent) / 2f;
            boolean active = (i == currentLine) && hasFocus();
            Paint paint = active ? gutterActivePaint : gutterTextPaint;
            canvas.drawText(String.valueOf(i + 1), numberX, centreY, paint);
        }
    }

    private int currentLineIndex(Layout layout) {
        int selStart = getSelectionStart();
        if (selStart < 0) return -1;
        return layout.getLineForOffset(Math.min(selStart, length()));
    }

    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        super.onSelectionChanged(selStart, selEnd);
        invalidate();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_TAB && getText() != null) {
            int s = getSelectionStart();
            int e = getSelectionEnd();
            getText().replace(Math.min(s, e), Math.max(s, e), "  ");
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
