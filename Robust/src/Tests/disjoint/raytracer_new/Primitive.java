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


public class Primitive
//public abstract class Primitive 
//implements java.io.Serializable 
{
	public Surface	surf;
	
	public Primitive(){
		surf=new Surface();
	}

	public void setColor(double r, double g, double b) {
		surf.color = new Vec(r, g, b);
	}

	public /*abstract*/ Vec normal(Vec pnt);
	public /*abstract*/ Isect intersect(Ray ry);
	public /*abstract*/ String toString();
	public /*abstract*/ Vec getCenter();
	public /*abstract*/ void setCenter(Vec c);
	
//	public  Vec normal(Vec pnt){
//		return null;
//	}
//	public  Isect intersect(Ray ry){
//		return null;
//	}
//	public  String toString(){
//		return null;
//	}
//	public  Vec getCenter(){
//		return null;
//	}
//	public  void setCenter(Vec c){
//	}
}
