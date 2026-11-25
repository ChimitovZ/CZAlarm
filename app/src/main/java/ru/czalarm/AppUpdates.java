package ru.czalarm;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.regex.Pattern;

final class AppUpdates {
    private static final String API = "https://api.github.com/repos/ChimitovZ/CZAlarm/releases/latest";
    private static final String DOWNLOADS = "https://github.com/ChimitovZ/CZAlarm/releases/download/";
    private static final String VERSION = "(?:0|[1-9][0-9]{0,5})(?:\\.(?:0|[1-9][0-9]{0,5})){2}";
    private static final Pattern APK = Pattern.compile("CZAlarm-v" + VERSION + "\\.apk");
    private static final Pattern DIGEST = Pattern.compile("sha256:[0-9a-f]{64}");
    private static final int MAX_RELEASE_BYTES = 256 * 1024;
    private static final long MAX_APK_BYTES = 32L * 1024 * 1024;

    record Release(String version, String digest, long size) {
        String fileName() {
            return "CZAlarm-v" + version + ".apk";
        }
    }

    private AppUpdates() {}

    static Release check(String installed) throws IOException, JSONException {
        HttpURLConnection connection = open(API, false);
        JSONObject latest;
        try (InputStream input = connection.getInputStream();
                ByteArrayOutputStream body = new ByteArrayOutputStream()) {
            copy(input, body, MAX_RELEASE_BYTES);
            latest = new JSONObject(new String(body.toByteArray(), StandardCharsets.UTF_8));
        } finally {
            connection.disconnect();
        }
        String tag = latest.optString("tag_name");
        if (latest.optBoolean("draft") || latest.optBoolean("prerelease") || !tag.startsWith("v")) {
            throw new IOException("Invalid GitHub release");
        }
        String version = tag.substring(1);
        if (compareVersions(version, installed) <= 0) return null;
        JSONArray assets = latest.optJSONArray("assets");
        if (assets != null) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.optJSONObject(i);
                if (asset == null || !asset.optString("name").equals("CZAlarm-" + tag + ".apk")
                        || !asset.optString("state").equals("uploaded")) continue;
                Release release = release(version, asset.optString("digest"), asset.optLong("size"));
                if (release != null) return release;
            }
        }
        throw new IOException("No verified APK for the current release");
    }

    static Release release(String version, String digest, long size) {
        if (version == null || !version.matches(VERSION) || digest == null || !DIGEST.matcher(digest).matches()
                || size <= 0 || size > MAX_APK_BYTES) return null;
        return new Release(version, digest, size);
    }

    static int compareVersions(String left, String right) throws IOException {
        if (!left.matches(VERSION) || !right.matches(VERSION)) throw new IOException("Invalid version");
        String[] a = left.split("\\.");
        String[] b = right.split("\\.");
        for (int i = 0; i < a.length; i++) {
            int difference = Integer.compare(Integer.parseInt(a[i]), Integer.parseInt(b[i]));
            if (difference != 0) return difference;
        }
        return 0;
    }

    static File apkFile(Context context, String name) {
        if (name == null || !APK.matcher(name).matches()) return null;
        return new File(new File(context.getCacheDir(), "updates"), name);
    }

    static File download(Context context, Release release) throws Exception {
        File apk = apkFile(context, release.fileName());
        File directory = apk.getParentFile();
        if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory()) {
            throw new IOException("Cannot create update directory");
        }
        File partial = File.createTempFile("download-", ".apk", directory);
        try {
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            HttpURLConnection connection = open(DOWNLOADS + "v" + release.version() + "/" + release.fileName(), true);
            long size;
            try (InputStream input = connection.getInputStream();
                    DigestOutputStream output = new DigestOutputStream(new FileOutputStream(partial), hash)) {
                size = copy(input, output, release.size());
            } finally {
                connection.disconnect();
            }
            if (size != release.size() || !MessageDigest.isEqual(hash.digest(), parseDigest(release.digest()))) {
                throw new IOException("APK checksum mismatch");
            }
            verifyPackage(context, partial, release);
            Files.move(partial.toPath(), apk.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return apk;
        } finally {
            Files.deleteIfExists(partial.toPath());
        }
    }

    private static byte[] parseDigest(String digest) {
        byte[] bytes = new byte[32];
        for (int i = 0; i < bytes.length; i++) {
            int offset = "sha256:".length() + i * 2;
            bytes[i] = (byte) Integer.parseInt(digest.substring(offset, offset + 2), 16);
        }
        return bytes;
    }

    private static void verifyPackage(Context context, File apk, Release release) throws Exception {
        PackageManager packages = context.getPackageManager();
        int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo installed = packages.getPackageInfo(context.getPackageName(), flags);
        PackageInfo update = packages.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
        if (update == null || !installed.packageName.equals(update.packageName)
                || !release.version().equals(update.versionName) || versionCode(update) <= versionCode(installed)) {
            throw new IOException("Unexpected APK package or version");
        }
        Signature[] expected = signatures(installed);
        Signature[] actual = signatures(update);
        if (expected == null || expected.length == 0 || !Arrays.equals(expected, actual)) {
            throw new IOException("APK signing certificate mismatch");
        }
    }

    private static long versionCode(PackageInfo info) {
        return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
    }

    private static Signature[] signatures(PackageInfo info) {
        if (Build.VERSION.SDK_INT < 28) return info.signatures;
        return info.signingInfo == null ? null : info.signingInfo.getApkContentsSigners();
    }

    private static long copy(InputStream input, OutputStream output, long limit) throws IOException {
        byte[] buffer = new byte[16 * 1024];
        long total = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
            total += count;
            if (total > limit) throw new IOException("Response exceeds size limit");
            output.write(buffer, 0, count);
        }
        return total;
    }

    private static HttpURLConnection open(String path, boolean apk) throws IOException {
        URL url = new URL(path);
        for (int hop = 0; hop <= 5; hop++) {
            validateUrl(url, apk);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(8_000);
            connection.setReadTimeout(12_000);
            connection.setRequestProperty("User-Agent", "CZAlarm");
            if (!apk) connection.setRequestProperty("Accept", "application/vnd.github+json");
            try {
                int status = connection.getResponseCode();
                if (status == HttpURLConnection.HTTP_OK) return connection;
                String location = connection.getHeaderField("Location");
                if (status < 300 || status >= 400 || location == null) {
                    throw new IOException("GitHub HTTP " + status);
                }
                url = new URL(url, location);
            } catch (IOException e) {
                connection.disconnect();
                throw e;
            }
            connection.disconnect();
        }
        throw new IOException("Too many GitHub redirects");
    }

    private static void validateUrl(URL url, boolean apk) throws IOException {
        boolean hostAllowed = apk
                ? url.getHost().equals("github.com") || url.getHost().equals("release-assets.githubusercontent.com")
                    || url.getHost().equals("objects.githubusercontent.com")
                : url.getHost().equals("api.github.com");
        if (!hostAllowed || !url.getProtocol().equals("https") || url.getUserInfo() != null
                || (url.getPort() != -1 && url.getPort() != 443)) throw new IOException("Unexpected GitHub URL");
    }
}
