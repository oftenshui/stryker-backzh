package com.zalexdev.stryker.logger;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.zalexdev.stryker.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LoggerAdapter extends RecyclerView.Adapter<LoggerAdapter.ViewHolder> {

    private static final int C_CMD = Color.parseColor("#4FC3F7");
    private static final int C_OUT = Color.parseColor("#CFD8DC");
    private static final int C_ERR = Color.parseColor("#EF5350");
    private static final int C_WARN = Color.parseColor("#FFB74D");
    private static final int C_OK = Color.parseColor("#81C784");
    private static final int C_INFO = Color.parseColor("#90A4AE");
    private static final int C_TIME = Color.parseColor("#607D8B");
    private static final int C_TOOL = Color.parseColor("#B39DDB");
    private static final int C_HIGHLIGHT = Color.parseColor("#664FC3F7");

    private final Context context;
    private final List<LogEntry> items;
    private final SimpleDateFormat clock = new SimpleDateFormat("HH:mm:ss", Locale.US);
    private String highlight;

    public LoggerAdapter(Context context, List<LogEntry> items) {
        this.context = context;
        this.items = items != null ? items : new ArrayList<>();
        setHasStableIds(true);
    }

    public void setHighlight(String term) {
        this.highlight = term == null || term.trim().isEmpty() ? null : term.trim();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setItems(List<LogEntry> fresh) {
        items.clear();
        if (fresh != null) items.addAll(fresh);
        notifyDataSetChanged();
    }

    public void append(List<LogEntry> older) {
        if (older == null || older.isEmpty()) return;
        int start = items.size();
        items.addAll(older);
        notifyItemRangeInserted(start, older.size());
    }

    public void prepend(List<LogEntry> newer) {
        if (newer == null || newer.isEmpty()) return;
        items.addAll(0, newer);
        notifyItemRangeInserted(0, newer.size());
    }

    public LogEntry first() {
        return items.isEmpty() ? null : items.get(0);
    }

    public LogEntry last() {
        return items.isEmpty() ? null : items.get(items.size() - 1);
    }

    public void trimTail(int keep) {
        int size = items.size();
        if (size <= keep) return;
        int removed = size - keep;
        items.subList(keep, size).clear();
        notifyItemRangeRemoved(keep, removed);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.logger_item, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        LogEntry e = items.get(position);
        int color = colorFor(e.level);

        h.rail.setBackgroundColor(color);
        h.time.setText(clock.format(new Date(e.ts)));
        h.time.setTextColor(C_TIME);

        if (e.tool == null || e.tool.isEmpty()) {
            h.tool.setVisibility(View.GONE);
        } else {
            h.tool.setVisibility(View.VISIBLE);
            h.tool.setText(e.tool);
            h.tool.setTextColor(C_TOOL);
        }

        h.msg.setTextColor(color);
        h.msg.setText(highlighted(e.msg));
    }

    private CharSequence highlighted(String msg) {
        if (highlight == null || msg == null) return msg;
        String lower = msg.toLowerCase(Locale.US);
        String term = highlight.toLowerCase(Locale.US);
        int idx = lower.indexOf(term);
        if (idx < 0) return msg;
        SpannableString sp = new SpannableString(msg);
        while (idx >= 0) {
            int end = idx + term.length();
            sp.setSpan(new BackgroundColorSpan(C_HIGHLIGHT), idx, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            sp.setSpan(new ForegroundColorSpan(Color.WHITE), idx, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            sp.setSpan(new StyleSpan(Typeface.BOLD), idx, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            idx = lower.indexOf(term, end);
        }
        return sp;
    }

    private static int colorFor(int level) {
        switch (level) {
            case LogEntry.CMD: return C_CMD;
            case LogEntry.OUT: return C_OUT;
            case LogEntry.ERR: return C_ERR;
            case LogEntry.WARN: return C_WARN;
            case LogEntry.SUCCESS: return C_OK;
            default: return C_INFO;
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).id;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final View rail;
        final TextView time;
        final TextView tool;
        final TextView msg;

        public ViewHolder(View v) {
            super(v);
            rail = v.findViewById(R.id.logger_type);
            time = v.findViewById(R.id.log_time);
            tool = v.findViewById(R.id.log_tool);
            msg = v.findViewById(R.id.input_text);
        }
    }
}
