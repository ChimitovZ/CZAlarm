package ru.czalarm;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import static ru.czalarm.Ui.*;

public final class UpdateActivity extends Activity {
    private static final int INSTALL_ACCESS = 1;
    private static final int INSTALL_APK = 2;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private String currentVersion;
    private AppUpdates.Release available;
    private File downloaded;
    private TextView status;
    private TextView checkButton;
    private TextView installButton;
    private ProgressBar progress;
    private boolean resumed;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        try {
            currentVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException impossible) {
            throw new IllegalStateException(impossible);
        }

        FrameLayout screen = new FrameLayout(this);
        ScrollView scroll = new ScrollView(this);
        screen.addView(scroll, centered(this, -1, Gravity.TOP));
        LinearLayout body = column(this);
        padding(body, 22, 20, 22, 24);
        scroll.addView(body);
        TextView back = button(this, "‹  Назад", CARD, PINE, 15);
        back.setOnClickListener(view -> finish());
        body.addView(back, -2, -2);
        space(body, 16);
        body.addView(text(this, "Обновления", 30, INK, "sans-serif-medium"));
        space(body, 16);

        LinearLayout panel = column(this);
        surface(panel, CARD, 18, LINE, false);
        padding(panel, 16, 16, 16, 16);
        panel.addView(text(this, getString(R.string.current_version, currentVersion), 15, MUTED, "sans-serif"));
        space(panel, 5);
        status = text(this, "", 19, INK, "sans-serif-medium");
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        panel.addView(status, -1, -2);
        space(panel, 12);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        panel.addView(progress, -1, dp(this, 5));
        installButton = button(this, "", PINE, WHITE, 13);
        installButton.setOnClickListener(view -> downloadOrInstall());
        panel.addView(installButton, -1, -2);
        space(panel, 8);
        checkButton = button(this, "Проверить обновления", SAGE, PINE, 13);
        checkButton.setOnClickListener(view -> check());
        panel.addView(checkButton, -1, -2);
        body.addView(panel, -1, -2);
        attach(this, screen);

        if (saved != null && currentVersion.equals(saved.getString("checkedWith"))) {
            available = AppUpdates.release(saved.getString("version"), saved.getString("digest"), saved.getLong("size"));
            if (available != null && saved.getBoolean("downloaded")) {
                File file = AppUpdates.apkFile(this, available.fileName());
                if (file.isFile() && file.length() == available.size()) downloaded = file;
            }
        }
        if (available == null) check();
        else showRelease();
    }

    @Override
    public void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (available == null) return;
        out.putString("checkedWith", currentVersion);
        out.putString("version", available.version());
        out.putString("digest", available.digest());
        out.putLong("size", available.size());
        out.putBoolean("downloaded", downloaded != null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
    }

    @Override
    protected void onPause() {
        resumed = false;
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    private void check() {
        available = null;
        downloaded = null;
        run("Проверяем GitHub…", "Не удалось проверить обновления",
                () -> AppUpdates.check(currentVersion), release -> {
                    available = release;
                    if (release == null) show("Установлена последняя версия", false);
                    else showRelease();
                });
    }

    private void downloadOrInstall() {
        if (downloaded != null && downloaded.isFile() && downloaded.length() == available.size()) {
            install();
            return;
        }
        downloaded = null;
        AppUpdates.Release release = available;
        run("Скачиваем и проверяем APK…", "Не удалось скачать или проверить APK",
                () -> AppUpdates.download(getApplicationContext(), release), apk -> {
                    downloaded = apk;
                    showRelease();
                    if (resumed) install();
                });
    }

    private void showRelease() {
        show(getString(downloaded == null ? R.string.update_available : R.string.update_downloaded,
                available.version()), false);
    }

    private void show(String message, boolean busy) {
        status.setText(message);
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        installButton.setVisibility(!busy && available != null ? View.VISIBLE : View.GONE);
        installButton.setText(downloaded == null ? "Скачать и установить" : "Установить");
        checkButton.setEnabled(!busy);
        checkButton.setAlpha(busy ? 0.5f : 1f);
    }

    private <T> void run(String working, String failed, Callable<T> action, Consumer<T> result) {
        show(working, true);
        worker.execute(() -> {
            try {
                T value = action.call();
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) result.accept(value);
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) show(failed, false);
                });
            }
        });
    }

    private void install() {
        if (downloaded == null || !downloaded.isFile() || downloaded.length() != available.size()) {
            downloaded = null;
            showRelease();
            return;
        }
        try {
            if (!getPackageManager().canRequestPackageInstalls()) {
                show("Разрешите установку из CZAlarm в настройках Android", false);
                startActivityForResult(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName())), INSTALL_ACCESS);
                return;
            }
            Intent installer = new Intent(Intent.ACTION_INSTALL_PACKAGE, UpdateApkProvider.uri(this, downloaded))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .putExtra(Intent.EXTRA_RETURN_RESULT, true);
            startActivityForResult(installer, INSTALL_APK);
        } catch (ActivityNotFoundException | SecurityException unavailable) {
            show("Не удалось открыть установку Android", false);
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == INSTALL_ACCESS && available != null) {
            if (getPackageManager().canRequestPackageInstalls()) install();
            else show("Для установки нужно разрешение Android", false);
        } else if (request == INSTALL_APK && result != RESULT_OK) {
            show("Установка не завершена", false);
        }
    }
}
