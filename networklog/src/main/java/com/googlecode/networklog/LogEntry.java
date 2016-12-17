/* (C) 2012 Pragmatic Software
   This Source Code Form is subject to the terms of the Mozilla Public
   License, v. 2.0. If a copy of the MPL was not distributed with this
   file, You can obtain one at http://mozilla.org/MPL/2.0/
 */

package com.googlecode.networklog;

import android.content.Context;
import android.util.Log;

public class LogEntry {
    private static HostNames hostNames;
    public int uid;
    public String uidString;
    public String in;
    public String out;
    public String proto;
    public String src;
    public String dst;
    public int len;
    public int spt;
    public int dpt;
    public long timestamp;
    private boolean validated;
    private boolean valid;

    public boolean isValid() {
        if (validated) {
            return valid;
        }

        validated = true;
        if (in.contains("{}:=")) {
            valid = false;
            return false;
        }

        if (out.contains("{}:=")) {
            valid = false;
            return false;
        }

        if (proto.contains("{}:=")) {
            valid = false;
            return false;
        }

        if (src.contains("{}:=")) {
            valid = false;
            return false;
        }

        if (dst.contains("{}:=")) {
            valid = false;
            return false;
        }

        valid = true;
        return true;
    }

    private boolean empty() {
        return !(in != null || out != null || proto != null || src != null || spt > 0 || dst != null || dpt > 0 || len > 0 | timestamp != 0);
    }

    public void applyHosts(Context context) {
        if (hostNames == null)
            hostNames = new HostNames(context);
        if (dst != null)
            dst = hostNames.getName(dst);
        if (src != null)
            src = hostNames.getName(src);
    }

    public void print() {
        if (!empty())
            MyLog.printSeperator();
        else {
            MyLog.log(this, "Empty");
            return;
        }
        /*if (app.name != null)
            Log.d("Tomer: package name", String.valueOf(app.name));*/
        if (in != null)
            Log.d("Tomer: Network", in);
        if (out != null)
            Log.d("Tomer: Network out", out);
        if (proto != null)
            Log.d("Tomer: protocol", proto);
        if (src != null)
            Log.d("Tomer: source name", src);
        if (dst != null)
            Log.d("Tomer: dest name", dst);
        if (len != 0)
            Log.d("Tomer: len", String.valueOf(len));
        if (spt != 0)
            Log.d("Tomer: src port", String.valueOf(spt));
        if (dpt != 0)
            Log.d("Tomer: dst port", String.valueOf(dpt));
        if (timestamp != 0)
            Log.d("Tomer: timestamp", String.valueOf(timestamp));
    }
}
