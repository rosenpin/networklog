/* (C) 2012 Pragmatic Software
   This Source Code Form is subject to the terms of the Mozilla Public
   License, v. 2.0. If a copy of the MPL was not distributed with this
   file, You can obtain one at http://mozilla.org/MPL/2.0/
 */

package com.googlecode.networklog;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class ApplicationsTracker {
    public static ArrayList<AppEntry> installedApps;
    static HashMap<String, AppEntry> uidMap;
    private static HashMap<String, AppEntry> packageMap;
    private static HashMap<String, Drawable> iconMap;
    private static ProgressDialog dialog;
    private static int appCount;
    private static final Object dialogLock = new Object();
    private static final Object installedAppsLock = new Object();
    private static PackageManager pm = null;
    private static PackageIntentReceiver packageIntentReceiver = null;

    private static void restoreData(RetainInstanceData data) {
        synchronized (installedAppsLock) {
            installedApps = data.applicationsTrackerInstalledApps;
        }

        uidMap = data.applicationsTrackerUidMap;
        packageMap = data.applicationsTrackerPackageMap;
    }

    private static void startWatchingPackages(Context context) {
        stopWatchingPackages();
        Log.d("NetworkLog", "Now listening for package broadcasts");
        packageIntentReceiver = new PackageIntentReceiver(context);
    }

    private static void stopWatchingPackages() {
        if (packageIntentReceiver != null) {
            try {
                Log.d("NetworkLog", "Stopped listening for package broadcasts");
                packageIntentReceiver.context.unregisterReceiver(packageIntentReceiver);
                packageIntentReceiver = null;
            } catch (Exception e) {
                Log.d("NetworkLog", "Caught exception while unregistering package receiver: ", e);
            }
        }
    }

    private static void removeApp(String packageName) {
        AppEntry app;

        for (Iterator<AppEntry> iterator = installedApps.iterator(); iterator.hasNext(); ) {
            app = iterator.next();
            if (app.packageName.equals(packageName)) {
                iterator.remove();
                break;
            }
        }

        for (Iterator<AppEntry> iterator = uidMap.values().iterator(); iterator.hasNext(); ) {
            app = iterator.next();
            if (app.packageName.equals(packageName)) {
                iterator.remove();
                break;
            }
        }

        packageMap.remove(packageName);
        iconMap.remove(packageName);

    }

    private static void addApp(Context context, String packageName) {
        boolean newApp = false;

        if (pm == null) {
            pm = context.getPackageManager();
        }

        try {
            ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);

            AppEntry app = packageMap.get(packageName);

            if (app == null) {
                app = new AppEntry();
                newApp = true;
            }

            app.name = StringPool.get(pm.getApplicationLabel(appInfo).toString());
            app.nameLowerCase = StringPool.get(StringPool.getLowerCase(app.name));
            app.uid = appInfo.uid;
            app.uidString = StringPool.get(String.valueOf(app.uid));
            app.packageName = StringPool.get(appInfo.packageName);

            if (newApp) {
                installedApps.add(app);
                packageMap.put(app.packageName, app);
                uidMap.put(app.uidString, app);
            }


        } catch (PackageManager.NameNotFoundException nfe) {
            Log.e("NetworkLog", "AppTracker addApp NameNotFoundException caught:", nfe);
        }
    }

    public static void getInstalledApps(final Context context, final Handler handler) {
        MyLog.d("[LoadApps] Loading installed apps");

        startWatchingPackages(context);

        synchronized (installedAppsLock) {
            if (NetworkLog.data == null) {
                installedApps = new ArrayList<>();
                uidMap = new HashMap<>();
                packageMap = new HashMap<>();
                iconMap = new HashMap<>();
            } else {
                restoreData(NetworkLog.data);
                installedApps.clear();
                uidMap.clear();
                packageMap.clear();
                iconMap.clear();
            }

            if (pm == null) {
                pm = context.getPackageManager();
            }

            List<ApplicationInfo> apps = pm.getInstalledApplications(0);

            appCount = apps.size();

            int count = 0;

            AppCache appCache = new AppCache(context);

            for (final ApplicationInfo app : apps) {
                MyLog.d(8, "Processing app " + app);

                final int progress = ++count;
                if (handler != null) {
                    handler.post(new Runnable() {
                        public void run() {
                            synchronized (dialogLock) {
                                if (dialog != null) {
                                    dialog.setProgress(progress);
                                }
                            }
                        }
                    });
                }

                int uid = app.uid;
                String uidString = Integer.toString(uid);

                AppEntry entryHash = uidMap.get(uidString);

                AppEntry entry = new AppEntry();

                entry.name = appCache.getLabel(pm, app);
                entry.nameLowerCase = StringPool.get(StringPool.getLowerCase(entry.name));
                entry.uid = uid;
                entry.uidString = StringPool.get(String.valueOf(uid));
                entry.packageName = StringPool.get(app.packageName);

                installedApps.add(entry);
                packageMap.put(entry.packageName, entry);

                if (entryHash != null) {
                    entryHash.name.concat("; " + entry.name);
                } else {
                    uidMap.put(uidString, entry);
                }
            }

            appCache.saveCache();

            AppEntry entry = new AppEntry();
            entry.name = StringPool.get("Kernel");
            entry.nameLowerCase = StringPool.getLowerCase("Kernel");
            entry.packageName = StringPool.get(entry.nameLowerCase);
            entry.uid = -1;
            entry.uidString = StringPool.get("-1");
            iconMap.put(entry.packageName, context.getResources().getDrawable(R.drawable.linux_icon));

            installedApps.add(entry);
            uidMap.put("-1", entry);
            packageMap.put(entry.packageName, entry);

            String[] systemUids = {"root", "system", "radio", "bluetooth", "nobody", "misc",
                    "graphics", "input", "audio", "camera", "log", "compass", "mount", "wifi", "dhcp",
                    "adb", "install", "media", "nfc", "shell", "cache", "diag", "vpn", "keystore", "usb",
                    "gps", "inet", "net_raw", "net_admin", "net_bt_admin", "net_bt",
        /* motorola */
                    "mot_accy", "mot_pwric", "mot_usb", "mot_drm", "mot_tcmd", "mot_sec_rtc", "mot_tombstone",
                    "mot_tpapi", "mot_secclkd"};

            for (String name : systemUids) {
                int uid = android.os.Process.getUidForName(name);

                if (uid == -1) {
                    continue;
                }

                String uidString = StringPool.get(String.valueOf(uid));
                AppEntry entryHash = uidMap.get(uidString);

                if (entryHash == null) {
                    entry = new AppEntry();
                    entry.name = StringPool.get(name);
                    entry.nameLowerCase = StringPool.getLowerCase(name);
                    entry.packageName = StringPool.get(entry.nameLowerCase);
                    entry.uid = uid;
                    entry.uidString = StringPool.get(uidString);
                    iconMap.put(entry.packageName, context.getResources().getDrawable(R.drawable.android_icon));

                    installedApps.add(entry);
                    uidMap.put(uidString, entry);
                    packageMap.put(entry.packageName, entry);
                }
            }

            if (handler != null) {
                handler.post(new Runnable() {
                    public void run() {
                        MyLog.d("[LoadApps] Dismissing dialog");

                        synchronized (dialogLock) {
                            if (dialog != null) {
                                dialog.dismiss();
                                dialog = null;
                            }
                        }
                    }
                });
            }
            MyLog.d("[LoadApps] Done getting installed apps");
        }
    }

    static class AppEntry {
        String name;
        String nameLowerCase;
        String packageName;
        int uid;
        String uidString;

        public String toString() {
            return "(" + uidString + ") " + name;
        }
    }

    private static class AppCache {
        public HashMap<String, String> labelCache;
        private Context context;
        private boolean isDirty = false;

        private AppCache() {
        }

        public AppCache(Context context) {
            this.context = context;
            loadCache();
        }

        public String getLabel(PackageManager pm, ApplicationInfo app) {
            String label = labelCache.get(app.packageName);

            if (label == null) {
                label = StringPool.get(pm.getApplicationLabel(app).toString());
                labelCache.put(app.packageName, label);
                isDirty = true;
            }

            return label;
        }

        public void loadCache() {
            try {
                File file = new File(context.getDir("data", Context.MODE_PRIVATE), "applabels.cache");
                ObjectInputStream inputStream = new ObjectInputStream(new FileInputStream(file));
                labelCache = (HashMap<String, String>) inputStream.readObject();
                isDirty = false;
                inputStream.close();
            } catch (Exception e) {
                Log.w("NetworkLog", "Exception loading app cache", e);
                labelCache = new HashMap<String, String>();
            }
        }

        public void saveCache() {
            if (isDirty == false) {
                return;
            }

            try {
                File file = new File(context.getDir("data", Context.MODE_PRIVATE), "applabels.cache");
                ObjectOutputStream outputStream = new ObjectOutputStream(new FileOutputStream(file));
                outputStream.writeObject(labelCache);
                outputStream.flush();
                outputStream.close();
            } catch (Exception e) {
                Log.w("NetworkLog", "Exception saving app cache", e);
            }
        }
    }

    public static class PackageIntentReceiver extends BroadcastReceiver {
        final Context context;

        public PackageIntentReceiver(Context context) {
            this.context = context;

            // Register for events related to package installation.
            IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
            filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
            filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
            filter.addDataScheme("package");
            context.registerReceiver(this, filter);

            // Register for events related to sdcard installation.
            IntentFilter sdFilter = new IntentFilter();
            sdFilter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE);
            sdFilter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE);
            context.registerReceiver(this, sdFilter);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d("NetworkLog", "Received package broadcast: " + action);

            if (Intent.ACTION_PACKAGE_ADDED.equals(action)) {
                Uri uri = intent.getData();
                String packageName = uri != null ? uri.getSchemeSpecificPart() : null;
                Bundle b = intent.getExtras();
                int uid = b.getInt(Intent.EXTRA_UID);
                boolean replacing = b.getBoolean(Intent.EXTRA_REPLACING);
                Log.d("NetworkLog", "Package added: " + packageName + " " + uid + "; replacing: " + replacing);

                addApp(context, packageName);
            }

            if (Intent.ACTION_PACKAGE_REMOVED.equals(action)) {
                Uri uri = intent.getData();
                String packageName = uri != null ? uri.getSchemeSpecificPart() : null;
                Bundle b = intent.getExtras();
                int uid = b.getInt(Intent.EXTRA_UID);
                boolean replacing = b.getBoolean(Intent.EXTRA_REPLACING);
                Log.d("NetworkLog", "Package removed: " + packageName + " " + uid + "; replacing: " + replacing);

                if (replacing == false) {
                    removeApp(packageName);
                }
            }

            if (Intent.ACTION_PACKAGE_CHANGED.equals(action)) {
                Uri uri = intent.getData();
                String packageName = uri != null ? uri.getSchemeSpecificPart() : null;
                Bundle b = intent.getExtras();
                int uid = b.getInt(Intent.EXTRA_UID);
                Log.d("NetworkLog", "Package changed: " + packageName + " " + uid);
            }
        }
    }
}
