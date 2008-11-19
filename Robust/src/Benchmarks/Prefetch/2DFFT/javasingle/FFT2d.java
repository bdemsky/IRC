//Title:        2-d mixed radix FFT.
//Version:
//Copyright:    Copyright (c) 1998
//Author:       Dongyan Wang
//Company:      University of Wisconsin-Milwaukee.
//Description:
//              . Use FFT1d to perform FFT2d.
//

public class FFT2d {
  //
  // Input of FFT, 2-d matrix.
  double dataRe[][], dataIm[][];

  // Width and height of 2-d matrix inputRe or inputIm.
  int width, height;

  // Constructor: 2-d FFT of Complex data.
  public FFT2d(double inputRe[], double inputIm[], int inputWidth) {
    // First make sure inputRe & inputIm are of the same length.
    if (inputRe.length != inputIm.length) {
      System.out.println("Error: the length of real part & imaginary part " +
          "of the input to 2-d FFT are different");
      return;
    } else {
      width = inputWidth;
      height = inputRe.length / width;
      dataRe = new double[height][width];
      dataIm = new double[height][width];
      //System.out.println("width = "+ width + " height = " + height + "\n");

      for (int i = 0; i < height; i++)
        for (int j = 0; j < width; j++) {
          dataRe[i][j] = inputRe[i * width + j];
          dataIm[i][j] = inputIm[i * width + j];
        }

      //System.out.println("Initially dataRe[100][8] = "+ dataRe[100][8] + "\n");
      //System.out.println("copy to Input[] inputRe[1008] = "+ inputRe[1008] + "\n");

      // Calculate FFT for each row of the data.
      FFT1d fft1 = new FFT1d(width);
      for (int i = 0; i < height; i++)
        fft1.fft(dataRe[i], dataIm[i]);

      //System.out.println("After row fft dataRe[100][8] = "+ dataRe[100][8] + "\n");
      //System.out.println("Element 100 is " + (int)inputRe[100]+ "\n");
      //System.out.println("Element 405 is " + (int)inputIm[405]+ "\n");
      // Tranpose data.
      // Calculate FFT for each column of the data.
      double temRe[][] = transpose(dataRe);
      double temIm[][] = transpose(dataIm);

      //System.out.println("before column fft dataRe[100][8] = "+ dataRe[100][8] + " temRe[8][100]= " + temRe[8][100] + "\n");
      FFT1d fft2 = new FFT1d(height);
      for (int j = 0; j < width; j++)
        fft2.fft(temRe[j], temIm[j]);
      //System.out.println("after column fft dataRe[100][8] = "+ dataRe[100][8] + " temRe[8][100]= " + temRe[8][100] + "\n");

      //System.out.println("Element 100 is " + (int)inputRe[100]+ "\n");
      //System.out.println("Element 405 is " + (int)inputIm[405]+ "\n");
      // Tranpose data.
      // Copy the result to input[], so the output can be
      // returned in the input array.
      for (int i = 0; i < height; i++)
        for (int j = 0; j < width; j++) {
          inputRe[i * width + j] = temRe[j][i];
          inputIm[i * width + j] = temIm[j][i];
        }
      //System.out.println("copy to Input[] inputRe[1008] = "+ inputRe[1008] + "\n");
    }
  }

  // Transpose matrix input.
  private double[][] transpose(double[][] input) {
    double[][] output = new double[width][height];

    for (int j = 0; j < width; j++)
      for (int i = 0; i < height; i++)
        output[j][i] = input[i][j];

    return output;
  } // End of function transpose().


  public static void main(String[] args) {
    int NUM_THREADS = 1;
    int SIZE = 800;
    int inputWidth = 10;
    if(args.length>0) {
      NUM_THREADS=Integer.parseInt(args[0]);
      if(args.length > 1)
        SIZE = Integer.parseInt(args[1]);
    }

    System.out.println("Num threads = " + NUM_THREADS + " SIZE= " + SIZE + "\n");

    // Initialize Matrix 
    // Matrix inputRe, inputIm;

    double[] inputRe;
    double[] inputIm;
    inputRe = new double[SIZE];
    inputIm = new double[SIZE];

    for(int i = 0; i<SIZE; i++){
      inputRe[i] = i;
      inputIm[i] = i;
    }

    //System.out.println("Element 231567 is " + (int)inputRe[231567]+ "\n");
    //System.out.println("Element 10 is " + (int)inputIm[10]+ "\n");
    // Start Barrier Server

    // Width and height of 2-d matrix inputRe or inputIm.
    int width, height;
    width = inputWidth;
    int Relength, Imlength;
    height = inputRe.length / width;
    Relength = inputRe.length;
    Imlength = inputIm.length;

    // Create threads to do FFT 
    FFT2d myfft2d = new FFT2d(inputRe, inputIm, inputWidth);

    System.out.println("2DFFT done! \n");
    //System.out.println("Element 23157 is " + (int)inputRe[23157]+ "\n");
    //System.out.println("Element 10 is " + (int)inputIm[10]+ "\n");
  }
}
