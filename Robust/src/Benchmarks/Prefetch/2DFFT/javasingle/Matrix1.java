public class Matrix {
  public int M, N; //M = column, N = row
  public double[][] dataRe;
  public double[][] dataIm;
  public double[][] dataRetrans;
  public double[][] dataImtrans;

  public Matrix(int M, int N) {
    this.M = M;
    this.N = N;
    dataRe = new double[M][N];
    dataIm = new double[M][N];
    dataRetrans = new double[N][M];
    dataImtrans = new double[N][M];
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

    for (int i = 0; i<N; i++) {
      double dataRei[] = dataRetrans[i];
      double dataImi[] = dataImtrans[i];
      for(int j = 0; j<M; j++) {
        dataRei[j] = 0;
        dataImi[j] = 0;
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
