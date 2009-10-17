/* =============================================================================
 *
 * coordinate.c
 *
 * =============================================================================
 *
 * Copyright (C) Stanford University, 2006.  All Rights Reserved.
 * Author: Chi Cao Minh
 *
 * =============================================================================
 *
 * For the license of bayes/sort.h and bayes/sort.c, please see the header
 * of the files.
 * 
 * ------------------------------------------------------------------------
 * 
 * For the license of kmeans, please see kmeans/LICENSE.kmeans
 * 
 * ------------------------------------------------------------------------
 * 
 * For the license of ssca2, please see ssca2/COPYRIGHT
 * 
 * ------------------------------------------------------------------------
 * 
 * For the license of lib/mt19937ar.c and lib/mt19937ar.h, please see the
 * header of the files.
 * 
 * ------------------------------------------------------------------------
 * 
 * For the license of lib/rbtree.h and lib/rbtree.c, please see
 * lib/LEGALNOTICE.rbtree and lib/LICENSE.rbtree
 * 
 * ------------------------------------------------------------------------
 * 
 * Unless otherwise noted, the following license applies to STAMP files:
 * 
 * Copyright (c) 2007, Stanford University
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * 
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 * 
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 * 
 *     * Neither the name of Stanford University nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY STANFORD UNIVERSITY ``AS IS'' AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL STANFORD UNIVERSITY BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 *
 * =============================================================================
 */

public class coordinate {
  double x;
  double y;

  public coordinate() {
  }

  static int coordinate_compare (coordinate aPtr, coordinate bPtr) {
    if (aPtr.x < bPtr.x) {
        return -1;
    } else if (aPtr.x > bPtr.x) {
      return 1;
    } else if (aPtr.y < bPtr.y) {
      return -1;
    } else if (aPtr.y > bPtr.y) {
      return 1;
    }
    return 0;
  }


/* =============================================================================
 * coordinate_distance
 * =============================================================================
 */
  static double coordinate_distance (coordinate coordinatePtr, coordinate aPtr) {
    double delta_x = coordinatePtr.x - aPtr.x;
    double delta_y = coordinatePtr.y - aPtr.y;

    return Math.sqrt((delta_x * delta_x) + (delta_y * delta_y));
  }


/* =============================================================================
 * coordinate_angle
 *
 *           (b - a) .* (c - a)
 * cos a = ---------------------
 *         ||b - a|| * ||c - a||
 *
 * =============================================================================
 */
  static double coordinate_angle(coordinate aPtr, coordinate bPtr, coordinate cPtr) {
    double delta_b_x;
    double delta_b_y;
    double delta_c_x;
    double delta_c_y;
    double distance_b;
    double distance_c;
    double numerator;
    double denominator;
    double cosine;
    double radian;

    delta_b_x = bPtr.x - aPtr.x;
    delta_b_y = bPtr.y - aPtr.y;

    delta_c_x = cPtr.x - aPtr.x;
    delta_c_y = cPtr.y - aPtr.y;

    numerator = (delta_b_x * delta_c_x) + (delta_b_y * delta_c_y);

    distance_b = coordinate_distance(aPtr, bPtr);
    distance_c = coordinate_distance(aPtr, cPtr);
    denominator = distance_b * distance_c;

    cosine = numerator / denominator;
    radian = Math.acos(cosine);

    return (180.0 * radian / 3.141592653589793238462643);
}


/* =============================================================================
 * coordinate_print
 * =============================================================================
 */
  void coordinate_print() {
    System.out.println("("+x+", "+y+")");
  }
}
/* =============================================================================
 *
 * End of coordinate.c
 *
 * =============================================================================
 */
