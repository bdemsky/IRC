public class Convolution {
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
    double tempinput[][] = img.inputImage;
    double tempout[][] = img.outputImage;

    double tinput0[] = tempinput[x0];
    double tinput1[] = tempinput[x0+1];
    double tinput2[] = tempinput[x0+2];
    double tinput3[] = tempinput[x0+3];
    double tinput4[] = tempinput[x0+4];

    for(int i=x0;i<x1;++i){
      double tout[] = tempout[x0];
      for(int j=y0;j<y1;++j){
        tout[y0] = 0;
        for(int b=0;b<kernelHeight;++b){
          tout[y0] = tout[y0] + (tinput0[j+b] * kernel[0][b] + tinput1[j+b] * kernel[1][b] + tinput2[j+b]*kernel[2][b] +
              tinput3[j+b]*kernel[3][b] + tinput4[j+b]*kernel[4][b]);
        }
      }
      if(i != 4095) {
        tinput0 = tinput1; tinput1=tinput2; tinput2=tinput3; tinput3=tinput4; tinput4=tempinput[i+5];
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

    Image img;
    Convolution[] conv;
    Convolution tmp;

    int kernelHeight = 5;
    int kernelWidth = 5;
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

    for(int i = 0; i <NUM_THREADS; i++) {
      tmp = conv[i];
      tmp.run();
    }

    System.printString("Done!");
  }

  //define 5X5 Gaussian kernel
  public void initKernel(double[][] kernel) {
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
