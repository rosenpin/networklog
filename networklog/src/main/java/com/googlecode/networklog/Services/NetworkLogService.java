/* (C) 2012 Pragmatic Software
   This Source Code Form is subject to the terms of the Mozilla Public
   License, v. 2.0. If a copy of the MPL was not distributed with this
   file, You can obtain one at http://mozilla.org/MPL/2.0/
 */

package com.googlecode.networklog.Services;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.Toast;

import com.googlecode.networklog.ApplicationsTracker;
import com.googlecode.networklog.FastParser;
import com.googlecode.networklog.InteractiveShell;
import com.googlecode.networklog.LogEntry;
import com.googlecode.networklog.MyLog;
import com.googlecode.networklog.NetStat;
import com.googlecode.networklog.NetworkLog;
import com.googlecode.networklog.R;
import com.googlecode.networklog.StringPool;
import com.googlecode.networklog.SysUtils;
import com.googlecode.networklog.ThroughputTracker;

import java.util.ArrayList;
import java.util.HashMap;

public class NetworkLogService extends Service {
    static final int NOTIFICATION_ID = "Network Log".hashCode();
    public static NetworkLogService instance = null;
    public static Handler handler;
    public static boolean toastShowAddress;
    public static boolean invertUploadDownload;
    public static boolean behindFirewall;
    public static boolean watchRules;
    public static int watchRulesTimeout;
    public static boolean throughputBps;
    private static Context context;
    private static HashMap<String, Integer> logEntriesMap = new HashMap<String, Integer>();
    boolean has_root = false;
    boolean has_binaries = false;
    private InteractiveShell loggerShell;
    private NetworkLogger logger;
    private NetStat netstat = new NetStat();
    private FastParser parser = new FastParser();

    public static void showToast(final Context context, final CharSequence text) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context, text, Toast.LENGTH_LONG).show();
            }
        });
    }

    public static void showToast(final CharSequence text) {
        if (context == null)
            return;
        showToast(context, text);
    }

    public void startForeground() {
        Notification notification = new Notification();
        notification.icon = R.drawable.icon;
        notification.tickerText = "Running";
        startForeground(NOTIFICATION_ID, notification);
    }

    public void stopForeground() {
        stopForeground(true);
    }

    public boolean hasRoot() {
        return SysUtils.checkRoot(this);
    }

    @Override
    public void onCreate() {
        MyLog.d(8, "[service] onCreate");
        if (NetworkLog.shell == null) {
            NetworkLog.shell = SysUtils.createRootShell(this, "NLServiceRootShell", true);
        }

        if (!hasRoot()) {
            SysUtils.showError(this, getString(R.string.error_default_title), getString(R.string.error_noroot));
            has_root = false;
            stopSelf();
            return;
        } else {
            has_root = true;
        }

        if (!SysUtils.installBinaries(this)) {
            has_binaries = false;
            stopSelf();
            return;
        } else {
            has_binaries = true;
        }

        if (instance != null) {
            Log.w("NetworkLog", "[service] Last instance destroyed unexpectedly");
        }

        instance = this;
        handler = new Handler();

        if (ApplicationsTracker.installedApps == null) {
            ApplicationsTracker.getInstalledApps(this, null);
        }

        behindFirewall = false;
        watchRules = false;
        watchRulesTimeout = 120000;
        throughputBps = true;

        ThroughputTracker.startUpdater();
        startForeground();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        MyLog.d(10, "[service] onStartCommand");

        if (!has_root || !has_binaries) {
            return Service.START_NOT_STICKY;
        }

        Bundle ext = null;

        if (intent == null) {
            MyLog.d("[service] Service null intent");
        } else {
            ext = intent.getExtras();
        }

        final Bundle extras = ext;
        context = this;

        // run in background thread
        new Thread(new Runnable() {
            public void run() {
                String logfile_from_intent = null;

                if (extras != null) {
                    logfile_from_intent = extras.getString("logfile");
                    MyLog.d("[service] set logfile: " + logfile_from_intent);
                }

                if (logfile_from_intent == null) {
                    logfile_from_intent = SysUtils.getLogFile();
                }

                MyLog.d(8, "[service] NetworkLog service starting [" + logfile_from_intent + "]");


                initEntriesMap();

                if (!startLogging()) {
                    MyLog.d("[service] start logging error, aborting");
                    handler.post(new Runnable() {
                        public void run() {
                            stopSelf();
                        }
                    });
                }
            }
        }).start();

        return Service.START_STICKY;
    }

    @Override
    public void onDestroy() {
        System.out.println("[service] onDestroy");

        stopForeground();

        ThroughputTracker.stopUpdater();

        if (has_root && has_binaries) {
            stopLogging();
            Toast.makeText(this, getString(R.string.logging_stopped), Toast.LENGTH_SHORT).show();
        }

        instance = null;
        context = null;
        handler = null;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public void initEntriesMap() {
        ArrayList<NetStat.Connection> connections = netstat.getConnections();

        for (NetStat.Connection connection : connections) {
            String mapKey = connection.src + ":" + connection.spt + " -> " + connection.dst + ":" + connection.dpt;

            if (MyLog.enabled && MyLog.level >= 5) {
                MyLog.d(5, "[netstat src-dst] New entry " + connection.uid + " for [" + mapKey + "]");
            }

            logEntriesMap.put(mapKey, Integer.valueOf(connection.uid));

            mapKey = connection.dst + ":" + connection.dpt + " -> " + connection.src + ":" + connection.spt;

            if (MyLog.enabled && MyLog.level >= 5) {
                MyLog.d(5, "[netstat dst-src] New entry " + connection.uid + " for [" + mapKey + "]");
            }

            logEntriesMap.put(mapKey, Integer.valueOf(connection.uid));
        }
    }

    public void parseResult(String result) {
        MyLog.d(10, "--------------- parsing network entry --------------");
        int pos = 0, lastpos, thisEntry, nextEntry, newline, space;
        String in, out, src, dst, proto, uidString;
        int spt, dpt, len, uid;
        parser.setLine(result.toCharArray(), result.length() - 1);

        while ((pos = result.indexOf("{NL}", pos)) > -1) {
            MyLog.d(10, "---- got {NL} at " + pos + " ----");


            pos += "{NL}".length(); // skip past "{NL}"

            thisEntry = pos;
            newline = result.indexOf("\n", pos);
            nextEntry = result.indexOf("{NL}", pos);

            if (newline == -1) {
                newline = result.length();
            }

            if (nextEntry != -1 && nextEntry < newline) {
                // Log.w("NetworkLog", "Skipping corrupted entry [" + result.substring(thisEntry, newline) + "]");
                pos = newline;
                continue;
            }

            try {
                pos = result.indexOf("IN=", pos);

                if (pos == -1 || pos > newline) {
                    // Log.w("NetworkLog", "Skipping corrupted entry [" + result.substring(thisEntry, newline) + "]");
                    pos = newline;
                    continue;
                }

                space = result.indexOf(" ", pos);

                if (space == -1 || space > newline) {
                    // Log.w("NetworkLog", "Skipping corrupted entry [" + result.substring(thisEntry, newline) + "]");
                    pos = newline;
                    continue;
                }

                parser.setPos(pos + 3);
                in = parser.getString();

                pos = result.indexOf("OUT=", pos);

                if (pos == -1 || pos > newline) {
                    // Log.w("NetworkLog", "Skipping corrupted entry [" + result.substring(thisEntry, newline) + "]");
                    pos = newline;
                    continue;
                }

                space = result.indexOf(" ", pos);

                if (space == -1 || space > newline) {
                    // Log.w("NetworkLog", "Skipping corrupted entry [" + result.substring(thisEntry, newline) + "]");
                    pos = newline;
                    continue;
                }

                parser.setPos(pos + 4);
                out = parser.getString();

                pos = result.indexOf("SRC=", pos);

                if (pos == -1 || pos > newline) {
                    // Log.w("NetworkLog", "Skipping corrupted entry [" + result.substring(thisEntry, newline) + "]");
                    pos = newline;
                    continue;
                }

                space = result.indexOf(" ", pos);

                if (space == -1 || space > newline) {
                    // Log.w("NetworkLog", "Skipping corrupted entry [" + result.substring(thisEntry, newline) + "]");
                    pos = newline;
                    continue;
                }

                parser.setPos(pos + 4);
                src = parser.getString();

                pos = result.indexOf("DST=", pos);

                if (pos == -1 || pos > newline) {
                    // Log.w("NetworkLog", "Skipping corrupted entry [" + result.substring(thisEntry, newline) + "]");
                    pos = newline;
                    continue;
                }

                space = result.indexOf(" ", pos);

                if (space == -1 || space > newline) {
                    // Log.w("NetworkLog", "Skipping corrupted entry [" + result.substring(thisEntry, newline) + "]");
                    pos = newline;
                    continue;
                }

                parser.setPos(pos + 4);
                dst = parser.getString();

                pos = result.indexOf("LEN=", pos);

                if (pos == -1 || pos > newline) {
                    // Log.w("NetworkLog", "Skipping corrupted entry [" + result.substring(thisEntry, newline) + "]");
                    pos = newline;
                    continue;
                }

                space = result.indexOf(" ", pos);

                if (space == -1 || space > newline) {
                    // Log.w("NetworkLog", "Skipping corrupted entry [" + result.substring(thisEntry, newline) + "]");
                    pos = newline;
                    continue;
                }

                parser.setPos(pos + 4);
                len = parser.getInt();

                pos = result.indexOf("PROTO=", pos);

                if (pos == -1 || pos > newline) {
                    // Log.w("NetworkLog", "Skipping corrupted entry [" + result.substring(thisEntry, newline) + "]");
                    pos = newline;
                    continue;
                }

                space = result.indexOf(" ", pos);

                if (space == -1 || space > newline) {
                    // Log.w("NetworkLog", "Skipping corrupted entry [" + result.substring(thisEntry, newline) + "]");
                    pos = newline;
                    continue;
                }

                parser.setPos(pos + 6);
                proto = parser.getString();

                lastpos = pos;
                pos = result.indexOf("SPT=", pos);

                if (pos == -1 || pos > newline) {
                    // no SPT field, probably a broadcast packet
                    spt = 0;
                    pos = lastpos;
                } else {
                    space = result.indexOf(" ", pos);

                    if (space == -1 || space > newline) {
                        // Log.w("NetworkLog", "Skipping corrupted entry [" + result.substring(thisEntry, newline) + "]");
                        pos = newline;
                        continue;
                    }

                    parser.setPos(pos + 4);
                    spt = parser.getInt();
                }

                lastpos = pos;
                pos = result.indexOf("DPT=", pos);

                if (pos == -1 || pos > newline) {
                    // no DPT field, probably a broadcast packet
                    dpt = 0;
                    pos = lastpos;
                } else {
                    space = result.indexOf(" ", pos);

                    if (space == -1 || space > newline) {
                        // Log.w("NetworkLog", "Skipping corrupted entry [" + result.substring(thisEntry, newline) + "]");
                        pos = newline;
                        continue;
                    }

                    parser.setPos(pos + 4);
                    dpt = parser.getInt();
                }

                lastpos = pos;
                pos = result.indexOf("UID=", pos);

                if (pos == -1 || pos > newline) {
                    uid = -1;
                    uidString = "-1";
                    pos = lastpos;
                } else {
                    parser.setPos(pos + 4);
                    uid = parser.getInt();
                    parser.setPos(pos + 4);
                    uidString = parser.getString();
                }
            } catch (Exception e) {
                Log.e("NetworkLog", "Bad data for: [" + result.substring(thisEntry, newline) + "]", e);
                pos = newline;
                continue;
            }

            if (MyLog.enabled && MyLog.level >= 9) {
                MyLog.d(9, "Setting map key: src=[" + src + "] spt=" + spt + " dst=[" + dst + "] dpt=" + dpt);
            }

            String srcDstMapKey = src + ":" + spt + "->" + dst + ":" + dpt;
            String dstSrcMapKey = dst + ":" + dpt + "->" + src + ":" + spt;

            if (MyLog.enabled && MyLog.level >= 10) {
                MyLog.d(10, "Checking entry for " + uid + " " + srcDstMapKey + " and " + dstSrcMapKey);
            }

            Integer srcDstMapUid = logEntriesMap.get(srcDstMapKey);
            Integer dstSrcMapUid = logEntriesMap.get(dstSrcMapKey);

            if (uid < 0) {
                // Unknown uid, retrieve from entries map
                if (MyLog.enabled && MyLog.level >= 9) {
                    MyLog.d(9, "Unknown uid");
                }

                if (srcDstMapUid == null || dstSrcMapUid == null) {
                    // refresh netstat and try again
                    if (MyLog.enabled && MyLog.level >= 9) {
                        MyLog.d(9, "Refreshing netstat ...");
                    }
                    initEntriesMap();
                    srcDstMapUid = logEntriesMap.get(srcDstMapKey);
                    dstSrcMapUid = logEntriesMap.get(dstSrcMapKey);
                }

                if (srcDstMapUid == null) {
                    if (MyLog.enabled && MyLog.level >= 9) {
                        MyLog.d(9, "[src-dst] No entry uid for " + uid + " [" + srcDstMapKey + "]");
                    }

                    if (uid == -1) {
                        if (dstSrcMapUid != null) {
                            if (MyLog.enabled && MyLog.level >= 9) {
                                MyLog.d(9, "[dst-src] Reassigning kernel packet -1 to " + dstSrcMapUid);
                            }
                            uid = dstSrcMapUid;
                            uidString = StringPool.get(dstSrcMapUid);
                        } else {
                            if (MyLog.enabled && MyLog.level >= 9) {
                                MyLog.d(9, "[src-dst] New kernel entry -1 for [" + srcDstMapKey + "]");
                            }
                            srcDstMapUid = uid;
                            logEntriesMap.put(srcDstMapKey, srcDstMapUid);
                        }
                    } else {
                        if (MyLog.enabled && MyLog.level >= 9) {
                            MyLog.d(9, "[src-dst] New entry " + uid + " for [" + srcDstMapKey + "]");
                        }
                        srcDstMapUid = uid;
                        logEntriesMap.put(srcDstMapKey, srcDstMapUid);
                    }
                } else {
                    if (MyLog.enabled && MyLog.level >= 9) {
                        MyLog.d(9, "[src-dst] Found entry uid " + srcDstMapUid + " for " + uid + " [" + srcDstMapKey + "]");
                    }
                    uid = srcDstMapUid;
                    uidString = StringPool.get(srcDstMapUid);
                }

                if (dstSrcMapUid == null) {
                    if (MyLog.enabled && MyLog.level >= 9) {
                        MyLog.d(9, "[dst-src] No entry uid for " + uid + " [" + dstSrcMapKey + "]");
                    }

                    if (uid == -1) {
                        if (MyLog.enabled && MyLog.level >= 9) {
                            MyLog.d(9, "[src-dst] Reassigning kernel packet -1 to " + srcDstMapUid);
                        }
                        uid = srcDstMapUid;
                        uidString = StringPool.get(srcDstMapUid);
                    } else {
                        if (MyLog.enabled && MyLog.level >= 9) {
                            MyLog.d(9, "[dst-src] New entry " + uid + " for [" + dstSrcMapKey + "]");
                        }
                        dstSrcMapUid = uid;
                        logEntriesMap.put(dstSrcMapKey, dstSrcMapUid);
                    }
                } else {
                    if (MyLog.enabled && MyLog.level >= 9) {
                        MyLog.d(9, "[dst-src] Found entry uid " + dstSrcMapUid + " for " + uid + " [" + dstSrcMapKey + "]");
                    }
                    uid = dstSrcMapUid;
                    uidString = StringPool.get(dstSrcMapUid);
                }
            } else {
                if (MyLog.enabled && MyLog.level >= 9) {
                    MyLog.d(9, "Known uid");
                }

                if (srcDstMapUid == null || dstSrcMapUid == null || srcDstMapUid != uid || dstSrcMapUid != uid) {
                    if (MyLog.enabled && MyLog.level >= 9) {
                        MyLog.d(9, "Updating uid " + uid + " to netstat map for " + srcDstMapKey + " and " + dstSrcMapKey);
                    }
                    logEntriesMap.put(srcDstMapKey, uid);
                    logEntriesMap.put(dstSrcMapKey, uid);
                }
            }

            LogEntry entry = new LogEntry();
            entry.uid = uid;
            entry.uidString = uidString;
            entry.in = in;
            entry.out = out;
            entry.src = src;
            entry.spt = spt;
            entry.dst = dst;
            entry.dpt = dpt;
            entry.proto = proto;
            entry.len = len;
            entry.timestamp = System.currentTimeMillis();
            if (MyLog.enabled && MyLog.level >= 10) {
                MyLog.d(10, "+++ entry: (" + entry.uid + ") in=" + entry.in + " out=" + entry.out + " " + entry.src + ":" + entry.spt + " -> " + entry.dst + ":" + entry.dpt + " proto=" + entry.proto + " len=" + entry.len);
            }
            entry.print(getApplicationContext());
            ThroughputTracker.updateEntry(entry);
        }
    }

    public void stopLogger() {
        if (logger != null) {
            logger.stop();
        }
    }

    public void killLoggerCommand() {
        if (loggerShell != null) {
            loggerShell.sendCommand("kill $!", InteractiveShell.IGNORE_OUTPUT);
            loggerShell.close();
            loggerShell = null;
        }
    }

    public boolean startLoggerCommand() {
        if (loggerShell == null) {
            loggerShell = new InteractiveShell("su", "LoggerShell");
            loggerShell.start();

            if (loggerShell.hasError()) {
                String error = loggerShell.getError(true);
                Log.e("NetworkLog", "Error starting logger shell: " + error);
                SysUtils.showError(context, context.getResources().getString(R.string.error_default_title), "Error starting logger shell: " + error);
                return false;
            }
        }


        MyLog.d("Logmethod", String.valueOf(0));

        switch (0) {
            case 1:
                loggerShell.sendCommand("grep '{NL}' /proc/kmsg &", InteractiveShell.BACKGROUND);
                break;
            case 2:
                loggerShell.sendCommand("cat /proc/kmsg &", InteractiveShell.BACKGROUND);
                break;
            default:
                MyLog.log(this, "Using new method");
                loggerShell.sendCommand("dmesg -c", InteractiveShell.BACKGROUND);
        }

        try {
            // give logger command a chance to do its thing
            Thread.sleep(1500);
        } catch (Exception e) {
        }

        if (loggerShell.hasError()) {
            SysUtils.showError(this, getString(R.string.error_default_title), loggerShell.getError(true));
            loggerShell.close();
            loggerShell = null;
            return false;
        }

        // ensure logger command didn't exit
        if (loggerShell.exitval == 0) {
            loggerShell.sendCommand("kill -0 $!", InteractiveShell.IGNORE_OUTPUT);
        }

        if (loggerShell.exitval != 0) {
            loggerShell.sendCommand("wait $!", InteractiveShell.IGNORE_OUTPUT);
            Log.e("NetworkLog", "Error starting logger: exit " + loggerShell.exitval);
            String error = "Error starting logger: exit " + loggerShell.exitval;
            loggerShell.close();
            loggerShell = null;
            SysUtils.showError(this, getString(R.string.error_default_title), error);
            return false;
        }

        return true;
    }

    public boolean startLogging() {
        killLoggerCommand();
        MyLog.d(8, "adding logging rules");

        if (!startLoggerCommand()) {
            Log.e("Logger", "Failed to start logger command");
            return false;
        }

        logger = new NetworkLogger();
        new Thread(logger, "NetworkLogger").run();
        return true;
    }

    public void stopLogging() {
        stopLogger();
        killLoggerCommand();
    }

    public class NetworkLogger implements Runnable {
        boolean running = false;

        public void stop() {
            MyLog.d(1, "NetworkLog", "Network logger " + this + " stopping");
            running = false;
        }

        public void run() {
            MyLog.d(1, "NetworkLog", "Network logger " + this + " starting");
            String result;
            running = true;

            while (true) {
                int counter = 0;
                while (running /*&& !loggerShell.checkForExit()*/) {
                    if (loggerShell.stdoutAvailable()) {
                        result = loggerShell.readLine();
                    } else {
                        try {
                            Thread.sleep(1500);
                            if (!loggerShell.stdoutAvailable()) {
                                if (loggerShell.command.hasError())
                                    MyLog.log(this, "Error in loggershell");
                                counter++;
                                if (counter > 3) {
                                    Log.d("Logger", "Restarting logger command");
                                    loggerShell.sendCommand("dmesg -c", InteractiveShell.BACKGROUND);
                                    while (loggerShell.stdoutAvailable()) {
                                        parseResult(loggerShell.readLine());
                                    }
                                    counter = 0;
                                }
                            }
                        } catch (Exception e) {
                            Log.d("NetworkLog", "NetworkLogger exception while sleeping", e);
                        }
                        continue;
                    }

                    if (!running) {
                        break;
                    }

                    if (result == null) {
                        Log.d("NetworkLog", "Network logger " + this + " read null; exiting");
                        break;
                    }
                    Log.d(NetworkLogService.class.getSimpleName(), "Parsing result");
                    parseResult(result);
                }

                if (running) {
                    Log.d("NetworkLog", "Network logger " + this + " terminated unexpectedly, restarting in 5 seconds");
                    try {
                        Thread.sleep(5000);
                    } catch (Exception e) {
                        // ignored
                    }
                    if (!startLoggerCommand()) {
                        SysUtils.showError(context, context.getResources().getString(R.string.error_default_title),
                                "Logger process has terminated unexpectedly and was unable to restart");
                        running = false;
                    }
                } else {
                    Log.d("NetworkLog", "Network logger " + this + " reached end of loop; exiting");
                    break;
                }
            }
        }
    }
}
