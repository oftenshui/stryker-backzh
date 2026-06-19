package com.zalexdev.stryker.dashboard;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.zalexdev.stryker.MainActivity;
import com.zalexdev.stryker.R;
import com.zalexdev.stryker.ota.NewsRepository;

public class NewsFragment extends Fragment {

    private final MainActivity.Receiver receiver = new MainActivity.Receiver();
    private NewsAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_news, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        receiver.setTitle("News");
        Activity activity = getActivity();
        Context context = getContext();
        RecyclerView list = view.findViewById(R.id.news_list);
        list.setLayoutManager(new LinearLayoutManager(context));
        list.setItemViewCacheSize(255);
        adapter = new NewsAdapter(context, activity, NewsRepository.defaults());
        list.setAdapter(adapter);
        NewsRepository.load(context, news -> {
            if (isAdded() && adapter != null) {
                adapter.setItems(news);
            }
        });
    }

    @Override
    public void onDestroy() {
        receiver.restoreTitle();
        super.onDestroy();
    }
}
