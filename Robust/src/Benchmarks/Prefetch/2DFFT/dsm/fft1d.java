 
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
// instead of double, but I do have a double version transforms.fft.
//
// Factorize() is done in constructor, transforms.fft() is needed to be
// called to do FFT, this is good for use in fft2d, then
// factorize() is not needed for each row/column of data, since
// each row/column of a matrix has the same length.
//


public class fft1d{
  // Maximum numbers of factors allowed.
  //private int MaxFactorsNumber = 30;
  public int MaxFactorsNumber;

  // cos2to3PI = cos(2*pi/3), using for 3 point FFT.
  // cos(2*PI/3) is not -1.5
  public double cos2to3PI;
  // sin2to3PI = sin(2*pi/3), using for 3 point FFT.
  public double sin2to3PI;

  // TwotoFivePI   = 2*pi/5.
  // c51, c52, c53, c54, c55 are used in fft5().
  // c51 =(cos(TwotoFivePI)+cos(2*TwotoFivePI))/2-1.
  public double c51;
  // c52 =(cos(TwotoFivePI)-cos(2*TwotoFivePI))/2.
  public double c52;
  // c53 = -sin(TwotoFivePI).
  public double c53;
  // c54 =-(sin(TwotoFivePI)+sin(2*TwotoFivePI)).
  public double c54;
  // c55 =(sin(TwotoFivePI)-sin(2*TwotoFivePI)).
  public double c55;

  // OnetoSqrt2 = 1/sqrt(2), used in fft8().
  public double OnetoSqrt2;

  public int lastRadix;

  int N;              // length of N point FFT.
  int NumofFactors;   // Number of factors of N.
  int maxFactor;      // Maximum factor of N.

  int factors[];      // Factors of N processed in the current stage.
  int sofar[];        // Finished factors before the current stage.
  int remain[];       // Finished factors after the current stage.

  double inputRe[],  inputIm[];   // Input  of FFT.
  double temRe[],    temIm[];     // Intermediate result of FFT.
  double outputRe[], outputIm[];  // Output of FFT.
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
    outputRe = global new double[N];
    outputIm = global new double[N];

    factorize();
    printFactors();

    // Allocate memory for intermediate result of FFT.
    temRe = global new double[maxFactor]; //Check usage of this
    temIm = global new double[maxFactor];
  }

  /*
  public void fft(double inputRe[], double inputIm[]) {
    // First make sure inputRe & inputIm are of the same length.
    if (inputRe.length != N || inputIm.length != N) {
      System.printString("Error: the length of real part & imaginary part " +
                         "of the input to 1-d FFT are different");
      return;
    } else {
      this.inputRe = inputRe;
      this.inputIm = inputIm;

      permute();
      //System.printString("ready to twiddle");

      for (int factorIndex = 0; factorIndex < NumofFactors; factorIndex++)
        twiddle(factorIndex);
      //System.printString("ready to copy");

      // Copy the output[] data to input[], so the output can be
      // returned in the input array.
      for (int i = 0; i < N; i++) {
        inputRe[i] = outputRe[i];
        inputIm[i] = outputIm[i];
      }
    }
  }
  */

  public void printFactors() {
    if (factorsWerePrinted) return;
    factorsWerePrinted = true;
    for (int i = 0; i < factors.length; i++)
      System.printString("factors[i] = " + factors[i]);
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
          //break;
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
    //if(temFactors[NumofFactors-1] > 10)
    //   maxFactor = n;
    //else
    //   maxFactor = 10;

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
/*
  private void permute() {
    int count[] = new int[MaxFactorsNumber];
    int j;
    int k = 0;

    for (int i = 0; i < N - 1; i++) {
      outputRe[i] = inputRe[k];
      outputIm[i] = inputIm[k];
      j = 0;
      k = k + remain[j];
      count[0] = count[0] + 1;
      while (count[j] >= factors[j]) {
        count[j] = 0;
        k = k - (j == 0?N:remain[j - 1]) + remain[j + 1];
        j++;
        count[j] = count[j] + 1;
      }
    }
    outputRe[N - 1] = inputRe[N - 1];
    outputIm[N - 1] = inputIm[N - 1];
  }   // End of function permute().
  */
/*
  private void twiddle(int factorIndex) {
    // Get factor data.
    int sofarRadix = sofar[factorIndex];
    int radix = factors[factorIndex];
    int remainRadix = remain[factorIndex];

    double tem;   // Temporary variable to do data exchange.

    double W = 2 * (double) Math.PI / (sofarRadix * radix);
    double cosW = (double) Math.cos(W);
    double sinW = -(double) Math.sin(W);

    double twiddleRe[] = new double[radix];
    double twiddleIm[] = new double[radix];
    double twRe = 1.0f, twIm = 0f;

    //Initialize twiddle addBk.address variables.
    int dataOffset = 0, groupOffset = 0, address = 0;

    for (int dataNo = 0; dataNo < sofarRadix; dataNo++) {
      //System.printString("datano="+dataNo);
      if (sofarRadix > 1) {
        twiddleRe[0] = 1.0f;
        twiddleIm[0] = 0.0f;
        twiddleRe[1] = twRe;
        twiddleIm[1] = twIm;
        for (int i = 2; i < radix; i++) {


          twiddleRe[i] = twRe * twiddleRe[i - 1] - twIm * twiddleIm[i - 1];
          twiddleIm[i] = twIm * twiddleRe[i - 1] + twRe * twiddleIm[i - 1];
        }
        tem = cosW * twRe - sinW * twIm;
        twIm = sinW * twRe + cosW * twIm;
        twRe = tem;
      }
      for (int groupNo = 0; groupNo < remainRadix; groupNo++) {
        //System.printString("groupNo="+groupNo);
        if ((sofarRadix > 1) && (dataNo > 0)) {
          temRe[0] = outputRe[address];
          temIm[0] = outputIm[address];
          int blockIndex = 1;
          do {
            address = address + sofarRadix;
            temRe[blockIndex] = twiddleRe[blockIndex] * outputRe[address] -
                twiddleIm[blockIndex] * outputIm[address];
            temIm[blockIndex] = twiddleRe[blockIndex] * outputIm[address] +
                twiddleIm[blockIndex] * outputRe[address];
            blockIndex++;
          } while (blockIndex < radix);
        } else
          for (int i = 0; i < radix; i++) {
            //System.printString("temRe.length="+temRe.length);
            //System.printString("i = "+i);
            temRe[i] = outputRe[address];
            temIm[i] = outputIm[address];
            address += sofarRadix;
          }
        //System.printString("radix="+radix);
        if(radix == 2) {
          case 2:
            tem = temRe[0] + temRe[1];
            temRe[1] = temRe[0] - temRe[1];
            temRe[0] = tem;
            tem = temIm[0] + temIm[1];
            temIm[1] = temIm[0] - temIm[1];
            temIm[0] = tem;
            break;
          case 3:
            double t1Re = temRe[1] + temRe[2];
            double t1Im = temIm[1] + temIm[2];
            temRe[0] = temRe[0] + t1Re;
            temIm[0] = temIm[0] + t1Im;

            double m1Re = cos2to3PI * t1Re;
            double m1Im = cos2to3PI * t1Im;
            double m2Re = sin2to3PI * (temIm[1] - temIm[2]);
            double m2Im = sin2to3PI * (temRe[2] - temRe[1]);
            double s1Re = temRe[0] + m1Re;
            double s1Im = temIm[0] + m1Im;

            temRe[1] = s1Re + m2Re;
            temIm[1] = s1Im + m2Im;
            temRe[2] = s1Re - m2Re;
            temIm[2] = s1Im - m2Im;
            break;
          case 4:
            fft4(temRe, temIm);
            break;
          case 5:
            fft5(temRe, temIm);
            break;
          case 8:
            fft8();
            break;
          case 10:
            fft10();
            break;
          default  :
            fftPrime(radix);
            break;
        }
        address = groupOffset;
        for (int i = 0; i < radix; i++) {
          outputRe[address] = temRe[i];
          outputIm[address] = temIm[i];
          address += sofarRadix;
        }
        groupOffset += sofarRadix * radix;
        address = groupOffset;
      }
      groupOffset = ++dataOffset;
      address = groupOffset;
    }
  } // End of function twiddle().
  */
/*
  // The two arguments dataRe[], dataIm[] are mainly for using in fft8();
  private void fft4(double dataRe[], double dataIm[]) {
    double t1Re,t1Im, t2Re,t2Im;
    double m2Re,m2Im, m3Re,m3Im;

    t1Re = dataRe[0] + dataRe[2];
    t1Im = dataIm[0] + dataIm[2];
    t2Re = dataRe[1] + dataRe[3];
    t2Im = dataIm[1] + dataIm[3];

    m2Re = dataRe[0] - dataRe[2];
    m2Im = dataIm[0] - dataIm[2];
    m3Re = dataIm[1] - dataIm[3];
    m3Im = dataRe[3] - dataRe[1];

    dataRe[0] = t1Re + t2Re;
    dataIm[0] = t1Im + t2Im;
    dataRe[2] = t1Re - t2Re;
    dataIm[2] = t1Im - t2Im;
    dataRe[1] = m2Re + m3Re;
    dataIm[1] = m2Im + m3Im;
    dataRe[3] = m2Re - m3Re;
    dataIm[3] = m2Im - m3Im;
  }   // End of function fft4().
  */
/*
  // The two arguments dataRe[], dataIm[] are mainly for using in fft10();
  private void fft5(double dataRe[], double dataIm[]) {
    double t1Re,t1Im, t2Re,t2Im, t3Re,t3Im, t4Re,t4Im, t5Re,t5Im;
    double m1Re,m1Im, m2Re,m2Im, m3Re,m3Im, m4Re,m4Im, m5Re,m5Im;
    double s1Re,s1Im, s2Re,s2Im, s3Re,s3Im, s4Re,s4Im, s5Re,s5Im;

    t1Re = dataRe[1] + dataRe[4];
    t1Im = dataIm[1] + dataIm[4];
    t2Re = dataRe[2] + dataRe[3];
    t2Im = dataIm[2] + dataIm[3];
    t3Re = dataRe[1] - dataRe[4];
    t3Im = dataIm[1] - dataIm[4];
    t4Re = dataRe[3] - dataRe[2];
    t4Im = dataIm[3] - dataIm[2];
    t5Re = t1Re + t2Re;
    t5Im = t1Im + t2Im;

    dataRe[0] = dataRe[0] + t5Re;
    dataIm[0] = dataIm[0] + t5Im;

    m1Re = c51 * t5Re;
    m1Im = c51 * t5Im;
    m2Re = c52 * (t1Re - t2Re);
    m2Im = c52 * (t1Im - t2Im);
    m3Re = -c53 * (t3Im + t4Im);
    m3Im = c53 * (t3Re + t4Re);
    m4Re = -c54 * t4Im;
    m4Im = c54 * t4Re;
    m5Re = -c55 * t3Im;
    m5Im = c55 * t3Re;

    s3Re = m3Re - m4Re;
    s3Im = m3Im - m4Im;
    s5Re = m3Re + m5Re;
    s5Im = m3Im + m5Im;
    s1Re = dataRe[0] + m1Re;
    s1Im = dataIm[0] + m1Im;
    s2Re = s1Re + m2Re;
    s2Im = s1Im + m2Im;
    s4Re = s1Re - m2Re;
    s4Im = s1Im - m2Im;

    dataRe[1] = s2Re + s3Re;
    dataIm[1] = s2Im + s3Im;
    dataRe[2] = s4Re + s5Re;
    dataIm[2] = s4Im + s5Im;
    dataRe[3] = s4Re - s5Re;
    dataIm[3] = s4Im - s5Im;
    dataRe[4] = s2Re - s3Re;
    dataIm[4] = s2Im - s3Im;
  }   // End of function fft5().
  */

  /*
  private void fft8() {
    double data1Re[] = new double[4];
    double data1Im[] = new double[4];
    double data2Re[] = new double[4];
    double data2Im[] = new double[4];
    double tem;

    // To improve the speed, use direct assaignment instead for loop here.
    data1Re[0] = temRe[0];
    data2Re[0] = temRe[1];
    data1Re[1] = temRe[2];
    data2Re[1] = temRe[3];
    data1Re[2] = temRe[4];
    data2Re[2] = temRe[5];
    data1Re[3] = temRe[6];
    data2Re[3] = temRe[7];

    data1Im[0] = temIm[0];
    data2Im[0] = temIm[1];
    data1Im[1] = temIm[2];
    data2Im[1] = temIm[3];
    data1Im[2] = temIm[4];
    data2Im[2] = temIm[5];
    data1Im[3] = temIm[6];
    data2Im[3] = temIm[7];

    fft4(data1Re, data1Im);
    fft4(data2Re, data2Im);

    tem = OnetoSqrt2 * (data2Re[1] + data2Im[1]);
    data2Im[1] = OnetoSqrt2 * (data2Im[1] - data2Re[1]);
    data2Re[1] = tem;
    tem = data2Im[2];
    data2Im[2] = -data2Re[2];
    data2Re[2] = tem;
    tem = OnetoSqrt2 * (data2Im[3] - data2Re[3]);
    data2Im[3] = -OnetoSqrt2 * (data2Re[3] + data2Im[3]);
    data2Re[3] = tem;

    temRe[0] = data1Re[0] + data2Re[0];
    temRe[4] = data1Re[0] - data2Re[0];
    temRe[1] = data1Re[1] + data2Re[1];
    temRe[5] = data1Re[1] - data2Re[1];
    temRe[2] = data1Re[2] + data2Re[2];
    temRe[6] = data1Re[2] - data2Re[2];
    temRe[3] = data1Re[3] + data2Re[3];
    temRe[7] = data1Re[3] - data2Re[3];

    temIm[0] = data1Im[0] + data2Im[0];
    temIm[4] = data1Im[0] - data2Im[0];
    temIm[1] = data1Im[1] + data2Im[1];
    temIm[5] = data1Im[1] - data2Im[1];
    temIm[2] = data1Im[2] + data2Im[2];
    temIm[6] = data1Im[2] - data2Im[2];
    temIm[3] = data1Im[3] + data2Im[3];
    temIm[7] = data1Im[3] - data2Im[3];
  }   // End of function fft8().
  */

  /*
  private void fft10() {
    double data1Re[] = new double[5];
    double data1Im[] = new double[5];
    double data2Re[] = new double[5];
    double data2Im[] = new double[5];

    // To improve the speed, use direct assaignment instead for loop here.
    data1Re[0] = temRe[0];
    data2Re[0] = temRe[5];
    data1Re[1] = temRe[2];
    data2Re[1] = temRe[7];
    data1Re[2] = temRe[4];
    data2Re[2] = temRe[9];
    data1Re[3] = temRe[6];
    data2Re[3] = temRe[1];
    data1Re[4] = temRe[8];
    data2Re[4] = temRe[3];

    data1Im[0] = temIm[0];
    data2Im[0] = temIm[5];
    data1Im[1] = temIm[2];
    data2Im[1] = temIm[7];
    data1Im[2] = temIm[4];
    data2Im[2] = temIm[9];
    data1Im[3] = temIm[6];
    data2Im[3] = temIm[1];
    data1Im[4] = temIm[8];
    data2Im[4] = temIm[3];

    fft5(data1Re, data1Im);
    fft5(data2Re, data2Im);

    temRe[0] = data1Re[0] + data2Re[0];
    temRe[5] = data1Re[0] - data2Re[0];
    temRe[6] = data1Re[1] + data2Re[1];
    temRe[1] = data1Re[1] - data2Re[1];
    temRe[2] = data1Re[2] + data2Re[2];
    temRe[7] = data1Re[2] - data2Re[2];
    temRe[8] = data1Re[3] + data2Re[3];
    temRe[3] = data1Re[3] - data2Re[3];
    temRe[4] = data1Re[4] + data2Re[4];
    temRe[9] = data1Re[4] - data2Re[4];

    temIm[0] = data1Im[0] + data2Im[0];
    temIm[5] = data1Im[0] - data2Im[0];
    temIm[6] = data1Im[1] + data2Im[1];
    temIm[1] = data1Im[1] - data2Im[1];
    temIm[2] = data1Im[2] + data2Im[2];
    temIm[7] = data1Im[2] - data2Im[2];
    temIm[8] = data1Im[3] + data2Im[3];
    temIm[3] = data1Im[3] - data2Im[3];
    temIm[4] = data1Im[4] + data2Im[4];
    temIm[9] = data1Im[4] - data2Im[4];
  }   // End of function fft10().
  */

    /*
  public double sqrt(double d) {
    return Math.sqrt(d);
  }
  */

    /*
  private void fftPrime(int radix) {
    // Initial WRe, WIm.
    double W = 2 * (double) Math.PI / radix;
    double cosW = (double) Math.cos(W);
    double sinW = -(double) Math.sin(W);
    double WRe[] = new double[radix];
    double WIm[] = new double[radix];

    WRe[0] = 1;
    WIm[0] = 0;
    WRe[1] = cosW;
    WIm[1] = sinW;

    for (int i = 2; i < radix; i++) {
      WRe[i] = cosW * WRe[i - 1] - sinW * WIm[i - 1];
      WIm[i] = sinW * WRe[i - 1] + cosW * WIm[i - 1];
    }

    // FFT of prime length data, using DFT, can be improved in the future.
    double rere, reim, imre, imim;
    int j, k;
    int max = (radix + 1) / 2;

    double tem1Re[] = new double[max];
    double tem1Im[] = new double[max];
    double tem2Re[] = new double[max];
    double tem2Im[] = new double[max];

    for (j = 1; j < max; j++) {
      tem1Re[j] = temRe[j] + temRe[radix - j];
      tem1Im[j] = temIm[j] - temIm[radix - j];
      tem2Re[j] = temRe[j] - temRe[radix - j];
      tem2Im[j] = temIm[j] + temIm[radix - j];
    }

    for (j = 1; j < max; j++) {
      temRe[j] = temRe[0];
      temIm[j] = temIm[0];
      temRe[radix - j] = temRe[0];
      temIm[radix - j] = temIm[0];
      k = j;
      for (int i = 1; i < max; i++) {
        rere = WRe[k] * tem1Re[i];
        imim = WIm[k] * tem1Im[i];
        reim = WRe[k] * tem2Im[i];
        imre = WIm[k] * tem2Re[i];

        temRe[radix - j] += rere + imim;
        temIm[radix - j] += reim - imre;
        temRe[j] += rere - imim;
        temIm[j] += reim + imre;

        k = k + j;
        if (k >= radix)
          k = k - radix;
      }
    }
    for (j = 1; j < max; j++) {
      temRe[0] = temRe[0] + tem1Re[j];
      temIm[0] = temIm[0] + tem2Im[j];
    }
  }   // End of function fftPrime().
  */

} // End of class FFT2d 
