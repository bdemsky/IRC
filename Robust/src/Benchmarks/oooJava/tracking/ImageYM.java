public class ImageYM {
    
    /* current processing image related */
    float[] m_image;
    int m_rows;
    int m_cols;

    int m_counter;
  
    /* constructor */
    public ImageYM(int counter,
                   int rows,
                   int cols) {
      this.m_counter = counter;
      this.m_rows = rows;
      this.m_cols = cols;
      this.m_image = new float[rows * cols];
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

    public boolean addCalcSobelResult(ImageY imy) {
      int startRow = imy.getRowsRS();
      int endRow = imy.getRowsRE();
      int i, j, k, cols;
      float[] image, r;
      
      image = this.m_image;
      this.m_counter--;
      cols = this.m_cols;
      
      // clone data piece      
      r = imy.getResult();
      k = 0;
      for(i = startRow; i < endRow; i++) {
        for(j = 0; j < cols; j++) {
          image[i * cols + j] = r[k * cols + j];
        }
        k++;
      }
      
      return (0 == this.m_counter);
    }
    
    public void calcSobel_dY() {
      int rows_k1, cols_k1, rows_k2, cols_k2;
      int[] kernel_1, kernel_2;
      float temp;
      int kernelSize, startCol, endCol, halfKernel, startRow, endRow;
      int k, i, j, kernelSum_1, kernelSum_2;
      float[] result, image;
      int rows = this.m_rows;
      int cols = this.m_cols;
      
      // level 1 is the base image.
      
      image = this.m_image;
      
      rows_k1 = 1;
      cols_k1 = 3;
      kernel_1 = new int[rows_k1 * cols_k1];
      rows_k2 = 1;
      cols_k2 = 3;
      kernel_2 = new int[rows_k2 * cols_k2];

      kernel_1[0] = 1;
      kernel_1[1] = 0;
      kernel_1[2] = -1;

      kernelSize = 3;
      kernelSum_1 = 2;
      
      kernel_2[0] = 1;
      kernel_2[1] = 2;
      kernel_2[2] = 1;

      kernelSum_2 = 4;

      startCol = 1;       //(kernelSize/2);
      endCol = cols - 1;  //(int)(cols - (kernelSize/2));
      halfKernel = 1;     //(kernelSize-1)/2;

      startRow = 1;       //(kernelSize)/2;
      endRow = (rows-1);  //(rows - (kernelSize)/2);

      for(i=startRow; i<endRow; i++) {
          for(j=startCol; j<endCol; j++) {
              temp = 0;
              for(k=-halfKernel; k<=halfKernel; k++) {
                  temp += (float)(image[(i+k) * cols + j] 
                                         * (float)(kernel_1[k+halfKernel]));
              }
              image[i * cols + j] = (float)(temp/kernelSum_1);
              image[i * cols + j] = (float)(image[i * cols + j] + 128);
          }
      }
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