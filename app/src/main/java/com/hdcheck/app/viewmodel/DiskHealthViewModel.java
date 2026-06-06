package com.hdcheck.app.viewmodel;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.hdcheck.app.model.DiskInfo;
import com.hdcheck.app.service.DiskHealthRepository;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 硬盘健康 ViewModel。
 */
public class DiskHealthViewModel extends ViewModel {

    private static final String TAG = "HDCheck.DiskViewModel";

    private final DiskHealthRepository repository = new DiskHealthRepository();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final MutableLiveData<DiskInfo> diskInfoLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loadingLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorLiveData = new MutableLiveData<>();

    public LiveData<DiskInfo> getDiskInfo()  { return diskInfoLiveData; }
    public LiveData<Boolean> isLoading()     { return loadingLiveData; }
    public LiveData<String> getError()       { return errorLiveData; }

    /**
     * 触发硬盘健康检查。
     */
    public void check() {
        Log.i(TAG, "check triggered");
        loadingLiveData.postValue(true);
        errorLiveData.postValue(null);

        executor.execute(() -> {
            try {
                DiskInfo info = repository.check();
                if (info.error != null) {
                    Log.w(TAG, "check: error = " + info.error);
                    errorLiveData.postValue(info.error);
                }
                Log.i(TAG, "check: posting result, device=" + info.blockDevice
                        + " read=" + info.totalReadBytes
                        + " write=" + info.totalWriteBytes);
                diskInfoLiveData.postValue(info);
            } catch (Exception e) {
                Log.e(TAG, "check failed", e);
                errorLiveData.postValue("检查失败: " + e.getMessage());
            } finally {
                loadingLiveData.postValue(false);
            }
        });
    }

    @Override
    protected void onCleared() {
        Log.i(TAG, "onCleared");
        executor.shutdownNow();
        super.onCleared();
    }
}
