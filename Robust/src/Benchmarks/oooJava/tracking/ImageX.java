public class ImageX {
    
    /* current processing image related */
    float[] m_image;
    int m_rows;
    int m_cols;
    
    /* results related */
    float[] m_result;
    int m_rows_rs;
    int m_rows_re;
    int m_cols_r;
    
    /* id indicating the piece # */
    int m_id;  
    int m_range;
    
    /* constructor */
    public ImageX(int id,
                  int range,
                  float[] data,
                  int rows,
                  int cols) {
      this.m_id = id;
      this.m_range = range;
      this.m_image = data;
      this.m_rows = rows;
      this.m_cols = cols;
    }
    
    public int getId() {
      return this.m_id;
    }
    
    public float[] getResult() {
      return this.m_result;
    }
    
    public int getRowsRS() {
      return this.m_rows_rs;
    }
    
    public int getRowsRE() {
      return this.m_rows_re;
    }
    
    public int getColsR() {
      return this.m_cols_r;
    }
    
    public int getRows() {
      return this.m_rows;
    }
    
    public int getCols() {
      return this.m_cols;
    }
    
    public void calcSobel_dX() {
      int rows_k1, cols_k1, rows_k2, cols_k2;
      int[] kernel_1, kernel_2;
      float temp;
      int kernelSize, startCol, endCol, halfKernel, startRow, endRow;
      int k, i, j, kernelSum_1, kernelSum_2;
      float[] result, image;
      int rows = this.m_rows;
      int cols = this.m_cols;

      image = this.m_image;
      
      this.m_rows_rs = this.m_id * this.m_range;
      this.m_rows_re = (this.m_id + 1) * this.m_range;
      this.m_cols_r = cols;
      result=this.m_result=new float[(this.m_rows_re-this.m_rows_rs)*this.m_cols_r];
      
      rows_k1 = 1;
      cols_k1 = 3;
      kernel_1 = new int[rows_k1 * cols_k1];
      rows_k2 = 1;
      cols_k2 = 3;
      kernel_2 = new int[rows_k2 * cols_k2];   

      kernel_1[0] = 1;
      kernel_1[1] = 2;
      kernel_1[2] = 1;

      kernelSize = 3;
      kernelSum_1 = 4;
      
      kernel_2[0] = 1;
      kernel_2[1] = 0;
      kernel_2[2] = -1;

      kernelSum_2 = 2;

      startCol = 1;           //((kernelSize)/2);
      endCol = cols - 1;      //(int)(cols - (kernelSize/2));
      halfKernel = 1;         //(kernelSize-1)/2;

      if((this.m_rows_re < 1) || (this.m_rows_rs > rows - 1)) {
        return;
      }
      startRow = (1>this.m_rows_rs)?1:(this.m_rows_rs);       //(kernelSize)/2;
      endRow = ((rows-1)<this.m_rows_re)?(rows-1):(this.m_rows_re);  //(rows - (kernelSize)/2);

      int ii = startRow - this.m_rows_rs;
      for(i=startRow; i<endRow; i++) {
          for(j=startCol; j<endCol; j++) {
              temp = 0;
              for(k=-halfKernel; k<=halfKernel; k++) {
                  temp += (float)(image[i * cols + (j+k)] 
                                        * (float)(kernel_2[k+halfKernel]));
              }
              result[ii * cols + j] = (float)(temp/kernelSum_2);
          }
          ii++;
      }
    }
    
    public void printResult() {
      //    result validation
      System.printI(11111111);
      for(int i=0; i<this.m_rows_re-this.m_rows_rs; i++) {
          for(int j=0; j<this.m_cols_r; j++) {
              System.printI((int)(this.m_result[i * this.m_cols_r + j]*10));
          }
      }
    }
}