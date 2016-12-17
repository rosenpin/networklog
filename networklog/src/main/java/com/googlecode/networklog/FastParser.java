/* (C) 2012 Pragmatic Software
   This Source Code Form is subject to the terms of the Mozilla Public
   License, v. 2.0. If a copy of the MPL was not distributed with this
   file, You can obtain one at http://mozilla.org/MPL/2.0/
   */

package com.googlecode.networklog;

// Inspired by Hugh Perkins's jfastparser
// https://github.com/hughperkins/jfastparser/blob/1434212efac5ed422724d75dbb3359b72dd7c614/src/jfastparser/Parser.java

public class FastParser {
    private char[] line;
    private int len;
    private int pos;
    private char delimiter;

    public FastParser() {
        this(null, 0, ' ');
    }

    public FastParser(char[] line, int len, char delimiter) {
        this.line = line;
        this.len = len;
        this.delimiter = delimiter;
    }

    public void setLine(char[] line, int len) {
        if (MyLog.enabled && MyLog.level >= 6) {
            MyLog.d(6, "setLine line: [" + new String(line, 0, len) + "] len: " + len);
        }
        this.line = line;
        this.len = len;
        pos = 0;
    }

    public void setPos(int newpos) {
        if (newpos < 0 || newpos >= len) {
            throw new IndexOutOfBoundsException("Attempt to set new pos " + newpos + " is out of range 0 - " + len);
        }

        pos = newpos;
    }

    public long getLong() {
        return getLong(delimiter);
    }

    public long getLong(char delimiter) {
        int newpos = pos;
        long value = 0;
        boolean neg = false;

        if (pos >= len) {
            throw new RuntimeException("pos at end of string");
        }

        if (line[pos] == '-') {
            neg = true;
            newpos++;
        }

        if (line[pos] == '+') {
            pos++;
            newpos++;
        }

        char thischar = 0;

        while (newpos < len && (thischar = line[newpos]) != delimiter && thischar >= '0' && thischar <= '9') {
            value = value * 10 + (line[newpos] - '0');
            newpos++;
        }

        if (pos == newpos) {
            throw new RuntimeException("expected long but found [" + line[pos] + "] in [" + new String(line, pos, len - pos) + "]");
        }

        pos = newpos;
        eatDelimiter();
        return neg ? -value : value;
    }

    public int getInt() {
        return getInt(delimiter);
    }

    public int getInt(char delimiter) {
        int newpos = pos;
        int value = 0;
        boolean neg = false;

        if (pos >= len) {
            throw new RuntimeException("pos at end of string");
        }

        if (line[pos] == '-') {
            neg = true;
            newpos++;
        }

        if (line[pos] == '+') {
            pos++;
            newpos++;
        }

        char thischar;

        while (newpos < len && (thischar = line[newpos]) != delimiter && thischar >= '0' && thischar <= '9') {
            value = value * 10 + (line[newpos] - '0');
            newpos++;
        }

        if (pos == newpos) {
            throw new RuntimeException("expected int but found [" + line[pos] + "] in [" + new String(line, pos, len - pos) + "]");
        }

        pos = newpos;
        eatDelimiter();
        return neg ? -value : value;
    }

    public String getString() {
        return getString(delimiter);
    }

    public String getString(char delimiter) {
        int newpos = pos;
        String value;

        while (newpos < len && line[newpos] != delimiter) {
            newpos++;
        }

        if (pos == newpos) {
            value = "";
        } else {
            value = StringPool.get(line, pos, newpos - pos);
        }

        pos = newpos;
        eatDelimiter();
        return value;
    }

    public void eatDelimiter() {
        eatChar(delimiter);
    }

    public void eatChar(char target) {
        if (pos < len && line[pos] != target) {
            throw new RuntimeException("expected [" + target + "] but got " + line[pos] + " in [" + new String(line, pos, len - pos) + "]");
        }
        pos++;
    }
}
