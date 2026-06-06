package com.hdcheck.app.ui;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.hdcheck.app.R;
import com.hdcheck.app.model.DiskInfo;
import com.hdcheck.app.util.HumanReadable;
import com.hdcheck.app.viewmodel.DiskHealthViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textview.MaterialTextView;

/**
 * 硬盘健康检查 Fragment。
 *
 * 点击按钮触发检查，展示块设备、读写总量、寿命信息。
 */
public class DiskHealthFragment extends Fragment {

    private static final String TAG = "HDCheck.DiskFragment";

    private DiskHealthViewModel viewModel;

    private MaterialButton btnCheck;
    private CircularProgressIndicator progressBar;
    private MaterialTextView tvError;
    private ScrollView scrollResult;

    // 结果字段
    private MaterialTextView tvBlockDevice;
    private MaterialTextView tvTotalRead;
    private MaterialTextView tvTotalWrite;
    private MaterialCardView cardLife;
    private MaterialTextView tvHealthDescPath;
    private MaterialTextView tvLifeA;
    private MaterialTextView tvLifeB;
    private MaterialTextView tvPreEOL;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView");
        return inflater.inflate(R.layout.fragment_disk_health, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d(TAG, "onViewCreated");

        viewModel = new ViewModelProvider(requireActivity()).get(DiskHealthViewModel.class);

        // 绑定视图
        btnCheck   = view.findViewById(R.id.btnCheck);
        progressBar= view.findViewById(R.id.progressBar);
        tvError    = view.findViewById(R.id.tvError);
        scrollResult = view.findViewById(R.id.scrollResult);

        tvBlockDevice   = view.findViewById(R.id.tvBlockDevice);
        tvTotalRead     = view.findViewById(R.id.tvTotalRead);
        tvTotalWrite    = view.findViewById(R.id.tvTotalWrite);
        cardLife        = view.findViewById(R.id.cardLife);
        tvHealthDescPath= view.findViewById(R.id.tvHealthDescPath);
        tvLifeA         = view.findViewById(R.id.tvLifeA);
        tvLifeB         = view.findViewById(R.id.tvLifeB);
        tvPreEOL        = view.findViewById(R.id.tvPreEOL);

        // 观察
        viewModel.getDiskInfo().observe(getViewLifecycleOwner(), this::onDiskInfo);
        viewModel.isLoading().observe(getViewLifecycleOwner(), this::onLoading);
        viewModel.getError().observe(getViewLifecycleOwner(), this::onError);

        // 按钮事件
        btnCheck.setOnClickListener(v -> {
            Log.d(TAG, "btnCheck clicked");
            viewModel.check();
        });
    }

    private void onDiskInfo(DiskInfo info) {
        if (info == null) {
            Log.d(TAG, "onDiskInfo: null, hiding result");
            scrollResult.setVisibility(View.GONE);
            return;
        }

        Log.d(TAG, "onDiskInfo: device=" + info.blockDevice
                + " readBytes=" + info.totalReadBytes
                + " writeBytes=" + info.totalWriteBytes
                + " lifeA='" + info.lifeEstimationA + "'"
                + " lifeB='" + info.lifeEstimationB + "'");

        scrollResult.setVisibility(View.VISIBLE);
        tvBlockDevice.setText(info.blockDevice != null ? info.blockDevice : "—");
        tvTotalRead.setText(HumanReadable.bytes(info.totalReadBytes));
        tvTotalWrite.setText(HumanReadable.bytes(info.totalWriteBytes));

        if (info.healthDescPath != null && !info.healthDescPath.isEmpty()) {
            cardLife.setVisibility(View.VISIBLE);
            tvHealthDescPath.setText(info.healthDescPath);
            tvLifeA.setText(HumanReadable.parseLifeEstimation(info.lifeEstimationA));
            tvLifeB.setText(HumanReadable.parseLifeEstimation(info.lifeEstimationB));
            tvPreEOL.setText(info.preEOLInfo != null ? info.preEOLInfo : "—");
        } else {
            cardLife.setVisibility(View.GONE);
        }
    }

    private void onLoading(boolean loading) {
        Log.d(TAG, "onLoading: " + loading);
        btnCheck.setEnabled(!loading);
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void onError(String error) {
        if (error != null) {
            Log.w(TAG, "onError: " + error);
            tvError.setVisibility(View.VISIBLE);
            tvError.setText(error);
        } else {
            tvError.setVisibility(View.GONE);
        }
    }
}
