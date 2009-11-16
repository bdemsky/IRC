public class Lambda {
    flag tocalcGF;
    flag tocalcInd;
    flag finish;
    
    /* current processing image related */
    float[] m_image;
    int m_rows;
    int m_cols;
    int m_r;
    
    int m_num_p;
    
    /* benchmark constants */
    int WINSZ;
    public int N_FEA;

    /* constructor */
    public Lambda(int winsz,
                  int nfea,
                  int pnum,
                  int nump) {
      this.WINSZ = winsz;
      this.N_FEA = nfea;
      this.m_num_p = nump;
    }
    
    public int getNumP() {
      return this.m_num_p;
    }
    
    public int getR() {
      return this.m_r;
    }
    
    public int getRows() {
      return this.m_rows;
    }
    
    public int getCols() {
      return this.m_cols;
    }
    
    public float[] getImage() {
      return this.m_image;
    }
    
    public void calcGoodFeature(ImageXM imxm,
                                ImageYM imym) {
      float[] dX, dY;
      int sizeX, sizeY, rowY, colY;
      int i, j;
      float[] xx, yy, xy, image;
      int rows_xx, cols_xx, rows_yy, cols_yy, rows_xy, cols_xy;
      float[] tr, det, c_xx, c_xy, c_yy;
      int rows_tr, cols_tr, rows_det, cols_det, rows_cxx, cols_cxx;
      int rows_cxy, cols_cxyrows_cyy, cols_cyy;
      
      dX = imxm.getImage();
      dY = imym.getImage();
      sizeX = imxm.getRows();
      sizeY = imxm.getCols();
      rowY = imym.getRows();
      colY = imym.getCols();

      rows_xx = sizeX;
      cols_xx = sizeY;
      xx = new float[rows_xx * cols_xx];
      rows_xy = sizeX;
      cols_xy = sizeY;
      xy = new float[rows_xy * cols_xy];
      rows_yy = sizeX;
      cols_yy = sizeY;
      yy = new float[rows_yy * cols_yy];

      for( i=0; i<sizeX; i++) {
        for( j=0; j<sizeY; j++) {
          xx[i * sizeY + j] = (float)(dX[i * sizeY + j] * dX[i * sizeY + j]);
          xy[i * sizeY + j] = (float)(dX[i * sizeY + j] * dY[i * sizeY + j]);            
          yy[i * sizeY + j] = (float)(dY[i * sizeY + j] * dY[i * sizeY + j]);            
        }
      }

      c_xx = calcAreaSum(xx, sizeY, sizeX);
      c_xy = calcAreaSum(xy, sizeY, sizeX);
      c_yy = calcAreaSum(yy, sizeY, sizeX);    

      rows_tr = sizeX;
      cols_tr = sizeY;
      tr = new float[rows_tr * cols_tr];
      rows_det = sizeX;
      cols_det = sizeY;
      det = new float[rows_det * cols_det];
      this.m_rows = sizeX;
      this.m_cols = sizeY;
      image = this.m_image = new float[this.m_rows * this.m_cols];        

      for( i=0; i<sizeX; i++) {
        for( j=0; j<sizeY; j++) {
          tr[i * sizeY + j] = c_xx[i * sizeY + j] + c_yy[i * sizeY + j];
          det[i * sizeY + j] = c_xx[i * sizeY + j] * c_yy[i * sizeY + j] 
                                                          - c_xy[i * sizeY + j] * c_xy[i * sizeY + j];            
//        lambda[i * sizeY + j] = (float)(det[i * sizeY + j]/(tr[i * sizeY + j]) + 0.00001);       
          image[i * sizeY + j] = (float)((det[i * sizeY + j]*100000)
              /((tr[i * sizeY + j]*100000) + 0.1));  
        }
      }
    }

    public float[] calcAreaSum(float[] src, 
        int sizeY, 
        int sizeX) {
      int nave, nave_half, i, j, k;
      float[] ret, a1;
      int rows_ret, cols_ret, rows_a1, cols_a1;
      float a1sum;

      nave = this.WINSZ;
      nave_half = (int)(Math.floor((nave+1)/2))-1;

      rows_ret = sizeX;
      cols_ret = sizeY;
      ret = new float[rows_ret * cols_ret];

      for(i=0; i<sizeX; i++) {
        rows_a1 = 1;
        cols_a1 = sizeY+nave;
        a1 = new float[rows_a1 * cols_a1];

        for(j=0; j<sizeY; j++) {
          a1[j+nave_half] = src[i*sizeY+j];
        }

        a1sum = 0;
        for(k=0; k<nave; k++) {
          a1sum += a1[k];
        }

        for(j=0; j<sizeY; j++) {
          ret[i*sizeY+j] = a1sum;
          a1sum += a1[j+nave] - a1[j];
        }
      }
      a1 = null;

      for(i=0; i<sizeY; i++) {
        rows_a1 = 1;
        cols_a1 = sizeX+nave;
        a1 = new float[rows_a1 * cols_a1];

        for(j=0; j<sizeX; j++) {
          a1[j+nave_half] = ret[j*sizeY+i];
        }

        a1sum = 0;
        for(k=0; k<nave; k++) {
          a1sum += a1[k];
        }

        for(j=0; j<sizeX; j++) {
          ret[j*sizeY+i] = a1sum;
          a1sum += a1[j+nave] - a1[j];
        }
      }
      a1 = null;

      return ret; 
    }
    
    public void reshape() {
      float[] out, image;
      int i, j, k;
      int r, c;

      image = this.m_image;
      r = this.m_rows;
      c = this.m_cols;

      out = new float[r * c];

      k = 0;
      for(i=0; i<c; i++) {
        for(j=0; j<r; j++) {
          out[k++] = image[j * c + i];
        }
      }
      this.m_image = out;
      this.m_rows = r * c;
      this.m_cols = 1;
      this.m_r= r;
    }

    
    public void printImage() {
      //    result validation
      for(int i=0; i<this.m_rows; i++) {
          for(int j=0; j<this.m_cols; j++) {
              System.printI((int)(this.m_image[i * this.m_cols + j]*10));
          }
      }
    }
}