public class fft2d extends Thread {
  //Title:        2-d mixed radix FFT.
  //Version:
  //Copyright:    Copyright (c) 1998
  //Author:       Dongyan Wang
  //Company:      University of Wisconsin-Milwaukee.
  //Description:
  //              . Use fft1d to perform fft2d.
  //
  // Code borrowed from :Java Digital Signal Processing book by Lyon and Rao

  public fft1d fft1, fft2;
  public Matrix data;
  public int x0, x1, y0, y1;
  public double inputRe[], inputIm[];

  // Constructor: 2-d FFT of Complex data.
  public fft2d(double[] inputRe, double[] inputIm, Matrix data, fft1d fft1, fft1d fft2, int x0, int x1, int y0, int y1) {
    this.data = data;
    this.x0 = x0;
    this.x1 = x1;
    this.y0 = y0;
    this.y1 = y1;
    this.fft1 = fft1;
    this.fft2 = fft2;
    this.inputRe = inputRe;
    this.inputIm = inputIm;
  }

  public void run() {
    Barrier barr;
    barr = new Barrier("128.195.175.84");
    double tempdataRe[][];
    double tempdataIm[][];
    double mytemRe[][];
    double mytemIm[][];
    int rowlength, columnlength;

    atomic {
      rowlength = data.M;  //height
      columnlength = data.N; //width
      tempdataRe = data.dataRe;
      tempdataIm = data.dataIm;

      // Calculate FFT for each row of the data.
      //System.printString("x0= " + x0 + " x1= " + x1 + " y0= "+ y0 + " y1= " + y1 + " width = " + columnlength + " height= " + rowlength+ "\n");
      for (int i = x0; i < x1; i++) {
        int N = fft1.N;
        if(columnlength != N) {
          System.printString("Error: the length of real part & imaginary part " + "of the input to 1-d FFT are different");
          return;
        } else {
          //Permute() operation on fft1
          //input of FFT
          double inputRe[] = tempdataRe[i]; //local array
          double inputIm[] = tempdataIm[i];
          //output of FFT
          double outputRe[] = fft1.outputRe; //local array
          double outputIm[] = fft1.outputIm;
          double temRe[] = fft1.temRe;   // intermediate results
          double temIm[] = fft1.temIm;
          int count[] = new int[fft1.MaxFactorsNumber];
          int j; 
          int k = 0;
          for(int a = 0; a < N-1; a++) {
            outputRe[a] = inputRe[k];
            outputIm[a] = inputIm[k];
            j = 0;
            k = k + fft1.remain[j];
            count[0] = count[0] + 1;
            while (count[j] >= fft1.factors[j]) {
              count[j] = 0;
              int tmp;
              if(j == 0) 
                tmp = N;
              else
                tmp = fft1.remain[j - 1];
              k = k - tmp + fft1.remain[j + 1];
              j++;
              count[j] = count[j] + 1;
            }
          }
          outputRe[N - 1] = inputRe[N - 1];
          outputIm[N - 1] = inputIm[N - 1];

          //Twiddle oeration on fft1
          for (int factorIndex = 0; factorIndex < fft1.NumofFactors; factorIndex++) {
            twiddle(factorIndex, fft1, temRe, temIm, outputRe, outputIm);
          }
          // Copy the output[] data to input[], so the output can be
          // returned in the input array.
          for (int b = 0; b < N; b++) {
            inputRe[b] = outputRe[b];
            inputIm[b] = outputIm[b];
          }
        }
      }//end of for
    }

    //Start Barrier
    Barrier.enterBarrier(barr);

    // Tranpose data.
    atomic {
      mytemRe = new double[columnlength][rowlength];
      mytemIm = new double[columnlength][rowlength];
      for(int i = x0; i<x1; i++) {
        double tRe[] = tempdataRe[i];
        double tIm[] = tempdataIm[i];
        for(int j = y0; j<y1; j++) { 
          mytemRe[j][i] = tRe[j];
          mytemIm[j][i] = tIm[j];
        }
      }
    }

    //Start Barrier
    Barrier.enterBarrier(barr);

    // Calculate FFT for each column of the data.
    atomic {
      for (int j = y0; j < y1; j++) {
        int N = fft2.N;
        if(rowlength != N) {
          System.printString("Error: the length of real part & imaginary part " + "of the input to 1-d FFT are different");
          return;
        } else {
          //Permute() operation on fft2
          //input of FFT
          double inputRe[] = mytemRe[j]; //local array
          double inputIm[] = mytemIm[j];
          //output of FFT
          double outputRe[] = fft2.outputRe; //local array
          double outputIm[] = fft2.outputIm;
          double temRe[] = fft2.temRe;   // intermediate results
          double temIm[] = fft2.temIm;
          int count[] = new int[fft2.MaxFactorsNumber];
          int r; 
          int k = 0;
          for(int a = 0; a < N-1; a++) {
            outputRe[a] = inputRe[k];
            outputIm[a] = inputIm[k];
            r = 0;
            k = k + fft2.remain[r];
            count[0] = count[0] + 1;
            while (count[r] >= fft2.factors[r]) {
              count[r] = 0;
              int tmp;
              if(r == 0)
                tmp = N;
              else
                tmp = fft2.remain[r - 1];
              k = k - tmp + fft2.remain[r + 1];
              r++;
              count[r] = count[r] + 1;
            }
          }
          outputRe[N - 1] = inputRe[N - 1];
          outputIm[N - 1] = inputIm[N - 1];

          //Twiddle oeration on fft2
          for (int factorIndex = 0; factorIndex < fft2.NumofFactors; factorIndex++) {
            twiddle(factorIndex, fft2, temRe, temIm, outputRe, outputIm);
          }
          // Copy the output[] data to input[], so the output can be
          // returned in the input array.
          for (int b = 0; b < N; b++) {
            inputRe[b] = outputRe[b];
            inputIm[b] = outputIm[b];
          }
        }
      }//end of fft2 for
    }

    //Start Barrier
    Barrier.enterBarrier(barr);

    // Tranpose data.
    // Copy the result to input[], so the output can be
    // returned in the input array.
    atomic {
      for (int j = y0; j < y1; j++) {
        double tRe[] = mytemRe[j];
        double tIm[] = mytemIm[j];
        for (int i = x0; i < x1; i++) {
          inputRe[i* data.N + j] = tRe[i];
          inputIm[i* data.N + j] = tIm[i];
        }
      }
    }

  }//end of run


  //("ready to twiddle");
  private void twiddle(int factorIndex, fft1d myfft, double[] temRe, double[] temIm, 
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

  // The two arguments dataRe[], dataIm[] are mainly for using in fft10();
  private void fft5(fft1d myfft, double dataRe[], double dataIm[]) {
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

  private void fft8(fft1d myfft, double[] temRe, double[] temIm) {
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

  private void fft10(fft1d myfft, double[] temRe, double[] temIm) {
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

  private void fftPrime(int radix, double[] temRe, double[] temIm) {
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

    // Initialize Matrix 
    // Matrix inputRe, inputIm;

    double[] inputRe;
    double[] inputIm;
    atomic {
      inputRe = global new double[SIZE];
      inputIm = global new double[SIZE];

      for(int i = 0; i<SIZE; i++){
        inputRe[i] = i;
        inputIm[i] = i;
      }
    }

    /* For testing 
    atomic {
      System.printString("Element 231567 is " + (int)inputRe[231567]+ "\n");
      System.printString("Element 10 is " + (int)inputIm[10]+ "\n");
    }
    */

    int[] mid = new int[8];
    mid[0] = (128<<24)|(195<<16)|(175<<8)|84; //dw-10
    mid[1] = (128<<24)|(195<<16)|(175<<8)|85; //dw-11
    mid[2] = (128<<24)|(195<<16)|(175<<8)|86; //dw-12
    mid[3] = (128<<24)|(195<<16)|(175<<8)|87; //dw-13
    mid[4] = (128<<24)|(195<<16)|(175<<8)|88; //dw-14
    mid[5] = (128<<24)|(195<<16)|(175<<8)|89; //dw-15
    mid[6] = (128<<24)|(195<<16)|(175<<8)|90; //dw-16
    mid[7] = (128<<24)|(195<<16)|(175<<8)|91; //dw-17

    // Start Barrier Server
    BarrierServer mybarr;
    atomic {
       mybarr = global new BarrierServer(NUM_THREADS);
    }
    mybarr.start(mid[0]);

    // Width and height of 2-d matrix inputRe or inputIm.
    int width, height;
    width = inputWidth;
    int Relength, Imlength;
    atomic {
      height = inputRe.length / width;
      Relength = inputRe.length;
      Imlength = inputIm.length;
    }

    //System.printString("Initialized width and height\n");
    Matrix data;
    fft1d fft1, fft2;
    // First make sure inputRe & inputIm are of the same length in terms of columns
    if (Relength != Imlength) {
      System.printString("Error: the length of real part & imaginary part " +
          "of the input to 2-d FFT are different");
      return;
    } else {
      atomic {
        fft1 = global new fft1d(width);
        fft2 = global new fft1d(height);
        // Set up data for FFT transform 
        data = global new Matrix(height, width);
        data.setValues(inputRe, inputIm);
      }

      // Create threads to do FFT 
      fft2d[] myfft2d;
      atomic {
        myfft2d = global new fft2d[NUM_THREADS];
        int increment = height/NUM_THREADS;
        int base = 0;
        for(int i =0 ; i<NUM_THREADS; i++) {
          if((i+1)==NUM_THREADS)
            myfft2d[i] = global new fft2d(inputRe, inputIm, data, fft1, fft2, base, increment, 0, width);
          else
            myfft2d[i] = global new fft2d(inputRe, inputIm, data, fft1, fft2, base, base+increment, 0, width);
          base+=increment;
        }
      }

      boolean waitfordone=true;
      while(waitfordone) {
        atomic {
          if (mybarr.done)
            waitfordone=false;
        }
      }

      fft2d tmp;
      //Start a thread to compute each c[l,n]
      for(int i = 0; i<NUM_THREADS; i++) {
        atomic {
          tmp = myfft2d[i];
        }
        tmp.start(mid[i]);
      }

      //Wait for thread to finish 
      for(int i = 0; i<NUM_THREADS; i++) {
        atomic {
          tmp = myfft2d[i];
        }
        tmp.join();
      }
    }

    System.printString("2DFFT done! \n");
    /* For testing 
    atomic {
      System.printString("Element 231567 is " + (int)inputRe[231567]+ "\n");
      System.printString("Element 10 is " + (int)inputIm[10]+ "\n");
    }
    */

    // Display results
    // Tranpose data.
    // Copy the result to input[], so the output can be
    // returned in the input array.
    /* For testing
    atomic {
      for (int i = 0; i < height; i++) {
        for (int j = 0; j < width; j++) {
          System.printString((int)inputRe[i * width + j]+ "\n");
          System.printString((int)inputIm[i * width + j]+ "\n");
        }
      }
    }
    */
  }
}
