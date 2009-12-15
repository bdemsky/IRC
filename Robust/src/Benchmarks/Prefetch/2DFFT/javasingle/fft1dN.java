
//Title:        1-d mixed radix FFT.
//Version:
//Copyright:    Copyright (c) 1998
//Author:       Dongyan Wang
//Company:      University of Wisconsin-Milwaukee.
//Description:
//  The number of DFT is factorized.
//
// Some short FFTs, such as length 2, 3, 4, 5, 8, 10, are used
// to improve the speed.
//
// Prime factors are processed using DFT. In the future, we can
// improve this part.
// Note: there is no limit how large the prime factor can be,
// because for a set of data of an image, the length can be
// random, ie. an image can have size 263 x 300, where 263 is
// a large prime factor.
//
// A permute() function is used to make sure FFT can be calculated
// in place.
//
// A triddle() function is used to perform the FFT.
//
// This program is for FFT of complex data, if the input is real,
// the program can be further improved. Because I want to use the
// same program to do IFFT, whose input is often complex, so I
// still use this program.
//
// To save the memory and improve the speed, float data are used
// instead of float, but I do have a float version transforms.fft.
//
// Factorize() is done in constructor, transforms.fft() is needed to be
// called to do FFT, this is good for use in fft2d, then
// factorize() is not needed for each row/column of data, since
// each row/column of a matrix has the same length.
//


public class fft1d {
  // Maximum numbers of factors allowed.
  //private int MaxFactorsNumber = 30;
  public int MaxFactorsNumber;

  // cos2to3PI = cos(2*pi/3), using for 3 point FFT.
  // cos(2*PI/3) is not -1.5
  public float cos2to3PI;
  // sin2to3PI = sin(2*pi/3), using for 3 point FFT.
  public float sin2to3PI;

  // TwotoFivePI   = 2*pi/5.
  // c51, c52, c53, c54, c55 are used in fft5().
  // c51 =(cos(TwotoFivePI)+cos(2*TwotoFivePI))/2-1.
  public float c51;
  // c52 =(cos(TwotoFivePI)-cos(2*TwotoFivePI))/2.
  public float c52;
  // c53 = -sin(TwotoFivePI).
  public float c53;
  // c54 =-(sin(TwotoFivePI)+sin(2*TwotoFivePI)).
  public float c54;
  // c55 =(sin(TwotoFivePI)-sin(2*TwotoFivePI)).
  public float c55;

  // OnetoSqrt2 = 1/sqrt(2), used in fft8().
  public float OnetoSqrt2;

  public int lastRadix;

  int N;              // length of N point FFT.
  int NumofFactors;   // Number of factors of N.
  int maxFactor;      // Maximum factor of N.

  int factors[];      // Factors of N processed in the current stage.
  int sofar[];        // Finished factors before the current stage.
  int remain[];       // Finished factors after the current stage.

  float inputRe[],  inputIm[];   // Input  of FFT.
  float temRe[],    temIm[];     // Intermediate result of FFT.
  float outputRe[], outputIm[];  // Output of FFT.
  boolean factorsWerePrinted;

  // Constructor: FFT of Complex data.
  public fft1d(int N) {
    this.N = N;
    MaxFactorsNumber = 37;
    cos2to3PI = -1.5000f;
    sin2to3PI = 8.6602540378444E-01f;
    c51 = -1.25f;
    c52 = 5.5901699437495E-01f;
    c53 = -9.5105651629515E-01f;
    c54 = -1.5388417685876E+00f;
    c55 = 3.6327126400268E-01f;
    OnetoSqrt2 = 7.0710678118655E-01f;
    lastRadix = 0;
    maxFactor = 20;
    factorsWerePrinted = false;
    outputRe = new float[N];
    outputIm = new float[N];

    factorize();
    //printFactors();

    // Allocate memory for intermediate result of FFT.
    temRe = new float[maxFactor]; //Check usage of this
    temIm = new float[maxFactor];
  }

  public void printFactors() {
    if (factorsWerePrinted) return;
    factorsWerePrinted = true;
    System.printString("factors.length = " + factors.length + "\n");
    for (int i = 0; i < factors.length; i++)
      System.printString("factors[i] = " + factors[i] + "\n");
  }

  public void factorize() {
    int radices[] = new int[6];
    radices[0] = 2;
    radices[1] = 3;
    radices[2] = 4;
    radices[3] = 5;
    radices[4] = 8;
    radices[5] = 10;
    int temFactors[] = new int[MaxFactorsNumber];

    // 1 - point FFT, no need to factorize N.
    if (N == 1) {
      temFactors[0] = 1;
      NumofFactors = 1;
    }

    // N - point FFT, N is needed to be factorized.
    int n = N;
    int index = 0;    // index of temFactors.
    int i = radices.length - 1;

    while ((n > 1) && (i >= 0)) {
      if ((n % radices[i]) == 0) {
	n /= radices[i];
	temFactors[index++] = radices[i];
      } else
	i--;
    }

    // Substitute 2x8 with 4x4.
    // index>0, in the case only one prime factor, such as N=263.
    if ((index > 0) && (temFactors[index - 1] == 2)) {
      int test = 0;
      for (i = index - 2; (i >= 0) && (test == 0); i--) {
	if (temFactors[i] == 8) {
	  temFactors[index - 1] = temFactors[i] = 4;
	  // break out of for loop, because only one '2' will exist in
	  // temFactors, so only one substitutation is needed.
	  test = 1;
	}
      }
    }

    if (n > 1) {
      for (int k = 2; k < Math.sqrt(n) + 1; k++)
	while ((n % k) == 0) {
	  n /= k;
	  temFactors[index++] = k;
	}
      if (n > 1) {
	temFactors[index++] = n;
      }
    }
    NumofFactors = index;

    // Inverse temFactors and store factors into factors[].
    factors = new int[NumofFactors];
    for (i = 0; i < NumofFactors; i++) {
      factors[i] = temFactors[NumofFactors - i - 1];
    }

    // Calculate sofar[], remain[].
    // sofar[]  : finished factors before the current stage.
    // factors[]: factors of N processed in the current stage.
    // remain[] : finished factors after the current stage.

    sofar = new int[NumofFactors];
    remain = new int[NumofFactors];

    remain[0] = N / factors[0];
    sofar[0] = 1;
    for (i = 1; i < NumofFactors; i++) {
      sofar[i] = sofar[i - 1] * factors[i - 1];
      remain[i] = remain[i - 1] / factors[i];
    }
  }   // End of function factorize().
} // End of class FFT1d
