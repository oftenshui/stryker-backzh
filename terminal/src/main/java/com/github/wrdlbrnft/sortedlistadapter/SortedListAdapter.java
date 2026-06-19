package com.github.wrdlbrnft.sortedlistadapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SortedList;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class SortedListAdapter<T extends SortedListAdapter.ViewModel>
        extends RecyclerView.Adapter<SortedListAdapter.ViewHolder<? extends SortedListAdapter.ViewModel>> {

    public interface ViewModel {
        <M> boolean isSameModelAs(@NonNull M model);

        <M> boolean isContentTheSameAs(@NonNull M model);
    }

    public interface Callback {
        void onEditStarted();

        void onEditFinished();
    }

    public interface Editor<T extends ViewModel> {
        Editor<T> add(T item);

        Editor<T> add(List<T> items);

        Editor<T> remove(T item);

        Editor<T> remove(List<T> items);

        Editor<T> replaceAll(List<T> items);

        Editor<T> removeAll();

        void commit();
    }

    public static abstract class ViewHolder<T extends ViewModel> extends RecyclerView.ViewHolder {
        private T currentItem;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
        }

        final void bind(@NonNull T item) {
            currentItem = item;
            performBind(item);
        }

        protected abstract void performBind(@NonNull T item);

        @NonNull
        public final T getCurrentItem() {
            return currentItem;
        }
    }

    private final LayoutInflater inflater;
    private final SortedList<T> sortedList;
    private final List<Callback> callbacks = new ArrayList<>();

    public SortedListAdapter(@NonNull Context context, @NonNull Class<T> itemClass,
                             @NonNull final Comparator<T> comparator) {
        this.inflater = LayoutInflater.from(context);
        this.sortedList = new SortedList<>(itemClass, new SortedList.Callback<T>() {
            @Override
            public int compare(T a, T b) {
                return comparator.compare(a, b);
            }

            @Override
            public void onInserted(int position, int count) {
                notifyItemRangeInserted(position, count);
            }

            @Override
            public void onRemoved(int position, int count) {
                notifyItemRangeRemoved(position, count);
            }

            @Override
            public void onMoved(int fromPosition, int toPosition) {
                notifyItemMoved(fromPosition, toPosition);
            }

            @Override
            public void onChanged(int position, int count) {
                notifyItemRangeChanged(position, count);
            }

            @Override
            public boolean areContentsTheSame(T oldItem, T newItem) {
                return oldItem.isContentTheSameAs(newItem);
            }

            @Override
            public boolean areItemsTheSame(T item1, T item2) {
                return item1.isSameModelAs(item2);
            }
        });
    }

    protected abstract ViewHolder<? extends T> onCreateViewHolder(@NonNull LayoutInflater inflater,
                                                                  @NonNull ViewGroup parent, int viewType);

    @NonNull
    @Override
    public ViewHolder<? extends ViewModel> onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return onCreateViewHolder(inflater, parent, viewType);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onBindViewHolder(@NonNull ViewHolder<? extends ViewModel> holder, int position) {
        ((ViewHolder<T>) holder).bind(sortedList.get(position));
    }

    @Override
    public int getItemCount() {
        return sortedList.size();
    }

    public T getItem(int position) {
        return sortedList.get(position);
    }

    public final void addCallback(@NonNull Callback callback) {
        if (!callbacks.contains(callback)) {
            callbacks.add(callback);
        }
    }

    public final void removeCallback(@NonNull Callback callback) {
        callbacks.remove(callback);
    }

    public Editor<T> edit() {
        return new EditorImpl();
    }

    private class EditorImpl implements Editor<T> {
        private final List<Runnable> actions = new ArrayList<>();

        @Override
        public Editor<T> add(final T item) {
            actions.add(() -> sortedList.add(item));
            return this;
        }

        @Override
        public Editor<T> add(final List<T> items) {
            actions.add(() -> sortedList.addAll(new ArrayList<>(items)));
            return this;
        }

        @Override
        public Editor<T> remove(final T item) {
            actions.add(() -> sortedList.remove(item));
            return this;
        }

        @Override
        public Editor<T> remove(final List<T> items) {
            actions.add(() -> {
                for (T item : items) {
                    sortedList.remove(item);
                }
            });
            return this;
        }

        @Override
        public Editor<T> replaceAll(final List<T> items) {
            actions.add(() -> sortedList.replaceAll(new ArrayList<>(items)));
            return this;
        }

        @Override
        public Editor<T> removeAll() {
            actions.add(sortedList::clear);
            return this;
        }

        @Override
        public void commit() {
            for (Callback callback : callbacks) {
                callback.onEditStarted();
            }
            sortedList.beginBatchedUpdates();
            try {
                for (Runnable action : actions) {
                    action.run();
                }
            } finally {
                sortedList.endBatchedUpdates();
            }
            for (Callback callback : callbacks) {
                callback.onEditFinished();
            }
        }
    }

    public static class ComparatorBuilder<T extends ViewModel> {
        private final Map<Class<?>, Comparator<?>> comparators = new HashMap<>();
        private final List<Class<?>> order = new ArrayList<>();

        public <M extends T> ComparatorBuilder<T> setOrderForModel(@NonNull Class<M> modelClass,
                                                                   @NonNull Comparator<M> comparator) {
            comparators.put(modelClass, comparator);
            order.add(modelClass);
            return this;
        }

        @NonNull
        @SuppressWarnings("unchecked")
        public Comparator<T> build() {
            return (a, b) -> {
                final Class<?> classA = a.getClass();
                final Class<?> classB = b.getClass();
                if (classA.equals(classB)) {
                    final Comparator<T> comparator = (Comparator<T>) comparators.get(classA);
                    if (comparator != null) {
                        return comparator.compare(a, b);
                    }
                    return 0;
                }
                return Integer.compare(order.indexOf(classA), order.indexOf(classB));
            };
        }
    }
}
