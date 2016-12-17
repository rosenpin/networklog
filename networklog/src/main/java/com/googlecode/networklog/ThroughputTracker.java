/* (C) 2012 Pragmatic Software
   This Source Code Form is subject to the terms of the Mozilla Public
   License, v. 2.0. If a copy of the MPL was not distributed with this
   file, You can obtain one at http://mozilla.org/MPL/2.0/
 */

package com.googlecode.networklog;

import android.util.Log;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;


public class ThroughputTracker {
    public static String throughputString = "";
    public static HashMap<String, ThroughputData> throughputMap = new HashMap<String, ThroughputData>();
    public static HashMap<String, ThroughputData> resetMap = new HashMap<String, ThroughputData>();
    static ThroughputUpdater updater;
    private static ApplicationsTracker.AppEntry appEntry;

    public static void updateEntry(LogEntry entry) {
        appEntry = ApplicationsTracker.uidMap.get(entry.uidString);

        if (appEntry == null) {
            Log.w("NetworkLog", "[ThroughputTracker] No app entry for uid " + entry.uidString);
            return;
        }

        synchronized (throughputMap) {
            ThroughputData throughput = throughputMap.get(appEntry.packageName);

            if (throughput == null) {
                throughput = new ThroughputData();
                throughput.app = appEntry;
                throughputMap.put(appEntry.packageName, throughput);
            }

            if (throughput.displayed == true) {
                throughput.download = 0;
                throughput.upload = 0;
            }

            if (entry.in != null && entry.in.length() > 0) {
                if (NetworkLogService.throughputBps) {
                    throughput.download += entry.len * Byte.SIZE;
                } else {
                    throughput.download += entry.len;
                }
                throughput.address = entry.src + ":" + entry.spt;
            } else {
                if (NetworkLogService.throughputBps) {
                    throughput.upload += entry.len * Byte.SIZE;
                } else {
                    throughput.upload += entry.len;
                }
                throughput.address = entry.dst + ":" + entry.dpt;
            }

            throughput.clearTime = System.currentTimeMillis() + NetworkLogService.toastDuration;
            throughput.displayed = false;
        }
    }

    public static void startUpdater() {
        if (updater != null) {
            stopUpdater();
        }

        updateThroughput(0, 0);
        updater = new ThroughputUpdater();
        new Thread(updater, "ThroughputUpdater").start();
    }

    public static void stopUpdater() {
        if (updater == null) {
            return;
        }

        updateThroughput(-1, -1);
        updater.stop();
        updater = null;
    }

    public static void updateThroughput(long upload, long download) {
        if (NetworkLogService.instance == null || upload == -1 || download == -1) {
            throughputString = "";
        } else {
            if (NetworkLogService.invertUploadDownload) {
                throughputString = StringUtils.formatToBytes(download) + (NetworkLogService.throughputBps ? "bps/" : "B/") + StringUtils.formatToBytes(upload) + (NetworkLogService.throughputBps ? "bps" : "B");
            } else {
                throughputString = StringUtils.formatToBytes(upload) + (NetworkLogService.throughputBps ? "bps/" : "B/") + StringUtils.formatToBytes(download) + (NetworkLogService.throughputBps ? "bps" : "B");
            }
            MyLog.d(6, "Throughput: " + throughputString);
        }
    }

    private static class ThroughputData {
        ApplicationsTracker.AppEntry app;
        String address;
        long upload;
        long download;
        long clearTime;
        boolean displayed;
    }

    private static class ThroughputUpdater implements Runnable {
        boolean running = false;
        long totalUpload;
        long totalDownload;
        StringBuilder toastString = new StringBuilder(512);

        public void stop() {
            running = false;
        }

        public void run() {
            String throughput;
            boolean isDirty = false;
            running = true;
            String newline;

            while (running) {
                synchronized (throughputMap) {
                    if (isDirty) {
                        isDirty = false;
                        if (!resetMap.isEmpty()) {

                            resetMap.clear();
                        }
                        updateThroughput(0, 0);
                        totalUpload = 0;
                        totalDownload = 0;
                    }

                    if (!throughputMap.isEmpty()) {
                        isDirty = true;
                        toastString.setLength(0);
                        newline = "";
                        long currentTime = System.currentTimeMillis();
                        boolean showToast = false;

                        Iterator entries = throughputMap.entrySet().iterator();
                        while (entries.hasNext()) {
                            Map.Entry entry = (Map.Entry) entries.next();
                            ThroughputData value = (ThroughputData) entry.getValue();

                            if (value.displayed == false) {
                                totalUpload += value.upload;
                                totalDownload += value.download;


                            }

                            showToast = true;

                            if (NetworkLogService.invertUploadDownload) {
                                throughput = StringUtils.formatToBytes(value.download) + (NetworkLogService.throughputBps ? "bps/" : "B/") + StringUtils.formatToBytes(value.upload) + (NetworkLogService.throughputBps ? "bps" : "B");
                            } else {
                                throughput = StringUtils.formatToBytes(value.upload) + (NetworkLogService.throughputBps ? "bps/" : "B/") + StringUtils.formatToBytes(value.download) + (NetworkLogService.throughputBps ? "bps" : "B");
                            }

                            if (MyLog.enabled && MyLog.level >= 2 && !value.displayed) {
                                MyLog.d(2, value.app.name + " throughput: " + throughput);
                            }

                            if (NetworkLogService.toastShowAddress) {
                                toastString.append(newline + "<b>" + value.app.name + "</b>: <u>" + value.address + "</u> <i>" + throughput + "</i>");
                            } else {
                                toastString.append(newline + "<b>" + value.app.name + "</b>: " + throughput);
                            }

                            newline = "<br>";


                            value.displayed = true;

                            if (currentTime >= value.clearTime) {
                                entries.remove();
                            }
                        }

                        if (showToast) {
                            NetworkLogService.showToast(toastString);
                        }

                        updateThroughput(totalUpload, totalDownload);

                        totalUpload = 0;
                        totalDownload = 0;
                    }
                }

                try {
                    Thread.sleep(1000);
                } catch (Exception e) {
                    Log.d("NetworkLog", "ThroughputUpdater", e);
                }
            }
        }
    }
}
