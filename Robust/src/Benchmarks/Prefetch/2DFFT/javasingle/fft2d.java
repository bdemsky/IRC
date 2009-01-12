public class fft2d {
  //Title:        2-d mixed radix FFT.
  //Version:
  //Copyright:    Copyright (c) 1998
  //Author:       Dongyan Wang
  //Company:      University of Wisconsin-Milwaukee.
  //Description:
  //              . Use fft1d to perform fft2d.
  //
  // Code borrowed from :Java Digital Signal Processing book by Lyon and Rao

  public Matrix data1, data2;
  public int x0, x1;

  // Constructor: 2-d FFT of Complex data.
  public fft2d(Matrix data1, Matrix data2, int x0, int x1) {
    this.data1 = data1;
    this.data2 = data2;
    this.x0 = x0;
    this.x1 = x1;
  }

  public void run() {
    fft1d fft1, fft2;
    double tempdataRe[][];
    double tempdataIm[][];
    int rowlength, columnlength;
    int start, end;

    // Calculate FFT for each row of the data.
    rowlength = data1.M;
    columnlength = data1.N;
    tempdataRe = data1.dataRe;
    tempdataIm = data1.dataIm;
    start = x0;
    end = x1;
    fft1 = new fft1d(columnlength);
    fft2 = new fft1d(rowlength);
    for (int i = x0; i < x1; i++) {
      //input of FFT
      double inputRe[] = tempdataRe[i]; //local array
      double inputIm[] = tempdataIm[i];
      fft(fft1, inputRe, inputIm);
    } //end of for

    // Tranpose data.
    if (start == 0) {
      transpose(tempdataRe,tempdataIm, data2.dataRe,data2.dataIm, rowlength, columnlength);
    }

    // Calculate FFT for each column of the data.
    double transtempRe[][];
    double transtempIm[][];
    transtempRe = data2.dataRe;
    transtempIm = data2.dataIm;
    for (int j = start; j < end; j++) {
      //input of FFT
      double inputRe[] = transtempRe[j]; //local array
      double inputIm[] = transtempIm[j];
      fft(fft2, inputRe, inputIm);
    } //end of fft2 for
  } //end of run

  public void transpose(double[][] tempdataRe, double[][] tempdataIm, double[][] outputRe, 
      double[][] outputIm, int rowlength, int columnlength) {
    for(int i = 0; i<rowlength; i++) {
      double tRe[] = tempdataRe[i];
      double tIm[] = tempdataIm[i];
      for(int j = 0; j<columnlength; j++) {
        outputRe[j][i] = tRe[j];
        outputIm[j][i] = tIm[j];
      }
    }
  }

  public static void main(String[] args) {
    int NUM_THREADS = 1;
    int SIZE = 800;
    int inputWidth = 10;
    if(args.length>0) {
      NUM_THREADS=Integer.parseInt(args[0]);
      if(args.length > 1)
        SIZE = Integer.parseInt(args[1]);
    }

    System.printString("Num threads = " + NUM_THREADS + " SIZE= " + SIZE + "\n");

    Matrix data1;
    Matrix data2;

    // Create threads to do FFT
    fft2d[] myfft2d;
    // Set up data for FFT transform
    data1 = new Matrix(SIZE, SIZE);
    data2 = new Matrix(SIZE, SIZE);
    data1.setValues(); //Input Matrix
    data2.setZeros(); //Transpose Matrix
    myfft2d = new fft2d[NUM_THREADS];
    int increment = SIZE/NUM_THREADS;
    int base = 0;
    for(int i =0 ; i<NUM_THREADS; i++) {
      if((i+1)==NUM_THREADS)
        myfft2d[i] = new fft2d(data1, data2, base, SIZE);
      else
        myfft2d[i] = new fft2d(data1, data2, base, base+increment);
      base+=increment;
    }

    fft2d tmp;
    //Start a thread to compute each c[l,n]
    for(int i = 0; i<NUM_THREADS; i++) {
      tmp = myfft2d[i];
      tmp.run();
    }

    System.printString("2DFFT done! \n");
  }

  public static void fft(fft1d myfft, double inputRe[], double inputIm[]) {
    //output of FFT
    double outputRe[] = myfft.outputRe;
    double outputIm[] = myfft.outputIm;
    // intermediate results
    double temRe[] = myfft.temRe;
    double temIm[] = myfft.temIm;
    //Permute() operation
    permute(myfft, outputRe, outputIm, inputRe, inputIm);

    //System.printString("ready to twiddle");
    for (int factorIndex = 0; factorIndex < myfft.NumofFactors; factorIndex++)
      twiddle(factorIndex, myfft, temRe, temIm, outputRe, outputIm);

    //System.printString("ready to copy");
    // Copy the output[] data to input[], so the output can be
    // returned in the input array.
    for (int i = 0; i < myfft.N; i++) {
      inputRe[i] = outputRe[i];
      inputIm[i] = outputIm[i];
    }
  }

  private static void permute(fft1d myfft, double[] outputRe, double[] outputIm, double[] inputRe, double[] inputIm) {
    int count[] = new int[myfft.MaxFactorsNumber];
    int j;
    int k = 0;

    for (int i = 0; i < myfft.N - 1; i++) {
      outputRe[i] = inputRe[k];
      outputIm[i] = inputIm[k];
      j = 0;
      k = k + myfft.remain[j];
      count[0] = count[0] + 1;
      while (count[j] >= myfft.factors[j]) {
        count[j] = 0;
        int tmp;
        if(j == 0)
          tmp = myfft.N;
        else
          tmp = myfft.remain[j - 1];
        k = k - tmp + myfft.remain[j + 1];
        j++;
        count[j] = count[j] + 1;
      }
    }
    outputRe[myfft.N - 1] = inputRe[myfft.N - 1];
    outputIm[myfft.N - 1] = inputIm[myfft.N - 1];
  }   // End of function permute().

  private static void twiddle(int factorIndex, fft1d myfft, double[] temRe, double[] temIm,
      double[] outputRe, double[] outputIm) {
    // Get factor data.
    int sofarRadix = myfft.sofar[factorIndex];
    int radix = myfft.factors[factorIndex];
    int remainRadix = myfft.remain[factorIndex];

    double tem;   // Temporary variable to do data exchange.

    double W = 2 * (double) Math.setPI() / (sofarRadix * radix);
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
        } else {
          for (int i = 0; i < radix; i++) {
            //System.printString("temRe.length="+temRe.length);
            //System.printString("i = "+i);
            temRe[i] = outputRe[address];
            temIm[i] = outputIm[address];
            address += sofarRadix;
          }
        }
        //System.printString("radix="+radix);
        if(radix == 2) {
          tem = temRe[0] + temRe[1];
          temRe[1] = temRe[0] - temRe[1];
          temRe[0] = tem;
          tem = temIm[0] + temIm[1];
          temIm[1] = temIm[0] - temIm[1];
          temIm[0] = tem;
        } else if( radix == 3) {
          double t1Re = temRe[1] + temRe[2];
          double t1Im = temIm[1] + temIm[2];
          temRe[0] = temRe[0] + t1Re;
          temIm[0] = temIm[0] + t1Im;

          double m1Re = myfft.cos2to3PI * t1Re;
          double m1Im = myfft.cos2to3PI * t1Im;
          double m2Re = myfft.sin2to3PI * (temIm[1] - temIm[2]);
          double m2Im = myfft.sin2to3PI * (temRe[2] - temRe[1]);
          double s1Re = temRe[0] + m1Re;
          double s1Im = temIm[0] + m1Im;

          temRe[1] = s1Re + m2Re;
          temIm[1] = s1Im + m2Im;
          temRe[2] = s1Re - m2Re;
          temIm[2] = s1Im - m2Im;
        } else if(radix == 4) {
          fft4(temRe, temIm);
        } else if(radix == 5) {
          fft5(myfft, temRe, temIm);
        } else if(radix == 8) {
          fft8(myfft, temRe, temIm);
        } else if(radix == 10) {
          fft10(myfft, temRe, temIm);
        } else {
          fftPrime(radix, temRe, temIm);
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
  } //twiddle operation

  // The two arguments dataRe[], dataIm[] are mainly for using in fft8();
  private static void fft4(double dataRe[], double dataIm[]) {
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

  // The two arguments dataRe[], dataIm[] are mainly for using in fft10();
  private static void fft5(fft1d myfft, double dataRe[], double dataIm[]) {
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

    m1Re = myfft.c51 * t5Re;
    m1Im = myfft.c51 * t5Im;
    m2Re = myfft.c52 * (t1Re - t2Re);
    m2Im = myfft.c52 * (t1Im - t2Im);
    m3Re = -(myfft.c53) * (t3Im + t4Im);
    m3Im = myfft.c53 * (t3Re + t4Re);
    m4Re = -(myfft.c54) * t4Im;
    m4Im = myfft.c54 * t4Re;
    m5Re = -(myfft.c55) * t3Im;
    m5Im = myfft.c55 * t3Re;

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

  private static void fft8(fft1d myfft, double[] temRe, double[] temIm) {
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

    tem = myfft.OnetoSqrt2 * (data2Re[1] + data2Im[1]);
    data2Im[1] = myfft.OnetoSqrt2 * (data2Im[1] - data2Re[1]);
    data2Re[1] = tem;
    tem = data2Im[2];
    data2Im[2] = -data2Re[2];
    data2Re[2] = tem;
    tem = myfft.OnetoSqrt2 * (data2Im[3] - data2Re[3]);
    data2Im[3] = -(myfft.OnetoSqrt2) * (data2Re[3] + data2Im[3]);
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

  private static void fft10(fft1d myfft, double[] temRe, double[] temIm) {
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

    fft5(myfft, data1Re, data1Im);
    fft5(myfft, data2Re, data2Im);

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

  private static void fftPrime(int radix, double[] temRe, double[] temIm) {
    // Initial WRe, WIm.
    double W = 2 * (double) Math.setPI() / radix;
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
}
