package RayTracer;

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



public class View
//implements java.io.Serializable
{
  /*    public  Vec     from;
	public  Vec	    at;
	public  Vec	    up;
	public  float	dist;
	public  float	angle;
	public  float	aspect;*/
  public final Vec       from;
  public final Vec	    at;
  public final Vec	    up;
  public final float	dist;
  public final float	angle;
  public final float	aspect;

  public View (Vec from, Vec at, Vec up, float dist, float angle, float aspect)
  {
    this.from = from;
    this.at = at;
    this.up = up;
    this.dist = dist;
    this.angle = angle;
    this.aspect = aspect;	    	    
  }
}



