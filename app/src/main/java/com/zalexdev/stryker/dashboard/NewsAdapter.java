package com.zalexdev.stryker.dashboard;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.imageview.ShapeableImageView;
import com.zalexdev.stryker.R;
import com.zalexdev.stryker.custom.News;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NewsAdapter extends RecyclerView.Adapter<NewsAdapter.ViewHolder> {

    private static final String PREFS = "stryker_news";
    private static final String KEY_READ = "read";

    private final Activity activity;
    private final Context context;
    private final List<News> items;
    private final SharedPreferences prefs;
    private final Set<String> readIds;

    public NewsAdapter(Context context, Activity activity, List<News> items) {
        this.activity = activity;
        this.context = context;
        this.items = new ArrayList<>(items);
        this.prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.readIds = new HashSet<>(prefs.getStringSet(KEY_READ, new HashSet<>()));
    }

    public void setItems(List<News> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.news_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        News item = items.get(position);
        holder.title.setText(item.title);
        holder.title.setTypeface(null, isRead(item) ? Typeface.NORMAL : Typeface.BOLD);
        holder.date.setText(formatDate(item.newsDate));
        holder.description.setText(item.description);
        holder.pin.setVisibility(item.pinned ? View.VISIBLE : View.GONE);
        holder.cardView.setOnClickListener(v -> openDialog(item, holder));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @SuppressLint("InflateParams")
    private void openDialog(News item, ViewHolder holder) {
        markRead(item, holder);

        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_news, null);
        AlertDialog dialog = new MaterialAlertDialogBuilder(context).setView(dialogView).create();

        ShapeableImageView image = dialogView.findViewById(R.id.news_image);
        TextView title = dialogView.findViewById(R.id.news_title);
        TextView date = dialogView.findViewById(R.id.news_date);
        TextView description = dialogView.findViewById(R.id.news_description);
        MaterialButton button1 = dialogView.findViewById(R.id.news_button1);
        MaterialButton button2 = dialogView.findViewById(R.id.news_button2);

        if (item.hasImage()) {
            image.setVisibility(View.VISIBLE);
            image.setImageBitmap(item.image);
        } else {
            image.setVisibility(View.GONE);
        }

        title.setText(item.title);
        date.setText(formatDate(item.newsDate));

        description.setText(item.description);
        if (item.hasArticleLink()) {
            description.append(" ");
            description.append(Html.fromHtml(
                    "<a href=\"" + item.newsUrl + "\">" + context.getString(R.string.news_read_more) + "</a>",
                    Html.FROM_HTML_MODE_LEGACY));
            description.setMovementMethod(LinkMovementMethod.getInstance());
        }

        bindButton(button1, item.actionbutton1, item.actionbutton1text, item.actionbutton1url, dialog);
        bindButton(button2, item.actionbutton2, item.actionbutton2text, item.actionbutton2url, dialog);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        dialog.show();
    }

    private void bindButton(MaterialButton button, boolean show, String text, String url, AlertDialog dialog) {
        if (!show) {
            button.setVisibility(View.GONE);
            return;
        }
        button.setVisibility(View.VISIBLE);
        button.setText(text);
        button.setOnClickListener(v -> {
            dialog.dismiss();
            openLink(url);
        });
    }

    private boolean isRead(News item) {
        return readIds.contains(String.valueOf(item.id));
    }

    private void markRead(News item, ViewHolder holder) {
        if (readIds.add(String.valueOf(item.id))) {
            prefs.edit().putStringSet(KEY_READ, new HashSet<>(readIds)).apply();
            int adapterPosition = holder.getBindingAdapterPosition();
            if (adapterPosition != RecyclerView.NO_POSITION) {
                notifyItemChanged(adapterPosition);
            }
        }
    }

    private static String formatDate(String date) {
        if (date == null || date.isEmpty()) {
            return "";
        }
        return date.replace("-", ".");
    }

    private void openLink(String url) {
        if (url == null || url.isEmpty()) {
            return;
        }
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(context, R.string.news_no_app, Toast.LENGTH_SHORT).show();
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        final TextView title;
        final TextView date;
        final TextView description;
        final ImageView pin;
        final MaterialCardView cardView;

        public ViewHolder(View view) {
            super(view);
            title = view.findViewById(R.id.news_title);
            date = view.findViewById(R.id.news_date);
            description = view.findViewById(R.id.news_description);
            pin = view.findViewById(R.id.news_pin);
            cardView = view.findViewById(R.id.news_card);
        }
    }
}
