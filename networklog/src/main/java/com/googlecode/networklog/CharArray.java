/* (C) 2012 Pragmatic Software
   This Source Code Form is subject to the terms of the Mozilla Public
   License, v. 2.0. If a copy of the MPL was not distributed with this
   file, You can obtain one at http://mozilla.org/MPL/2.0/
   */

// Utility class for manipulating a char[] without unnecessary allocations

package com.googlecode.networklog;

public class CharArray implements Comparable<CharArray> {
  char[] value;
  int offset;
  int length;

  public CharArray() {
    value = null;
    offset = 0;
    length = 0;
  }


  public String toString() {
    return new String(value, offset, length);
  }

  public int compareTo(String target) {
    int i = offset;
    int j = 0;
    int targetLength = target.length();
    int end = offset + length;
    int difference;

    while(i < end && j < targetLength) {
      difference = value[i++] - target.charAt(j++);

      if(difference > 0) {
        return 1;
      } else if(difference < 0) {
        return -1;
      }
    }

    if(length > targetLength) {
      return 1;
    } else if(length < targetLength) {
      return -1;
    } else {
      return 0;
    }
  }

  public int compareTo(CharArray target) {
    int i = offset;
    int j = target.offset;
    int end = offset + length;
    int targetEnd = target.offset + target.length;
    int difference;

    while(i < end && j < targetEnd) {
      difference = value[i++] - target.value[j++];

      if(difference > 0) {
        return 1;
      } else if(difference < 0) {
        return -1;
      }
    }

    if(length > target.length) {
      return 1;
    } else if(length < target.length) {
      return -1;
    } else {
      return 0;
    }
  }

  public void setValue(char[] value, int offset, int length) {
    this.value = value;
    this.offset = offset;
    this.length = length;
  }

  public char[] getValue() {
    return value;
  }

  public int getOffset() {
    return offset;
  }

  public int getLength() {
    return length;
  }
}
