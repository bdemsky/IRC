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

  public Matrix data1;
  public int x0, x1;

  // Constructor: 2-d FFT of Complex data.
  public fft2d(Matrix data1, int x0, int x1) {
    this.data1 = data1;
    this.x0 = x0;
    this.x1 = x1;
  }

  public void run() {
    fft1d fft1, fft2;
    Barrier barr;
    barr = new Barrier("128.195.136.162");
    float tempdataRe[][];
    float tempdataIm[][];
    int rowlength, columnlength;
    int start, end,nmatrix;

    // Calculate FFT for each row of the data.
    atomic {
      nmatrix = data1.numMatrix;
      //
      // Add manual prefetch for
      // this.data1.dataRe[start -> end]

      short[] offsets1 = new short[8];
      offsets1[0] = getoffset{fft2d, data1};
      offsets1[1] = (short) 0;
      offsets1[2] = getoffset{Matrix, dataRe};
      offsets1[3] = (short) 0;
      offsets1[4] = (short) 0; 
      offsets1[5] = (short) nmatrix;
      offsets1[6] = (short) x0; 
      offsets1[7] = (short) 15;
      System.rangePrefetch(this, offsets1);

      // prefetch data1.dataIm[x0 -> x1]
      offsets1[2] = getoffset{Matrix, dataIm};
      offsets1[3] = (short) 0;
      System.rangePrefetch(this, offsets1);
     ///////////////////////////// //////////

      short[] offsets2 = new short[2];

      rowlength = data1.M;
      columnlength = data1.N;
      start = x0;
      end = x1;
      fft1 = new fft1d(columnlength);
      fft2 = new fft1d(rowlength);
      for(int z=0; z<nmatrix; z++) {
        tempdataRe = data1.dataRe[z];
        tempdataIm = data1.dataIm[z];
        int l=8;
        for (int i = start; i < end; i++,l++) {
          //input of FFT
          if ((l&15)==0) {
            offsets2[0] = (short) (l+start); 
            if ((l+start+16)>= end) {
              int t=end-l-start-1;
              if (t>0) {
                offsets2[1] = (short) t;
                System.rangePrefetch(tempdataRe, offsets2);
                System.rangePrefetch(tempdataIm, offsets2);
              }
            } else {
              offsets2[1] = (short) 15;
              System.rangePrefetch(tempdataRe, offsets2);
              System.rangePrefetch(tempdataIm, offsets2);
            }
          }
          float inputRe[] = tempdataRe[i]; //local array
          float inputIm[] = tempdataIm[i];
          fft(fft1, inputRe, inputIm);
        } //end of for
      }
    }

    //Start Barrier
    Barrier.enterBarrier(barr);

    // Tranpose data.
    if (start == 0) {
      atomic {
        for(int z=0; z<nmatrix; z++) {
          tempdataRe = data1.dataRe[z];
          tempdataIm = data1.dataIm[z];
          transpose(tempdataRe, tempdataIm, rowlength, columnlength);
        }
      }
    }

    //Start Barrier
    Barrier.enterBarrier(barr);

    // Calculate FFT for each column of the data.
    float transtempRe[][];
    float transtempIm[][];
    atomic {
      for(int z=0; z<nmatrix; z++) {
      // 
      // Add manual prefetch 
      // prefetch data2.dataRe[start -> end]
      Object o1 = data1.dataRe[z];
      short[] offsets1 = new short[2];
      offsets1[0] = (short) start;
      offsets1[1] = (short) 15;
      System.rangePrefetch(o1, offsets1);

      o1 = data1.dataIm;
      System.rangePrefetch(o1, offsets1);
      /////////////////////


        transtempRe = data1.dataRe[z];
        transtempIm = data1.dataIm[z];
        int l=8;
        for (int j = start; j < end; j++,l++) {
          if ((l&15)==0) {  //prefetch every 16th iteration
            offsets1[0]=(short) (l+start);
            if ((start+l+16)>=end) {
              int t=end-start-l-1;
              if (t>0) {
                offsets1[1]=(short)t;
                System.rangePrefetch(transtempRe, offsets1);
                System.rangePrefetch(transtempIm, offsets1);
              }
            } else {
              offsets1[1]=(short) 15;
              System.rangePrefetch(transtempRe, offsets1);
              System.rangePrefetch(transtempIm, offsets1);
            }
          }
          //input of FFT
          float inputRe[] = transtempRe[j]; //local array
          float inputIm[] = transtempIm[j];
          fft(fft2, inputRe, inputIm);
        } //end of fft2 for
      }
    } //end of atomic
  } //end of run

  public void transpose(float[][] tempdataRe, float[][] tempdataIm, int rowlength, int columnlength) {
    for(int i = 0; i<rowlength; i++) {
      float tRe[] = tempdataRe[i];
      float tIm[] = tempdataIm[i];
      float a;

      for(int j = 0; j<i; j++) {
	a=tempdataRe[j][i];
	tempdataRe[j][i] = tRe[j];
        tRe[j]=a;
	a=tempdataIm[j][i];
	tempdataIm[j][i] = tIm[j];
        tIm[j]=a;
      }
    }
  }

  public static void main(String[] args) {
    int NUM_THREADS = 1;
    int NUM_MATRIX = 1;
    int SIZE = 800;
    int inputWidth = 10;
    if(args.length>0) {
      NUM_THREADS=Integer.parseInt(args[0]);
      if(args.length > 1) {
        SIZE = Integer.parseInt(args[1]);
        if(args.length > 2)
          NUM_MATRIX = Integer.parseInt(args[2]);
      }
    }

    //System.printString("Num threads = " + NUM_THREADS + " SIZE= " + SIZE + "\n");

    System.printString("Num threads = " + NUM_THREADS + " SIZE= " + SIZE + " NUM_MATRIX= " + NUM_MATRIX +"\n");

    int[] mid = new int[8];
    mid[0] = (128<<24)|(195<<16)|(136<<8)|162; //dw-10
    mid[1] = (128<<24)|(195<<16)|(136<<8)|163; //dw-11
    mid[2] = (128<<24)|(195<<16)|(136<<8)|164; //dw-12
    mid[3] = (128<<24)|(195<<16)|(136<<8)|165; //dw-13
    mid[4] = (128<<24)|(195<<16)|(136<<8)|166; //dw-14
    mid[5] = (128<<24)|(195<<16)|(136<<8)|167; //dw-15
    mid[6] = (128<<24)|(195<<16)|(136<<8)|168; //dw-16
    mid[7] = (128<<24)|(195<<16)|(136<<8)|169; //dw-17
    

    // Start Barrier Server
    BarrierServer mybarr;
    atomic {
      mybarr = global new BarrierServer(NUM_THREADS);
    }
    mybarr.start(mid[0]);

    Matrix data1;

    // Create threads to do FFT
    fft2d[] myfft2d;
    atomic {
      // Set up data for FFT transform
      data1 = global new Matrix(SIZE, SIZE, NUM_MATRIX);
      data1.setValues(); //Input Matrix
      myfft2d = global new fft2d[NUM_THREADS];
      int increment = SIZE/NUM_THREADS;
      int base = 0;
      for(int i =0 ; i<NUM_THREADS; i++) {
	if((i+1)==NUM_THREADS)
	  myfft2d[i] = global new fft2d(data1, base, SIZE);
	else
	  myfft2d[i] = global new fft2d(data1, base, base+increment);
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

    System.printString("2DFFT done! \n");
  }

  public static void fft(fft1d myfft, float inputRe[], float inputIm[]) {
    //output of FFT
    float outputRe[] = myfft.outputRe;
    float outputIm[] = myfft.outputIm;
    // intermediate results
    float temRe[] = myfft.temRe;
    float temIm[] = myfft.temIm;
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

  private static void permute(fft1d myfft, float[] outputRe, float[] outputIm, float[] inputRe, float[] inputIm) {
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

  private static void twiddle(int factorIndex, fft1d myfft, float[] temRe, float[] temIm,
                              float[] outputRe, float[] outputIm) {
    // Get factor data.
    int sofarRadix = myfft.sofar[factorIndex];
    int radix = myfft.factors[factorIndex];
    int remainRadix = myfft.remain[factorIndex];

    float tem;   // Temporary variable to do data exchange.

    float W = 2 * (float) Math.setPI() / (sofarRadix * radix);
    float cosW = (float) Math.cos(W);
    float sinW = -(float) Math.sin(W);

    float twiddleRe[] = new float[radix];
    float twiddleIm[] = new float[radix];
    float twRe = 1.0f, twIm = 0f;

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
	  float t1Re = temRe[1] + temRe[2];
	  float t1Im = temIm[1] + temIm[2];
	  temRe[0] = temRe[0] + t1Re;
	  temIm[0] = temIm[0] + t1Im;

	  float m1Re = myfft.cos2to3PI * t1Re;
	  float m1Im = myfft.cos2to3PI * t1Im;
	  float m2Re = myfft.sin2to3PI * (temIm[1] - temIm[2]);
	  float m2Im = myfft.sin2to3PI * (temRe[2] - temRe[1]);
	  float s1Re = temRe[0] + m1Re;
	  float s1Im = temIm[0] + m1Im;

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
  private static void fft4(float dataRe[], float dataIm[]) {
    float t1Re,t1Im, t2Re,t2Im;
    float m2Re,m2Im, m3Re,m3Im;

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
  private static void fft5(fft1d myfft, float dataRe[], float dataIm[]) {
    float t1Re,t1Im, t2Re,t2Im, t3Re,t3Im, t4Re,t4Im, t5Re,t5Im;
    float m1Re,m1Im, m2Re,m2Im, m3Re,m3Im, m4Re,m4Im, m5Re,m5Im;
    float s1Re,s1Im, s2Re,s2Im, s3Re,s3Im, s4Re,s4Im, s5Re,s5Im;

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

  private static void fft8(fft1d myfft, float[] temRe, float[] temIm) {
    float data1Re[] = new float[4];
    float data1Im[] = new float[4];
    float data2Re[] = new float[4];
    float data2Im[] = new float[4];
    float tem;

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

  private static void fft10(fft1d myfft, float[] temRe, float[] temIm) {
    float data1Re[] = new float[5];
    float data1Im[] = new float[5];
    float data2Re[] = new float[5];
    float data2Im[] = new float[5];

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

  private static void fftPrime(int radix, float[] temRe, float[] temIm) {
    // Initial WRe, WIm.
    float W = 2 * (float) Math.setPI() / radix;
    float cosW = (float) Math.cos(W);
    float sinW = -(float) Math.sin(W);
    float WRe[] = new float[radix];
    float WIm[] = new float[radix];

    WRe[0] = 1;
    WIm[0] = 0;
    WRe[1] = cosW;
    WIm[1] = sinW;

    for (int i = 2; i < radix; i++) {
      WRe[i] = cosW * WRe[i - 1] - sinW * WIm[i - 1];
      WIm[i] = sinW * WRe[i - 1] + cosW * WIm[i - 1];
    }

    // FFT of prime length data, using DFT, can be improved in the future.
    float rere, reim, imre, imim;
    int j, k;
    int max = (radix + 1) / 2;

    float tem1Re[] = new float[max];
    float tem1Im[] = new float[max];
    float tem2Re[] = new float[max];
    float tem2Im[] = new float[max];

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
