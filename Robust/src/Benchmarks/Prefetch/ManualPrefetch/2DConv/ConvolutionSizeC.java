public class Convolution extends Thread {
  Image img;
  int x0,x1,y0,y1;

  public Convolution(Image img, int x0, int x1, int y0, int y1) {
    this.img = img;
    this.x0 = x0;
    this.x1 = x1;
    this.y0 = y0;
    this.y1 = y1;
  }

  public void run() {

    int tempx0, tempy0, tempx1, tempy1;
    atomic {
      tempx0 = x0;
      tempy0 = y0;
      tempx1 = x1;
      tempy1 = y1;  
    }

    //
    //Add manual prefetch this.img.inputImage[] the first 32 objects
    short[] offsets = new short[6];
    offsets[0] = getoffset{Convolution, img};
    offsets[1] = (short) 0;
    offsets[2] = getoffset{Image, inputImage};
    offsets[3] = (short) 0;
    offsets[4] = (short) tempx0;
    offsets[5] = (short) 31;
    System.rangePrefetch(this, offsets);

    //Prefetch this.img.outputImage[] the first 32 objects 
    offsets[2] = getoffset{Image, outputImage};
    offsets[3] = (short) 0;
    System.rangePrefetch(this, offsets);

    int kernelHeight=15;
    int kernelWidth=15;

    double[][] kernel = new double[kernelHeight][kernelWidth];
    initKernel15(kernel);

    atomic {
      double tempinput[][] = img.inputImage;
      double tempout[][] = img.outputImage;

      double tinput1[] = tempinput[x0];
      double tinput2[] = tempinput[x0+1];
      double tinput3[] = tempinput[x0+2];
      double tinput4[] = tempinput[x0+3];
      double tinput5[] = tempinput[x0+4];
      double tinput6[] = tempinput[x0+5];
      double tinput7[] = tempinput[x0+6];
      double tinput8[] = tempinput[x0+7];
      double tinput9[] = tempinput[x0+8];
      double tinput10[] = tempinput[x0+9];
      double tinput11[] = tempinput[x0+10];
      double tinput12[] = tempinput[x0+11];
      double tinput13[] = tempinput[x0+12];
      double tinput14[] = tempinput[x0+13];
      double tinput0[] = tinput1;
      short[] offsets2 = new short[2];
	  

      int l=14;
      for(int i=x0;i<x1;i++,l++){
        if((l&31) == 0) {  //prefetch every 32th iteration
          //Prefetch this.img.inputImage[] 
          offsets2[0] = (short) (l+x0);
          offsets2[1] = (short) 31;
          System.rangePrefetch(tempinput, offsets2);
          System.rangePrefetch(tempout, offsets2);
        }

        double tout[] = tempout[i];
        tinput0 = tinput1; tinput1=tinput2; tinput2=tinput3; tinput3=tinput4; tinput4=tinput5;
        tinput5 = tinput6; tinput6=tinput7; tinput7=tinput8; tinput8=tinput9; tinput9=tinput10; 
        tinput10 = tinput11; tinput11=tinput12; tinput12=tinput13; tinput13=tinput14; tinput14=tempinput[l];
        for(int j=y0;j<y1;++j){
          double s=0;
          for(int b=0;b<kernelHeight;++b) {
            s+=(tinput0[j+b] * kernel[0][b] + tinput1[j+b] * kernel[1][b] + tinput2[j+b]*kernel[2][b] +
                tinput3[j+b]*kernel[3][b] + tinput4[j+b]*kernel[4][b] + tinput5[j+b]*kernel[5][b]+ 
                tinput6[j+b]*kernel[6][b] + tinput7[j+b]*kernel[7][b] + tinput8[j+b]*kernel[8][b]+
                tinput9[j+b]*kernel[9][b] + tinput10[j+b]*kernel[10][b] + tinput11[j+b]*kernel[11][b]+
                tinput12[j+b]*kernel[12][b]+ tinput13[j+b]*kernel[13][b] + tinput14[j+b]*kernel[14][b]);
          }
          tout[j]=s;
        }
      }
    }
  }

  public static void main(String[] args) {
    int SIZE = 256;
    int NUM_THREADS = 1;
    int kernelHeight=15, kernelWidth=15;

    if(args.length>0) {
      NUM_THREADS = Integer.parseInt(args[0]);
      if(args.length>1) {
        SIZE = Integer.parseInt(args[1]);
      }
    }

    int[] mid = new int[8];
    mid[0] = (128<<24)|(195<<16)|(136<<8)|162; //dw-10
    mid[1] = (128<<24)|(195<<16)|(136<<8)|163; //dw-11
    mid[2] = (128<<24)|(195<<16)|(136<<8)|164; //dw-12
    mid[3] = (128<<24)|(195<<16)|(136<<8)|165; //dw-13
    mid[4] = (128<<24)|(195<<16)|(136<<8)|166; //dw-14
    mid[5] = (128<<24)|(195<<16)|(136<<8)|167; //dw-15
    mid[6] = (128<<24)|(195<<16)|(136<<8)|168; //dw-16
    mid[7] = (128<<24)|(195<<16)|(136<<8)|169; //dw-17

    Image img;
    Convolution[] conv;
    Convolution tmp;

    atomic {
      img = global new Image(SIZE,SIZE,kernelHeight,kernelWidth);
      img.setValues();
      conv = global new Convolution[NUM_THREADS];
      int increment=SIZE/NUM_THREADS;
      int base = 0;
      for(int i = 0; i<NUM_THREADS; i++) {
        if((i+1)==NUM_THREADS)
          conv[i] = global new Convolution(img, base, SIZE, 0, SIZE);
        else 
          conv[i] = global new Convolution(img, base, base+increment, 0, SIZE);
        base+=increment;
      }
    }
 
    /*
    atomic{
      System.printString("img.outputImage[10][20] = " +(int) img.outputImage[10][20] + "\n");
      System.printString("img.outputImage[256][890] = " +(int) img.outputImage[256][890] + "\n");
    }
    */

	System.printString("Convolution: Size=");
    System.printInt(SIZE);
	System.printString("\n");

    for(int i = 0; i <NUM_THREADS; i++) {
      atomic {
        tmp = conv[i];
      }
      tmp.start(mid[i]);
    }

    for(int i = 0; i < NUM_THREADS; i++) {
      atomic {
        tmp = conv[i];
      }
      tmp.join();
    }

    System.printString("2DConv Done!\n");

    /*
    atomic{
      System.printString("img.outputImage[10][20] = " +(int) img.outputImage[10][20] + "\n");
      System.printString("img.outputImage[256][890] = " +(int) img.outputImage[256][890] + "\n");
    }
    */
  }

  //define 15X15 Gaussian kernel
  public static void initKernel15(double[][] kernel) {
    kernel[0][0] = 1/256.0;
    kernel[0][1] = 4/256.0;
    kernel[0][2] = 6/256.0;
    kernel[0][3] = 8/256.0;
    kernel[0][4] = 10/256.0;
    kernel[0][5] = 12/256.0;
    kernel[0][6] = 14/256.0;
    kernel[0][7] = 16/256.0;
    kernel[0][8] = 14/256.0;
    kernel[0][9] = 12/256.0;
    kernel[0][10] = 10/256.0;
    kernel[0][11] = 8/256.0;
    kernel[0][12] = 6/256.0;
    kernel[0][13] = 4/256.0;
    kernel[0][14] = 1/256.0;

    kernel[1][0] = 4/256.0;
    kernel[1][1] = 16/256.0;
    kernel[1][2] = 24/256.0;
    kernel[1][3] = 32/256.0;
    kernel[1][4] = 40/256.0;
    kernel[1][5] = 48/256.0;
    kernel[1][6] = 56/256.0;
    kernel[1][7] = 64/256.0;
    kernel[1][8] = 56/256.0;
    kernel[1][9] = 48/256.0;
    kernel[1][10] = 40/256.0;
    kernel[1][11] = 32/256.0;
    kernel[1][12] = 24/256.0;
    kernel[1][13] = 16/256.0;
    kernel[1][14] = 4/256.0;

    kernel[2][0] = 6/256.0;
    kernel[2][1] = 24/256.0;
    kernel[2][2] = 36/256.0;
    kernel[2][3] = 48/256.0;
    kernel[2][4] = 60/256.0;
    kernel[2][5] = 72/256.0;
    kernel[2][6] = 84/256.0;
    kernel[2][7] = 96/256.0;
    kernel[2][8] = 84/256.0;
    kernel[2][9] = 72/256.0;
    kernel[2][10] = 60/256.0;
    kernel[2][11] = 48/256.0;
    kernel[2][12] = 36/256.0;
    kernel[2][13] = 24/256.0;
    kernel[2][14] = 6/256.0;

    kernel[3][0] = 8/256.0;
    kernel[3][1] = 32/256.0;
    kernel[3][2] = 48/256.0;
    kernel[3][3] = 64/256.0;
    kernel[3][4] = 80/256.0;
    kernel[3][5] = 96/256.0;
    kernel[3][6] = 112/256.0;
    kernel[3][7] = 128/256.0;
    kernel[3][8] = 112/256.0;
    kernel[3][9] = 96/256.0;
    kernel[3][10] = 80/256.0;
    kernel[3][11] = 64/256.0;
    kernel[3][12] = 48/256.0;
    kernel[3][13] = 32/256.0;
    kernel[3][14] = 8/256.0;


    kernel[4][0] = 10/256.0;
    kernel[4][1] = 40/256.0;
    kernel[4][2] = 60/256.0;
    kernel[4][3] = 80/256.0;
    kernel[4][4] = 100/256.0;
    kernel[4][5] = 120/256.0;
    kernel[4][6] = 140/256.0;
    kernel[4][7] = 160/256.0;
    kernel[4][8] = 140/256.0;
    kernel[4][9] = 120/256.0;
    kernel[4][10] = 100/256.0;
    kernel[4][11] = 80/256.0;
    kernel[4][12] = 60/256.0;
    kernel[4][13] = 40/256.0;
    kernel[4][14] = 10/256.0;

    kernel[5][0] = 12/256.0;
    kernel[5][1] = 48/256.0;
    kernel[5][2] = 72/256.0;
    kernel[5][3] = 96/256.0;
    kernel[5][4] = 120/256.0;
    kernel[5][5] = 144/256.0;
    kernel[5][6] = 168/256.0;
    kernel[5][7] = 192/256.0;
    kernel[5][8] = 168/256.0;
    kernel[5][9] = 144/256.0;
    kernel[5][10] = 120/256.0;
    kernel[5][11] = 96/256.0;
    kernel[5][12] = 72/256.0;
    kernel[5][13] = 48/256.0;
    kernel[5][14] = 12/256.0;

    kernel[6][0] = 14/256.0;
    kernel[6][1] = 56/256.0;
    kernel[6][2] = 84/256.0;
    kernel[6][3] = 112/256.0;
    kernel[6][4] = 140/256.0;
    kernel[6][5] = 168/256.0;
    kernel[6][6] = 196/256.0;
    kernel[6][7] = 224/256.0;
    kernel[6][8] = 196/256.0;
    kernel[6][9] = 168/256.0;
    kernel[6][10] = 140/256.0;
    kernel[6][11] = 112/256.0;
    kernel[6][12] = 84/256.0;
    kernel[6][13] = 56/256.0;
    kernel[6][14] = 14/256.0;

    kernel[7][0] = 16/256.0;
    kernel[7][1] = 64/256.0;
    kernel[7][2] = 96/256.0;
    kernel[7][3] = 128/256.0;
    kernel[7][4] = 160/256.0;
    kernel[7][5] = 192/256.0;
    kernel[7][6] = 224/256.0;
    kernel[7][7] = 256/256.0;
    kernel[7][8] = 224/256.0;
    kernel[7][9] = 192/256.0;
    kernel[7][10] = 160/256.0;
    kernel[7][11] = 128/256.0;
    kernel[7][12] = 96/256.0;
    kernel[7][13] = 64/256.0;
    kernel[7][14] = 16/256.0;

    kernel[8][0] = 14/256.0;
    kernel[8][1] = 56/256.0;
    kernel[8][2] = 84/256.0;
    kernel[8][3] = 112/256.0;
    kernel[8][4] = 140/256.0;
    kernel[8][5] = 168/256.0;
    kernel[8][6] = 196/256.0;
    kernel[8][7] = 224/256.0;
    kernel[8][8] = 196/256.0;
    kernel[8][9] = 168/256.0;
    kernel[8][10] = 140/256.0;
    kernel[8][11] = 112/256.0;
    kernel[8][12] = 84/256.0;
    kernel[8][13] = 56/256.0;
    kernel[8][14] = 14/256.0;

    kernel[9][0] = 12/256.0;
    kernel[9][1] = 48/256.0;
    kernel[9][2] = 72/256.0;
    kernel[9][3] = 96/256.0;
    kernel[9][4] = 120/256.0;
    kernel[9][5] = 144/256.0;
    kernel[9][6] = 168/256.0;
    kernel[9][7] = 192/256.0;
    kernel[9][8] = 168/256.0;
    kernel[9][9] = 144/256.0;
    kernel[9][10] = 120/256.0;
    kernel[9][11] = 96/256.0;
    kernel[9][12] = 72/256.0;
    kernel[9][13] = 48/256.0;
    kernel[9][14] = 12/256.0;

    kernel[10][0] = 10/256.0;
    kernel[10][1] = 40/256.0;
    kernel[10][2] = 60/256.0;
    kernel[10][3] = 80/256.0;
    kernel[10][4] = 100/256.0;
    kernel[10][5] = 120/256.0;
    kernel[10][6] = 140/256.0;
    kernel[10][7] = 160/256.0;
    kernel[10][8] = 140/256.0;
    kernel[10][9] = 120/256.0;
    kernel[10][10] = 100/256.0;
    kernel[10][11] = 80/256.0;
    kernel[10][12] = 60/256.0;
    kernel[10][13] = 40/256.0;
    kernel[10][14] = 10/256.0;

    kernel[11][0] = 8/256.0;
    kernel[11][1] = 32/256.0;
    kernel[11][2] = 48/256.0;
    kernel[11][3] = 64/256.0;
    kernel[11][4] = 80/256.0;
    kernel[11][5] = 96/256.0;
    kernel[11][6] = 112/256.0;
    kernel[11][7] = 128/256.0;
    kernel[11][8] = 112/256.0;
    kernel[11][9] = 96/256.0;
    kernel[11][10] = 80/256.0;
    kernel[11][11] = 64/256.0;
    kernel[11][12] = 48/256.0;
    kernel[11][13] = 32/256.0;
    kernel[11][14] = 8/256.0;

    kernel[12][0] = 6/256.0;
    kernel[12][1] = 24/256.0;
    kernel[12][2] = 36/256.0;
    kernel[12][3] = 48/256.0;
    kernel[12][4] = 60/256.0;
    kernel[12][5] = 72/256.0;
    kernel[12][6] = 84/256.0;
    kernel[12][7] = 96/256.0;
    kernel[12][8] = 84/256.0;
    kernel[12][9] = 72/256.0;
    kernel[12][10] = 60/256.0;
    kernel[12][11] = 48/256.0;
    kernel[12][12] = 36/256.0;
    kernel[12][13] = 24/256.0;
    kernel[12][14] = 6/256.0;

    kernel[13][0] = 4/256.0;
    kernel[13][1] = 16/256.0;
    kernel[13][2] = 24/256.0;
    kernel[13][3] = 32/256.0;
    kernel[13][4] = 40/256.0;
    kernel[13][5] = 48/256.0;
    kernel[13][6] = 56/256.0;
    kernel[13][7] = 64/256.0;
    kernel[13][8] = 56/256.0;
    kernel[13][9] = 48/256.0;
    kernel[13][10] = 40/256.0;
    kernel[13][11] = 32/256.0;
    kernel[13][12] = 24/256.0;
    kernel[13][13] = 16/256.0;
    kernel[13][14] = 4/256.0;

    kernel[14][0] = 1/256.0;
    kernel[14][1] = 4/256.0;
    kernel[14][2] = 6/256.0;
    kernel[14][3] = 8/256.0;
    kernel[14][4] = 10/256.0;
    kernel[14][5] = 12/256.0;
    kernel[14][6] = 14/256.0;
    kernel[14][7] = 16/256.0;
    kernel[14][8] = 14/256.0;
    kernel[14][9] = 12/256.0;
    kernel[14][10] = 10/256.0;
    kernel[14][11] = 8/256.0;
    kernel[14][12] = 6/256.0;
    kernel[14][13] = 4/256.0;
    kernel[14][14] = 1/256.0;
  }
}

public class Image {
  int width, height;
  int kernelWidth, kernelHeight;
  double[][] inputImage;
  double[][] outputImage;

  public Image(int width, int height, int kernelWidth, int kernelHeight) {
    this.width = width;
    this.height = height;
    this.kernelWidth = kernelWidth;
    this.kernelHeight = kernelHeight;
    inputImage = global new double[height+kernelHeight-1][width+kernelWidth-1];
    outputImage = global new double[height][width];
  }

  /* Create a valid image */
  public void setValues() {
    for (int i = 0; i < (height+kernelHeight - 1); i++) {
      double ainput[] = inputImage[i];
      for(int j = 0; j < (width+kernelWidth - 1); j++) {
        ainput[j] = 256-j;
      }
    }

    for (int i = 0; i < height; i++){
      double aout[] = outputImage[i];
      for(int j = 0; j < width; j++) {
        aout[j] = 0;
      }
    }
  }
}
