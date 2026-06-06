package com.hdcheck.app.viewmodel;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.hdcheck.app.model.AppRow;
import com.hdcheck.app.service.IOMonitorRepository;
import com.hdcheck.app.util.PackageResolver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * I/O 监控 ViewModel。
 *
 * 管理监控生命周期、排序模式、数据分发。
 */
public class IOMonitorViewModel extends AndroidViewModel {

    private static final String TAG = "HDCheck.IOViewModel";
    private static final int DEFAULT_INTERVAL_MS = 1000;
    private static final int MIN_INTERVAL_MS = 200;
    private static final int MAX_INTERVAL_MS = 30000;

    /** 排序模式 */
    public enum SortMode {
        SPEED_READ,
        SPEED_WRITE,
        ACCUM_READ,
        ACCUM_WRITE;

        /** 返回对应的 Comparator */
        Comparator<AppRow> comparator() {
            switch (this) {
                case SPEED_READ:  return (a, b) -> Long.compare(b.speedRead,  a.speedRead);
                case SPEED_WRITE: return (a, b) -> Long.compare(b.speedWrite, a.speedWrite);
                case ACCUM_READ:  return (a, b) -> Long.compare(b.accumRead,  a.accumRead);
                case ACCUM_WRITE: return (a, b) -> Long.compare(b.accumWrite, a.accumWrite);
                default:          return (a, b) -> Long.compare(b.speedRead,  a.speedRead);
            }
        }
    }

    private final IOMonitorRepository repository;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> future;
    private long lastSampleTime;
    private boolean isFirst = true;
    private int intervalMs = DEFAULT_INTERVAL_MS;

    /** 上次采样的原始数据（用于切换排序时无需重新采样） */
    private List<AppRow> lastRawRows = new ArrayList<>();
    private SortMode sortMode = SortMode.SPEED_READ;

    private final MutableLiveData<List<AppRow>> rowsLiveData = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> runningLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorLiveData = new MutableLiveData<>();
    private final MutableLiveData<SortMode> sortModeLiveData = new MutableLiveData<>(SortMode.SPEED_READ);

    public IOMonitorViewModel(@NonNull Application app) {
        super(app);
        PackageResolver resolver = new PackageResolver(app);
        repository = new IOMonitorRepository(resolver);
        Log.i(TAG, "ViewModel created");
    }

    public LiveData<List<AppRow>> getRows()        { return rowsLiveData; }
    public LiveData<Boolean> isRunning()            { return runningLiveData; }
    public LiveData<String> getError()              { return errorLiveData; }
    public LiveData<SortMode> getSortMode()         { return sortModeLiveData; }

    // ─── 排序 ─────────────────────────────────────────────────────────

    public void setSortMode(SortMode mode) {
        if (this.sortMode == mode) return;
        Log.i(TAG, "setSortMode: " + this.sortMode + " -> " + mode);
        this.sortMode = mode;
        sortModeLiveData.postValue(mode);
        // 立即对已持有数据重新排序并推送
        applySortAndPost();
    }

    /** 对 lastRawRows 排序后 post 到 LiveData */
    private void applySortAndPost() {
        List<AppRow> sorted = new ArrayList<>(lastRawRows);
        Collections.sort(sorted, sortMode.comparator());
        rowsLiveData.postValue(sorted);
    }

    // ─── 控制 ─────────────────────────────────────────────────────────

    public void start() {
        if (repository.isRunning()) {
            Log.w(TAG, "start: already running, ignoring");
            return;
        }

        Log.i(TAG, "start: intervalMs=" + intervalMs + " sortMode=" + sortMode);
        repository.reset();
        lastRawRows.clear();
        isFirst = true;
        lastSampleTime = System.currentTimeMillis();
        repository.setRunning(true);
        runningLiveData.postValue(true);

        future = executor.scheduleWithFixedDelay(() -> {
            if (!repository.isRunning()) return;
            long now = System.currentTimeMillis();
            long elapsed = now - lastSampleTime;
            lastSampleTime = now;

            try {
                List<AppRow> rows = repository.sample(elapsed, isFirst);
                isFirst = false;
                lastRawRows = rows;
                applySortAndPost();
            } catch (Exception e) {
                Log.e(TAG, "sample failed", e);
                errorLiveData.postValue("采样失败: " + e.getMessage());
            }
        }, 0, intervalMs, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        Log.i(TAG, "stop");
        repository.setRunning(false);
        runningLiveData.postValue(false);
        if (future != null && !future.isCancelled()) {
            future.cancel(false);
            future = null;
        }
    }

    /**
     * 手动刷新一次。
     */
    public void refreshOnce() {
        Log.d(TAG, "refreshOnce");
        executor.execute(() -> {
            try {
                long now = System.currentTimeMillis();
                long elapsed = now - lastSampleTime;
                if (isFirst) elapsed = 0;
                lastSampleTime = now;
                List<AppRow> rows = repository.sample(elapsed, isFirst);
                isFirst = false;
                lastRawRows = rows;
                applySortAndPost();
            } catch (Exception e) {
                Log.e(TAG, "refreshOnce failed", e);
                errorLiveData.postValue("刷新失败: " + e.getMessage());
            }
        });
    }

    // ─── 间隔配置 ─────────────────────────────────────────────────────

    public int getIntervalMs() { return intervalMs; }

    public void setIntervalMs(int ms) {
        int clamped = Math.max(MIN_INTERVAL_MS, Math.min(MAX_INTERVAL_MS, ms));
        if (clamped == intervalMs) return;
        Log.i(TAG, "setIntervalMs: " + intervalMs + " -> " + clamped);
        intervalMs = clamped;

        if (repository.isRunning()) {
            if (future != null && !future.isCancelled()) {
                future.cancel(false);
            }
            future = executor.scheduleWithFixedDelay(() -> {
                if (!repository.isRunning()) return;
                long now = System.currentTimeMillis();
                long elapsed = now - lastSampleTime;
                lastSampleTime = now;
                try {
                    List<AppRow> rows = repository.sample(elapsed, isFirst);
                    isFirst = false;
                    lastRawRows = rows;
                    applySortAndPost();
                } catch (Exception e) {
                    Log.e(TAG, "sample failed", e);
                    errorLiveData.postValue("采样失败: " + e.getMessage());
                }
            }, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    protected void onCleared() {
        Log.i(TAG, "onCleared");
        stop();
        executor.shutdownNow();
        super.onCleared();
    }
}
