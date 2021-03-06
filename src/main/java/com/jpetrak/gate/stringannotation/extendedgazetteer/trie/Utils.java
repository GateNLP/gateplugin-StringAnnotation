/*
 * Copyright (c) 2010- Austrian Research Institute for Artificial Intelligence (OFAI). 
 * Copyright (C) 2014-2019 The University of Sheffield.
 *
 * This file is part of gateplugin-ModularPipelines
 * (see https://github.com/johann-petrak/gateplugin-StringAnnotation)
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software. If not, see <http://www.gnu.org/licenses/>.
 */
package com.jpetrak.gate.stringannotation.extendedgazetteer.trie;

public class Utils {
  
  /** 
   * Converts two characters two an int number
   */
  public static int twoChars2Int(char ch, char cl) {
    int l = (ch << 16) + cl;
    return l;
  }
  
  public static int twoChars2Int(char[] cs) {
    if(cs.length != 2) {
      throw new RuntimeException("Called twoChars2Int with an array that does not have 2 elements but "+cs.length);
    }
    return twoChars2Int(cs[0],cs[1]);    
  }
  
  /**
   * Converts an int into an array of two characters with the high bits
   * in the first element and the low bits in the second element
   */
  public static char[] int2TwoChars(int i) {
    char[] asChars = new char[2];
    asChars[0] =  (char)(i >>> 16);
    asChars[1] =  (char)(i & 0xFFFF);
    return asChars;
  }
  
  /**
   * Sets two characters at position pos to the representation of int as two successive 
   * characters.
   */
  public static void setTwoCharsFromInt(int i, char[] chars, int pos) {
    chars[pos] =  (char)(i >>> 16);
    chars[pos+1] =  (char)(i & 0xFFFF);    
  }
  
  
  /** 
   * Converts four characters two a long number
   */
  public static long fourChars2Long(char c4, char c3, char c2, char c1) {
    long l = c1;
    l += ((long)c2) << 16;
    l += ((long)c3) << 32;
    l += ((long)c4) << 48; 
    return l;
  }
  
  public static long fourChars2Long(char[] cs) {
    if(cs.length != 4) {
      throw new RuntimeException("Called fourChars2Long with an array that does not have 4 elements but "+cs.length);
    }
    return fourChars2Long(cs[0],cs[1],cs[2],cs[3]);
  }
  
  
  /**
   * Converts a long to a char array of four elements
   */
  public static char[] long2FourChars(long i) {
    char[] asChars = new char[4];
    asChars[0] =  (char)(i >>> 48);
    asChars[1] =  (char)((i >>> 32) & 0xFFFF);
    asChars[2] =  (char)((i >>> 16) & 0xFFFF);
    asChars[3] =  (char)(i & 0xFFFF);
    return asChars;
  }
  
  
}
