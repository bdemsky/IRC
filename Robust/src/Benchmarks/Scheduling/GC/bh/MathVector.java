
/**
 * A class representing a three dimensional vector that implements
 * several math operations.  To improve speed we implement the
 * vector as an array of doubles rather than use the exising
 * code in the java.util.Vector class.
 **/
class MathVector
{
  /**
   * The number of dimensions in the vector
   **/
  public final int NDIM;
  /**
   * An array containing the values in the vector.
   **/
  private double data[];

  /**
   * Construct an empty 3 dimensional vector for use in Barnes-Hut algorithm.
   **/
  public MathVector()
  {
    NDIM = 3;
    data = new double[NDIM];
    for (int i=0; i < NDIM; i++) {
      data[i] = 0.0;
    }
  }

  public MathVector(boolean x) {
    NDIM = 3;
    data = null;
  }

  /**
   * Create a copy of the vector.
   * @return a clone of the math vector
   **/
  public Object clone() 
  {
    MathVector v = new MathVector();
    v.data = new double[NDIM];
    for (int i = 0; i < NDIM; i++) {
      v.data[i] = data[i];
    }
    return v;
  }

  /**
   * Return the value at the i'th index of the vector.
   * @param i the vector index 
   * @return the value at the i'th index of the vector.
   **/
  public  double value(int i)
  {
    return data[i];
  }

  /**
   * Set the value of the i'th index of the vector.
   * @param i the vector index
   * @param v the value to store
   **/
  public  void value(int i, double v)
  {
    data[i] = v;
  }

  /**
   * Set one of the dimensions of the vector to 1.0
   * param j the dimension to set.
   **/
  public  void unit(int j)
  {
    for (int i=0; i < NDIM; i++) {
      data[i] = (i == j ? 1.0 : 0.0);
    }
  }

  /**
   * Add two vectors and the result is placed in this vector.
   * @param u the other operand of the addition
   **/
  public  void addition(MathVector u)
  {
    for (int i=0; i < NDIM; i++) {
      data[i] += u.data[i];
    }
  }

  /**
   * Subtract two vectors and the result is placed in this vector.
   * This vector contain the first operand.
   * @param u the other operand of the subtraction.
   **/
  public  void subtraction(MathVector u)
  {
    for (int i=0; i < NDIM; i++) {
      data[i] -= u.data[i];
    }
  }

  /**
   * Subtract two vectors and the result is placed in this vector.
   * @param u the first operand of the subtraction.
   * @param v the second opernd of the subtraction
   **/
  public  void subtraction(MathVector u, MathVector v)
  {
    for (int i=0; i < NDIM; i++) {
      data[i] = u.data[i] - v.data[i];
    }
  }

  /**
   * Multiply the vector times a scalar.
   * @param s the scalar value
   **/
  public  void multScalar(double s)
  {
    for (int i=0; i < NDIM; i++) {
      data[i] *= s;
    }
  }

  /**
   * Multiply the vector times a scalar and place the result in this vector.
   * @param u the vector
   * @param s the scalar value
   **/
  public  void multScalar(MathVector u, double s)
  {
    for (int i=0; i < NDIM; i++) {
      data[i] = u.data[i] * s;
    }
  }

  /**
   * Divide each element of the vector by a scalar value.
   * @param s the scalar value.
   **/
  public  void divScalar(double s)
  {
    for (int i=0; i < NDIM; i++) {
      data[i] /= s;
    }
  }

  /**
   * Return the dot product of a vector.
   * @return the dot product of a vector.
   **/
  public  double dotProduct()
  {
    double s = 0.0;
    for (int i=0; i < NDIM; i++) {
      s += data[i] * data[i];
    }
    return s;
  }

  public  double absolute()
  {
    double tmp = 0.0;
    for (int i = 0; i < NDIM; i++) {
      tmp += data[i] * data[i];
    }
    return Math.sqrt(tmp);
  }

  public  double distance(MathVector v)
  {
    double tmp = 0.0;
    for (int i = 0; i < NDIM; i++) {
      tmp += (data[i] - v.data[i]) * (data[i] - v.data[i]);
    }
    return Math.sqrt(tmp);
  }

  public  void crossProduct(MathVector u, MathVector w)
  {
    data[0] = u.data[1] * w.data[2] - u.data[2]*w.data[1];
    data[1] = u.data[2] * w.data[0] - u.data[0]*w.data[2];
    data[2] = u.data[0] * w.data[1] - u.data[1]*w.data[0];
  }

  public  void incrementalAdd(MathVector u)
  {
    for (int i = 0; i < NDIM; i++) {
      data[i] += u.data[i];
    }
  }

  public  void incrementalSub(MathVector u)
  {
    for (int i = 0; i < NDIM; i++) {
      data[i] -= u.data[i];
    }
  }

  public  void incrementalMultScalar(double s) 
  {
    for (int i=0; i < NDIM; i++) {
      data[i] *= s;
    }
  }

  public  void incrementalDivScalar(double s)
  {
    for (int i=0; i < NDIM; i++) {
      data[i] /= s;
    }
  }

  /**
   * Add a scalar to each element in the vector and put the
   * result in this vector.
   * @param u a vector
   * @param s the scalar
   **/
  public  void addScalar(MathVector u, double s) 
  {
    for (int i = 0; i < NDIM; i++) {
      data[i] = u.data[i] + s;
    }
  }


  /**
   * Return the string representation of the vector
   **/
  /*public String toString()
  {
    StringBuffer s = new StringBuffer();
    for (int i = 0; i < NDIM; i++) {
      s.append(data[i] + " ");
    }
    return s.toString();
  }*/
}
