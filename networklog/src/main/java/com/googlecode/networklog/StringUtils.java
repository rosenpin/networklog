/* (C) 2012 Pragmatic Software
   This Source Code Form is subject to the terms of the Mozilla Public
   License, v. 2.0. If a copy of the MPL was not distributed with this
   file, You can obtain one at http://mozilla.org/MPL/2.0/
 */

package com.googlecode.networklog;

public class StringUtils {
    private static long gigs[] = {1000000000, 1073741824};
    private static long megs[] = {1000000, 1048576};

    ;
    static long mult[] = {1000, 1024};

    static boolean contains(String string, String chars) {
        int stringLength = string.length();
        int charsLength = chars.length();
        int i, j;
        char c;

        for (i = 0; i < stringLength; i++) {
            c = string.charAt(i);
            for (j = 0; j < charsLength; j++) {
                if (c == chars.charAt(j)) {
                    return true;
                }
            }
        }
        return false;
    }


    static String formatToBytes(long value) {
        return formatToMultiplier(value, Format.BYTES);
    }

    private static String formatToMultiplier(long value, Format format) {
        String result;
        if (value < mult[format.value]) {
            result = String.valueOf(value);
        } else if (value < megs[format.value]) {
            result = String.format("%.2f", (value / (float) mult[format.value])) + "K";
        } else if (value < gigs[format.value]) {
            result = String.format("%.2f", (value / (float) megs[format.value])) + "M";
        } else {
            result = String.format("%.2f", (value / (float) gigs[format.value])) + "G";
        }
        return result;
    }

    private enum Format {
        BYTES(1);

        public int value;

        Format(int value) {
            this.value = value;
        }
    }
}
