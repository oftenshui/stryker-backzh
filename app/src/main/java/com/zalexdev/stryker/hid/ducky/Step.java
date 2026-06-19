package com.zalexdev.stryker.hid.ducky;

import androidx.annotation.NonNull;

public abstract class Step {

    public final int sourceLine;

    Step(int sourceLine) {
        this.sourceLine = sourceLine;
    }

    public static final class Delay extends Step {
        public final long millis;
        public Delay(int line, long millis) { super(line); this.millis = millis; }
    }

    public static final class TypeString extends Step {
        public final String text;
        public final boolean trailingEnter;
        public final long perCharDelay;
        public TypeString(int line, @NonNull String text, boolean trailingEnter, long perCharDelay) {
            super(line);
            this.text = text;
            this.trailingEnter = trailingEnter;
            this.perCharDelay = perCharDelay;
        }
    }

    public static final class Combo extends Step {
        public final int modifier;
        public final int keycode;
        public Combo(int line, int modifier, int keycode) {
            super(line);
            this.modifier = modifier;
            this.keycode = keycode;
        }
    }

    public static final class Hold extends Step {
        public final int modifier;
        public final int keycode;
        public Hold(int line, int modifier, int keycode) {
            super(line);
            this.modifier = modifier;
            this.keycode = keycode;
        }
    }

    public static final class Release extends Step {
        public Release(int line) { super(line); }
    }

    public static final class MouseMove extends Step {
        public final int dx;
        public final int dy;
        public MouseMove(int line, int dx, int dy) {
            super(line);
            this.dx = dx;
            this.dy = dy;
        }
    }

    public static final class MouseClick extends Step {
        public final int button;
        public MouseClick(int line, int button) {
            super(line);
            this.button = button;
        }
    }

    public static final class MouseScroll extends Step {
        public final int amount;
        public MouseScroll(int line, int amount) {
            super(line);
            this.amount = amount;
        }
    }

    public static final class Comment extends Step {
        public final String text;
        public Comment(int line, String text) {
            super(line);
            this.text = text;
        }
    }

    public static final class Capture extends Step {
        public final long timeoutMillis;
        public Capture(int line, long timeoutMillis) {
            super(line);
            this.timeoutMillis = timeoutMillis;
        }
    }

    public static final class OpenViewer extends Step {
        public OpenViewer(int line) { super(line); }
    }
}
