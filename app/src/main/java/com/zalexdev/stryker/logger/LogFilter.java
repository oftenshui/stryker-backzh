package com.zalexdev.stryker.logger;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class LogFilter {

    public final Set<Integer> levels = new LinkedHashSet<>();
    public String tool;
    public String search;

    public boolean isEmpty() {
        return levels.isEmpty()
                && (tool == null || tool.isEmpty())
                && (search == null || search.trim().isEmpty());
    }

    @NonNull
    String buildWhere(long beforeId, long afterId, @NonNull List<String> outArgs) {
        StringBuilder w = new StringBuilder();

        if (beforeId != Long.MAX_VALUE) {
            append(w, "_id < ?");
            outArgs.add(Long.toString(beforeId));
        }
        if (afterId > 0) {
            append(w, "_id > ?");
            outArgs.add(Long.toString(afterId));
        }
        if (!levels.isEmpty()) {
            StringBuilder in = new StringBuilder("level IN (");
            boolean first = true;
            for (Integer lvl : levels) {
                if (!first) in.append(',');
                in.append('?');
                outArgs.add(Integer.toString(lvl));
                first = false;
            }
            in.append(')');
            append(w, in.toString());
        }
        if (tool != null && !tool.isEmpty()) {
            append(w, "tool = ?");
            outArgs.add(tool);
        }
        if (search != null && !search.trim().isEmpty()) {
            append(w, "msg LIKE ? ESCAPE '\\'");
            outArgs.add('%' + escapeLike(search.trim()) + '%');
        }

        return w.length() == 0 ? "1=1" : w.toString();
    }

    private static void append(StringBuilder w, String clause) {
        if (w.length() > 0) w.append(" AND ");
        w.append(clause);
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    public LogFilter copy() {
        LogFilter f = new LogFilter();
        f.levels.addAll(this.levels);
        f.tool = this.tool;
        f.search = this.search;
        return f;
    }

    public List<Integer> levelList() {
        return levels.isEmpty() ? Collections.emptyList() : new ArrayList<>(levels);
    }
}
