package com.amarullz.androidtv.animetvjmto;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Process;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class LogcatManager {
  private static final String TAG = "ATVLOG-LOGCAT";
  private static final String PREF_KEY = "debug_logcat";
  private static LogcatManager instance;

  private final Context appContext;
  private final SharedPreferences pref;
  private java.lang.Process logcatProcess = null;

  private LogcatManager(Context context) {
    this.appContext = context.getApplicationContext();
    this.pref = appContext.getSharedPreferences("SERVER", Context.MODE_PRIVATE);
  }

  public static synchronized LogcatManager getInstance(Context context) {
    if (instance == null) {
      instance = new LogcatManager(context);
    }
    return instance;
  }

  public File getLogFile() {
    File dir = appContext.getExternalFilesDir(null);
    if (dir == null) {
      dir = appContext.getCacheDir();
    }
    return new File(dir, "animetv-debug.log");
  }

  public String getLogPath() {
    return getLogFile().getAbsolutePath();
  }

  public boolean isEnabled() {
    return pref.getBoolean(PREF_KEY, false);
  }

  public void setEnabled(boolean enabled) {
    pref.edit().putBoolean(PREF_KEY, enabled).apply();
    if (enabled) {
      startLogging();
    } else {
      stopLogging();
    }
  }

  public synchronized void startLogging() {
    if (logcatProcess != null) {
      return;
    }
    try {
      File logFile = getLogFile();
      int pid = Process.myPid();
      // Rotate 2 files of max 2048 KB each
      String[] cmd = new String[] {
          "logcat",
          "-v", "time",
          "--pid=" + pid,
          "-f", logFile.getAbsolutePath(),
          "-r", "2048",
          "-n", "2"
      };
      logcatProcess = Runtime.getRuntime().exec(cmd);
      Log.i(TAG, "Started logcat capture to: " + logFile.getAbsolutePath());
    } catch (Exception e) {
      Log.e(TAG, "Failed to start logcat process", e);
    }
  }

  public synchronized void stopLogging() {
    if (logcatProcess != null) {
      try {
        logcatProcess.destroy();
      } catch (Exception ignored) {}
      logcatProcess = null;
      Log.i(TAG, "Stopped logcat capture");
    }
  }

  public String getRecentLogs(int lines) {
    StringBuilder sb = new StringBuilder();
    try {
      int pid = Process.myPid();
      String[] cmd = new String[] {
          "logcat",
          "-d",
          "-v", "time",
          "--pid=" + pid,
          "-t", String.valueOf(lines > 0 ? lines : 500)
      };
      java.lang.Process p = Runtime.getRuntime().exec(cmd);
      try (BufferedReader reader = new BufferedReader(
          new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          sb.append(line).append("\n");
        }
      }
      p.waitFor();
    } catch (Exception e) {
      sb.append("Error retrieving logs: ").append(e.getMessage()).append("\n");
    }
    return sb.toString();
  }

  public void clearLogs() {
    try {
      File file = getLogFile();
      if (file.exists()) file.delete();
      File file1 = new File(file.getAbsolutePath() + ".1");
      if (file1.exists()) file1.delete();
      File file2 = new File(file.getAbsolutePath() + ".2");
      if (file2.exists()) file2.delete();
      Runtime.getRuntime().exec("logcat -c");
    } catch (Exception ignored) {}
  }

  public void copyToClipboard() {
    try {
      String logs = getRecentLogs(1000);
      ClipboardManager clipboard = (ClipboardManager) appContext.getSystemService(Context.CLIPBOARD_SERVICE);
      if (clipboard != null) {
        ClipData clip = ClipData.newPlainText("AnimeTV Logs", logs);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(appContext, "Logs copied to clipboard", Toast.LENGTH_SHORT).show();
      }
    } catch (Exception e) {
      Toast.makeText(appContext, "Failed to copy logs", Toast.LENGTH_SHORT).show();
    }
  }
}
