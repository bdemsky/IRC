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
    int kernelHeight = 5;
    int kernelWidth = 5;
    double[][] kernel = new double[kernelHeight][kernelWidth];
    initKernel(kernel);

    atomic {
      double tempinput[][] = img.inputImage;
      double tempout[][] = img.outputImage;

      double tinput1[] = tempinput[x0];
      double tinput2[] = tempinput[x0+1];
      double tinput3[] = tempinput[x0+2];
      double tinput4[] = tempinput[x0+3];
      double tinput0[] = tinput1;

      int l=x0+4;
      for(int i=x0;i<x1;i++,l++){
        double tout[] = tempout[i];
        tinput0 = tinput1; tinput1=tinput2; tinput2=tinput3; tinput3=tinput4; tinput4=tempinput[l];
        for(int j=y0;j<y1;++j){
          double s=0;
          for(int b=0;b<kernelHeight;++b){
            s+=(tinput0[j+b] * kernel[0][b] + tinput1[j+b] * kernel[1][b] + tinput2[j+b]*kernel[2][b] +
                tinput3[j+b]*kernel[3][b] + tinput4[j+b]*kernel[4][b]);
          }
	  tout[j]=s;
        }
      }
    }
  }

  public static void main(String[] args) {
    int SIZE = 256;
    int NUM_THREADS = 1;
    if(args.length>0) {
      NUM_THREADS = Integer.parseInt(args[0]);
      if(args.length>1)
        SIZE = Integer.parseInt(args[1]);
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

    int kernelHeight = 5;
    int kernelWidth = 5;
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

  //define 5X5 Gaussian kernel
    public static void initKernel(double[][] kernel) {
    kernel[0][0] = 1/256.0;
    kernel[0][1] = 4/256.0;
    kernel[0][2] = 6/256.0;
    kernel[0][3] = 4/256.0;
    kernel[0][4] = 1/256.0;
    kernel[1][0] = 4/256.0;
    kernel[1][1] = 16/256.0;
    kernel[1][2] = 24/256.0;
    kernel[1][3] = 16/256.0;
    kernel[1][4] = 4/256.0;
    kernel[2][0] = 6/256.0;
    kernel[2][1] = 24/256.0;
    kernel[2][2] = 36/256.0;
    kernel[2][3] = 24/256.0;
    kernel[2][4] = 6/256.0;
    kernel[3][0] = 4/256.0;
    kernel[3][1] = 16/256.0;
    kernel[3][2] = 24/256.0;
    kernel[3][3] = 16/256.0;
    kernel[3][4] = 4/256.0;
    kernel[4][0] = 1/256.0;
    kernel[4][1] = 4/256.0;
    kernel[4][2] = 6/256.0;
    kernel[4][3] = 4/256.0;
    kernel[4][4] = 1/256.0;
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
