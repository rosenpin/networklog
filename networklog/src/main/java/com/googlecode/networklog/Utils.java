package com.googlecode.networklog;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;

import static android.content.Context.ACTIVITY_SERVICE;

public class Utils {
    public static String getLogFile() {
        return Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "networklog.txt";
    }

    public static ArrayList<String> getLocalIpAddresses() {
        MyLog.d("getLocalIpAddresses");
        ArrayList<String> localIpAddrs = new ArrayList<String>();

        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();
                MyLog.d("Network interface found: " + intf.toString());

                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    MyLog.d("InetAddress: " + inetAddress.toString());

                    if (!inetAddress.isLoopbackAddress()) {
                        MyLog.d("Adding local IP address: [" + inetAddress.getHostAddress() + "]");
                        localIpAddrs.add(inetAddress.getHostAddress());
                    }
                }
            }
        } catch (SocketException ex) {
            Log.e("NetworkLog", ex.toString());
        }
        return localIpAddrs;
    }

    public static boolean isServiceRunning(Context context, String serviceName) {
        ActivityManager manager = (ActivityManager) context.getSystemService(ACTIVITY_SERVICE);

        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            MyLog.d("Service: " + service.service.getClassName() + "; " + service.pid + "; " + service.clientCount + "; " + service.foreground + "; " + service.process);
            if (serviceName.equals(service.service.getClassName())) {
                return true;
            }
        }

        return false;
    }
}
