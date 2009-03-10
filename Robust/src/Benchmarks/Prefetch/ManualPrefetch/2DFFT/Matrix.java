public class Matrix {
  public int M, N; //M = column, N = row
  public double[][] dataRe;
  public double[][] dataIm;

  public Matrix(int M, int N) {
    this.M = M;
    this.N = N;
    dataRe = global new double[M][N];
    dataIm = global new double[M][N];
  }

  public void setValues() {
    for (int i = 0; i<M; i++) {
      double dataRei[] = dataRe[i];
      double dataImi[] = dataIm[i];
      for(int j = 0; j<N; j++) {
	dataRei[j] = j + 1;
	dataImi[j] = j + 1;
      }
    }
  }

  public void setZeros() {
    for (int i = 0; i<M; i++) {
      double dataRei[] = dataRe[i];
      double dataImi[] = dataIm[i];
      for(int j = 0; j<N; j++) {
	dataRei[j] = 0;
	dataImi[j] = 0;
      }
    }
  }

  //Transpose matrix input.
  private float[][] transpose(float[][] input) {
    float[][] output = new float[N][M];

    for (int j = 0; j < N; j++)
      for (int i = 0; i < M; i++)
	output[j][i] = input[i][j];

    return output;
  } // End of function transpose().
}
