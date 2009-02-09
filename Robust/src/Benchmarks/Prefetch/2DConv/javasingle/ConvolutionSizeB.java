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
    int kernelHeight=11;
    int kernelWidth=11;

    double[][] kernel = new double[kernelHeight][kernelWidth];
    initKernel11(kernel);

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
    double tinput0[] = tinput1;

    int l=x0+10;
    for(int i=x0;i<x1;i++,l++){
      double tout[] = tempout[i];
      tinput0 = tinput1; tinput1=tinput2; tinput2=tinput3; tinput3=tinput4; tinput4=tinput5;
      tinput5 = tinput6; tinput6=tinput7; tinput7=tinput8; tinput8=tinput9; tinput9=tinput10; tinput10=tempinput[l];
      for(int j=y0;j<y1;++j){
        double s=0;
        for(int b=0;b<kernelHeight;++b) {
          s+=(tinput0[j+b] * kernel[0][b] + tinput1[j+b] * kernel[1][b] + tinput2[j+b]*kernel[2][b] +
              tinput3[j+b]*kernel[3][b] + tinput4[j+b]*kernel[4][b] + tinput5[j+b]*kernel[5][b]+ 
              tinput6[j+b]*kernel[6][b] + tinput7[j+b]*kernel[7][b] + tinput8[j+b]*kernel[8][b]+
              tinput9[j+b]*kernel[9][b] + tinput10[j+b]*kernel[10][b]);
        }
          tout[j]=s;
      }
    }
  }

  public static void main(String[] args) {
    int SIZE = 256;
    int NUM_THREADS = 1;
    int kernelHeight=11, kernelWidth=11;

    if(args.length>0) {
      NUM_THREADS = Integer.parseInt(args[0]);
      if(args.length>1) {
        SIZE = Integer.parseInt(args[1]);
      }
    }

    Image img;
    Convolution[] conv;
    Convolution tmp;

    img = new Image(SIZE,SIZE,kernelHeight,kernelWidth);
    img.setValues();
    conv = new Convolution[NUM_THREADS];
    int increment=SIZE/NUM_THREADS;
    int base = 0;
    for(int i = 0; i<NUM_THREADS; i++) {
      if((i+1)==NUM_THREADS)
        conv[i] = new Convolution(img, base, SIZE, 0, SIZE);
      else 
        conv[i] = new Convolution(img, base, base+increment, 0, SIZE);
      base+=increment;
    }

    /*
    System.printString("img.outputImage[10][20] = " +(int) img.outputImage[10][20] + "\n");
    System.printString("img.outputImage[256][890] = " +(int) img.outputImage[256][890] + "\n");
    */

	System.printString("Convolution: Size=");
    System.printInt(SIZE);
	System.printString("\n");

    for(int i = 0; i <NUM_THREADS; i++) {
      tmp = conv[i];
      tmp.run();
    }

    /*
    System.printString("img.outputImage[10][20] = " +(int) img.outputImage[10][20] + "\n");
    System.printString("img.outputImage[256][890] = " +(int) img.outputImage[256][890] + "\n");
    */
    System.printString("2DConv Done!\n");
  }

  //define 11X11 Gaussian kernel
  public static void initKernel11(double[][] kernel) {
    kernel[0][0] = 1/256.0;
    kernel[0][1] = 4/256.0;
    kernel[0][2] = 6/256.0;
    kernel[0][3] = 8/256.0;
    kernel[0][4] = 10/256.0;
    kernel[0][5] = 12/256.0;
    kernel[0][6] = 10/256.0;
    kernel[0][7] = 8/256.0;
    kernel[0][8] = 6/256.0;
    kernel[0][9] = 4/256.0;
    kernel[0][10] = 1/256.0;

    kernel[1][0] = 4/256.0;
    kernel[1][1] = 16/256.0;
    kernel[1][2] = 24/256.0;
    kernel[1][3] = 32/256.0;
    kernel[1][4] = 40/256.0;
    kernel[1][5] = 48/256.0;
    kernel[1][6] = 40/256.0;
    kernel[1][7] = 32/256.0;
    kernel[1][8] = 24/256.0;
    kernel[1][9] = 8/256.0;
    kernel[1][10] = 4/256.0;

    kernel[2][0] = 6/256.0;
    kernel[2][1] = 24/256.0;
    kernel[2][2] = 36/256.0;
    kernel[2][3] = 48/256.0;
    kernel[2][4] = 60/256.0;
    kernel[2][5] = 72/256.0;
    kernel[2][6] = 60/256.0;
    kernel[2][7] = 48/256.0;
    kernel[2][8] = 36/256.0;
    kernel[2][9] = 24/256.0;
    kernel[2][10] = 6/256.0;

    kernel[3][0] = 8/256.0;
    kernel[3][1] = 32/256.0;
    kernel[3][2] = 48/256.0;
    kernel[3][3] = 64/256.0;
    kernel[3][4] = 80/256.0;
    kernel[3][5] = 96/256.0;
    kernel[3][6] = 80/256.0;
    kernel[3][7] = 64/256.0;
    kernel[3][8] = 48/256.0;
    kernel[3][9] = 32/256.0;
    kernel[3][10] = 8/256.0;

    kernel[4][0] = 10/256.0;
    kernel[4][1] = 40/256.0;
    kernel[4][2] = 60/256.0;
    kernel[4][3] = 80/256.0;
    kernel[4][4] = 100/256.0;
    kernel[4][5] = 120/256.0;
    kernel[4][6] = 100/256.0;
    kernel[4][7] = 80/256.0;
    kernel[4][8] = 60/256.0;
    kernel[4][9] = 40/256.0;
    kernel[4][10] = 10/256.0;

    kernel[5][0] = 12/256.0;
    kernel[5][1] = 48/256.0;
    kernel[5][2] = 72/256.0;
    kernel[5][3] = 96/256.0;
    kernel[5][4] = 120/256.0;
    kernel[5][5] = 144/256.0;
    kernel[5][6] = 120/256.0;
    kernel[5][7] = 96/256.0;
    kernel[5][8] = 72/256.0;
    kernel[5][9] = 48/256.0;
    kernel[5][10] = 12/256.0;

    kernel[6][0] = 10/256.0;
    kernel[6][1] = 40/256.0;
    kernel[6][2] = 60/256.0;
    kernel[6][3] = 80/256.0;
    kernel[6][4] = 100/256.0;
    kernel[6][5] = 120/256.0;
    kernel[6][6] = 100/256.0;
    kernel[6][7] = 80/256.0;
    kernel[6][8] = 60/256.0;
    kernel[6][9] = 40/256.0;
    kernel[6][10] = 10/256.0;

    kernel[7][0] = 8/256.0;
    kernel[7][1] = 32/256.0;
    kernel[7][2] = 48/256.0;
    kernel[7][3] = 64/256.0;
    kernel[7][4] = 80/256.0;
    kernel[7][5] = 96/256.0;
    kernel[7][6] = 80/256.0;
    kernel[7][7] = 64/256.0;
    kernel[7][8] = 48/256.0;
    kernel[7][9] = 32/256.0;
    kernel[7][10] = 8/256.0;

    kernel[8][0] = 6/256.0;
    kernel[8][1] = 24/256.0;
    kernel[8][2] = 36/256.0;
    kernel[8][3] = 48/256.0;
    kernel[8][4] = 60/256.0;
    kernel[8][5] = 72/256.0;
    kernel[8][6] = 60/256.0;
    kernel[8][7] = 48/256.0;
    kernel[8][8] = 36/256.0;
    kernel[8][9] = 24/256.0;
    kernel[8][10] = 6/256.0;

    kernel[9][0] = 4/256.0;
    kernel[9][1] = 8/256.0;
    kernel[9][2] = 24/256.0;
    kernel[9][3] = 32/256.0;
    kernel[9][4] = 40/256.0;
    kernel[9][5] = 48/256.0;
    kernel[9][6] = 40/256.0;
    kernel[9][7] = 32/256.0;
    kernel[9][8] = 24/256.0;
    kernel[9][9] = 8/256.0;
    kernel[9][10] = 4/256.0;

    kernel[10][0] = 1/256.0;
    kernel[10][1] = 4/256.0;
    kernel[10][2] = 6/256.0;
    kernel[10][3] = 8/256.0;
    kernel[10][4] = 10/256.0;
    kernel[10][5] = 12/256.0;
    kernel[10][6] = 10/256.0;
    kernel[10][7] = 8/256.0;
    kernel[10][8] = 6/256.0;
    kernel[10][9] = 4/256.0;
    kernel[10][10] = 1/256.0;
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
    inputImage = new double[height+kernelHeight-1][width+kernelWidth-1];
    outputImage = new double[height][width];
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
