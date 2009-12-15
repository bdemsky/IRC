public class Matrix {
  public int M, N; //M = column, N = row
  public float[][][] dataRe;
  public float[][][] dataIm;
  public int numMatrix;

  public Matrix(int M, int N, int numMatrix) {
    this.M = M;
    this.N = N;
    this.numMatrix = numMatrix;
    dataRe = new float[numMatrix][M][N];
    dataIm = new float[numMatrix][M][N];
  }

  public void setValues() {
    for(int z=0; z<numMatrix; z++) {
      for(int i = 0; i<M; i++) {
        float dataRei[] = new float[N];
        float dataImi[] = new float[N];
        for(int j = 0; j<N; j++) {
          dataRei[j] = j + 1;
          dataImi[j] = j + 1;
        }
        dataRe[z][i] = dataRei;
        dataIm[z][i] = dataImi;
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
