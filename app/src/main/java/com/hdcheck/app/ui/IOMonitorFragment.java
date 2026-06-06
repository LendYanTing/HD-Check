package com.hdcheck.app.ui;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textview.MaterialTextView;

import com.hdcheck.app.R;
import com.hdcheck.app.model.AppRow;
import com.hdcheck.app.viewmodel.IOMonitorViewModel;
import com.hdcheck.app.viewmodel.IOMonitorViewModel.SortMode;

import java.util.List;

/**
 * I/O 监控 Fragment。
 *
 * 按钮控制（开始/停止/手动刷新）+ 自定义刷新间隔
 * + 排序选择（速率-读 / 速率-写 / 累计-读 / 累计-写）+ RecyclerView 表格展示。
 */
public class IOMonitorFragment extends Fragment {

    private static final String TAG = "HDCheck.IOFragment";

    private IOMonitorViewModel viewModel;
    private IoRowAdapter adapter;

    private MaterialButton btnStart, btnStop, btnRefresh, btnApplyInterval;
    private TextInputEditText etInterval;
    private MaterialTextView tvStatus;
    private RecyclerView recyclerView;
    private ChipGroup chipGroupSort;
    private Chip chipSpeedRead, chipSpeedWrite, chipAccumRead, chipAccumWrite;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView");
        return inflater.inflate(R.layout.fragment_io_monitor, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d(TAG, "onViewCreated");

        // ViewModel
        viewModel = new ViewModelProvider(requireActivity()).get(IOMonitorViewModel.class);

        // 绑定视图
        btnStart         = view.findViewById(R.id.btnStart);
        btnStop          = view.findViewById(R.id.btnStop);
        btnRefresh       = view.findViewById(R.id.btnRefresh);
        btnApplyInterval = view.findViewById(R.id.btnApplyInterval);
        etInterval       = view.findViewById(R.id.etInterval);
        tvStatus         = view.findViewById(R.id.tvStatus);
        recyclerView     = view.findViewById(R.id.recyclerView);

        // 排序 ChipGroup
        chipGroupSort    = view.findViewById(R.id.chipGroupSort);
        chipSpeedRead    = view.findViewById(R.id.chipSortSpeedRead);
        chipSpeedWrite   = view.findViewById(R.id.chipSortSpeedWrite);
        chipAccumRead    = view.findViewById(R.id.chipSortAccumRead);
        chipAccumWrite   = view.findViewById(R.id.chipSortAccumWrite);

        // RecyclerView
        adapter = new IoRowAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        // 观察数据
        viewModel.getRows().observe(getViewLifecycleOwner(), this::onRowsChanged);
        viewModel.isRunning().observe(getViewLifecycleOwner(), this::onRunningChanged);
        viewModel.getError().observe(getViewLifecycleOwner(), this::onError);
        viewModel.getSortMode().observe(getViewLifecycleOwner(), this::onSortModeChanged);

        // 设置默认间隔
        etInterval.setText(String.valueOf(viewModel.getIntervalMs() / 1000.0));

        // ChipGroup 排序切换
        chipGroupSort.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            Log.d(TAG, "chip sort selected: " + id);
            if (id == R.id.chipSortSpeedRead) {
                viewModel.setSortMode(SortMode.SPEED_READ);
            } else if (id == R.id.chipSortSpeedWrite) {
                viewModel.setSortMode(SortMode.SPEED_WRITE);
            } else if (id == R.id.chipSortAccumRead) {
                viewModel.setSortMode(SortMode.ACCUM_READ);
            } else if (id == R.id.chipSortAccumWrite) {
                viewModel.setSortMode(SortMode.ACCUM_WRITE);
            }
        });

        // 按钮事件
        btnStart.setOnClickListener(v -> {
            Log.d(TAG, "btnStart clicked");
            viewModel.start();
        });
        btnStop.setOnClickListener(v -> {
            Log.d(TAG, "btnStop clicked");
            viewModel.stop();
        });
        btnRefresh.setOnClickListener(v -> {
            Log.d(TAG, "btnRefresh clicked");
            viewModel.refreshOnce();
        });
        btnApplyInterval.setOnClickListener(v -> applyInterval());
    }

    private void onRowsChanged(List<AppRow> rows) {
        Log.d(TAG, "onRowsChanged: " + rows.size() + " rows");
        adapter.submitList(rows);
        tvStatus.setText("共 " + rows.size() + " 个应用 | 间隔 " + (viewModel.getIntervalMs() / 1000.0) + "s");
    }

    private void onRunningChanged(boolean running) {
        Log.d(TAG, "onRunningChanged: " + running);
        btnStart.setEnabled(!running);
        btnStop.setEnabled(running);
        etInterval.setEnabled(!running);
        btnApplyInterval.setEnabled(!running);
    }

    private void onError(String error) {
        if (error != null && !error.isEmpty()) {
            Log.w(TAG, "onError: " + error);
            tvStatus.setText(error);
        }
    }

    /**
     * ViewModel 中的排序模式发生变化时同步 ChipGroup 选中状态。
     */
    private void onSortModeChanged(SortMode mode) {
        Log.d(TAG, "onSortModeChanged: " + mode);
        // 先静默取消监听避免循环触发
        chipGroupSort.setOnCheckedStateChangeListener(null);
        switch (mode) {
            case SPEED_READ:
                chipSpeedRead.setChecked(true);
                break;
            case SPEED_WRITE:
                chipSpeedWrite.setChecked(true);
                break;
            case ACCUM_READ:
                chipAccumRead.setChecked(true);
                break;
            case ACCUM_WRITE:
                chipAccumWrite.setChecked(true);
                break;
        }
        // 恢复监听
        chipGroupSort.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.chipSortSpeedRead) {
                viewModel.setSortMode(SortMode.SPEED_READ);
            } else if (id == R.id.chipSortSpeedWrite) {
                viewModel.setSortMode(SortMode.SPEED_WRITE);
            } else if (id == R.id.chipSortAccumRead) {
                viewModel.setSortMode(SortMode.ACCUM_READ);
            } else if (id == R.id.chipSortAccumWrite) {
                viewModel.setSortMode(SortMode.ACCUM_WRITE);
            }
        });
    }

    private void applyInterval() {
        try {
            double seconds = Double.parseDouble(etInterval.getText().toString().trim());
            int ms = (int) (seconds * 1000);
            Log.d(TAG, "applyInterval: " + seconds + "s => " + ms + "ms");
            viewModel.setIntervalMs(ms);
            etInterval.setText(String.valueOf(viewModel.getIntervalMs() / 1000.0));
        } catch (NumberFormatException e) {
            Log.w(TAG, "applyInterval: invalid input");
            etInterval.setError("请输入数字");
        }
    }
}
