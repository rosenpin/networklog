package com.googlecode.networklog;

import android.content.Context;
import android.content.Intent;

import com.googlecode.networklog.Services.NetworkLogService;

import java.util.ArrayList;

public class NetworkSpyWareExample {
    public static ArrayList<LogEntry> networkTraffic = new ArrayList<>();
    public static boolean collecting = false;

    public static void startSpying(Context context) {
        collecting = true;
        Intent i = new Intent(context, NetworkLogService.class);
        context.startService(i);
    }

    public static void stopSpying(Context context){
        Intent i = new Intent(context, NetworkLogService.class);
        context.stopService(i);
    }
}
