package com.hdcheck.app;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.hdcheck.app.util.RootChecker;

/**
 * 主 Activity。
 *
 * 启动时检查 root 权限，不可用时弹出对话框提示配置提权命令或退出。
 * 使用 MD3 BottomNavigationView + Navigation Component 切换两个 Fragment。
 */
public class MainActivity extends AppCompatActivity {

    private NavController navController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 设置导航
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        NavHostFragment navHost = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (navHost != null) {
            navController = navHost.getNavController();
            NavigationUI.setupWithNavController(bottomNav, navController);
        }

        // 检查 root
        checkRoot();
    }

    /**
     * 检查 root 权限，不可用时弹出对话框。
     */
    private void checkRoot() {
        boolean available = RootChecker.checkRoot(this);
        if (!available) {
            showRootUnavailableDialog();
        }
    }

    /**
     * Root 不可用时的对话框：提示配置提权命令 / 重试 / 退出。
     */
    private void showRootUnavailableDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.root_unavailable_title)
                .setMessage(R.string.root_unavailable_msg)
                .setCancelable(false)
                .setPositiveButton(R.string.btn_configure_su, (dialog, which) -> showConfigureSuDialog())
                .setNeutralButton(R.string.btn_retry, (dialog, which) -> checkRoot())
                .setNegativeButton(R.string.btn_exit, (dialog, which) -> finish())
                .show();
    }

    /**
     * 自定义提权命令对话框。
     */
    private void showConfigureSuDialog() {
        EditText input = new EditText(this);
        input.setHint(R.string.dialog_su_hint);
        input.setSingleLine(true);
        String current = RootChecker.getCustomSu(this);
        if (!TextUtils.isEmpty(current) && !current.equals("su")) {
            input.setText(current);
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_su_title)
                .setMessage(R.string.dialog_su_message)
                .setView(input)
                .setPositiveButton("保存", (dialog, which) -> {
                    String cmd = input.getText().toString().trim();
                    if (!TextUtils.isEmpty(cmd)) {
                        RootChecker.setCustomSu(MainActivity.this, cmd);
                        RootChecker.resetCache();
                    }
                    checkRoot();
                })
                .setNegativeButton("取消", (dialog, which) -> checkRoot())
                .show();
    }
}
