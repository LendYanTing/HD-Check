package com.hdcheck.app.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.hdcheck.app.R;
import com.hdcheck.app.model.AppRow;
import com.hdcheck.app.util.HumanReadable;

/**
 * IO 监控列表的 RecyclerView Adapter。
 * 每行显示：应用名 | 读 | 写 | 总读 | 总写
 */
public class IoRowAdapter extends ListAdapter<AppRow, IoRowAdapter.ViewHolder> {

    private static final DiffUtil.ItemCallback<AppRow> DIFF_CALLBACK = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(@NonNull AppRow oldItem, @NonNull AppRow newItem) {
            return oldItem.uid == newItem.uid;
        }

        @Override
        public boolean areContentsTheSame(@NonNull AppRow oldItem, @NonNull AppRow newItem) {
            return oldItem.speedRead == newItem.speedRead
                && oldItem.speedWrite == newItem.speedWrite
                && oldItem.accumRead == newItem.accumRead
                && oldItem.accumWrite == newItem.accumWrite
                && oldItem.label.equals(newItem.label);
        }
    };

    public IoRowAdapter() {
        super(DIFF_CALLBACK);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_app_row, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppRow row = getItem(position);
        holder.bind(row);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvLabel;
        final TextView tvSpeedRead;
        final TextView tvSpeedWrite;
        final TextView tvAccumRead;
        final TextView tvAccumWrite;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvLabel      = itemView.findViewById(R.id.tvLabel);
            tvSpeedRead  = itemView.findViewById(R.id.tvSpeedRead);
            tvSpeedWrite = itemView.findViewById(R.id.tvSpeedWrite);
            tvAccumRead  = itemView.findViewById(R.id.tvAccumRead);
            tvAccumWrite = itemView.findViewById(R.id.tvAccumWrite);
        }

        void bind(AppRow row) {
            tvLabel.setText(row.label);
            tvSpeedRead.setText(HumanReadable.bytes(row.speedRead) + "/s");
            tvSpeedWrite.setText(HumanReadable.bytes(row.speedWrite) + "/s");
            tvAccumRead.setText(HumanReadable.bytes(row.accumRead));
            tvAccumWrite.setText(HumanReadable.bytes(row.accumWrite));
        }
    }
}
