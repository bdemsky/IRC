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



public class Surface
//implements java.io.Serializable
{
  public Vec	color;
  public float	kd;
  public float	ks;
  public float	shine;
  public float	kt;
  public float	ior;
  public boolean isnull;

  public Surface() {
    color = new Vec(1, 0, 0);
    kd =(float)  1.0;
    ks = (float) 0.0;
    shine = (float) 0.0;
    kt = (float) 0.0;
    ior = (float) 1.0;
    isnull=false;
  }

  public String toString() {
    return "Surface { color=" + color + "}";
  }
}


