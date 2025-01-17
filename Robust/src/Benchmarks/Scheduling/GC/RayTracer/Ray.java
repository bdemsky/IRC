/**************************************************************************
 *                                                                         *
 *             Java Grande Forum Benchmark Suite - Version 2.0             *
 *                                                                         *
 *                            produced by                                  *
 *                                                                         *
 *                  Java Grande Benchmarking Project                       *
 *                                                                         *
 *                                at                                       *
 *                                                                         *
 *                Edinburgh Parallel Computing Centre                      *
 *                                                                         *
 *                email: epcc-javagrande@epcc.ed.ac.uk                     *
 *                                                                         *
 *                 Original version of this code by                        *
 *            Florian Doyon (Florian.Doyon@sophia.inria.fr)                *
 *              and  Wilfried Klauser (wklauser@acm.org)                   *
 *                                                                         *
 *      This version copyright (c) The University of Edinburgh, 1999.      *
 *                         All rights reserved.                            *
 *                                                                         *
 **************************************************************************/



final public class Ray {
  public Vec P, D;

  public Ray(Vec pnt, Vec dir) {
    P = new Vec(pnt.x, pnt.y, pnt.z);
    D = new Vec(dir.x, dir.y, dir.z);
    D.normalize();
  }

  public Ray() {
    P = new Vec();
    D = new Vec();
  }

  public Vec point(float t) {
    return new Vec(P.x + D.x * t, P.y + D.y * t, P.z + D.z * t);
  }

  public String toString() {
    return "{" + P.toString() + " -> " + D.toString() + "}";
  }
}
