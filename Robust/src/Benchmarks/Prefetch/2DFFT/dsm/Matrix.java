public class Matrix {
  public int M, N; //M = height, N = width
  public double[][] dataRe;
  public double[][] dataIm;

  public Matrix(int M, int N) {
    this.M = M;
    this.N = N;
    dataRe = global new double[M][N];
    dataIm = global new double[M][N];
  }

  public void setValues(double[] inputRe, double[] inputIm) {
    for (int i = 0; i<M; i++) {
      double dataRei[] = dataRe[i];
      double dataImi[] = dataIm[i];
      for(int j = 0; j<N; j++) {
        dataRei[j] = inputRe[i * N +j];
        dataImi[j] = inputIm[i * N +j];
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
