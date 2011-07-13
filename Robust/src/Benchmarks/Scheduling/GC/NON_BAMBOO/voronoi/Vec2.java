

/**
 * Vector Routines from CMU vision library.  
 * They are used only for the Voronoi Diagram, not the Delaunay Triagulation.
 * They are slow because of large call-by-value parameters.
 **/
class Vec2 
{
  float x,y;
  float norm;

  public Vec2() {}
  
  public Vec2(float xx, float yy) 
  {
    x = xx;
    y = yy;
    norm =  (float)(x*x + y*y);
  }

  public float X()
  {
    return x;
  }

  public float Y()
  {
    return y;
  }

  public float Norm()
  {
    return norm;
  }
  
  public void setNorm(float d)
  {
    norm = d;
  }

  /*public String toString()
  {
    return x + " " + y;
  }*/

  Vec2 circle_center(Vec2 b, Vec2 c)
  {
    Vec2 vv1 = b.sub(c);
    float d1 = vv1.magn();
    vv1 = sum(b);
    Vec2 vv2 = vv1.times(0.5f);
    if (d1 < 0.0) /*there is no intersection point, the bisectors coincide. */
      return(vv2);
    else {
      Vec2 vv3 = b.sub(this);
      Vec2 vv4 = c.sub(this); 
      float d3 = vv3.cprod(vv4) ;
      float d2 = (float)(-2.0f * d3) ;
      Vec2 vv5 = c.sub(b);
      float d4 = vv5.dot(vv4);
      Vec2 vv6 = vv3.cross();
      Vec2 vv7 = vv6.times((float)(d4/d2));
      return vv2.sum(vv7);
    }
  }



  /**
   * cprod: forms triple scalar product of [u,v,k], where k = u cross v 
   * (returns the magnitude of u cross v in space)
   **/
  float cprod(Vec2 v)
  {
    return((float)(x * v.y - y * v.x)); 
  }

  /* V2_dot: vector dot product */

  float dot(Vec2 v)
  {
    return((float)(x * v.x + y * v.y));
  }

  /* V2_times: multiply a vector by a scalar */

  Vec2 times(float c)
  {
    return (new Vec2((float)(c*x), (float)(c*y)));
  }

  /* V2_sum, V2_sub: Vector addition and subtraction */

  Vec2 sum(Vec2 v)
  {
    return (new Vec2((float)(x + v.x), (float)(y + v.y)));
  }

  Vec2 sub(Vec2 v)
  {
     return(new Vec2((float)(x - v.x), (float)(y - v.y)));
  }

/* V2_magn: magnitude of vector */

  float magn()
  {
    return (float) (Math.sqrt((float)(x*x+y*y)));
  }

  /* returns k X v (cross product).  this is a vector perpendicular to v */

  Vec2 cross()
  {
    return(new Vec2((float)y,(float)(-x)));
  }
}


 
