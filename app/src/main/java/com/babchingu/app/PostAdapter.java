package com.babchingu.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.babchingu.app.model.Post;

import java.util.ArrayList;
import java.util.List;

public class PostAdapter
        extends RecyclerView.Adapter<PostAdapter.ViewHolder> {

    public interface OnItemClick {
        void onClick(Post post);
    }

    private List<Post> posts = new ArrayList<>();
    private final OnItemClick listener;

    public PostAdapter(OnItemClick listener) {
        this.listener = listener;
    }

    public void setPosts(List<Post> list) {
        posts = list == null
                ? new ArrayList<>()
                : new ArrayList<>(list);
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvTime;
        final TextView tvPlace;
        final TextView tvPeople;
        final TextView tvAuthor;

        ViewHolder(View view) {
            super(view);
            tvTime = view.findViewById(R.id.tvItemTime);
            tvPlace = view.findViewById(R.id.tvItemPlace);
            tvPeople = view.findViewById(R.id.tvItemPeople);
            tvAuthor = view.findViewById(R.id.tvItemAuthor);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_post, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull ViewHolder holder, int position) {
        Post post = posts.get(position);

        holder.tvTime.setText(post.mealTime);
        holder.tvPlace.setText(post.place);

        int people = post.participants == null
                ? 0 : post.participants.size();

        if (Keys.STATUS_CLOSED.equals(post.status)) {
            holder.tvPeople.setText("마감");
        } else {
            holder.tvPeople.setText(
                    people + " / " + post.maxPeople + "명");
        }

        holder.tvAuthor.setText(
                post.authorDept + " · " + post.authorName);

        holder.itemView.setOnClickListener(
                view -> listener.onClick(post));
    }

    @Override
    public int getItemCount() {
        return posts.size();
    }
}