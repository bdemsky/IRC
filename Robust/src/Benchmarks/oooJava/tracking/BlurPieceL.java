public class BlurPieceL {
    
    /* current processing image related */
    int[] m_image;
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
    int m_pnum;

    /* constructor */
    public BlurPieceL(int id,
                      int range,
                      int[] data,
                      int pnum) {
      this.m_id = id;
      this.m_range = range;
      this.m_image = data;
      this.m_rows = data[0];
      this.m_cols = data[1];
      this.m_pnum = pnum;
    }
    
    public int getId() {
      return this.m_id;
    }
    
    public int getRows() {
      return this.m_rows;
    }
    
    public int getCols() {
      return this.m_cols;
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
    
    public void blur() {
      int rows, cols;
      float temp;
      int[] kernel, imageIn;
      int rows_k, cols_k;
      int k, i, j;
      int kernelSize, startCol, endCol, halfKernel, startRow, endRow, kernelSum;
      int[] inputs;
      float[] image;

      inputs = this.m_image;
      rows = this.m_rows;
      cols = this.m_cols;
      this.m_rows_rs = this.m_id * this.m_range;
      this.m_rows_re = (this.m_id + 1) * this.m_range;
      if(rows < this.m_rows_re) {
        this.m_rows_re = rows;
      }
      if(this.m_id == this.m_pnum - 1) {
        this.m_rows_re = rows;
      }
      this.m_cols_r = this.m_cols;
      image = this.m_result = new float[(this.m_rows_re-this.m_rows_rs)*cols];

      kernel = new int[5];
      rows_k = 1;
      cols_k = 5;

      kernel[0] = 1;
      kernel[1] = 4;
      kernel[2] = 6;
      kernel[3] = 4;
      kernel[4] = 1;

      kernelSize = 5;
      kernelSum = 16;

      startCol = 2;       //((kernelSize)/2);
      endCol = cols - 2;  //round(cols - (kernelSize/2));
      halfKernel = 2;     //(kernelSize-1)/2;

      if((this.m_rows_re <= 2) || (this.m_rows_rs >= rows - 2)) {
        return;
      }
      startRow = (2>this.m_rows_rs)?2:(this.m_rows_rs);       //(kernelSize)/2;
      endRow = ((rows-2)<this.m_rows_re)?(rows-2):(this.m_rows_re);  //(rows - (kernelSize)/2);
      
      int ii = startRow - this.m_rows_rs;
      for(i=startRow; i<endRow; i++){
        for(j=startCol; j<endCol; j++) {
          temp = 0;
          for(k=-halfKernel; k<=halfKernel; k++) {
            temp += (float)((inputs[4 + i * cols + (j+k)] 
                                    * (kernel[k+halfKernel])));
          }

          image[ii * cols + j] = (float)(temp/kernelSum);
        }
        ii++;
      }

      /*ii = startRow - this.m_rows_rs;
      for(i=startRow; i<endRow; i++) {
        for(j=startCol; j<endCol; j++) {
          temp = 0;
          for(k=-halfKernel; k<=halfKernel; k++)  {
            temp += (float)((image[(ii+k) * cols + j] 
                                   * (float)(kernel[k+halfKernel])));
          }
          image[ii * cols + j] = (float)(temp/kernelSum);
        }
        ii++;
      }*/
    }
    
    public void printImage() {
      //    result validation
      System.printI(33333333);
      for(int i=0; i<this.m_rows; i++) {
          for(int j=0; j<this.m_cols; j++) {
              System.printI(this.m_image[i * this.m_cols + j + 4]);
          }
      }
    }
}