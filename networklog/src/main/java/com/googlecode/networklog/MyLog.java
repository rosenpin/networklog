/* (C) 2012 Pragmatic Software
   This Source Code Form is subject to the terms of the Mozilla Public
   License, v. 2.0. If a copy of the MPL was not distributed with this
   file, You can obtain one at http://mozilla.org/MPL/2.0/
 */

package com.googlecode.networklog;

import android.util.Log;

public class MyLog {
    public static final boolean enabled = true;
    public static int level = 5;
    public static String tag = "DefaultNetworkLogTag";

    public static void d(String msg) {
        d(6, tag, msg);
    }

    public static void d(String tag, String msg) {
        d(6, tag, msg);
    }

    public static void d(int level, String msg) {
        d(level, tag, msg);
    }

    public static void d(int level, String tag, String msg) {
        if (MyLog.level > level)
            for (String line : msg.split("\n")) {
                Log.d(tag, line);
            }
    }

    public static void printSeperator() {
        printRepeat('=', 15);
        System.out.print("New data");
        printRepeat('=', 15);
        System.out.println("");
    }

    static void printRepeat(char c, int times) {
        for (int i = 0; i < times; i++) {
            System.out.print(c);
        }
    }

    public static void log(Object c, String text) {
        Log.d(c.getClass().getSimpleName(), text);
    }
}
