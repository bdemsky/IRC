public class IDX {
    /* current processing image related */
    float[] m_image;
    int m_rows;
    int m_cols;
    int m_r;
    
    /* results related */
    int m_rows_rs;
    int m_rows_re;
    int m_cols_r;
    int[] m_ind;
    
    /* benchmark constants */
    public int N_FEA;
    
    /* id indicating the piece # */
    int m_id;  
    int m_range;

    /* constructor */
    public IDX(int nfea,
               int id,
               int range,
               float[] data,
               int rows,
               int cols,
               int r) {
      this.N_FEA = nfea;
      
      this.m_id = id;
      this.m_range = range;
      
      this.m_image = data;
      this.m_rows = rows;
      this.m_cols = cols;
      this.m_r = r;

      this.m_rows_rs = this.m_id * this.m_range;
      this.m_rows_re = (this.m_id + 1) * this.m_range;
      this.m_cols_r = cols;
      
      this.m_ind = new int[(this.m_rows_re - this.m_rows_rs) * this.m_cols_r];
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
    
    public int[] getInd() {
      return this.m_ind;
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
    
    public void fSortIndices() {
      int i, j, k, startRow, endRow;
      int[] ind;
      int rows_i, cols_i;
      float[] image;

      image = this.m_image;
      
      rows_i = this.m_rows;
      cols_i = this.m_cols;
      ind = this.m_ind;

      startRow = this.m_rows_rs;  
      endRow = this.m_rows_re;
      int ii = 0;
      for(k=0; k<cols_i; k++) {
        for(i=0; i<rows_i; i++) {
          float local = image[i * cols_i + k];
          ii = 0;
          for(j=startRow; j<endRow; j++) {
            if(local <= image[j*cols_i+k]) {
              ind[ii * cols_i + k]++;
            }
            ii++;
          }
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
    
    public void printInd() {
      //    result validation
      System.printI(44444444);
      for(int i=0; i<this.m_rows_re-this.m_rows_rs; i++) {
          for(int j=0; j<this.m_cols_r; j++) {
              System.out.println(this.m_ind[i * this.m_cols_r + j] + " " + this.m_image[i * this.m_cols_r  + j]);
          }
      }
    }
}