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
    int kernelHeight=14;
    int kernelWidth=kernelHeight;

    float[][] kernel = new float[kernelHeight][kernelWidth];
    /**
     * Note :Used in ECOOP.DSM.2010 results
     **/
     initKernel14(kernel);
    //initKernel15(kernel);
    //initKernel13(kernel);
    //initKernel12(kernel);

    atomic {
	int myx0=x0;
	int myy0=y0;
	int myx1=x1;
	int myy1=y1;

      float tempinput[][] = img.inputImage;
      float tempout[][] = img.outputImage;

      float tinput1[] = tempinput[myx0+0];
      float tinput2[] = tempinput[myx0+1];
      float tinput3[] = tempinput[myx0+2];
      float tinput4[] = tempinput[myx0+3];
      float tinput5[] = tempinput[myx0+4];
      float tinput6[] = tempinput[myx0+5];
      float tinput7[] = tempinput[myx0+6];
      float tinput8[] = tempinput[myx0+7];
      float tinput9[] = tempinput[myx0+8];
      float tinput10[] = tempinput[myx0+9];
      float tinput11[] = tempinput[myx0+10];
      float tinput12[] = tempinput[myx0+11];
      float tinput13[] = tempinput[myx0+12];
      float tinput14[] = tempinput[myx0+13];
      float tinput0[] = tinput1;

      int l;
      if(kernelHeight==15){
          l=myx0+14;
          for(int i=myx0;i<myx1;i++,l++){
            float tout[] = tempout[i];
            for(int j=myy0;j<myy1;++j){
              float s=0;
              for(int b=0;b<kernelHeight;++b) {
                s+=(tinput0[j+b] * kernel[0][b] + tinput1[j+b] * kernel[1][b] + tinput2[j+b]*kernel[2][b] +
                    tinput3[j+b]*kernel[3][b] + tinput4[j+b]*kernel[4][b] + tinput5[j+b]*kernel[5][b]+ 
                    tinput6[j+b]*kernel[6][b] + tinput7[j+b]*kernel[7][b] + tinput8[j+b]*kernel[8][b]+
                    tinput9[j+b]*kernel[9][b] + tinput10[j+b]*kernel[10][b] + tinput11[j+b]*kernel[11][b]+
                    tinput12[j+b]*kernel[12][b]+ tinput13[j+b]*kernel[13][b] + tinput14[j+b]*kernel[14][b]);
              }
              tout[j]=s;
            }
            tinput0 = tinput1; tinput1=tinput2; tinput2=tinput3; tinput3=tinput4; tinput4=tinput5;
            tinput5 = tinput6; tinput6=tinput7; tinput7=tinput8; tinput8=tinput9; tinput9=tinput10; 
            tinput10 = tinput11; tinput11=tinput12; tinput12=tinput13; tinput13=tinput14; tinput14=tempinput[l];
          } 
      }else if(kernelHeight==14){
           l=myx0+13;
          for(int i=myx0;i<myx1;i++,l++){
            float tout[] = tempout[i];
            for(int j=myy0;j<myy1;++j){
              float s=0;
              for(int b=0;b<kernelHeight;++b) {
                s+=(tinput0[j+b] * kernel[0][b] + tinput1[j+b] * kernel[1][b] + tinput2[j+b]*kernel[2][b] +
                    tinput3[j+b]*kernel[3][b] + tinput4[j+b]*kernel[4][b] + tinput5[j+b]*kernel[5][b]+ 
                    tinput6[j+b]*kernel[6][b] + tinput7[j+b]*kernel[7][b] + tinput8[j+b]*kernel[8][b]+
                    tinput9[j+b]*kernel[9][b] + tinput10[j+b]*kernel[10][b] + tinput11[j+b]*kernel[11][b]+
                    tinput12[j+b]*kernel[12][b]+ tinput13[j+b]*kernel[13][b]);
              }
              tout[j]=s;
            }
            tinput0 = tinput1; tinput1=tinput2; tinput2=tinput3; tinput3=tinput4; tinput4=tinput5;
            tinput5 = tinput6; tinput6=tinput7; tinput7=tinput8; tinput8=tinput9; tinput9=tinput10; 
            tinput10 = tinput11; tinput11=tinput12; tinput12=tinput13; tinput13=tempinput[l];
          }
          }else if(kernelHeight==13){
        	      l=myx0+12;
                 for(int i=myx0;i<myx1;i++,l++){
                   float tout[] = tempout[i];
                   for(int j=myy0;j<myy1;++j){
                     float s=0;
                     for(int b=0;b<kernelHeight;++b) {
                       s+=(tinput0[j+b] * kernel[0][b] + tinput1[j+b] * kernel[1][b] + tinput2[j+b]*kernel[2][b] +
                           tinput3[j+b]*kernel[3][b] + tinput4[j+b]*kernel[4][b] + tinput5[j+b]*kernel[5][b]+ 
                           tinput6[j+b]*kernel[6][b] + tinput7[j+b]*kernel[7][b] + tinput8[j+b]*kernel[8][b]+
                           tinput9[j+b]*kernel[9][b] + tinput10[j+b]*kernel[10][b] + tinput11[j+b]*kernel[11][b]+
                           tinput12[j+b]*kernel[12][b]);
                     }
                     tout[j]=s;
                   }
                   tinput0 = tinput1; tinput1=tinput2; tinput2=tinput3; tinput3=tinput4; tinput4=tinput5;
                   tinput5 = tinput6; tinput6=tinput7; tinput7=tinput8; tinput8=tinput9; tinput9=tinput10; 
                   tinput10 = tinput11; tinput11=tinput12; tinput12=tempinput[l];
                 }
      }else if(kernelHeight==12){
 	      l=myx0+11;
         for(int i=myx0;i<myx1;i++,l++){
           float tout[] = tempout[i];
           for(int j=myy0;j<myy1;++j){
             float s=0;
             for(int b=0;b<kernelHeight;++b) {
               s+=(tinput0[j+b] * kernel[0][b] + tinput1[j+b] * kernel[1][b] + tinput2[j+b]*kernel[2][b] +
                   tinput3[j+b]*kernel[3][b] + tinput4[j+b]*kernel[4][b] + tinput5[j+b]*kernel[5][b]+ 
                   tinput6[j+b]*kernel[6][b] + tinput7[j+b]*kernel[7][b] + tinput8[j+b]*kernel[8][b]+
                   tinput9[j+b]*kernel[9][b] + tinput10[j+b]*kernel[10][b] + tinput11[j+b]*kernel[11][b]);
             }
             tout[j]=s;
           }
           tinput0 = tinput1; tinput1=tinput2; tinput2=tinput3; tinput3=tinput4; tinput4=tinput5;
           tinput5 = tinput6; tinput6=tinput7; tinput7=tinput8; tinput8=tinput9; tinput9=tinput10; 
           tinput10 = tinput11; tinput11=tempinput[l];
         }
    	  
      }
    	  
      }
      
  }


  public static void main(String[] args) {
    int WIDTH = 256;
    int HEIGHT = 256;
    int NUM_THREADS = 1;
    
    int kernelHeight=14; 
    int kernelWidth=kernelHeight;

    if(args.length>0) {
      NUM_THREADS = Integer.parseInt(args[0]);
      if(args.length>1) {
        WIDTH = Integer.parseInt(args[1]);
        if(args.length>2) 
          HEIGHT = Integer.parseInt(args[2]);
      }
    }

    int[] mid = new int[8];
    mid[0] = (128<<24)|(195<<16)|(136<<8)|162; //dc-1
    mid[1] = (128<<24)|(195<<16)|(136<<8)|163; //dc-2
    mid[2] = (128<<24)|(195<<16)|(136<<8)|164; //dc-3
    mid[3] = (128<<24)|(195<<16)|(136<<8)|165; //dc-4
    mid[4] = (128<<24)|(195<<16)|(136<<8)|166; //dc-5
    mid[5] = (128<<24)|(195<<16)|(136<<8)|167; //dc-6
    mid[6] = (128<<24)|(195<<16)|(136<<8)|168; //dc-7
    mid[7] = (128<<24)|(195<<16)|(136<<8)|169; //dc-8

    Image img;
    Convolution[] conv;
    Convolution tmp;

    atomic {
      img = global new Image(HEIGHT,WIDTH,kernelHeight,kernelWidth);
      img.setValues();
      conv = global new Convolution[NUM_THREADS];
      int increment=HEIGHT/NUM_THREADS;
      int base = 0;
      for(int i = 0; i<NUM_THREADS; i++) {
        if((i+1)==NUM_THREADS)
          conv[i] = global new Convolution(img, base, HEIGHT, 0, WIDTH);
        else 
          conv[i] = global new Convolution(img, base, base+increment, 0, WIDTH);
        base+=increment;
      }
    }
 
    /*
    atomic{
      System.printString("img.outputImage[10][20] = " +(int) img.outputImage[10][20] + "\n");
      System.printString("img.outputImage[256][890] = " +(int) img.outputImage[256][890] + "\n");
    }
    */

	System.printString("Convolution: HEIGHT= ");
    System.printInt(HEIGHT);
    System.out.println(" WIDTH= " + WIDTH);
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

  //define 12X12 Gaussian kernel
  public static void initKernel12(float[][] kernel) {
    kernel[0][0] = 1/256.0f;
    kernel[0][1] = 4/256.f;
    kernel[0][2] = 6/256.f;
    kernel[0][3] = 8/256.f;
    kernel[0][4] = 10/256.f;
    kernel[0][5] = 12/256.f;
    kernel[0][6] = 12/256.f;
    kernel[0][7] = 10/256.f;
    kernel[0][8] = 8/256.0f;
    kernel[0][9] = 6/256.0f;
    kernel[0][10] = 4/256.0f;
    kernel[0][11] = 1/256.0f;

    kernel[1][0] = 4/256.0f;
    kernel[1][1] = 16/256.0f;
    kernel[1][2] = 24/256.0f;
    kernel[1][3] = 32/256.0f;
    kernel[1][4] = 40/256.0f;
    kernel[1][5] = 48/256.0f;
    kernel[1][6] = 48/256.0f;
    kernel[1][7] = 40/256.0f;
    kernel[1][8] = 32/256.0f;
    kernel[1][9] = 24/256.0f;
    kernel[1][10] = 16/256.0f;
    kernel[1][11] = 4/256.0f;

    kernel[2][0] = 6/256.0f;
    kernel[2][1] = 24/256.0f;
    kernel[2][2] = 36/256.0f;
    kernel[2][3] = 48/256.0f;
    kernel[2][4] = 60/256.0f;
    kernel[2][5] = 72/256.0f;
    kernel[2][6] = 84/256.0f;
    kernel[2][7] = 96/256.0f;
    kernel[2][8] = 84/256.0f;
    kernel[2][9] = 72/256.0f;
    kernel[2][10] = 60/256.0f;
    kernel[2][11] = 48/256.0f;

    kernel[3][0] = 8/256.0f;
    kernel[3][1] = 32/256.0f;
    kernel[3][2] = 48/256.0f;
    kernel[3][3] = 64/256.0f;
    kernel[3][4] = 80/256.0f;
    kernel[3][5] = 96/256.0f;
    kernel[3][6] = 112/256.0f;
    kernel[3][7] = 128/256.0f;
    kernel[3][8] = 112/256.0f;
    kernel[3][9] = 96/256.0f;
    kernel[3][10] = 80/256.0f;
    kernel[3][11] = 64/256.0f;


    kernel[4][0] = 10/256.0f;
    kernel[4][1] = 40/256.0f;
    kernel[4][2] = 60/256.0f;
    kernel[4][3] = 80/256.0f;
    kernel[4][4] = 100/256.0f;
    kernel[4][5] = 120/256.0f;
    kernel[4][6] = 140/256.0f;
    kernel[4][7] = 160/256.0f;
    kernel[4][8] = 140/256.0f;
    kernel[4][9] = 120/256.0f;
    kernel[4][10] = 100/256.0f;
    kernel[4][11] = 80/256.0f;

    kernel[5][0] = 12/256.0f;
    kernel[5][1] = 48/256.0f;
    kernel[5][2] = 72/256.0f;
    kernel[5][3] = 96/256.0f;
    kernel[5][4] = 120/256.0f;
    kernel[5][5] = 144/256.0f;
    kernel[5][6] = 168/256.0f;
    kernel[5][7] = 192/256.0f;
    kernel[5][8] = 168/256.0f;
    kernel[5][9] = 144/256.0f;
    kernel[5][10] = 120/256.0f;
    kernel[5][11] = 96/256.0f;

    kernel[6][0] = 14/256.0f;
    kernel[6][1] = 56/256.0f;
    kernel[6][2] = 84/256.0f;
    kernel[6][3] = 112/256.0f;
    kernel[6][4] = 140/256.0f;
    kernel[6][5] = 168/256.0f;
    kernel[6][6] = 196/256.0f;
    kernel[6][7] = 224/256.0f;
    kernel[6][8] = 196/256.0f;
    kernel[6][9] = 168/256.0f;
    kernel[6][10] = 140/256.0f;
    kernel[6][11] = 112/256.0f;

    kernel[7][0] = 16/256.0f;
    kernel[7][1] = 64/256.0f;
    kernel[7][2] = 96/256.0f;
    kernel[7][3] = 128/256.0f;
    kernel[7][4] = 160/256.0f;
    kernel[7][5] = 192/256.0f;
    kernel[7][6] = 224/256.0f;
    kernel[7][7] = 256/256.0f;
    kernel[7][8] = 224/256.0f;
    kernel[7][9] = 192/256.0f;
    kernel[7][10] = 160/256.0f;
    kernel[7][11] = 128/256.0f;

    kernel[8][0] = 14/256.0f;
    kernel[8][1] = 56/256.0f;
    kernel[8][2] = 84/256.0f;
    kernel[8][3] = 112/256.0f;
    kernel[8][4] = 140/256.0f;
    kernel[8][5] = 168/256.0f;
    kernel[8][6] = 196/256.0f;
    kernel[8][7] = 224/256.0f;
    kernel[8][8] = 196/256.0f;
    kernel[8][9] = 168/256.0f;
    kernel[8][10] = 140/256.0f;
    kernel[8][11] = 112/256.0f;

    kernel[9][0] = 12/256.0f;
    kernel[9][1] = 48/256.0f;
    kernel[9][2] = 72/256.0f;
    kernel[9][3] = 96/256.0f;
    kernel[9][4] = 120/256.0f;
    kernel[9][5] = 144/256.0f;
    kernel[9][6] = 168/256.0f;
    kernel[9][7] = 192/256.0f;
    kernel[9][8] = 168/256.0f;
    kernel[9][9] = 144/256.0f;
    kernel[9][10] = 120/256.0f;
    kernel[9][11] = 96/256.0f;

    kernel[10][0] = 10/256.0f;
    kernel[10][1] = 40/256.0f;
    kernel[10][2] = 60/256.0f;
    kernel[10][3] = 80/256.0f;
    kernel[10][4] = 100/256.0f;
    kernel[10][5] = 120/256.0f;
    kernel[10][6] = 140/256.0f;
    kernel[10][7] = 160/256.0f;
    kernel[10][8] = 140/256.0f;
    kernel[10][9] = 120/256.0f;
    kernel[10][10] = 100/256.0f;
    kernel[10][11] = 80/256.0f;

    kernel[11][0] = 8/256.0f;
    kernel[11][1] = 32/256.0f;
    kernel[11][2] = 48/256.0f;
    kernel[11][3] = 64/256.0f;
    kernel[11][4] = 80/256.0f;
    kernel[11][5] = 96/256.0f;
    kernel[11][6] = 112/256.0f;
    kernel[11][7] = 128/256.0f;
    kernel[11][8] = 112/256.0f;
    kernel[11][9] = 96/256.0f;
    kernel[11][10] = 80/256.0f;
    kernel[11][11] = 64/256.0f;
  }

  //define 13X13 Gaussian kernel
  public static void initKernel13(float[][] kernel) {
    kernel[0][0] = 1/256.0f;
    kernel[0][1] = 4/256.f;
    kernel[0][2] = 6/256.f;
    kernel[0][3] = 8/256.f;
    kernel[0][4] = 10/256.f;
    kernel[0][5] = 12/256.f;
    kernel[0][6] = 14/256.f;
    kernel[0][7] = 12/256.f;
    kernel[0][8] = 10/256.0f;
    kernel[0][9] = 8/256.0f;
    kernel[0][10] = 6/256.0f;
    kernel[0][11] = 4/256.0f;
    kernel[0][12] = 1/256.0f;

    kernel[1][0] = 4/256.0f;
    kernel[1][1] = 16/256.0f;
    kernel[1][2] = 24/256.0f;
    kernel[1][3] = 32/256.0f;
    kernel[1][4] = 40/256.0f;
    kernel[1][5] = 48/256.0f;
    kernel[1][6] = 56/256.0f;
    kernel[1][7] = 48/256.0f;
    kernel[1][8] = 40/256.0f;
    kernel[1][9] = 32/256.0f;
    kernel[1][10] = 24/256.0f;
    kernel[1][11] = 16/256.0f;
    kernel[1][12] = 4/256.0f;

    kernel[2][0] = 6/256.0f;
    kernel[2][1] = 24/256.0f;
    kernel[2][2] = 36/256.0f;
    kernel[2][3] = 48/256.0f;
    kernel[2][4] = 60/256.0f;
    kernel[2][5] = 72/256.0f;
    kernel[2][6] = 84/256.0f;
    kernel[2][7] = 72/256.0f;
    kernel[2][8] = 60/256.0f;
    kernel[2][9] = 48/256.0f;
    kernel[2][10] = 36/256.0f;
    kernel[2][11] = 24/256.0f;
    kernel[2][12] = 6/256.0f;

    kernel[3][0] = 8/256.0f;
    kernel[3][1] = 32/256.0f;
    kernel[3][2] = 48/256.0f;
    kernel[3][3] = 64/256.0f;
    kernel[3][4] = 80/256.0f;
    kernel[3][5] = 96/256.0f;
    kernel[3][6] = 112/256.0f;
    kernel[3][7] = 96/256.0f;
    kernel[3][8] = 80/256.0f;
    kernel[3][9] = 64/256.0f;
    kernel[3][10] = 48/256.0f;
    kernel[3][11] = 32/256.0f;
    kernel[3][12] = 8/256.0f;

    kernel[4][0] = 10/256.0f;
    kernel[4][1] = 40/256.0f;
    kernel[4][2] = 60/256.0f;
    kernel[4][3] = 80/256.0f;
    kernel[4][4] = 100/256.0f;
    kernel[4][5] = 120/256.0f;
    kernel[4][6] = 140/256.0f;
    kernel[4][7] = 120/256.0f;
    kernel[4][8] = 100/256.0f;
    kernel[4][9] = 80/256.0f;
    kernel[4][10] = 60/256.0f;
    kernel[4][11] = 40/256.0f;
    kernel[4][12] = 10/256.0f;

    kernel[5][0] = 12/256.0f;
    kernel[5][1] = 48/256.0f;
    kernel[5][2] = 72/256.0f;
    kernel[5][3] = 96/256.0f;
    kernel[5][4] = 120/256.0f;
    kernel[5][5] = 144/256.0f;
    kernel[5][6] = 168/256.0f;
    kernel[5][7] = 144/256.0f;
    kernel[5][8] = 120/256.0f;
    kernel[5][9] = 96/256.0f;
    kernel[5][10] = 72/256.0f;
    kernel[5][11] = 48/256.0f;
    kernel[5][12] = 12/256.0f;

    kernel[6][0] = 14/256.0f;
    kernel[6][1] = 56/256.0f;
    kernel[6][2] = 84/256.0f;
    kernel[6][3] = 112/256.0f;
    kernel[6][4] = 140/256.0f;
    kernel[6][5] = 168/256.0f;
    kernel[6][6] = 196/256.0f;
    kernel[6][7] = 168/256.0f;
    kernel[6][8] = 140/256.0f;
    kernel[6][9] = 112/256.0f;
    kernel[6][10] = 84/256.0f;
    kernel[6][11] = 56/256.0f;
    kernel[6][12] = 14/256.0f;

    kernel[7][0] = 12/256.0f;
    kernel[7][1] = 48/256.0f;
    kernel[7][2] = 72/256.0f;
    kernel[7][3] = 96/256.0f;
    kernel[7][4] = 120/256.0f;
    kernel[7][5] = 144/256.0f;
    kernel[7][6] = 168/256.0f;
    kernel[7][7] = 144/256.0f;
    kernel[7][8] = 120/256.0f;
    kernel[7][9] = 96/256.0f;
    kernel[7][10] = 72/256.0f;
    kernel[7][11] = 48/256.0f;
    kernel[7][12] = 12/256.0f;

    kernel[8][0] = 10/256.0f;
    kernel[8][1] = 40/256.0f;
    kernel[8][2] = 60/256.0f;
    kernel[8][3] = 80/256.0f;
    kernel[8][4] = 100/256.0f;
    kernel[8][5] = 120/256.0f;
    kernel[8][6] = 140/256.0f;
    kernel[8][7] = 120/256.0f;
    kernel[8][8] = 100/256.0f;
    kernel[8][9] = 80/256.0f;
    kernel[8][10] = 60/256.0f;
    kernel[8][11] = 40/256.0f;
    kernel[8][12] = 10/256.0f;

    kernel[9][0] = 8/256.0f;
    kernel[9][1] = 32/256.0f;
    kernel[9][2] = 48/256.0f;
    kernel[9][3] = 64/256.0f;
    kernel[9][4] = 80/256.0f;
    kernel[9][5] = 96/256.0f;
    kernel[9][6] = 112/256.0f;
    kernel[9][7] = 96/256.0f;
    kernel[9][8] = 80/256.0f;
    kernel[9][9] = 64/256.0f;
    kernel[9][10] = 48/256.0f;
    kernel[9][11] = 32/256.0f;
    kernel[9][12] = 8/256.0f;

    kernel[10][0] = 6/256.0f;
    kernel[10][1] = 24/256.0f;
    kernel[10][2] = 36/256.0f;
    kernel[10][3] = 48/256.0f;
    kernel[10][4] = 60/256.0f;
    kernel[10][5] = 72/256.0f;
    kernel[10][6] = 84/256.0f;
    kernel[10][7] = 72/256.0f;
    kernel[10][8] = 60/256.0f;
    kernel[10][9] = 48/256.0f;
    kernel[10][10] = 36/256.0f;
    kernel[10][11] = 24/256.0f;
    kernel[10][12] = 6/256.0f;

    kernel[11][0] = 4/256.0f;
    kernel[11][1] = 16/256.0f;
    kernel[11][2] = 24/256.0f;
    kernel[11][3] = 32/256.0f;
    kernel[11][4] = 40/256.0f;
    kernel[11][5] = 48/256.0f;
    kernel[11][6] = 56/256.0f;
    kernel[11][7] = 48/256.0f;
    kernel[11][8] = 40/256.0f;
    kernel[11][9] = 32/256.0f;
    kernel[11][10] = 24/256.0f;
    kernel[11][11] = 16/256.0f;
    kernel[11][12] = 4/256.0f;

    kernel[12][0] = 1/256.0f;
    kernel[12][1] = 4/256.f;
    kernel[12][2] = 6/256.f;
    kernel[12][3] = 8/256.f;
    kernel[12][4] = 10/256.f;
    kernel[12][5] = 12/256.f;
    kernel[12][6] = 14/256.f;
    kernel[12][7] = 12/256.f;
    kernel[12][8] = 10/256.0f;
    kernel[12][9] = 8/256.0f;
    kernel[12][10] = 6/256.0f;
    kernel[12][11] = 4/256.0f;
    kernel[12][12] = 1/256.0f;
  }

  //define 14X14 Gaussian kernel
  public static void initKernel14(float[][] kernel) {
    kernel[0][0] = 1/256.0f;
    kernel[0][1] = 4/256.f;
    kernel[0][2] = 6/256.f;
    kernel[0][3] = 8/256.f;
    kernel[0][4] = 10/256.f;
    kernel[0][5] = 12/256.f;
    kernel[0][6] = 14/256.f;
    kernel[0][7] = 16/256.f;
    kernel[0][8] = 14/256.0f;
    kernel[0][9] = 12/256.0f;
    kernel[0][10] = 10/256.0f;
    kernel[0][11] = 8/256.0f;
    kernel[0][12] = 6/256.0f;
    kernel[0][13] = 4/256.0f;

    kernel[1][0] = 4/256.0f;
    kernel[1][1] = 16/256.0f;
    kernel[1][2] = 24/256.0f;
    kernel[1][3] = 32/256.0f;
    kernel[1][4] = 40/256.0f;
    kernel[1][5] = 48/256.0f;
    kernel[1][6] = 56/256.0f;
    kernel[1][7] = 64/256.0f;
    kernel[1][8] = 56/256.0f;
    kernel[1][9] = 48/256.0f;
    kernel[1][10] = 40/256.0f;
    kernel[1][11] = 32/256.0f;
    kernel[1][12] = 24/256.0f;
    kernel[1][13] = 16/256.0f;

    kernel[2][0] = 6/256.0f;
    kernel[2][1] = 24/256.0f;
    kernel[2][2] = 36/256.0f;
    kernel[2][3] = 48/256.0f;
    kernel[2][4] = 60/256.0f;
    kernel[2][5] = 72/256.0f;
    kernel[2][6] = 84/256.0f;
    kernel[2][7] = 96/256.0f;
    kernel[2][8] = 84/256.0f;
    kernel[2][9] = 72/256.0f;
    kernel[2][10] = 60/256.0f;
    kernel[2][11] = 48/256.0f;
    kernel[2][12] = 36/256.0f;
    kernel[2][13] = 24/256.0f;

    kernel[3][0] = 8/256.0f;
    kernel[3][1] = 32/256.0f;
    kernel[3][2] = 48/256.0f;
    kernel[3][3] = 64/256.0f;
    kernel[3][4] = 80/256.0f;
    kernel[3][5] = 96/256.0f;
    kernel[3][6] = 112/256.0f;
    kernel[3][7] = 128/256.0f;
    kernel[3][8] = 112/256.0f;
    kernel[3][9] = 96/256.0f;
    kernel[3][10] = 80/256.0f;
    kernel[3][11] = 64/256.0f;
    kernel[3][12] = 48/256.0f;
    kernel[3][13] = 32/256.0f;

    kernel[4][0] = 10/256.0f;
    kernel[4][1] = 40/256.0f;
    kernel[4][2] = 60/256.0f;
    kernel[4][3] = 80/256.0f;
    kernel[4][4] = 100/256.0f;
    kernel[4][5] = 120/256.0f;
    kernel[4][6] = 140/256.0f;
    kernel[4][7] = 160/256.0f;
    kernel[4][8] = 140/256.0f;
    kernel[4][9] = 120/256.0f;
    kernel[4][10] = 100/256.0f;
    kernel[4][11] = 80/256.0f;
    kernel[4][12] = 60/256.0f;
    kernel[4][13] = 40/256.0f;

    kernel[5][0] = 12/256.0f;
    kernel[5][1] = 48/256.0f;
    kernel[5][2] = 72/256.0f;
    kernel[5][3] = 96/256.0f;
    kernel[5][4] = 120/256.0f;
    kernel[5][5] = 144/256.0f;
    kernel[5][6] = 168/256.0f;
    kernel[5][7] = 192/256.0f;
    kernel[5][8] = 168/256.0f;
    kernel[5][9] = 144/256.0f;
    kernel[5][10] = 120/256.0f;
    kernel[5][11] = 96/256.0f;
    kernel[5][12] = 72/256.0f;
    kernel[5][13] = 48/256.0f;

    kernel[6][0] = 14/256.0f;
    kernel[6][1] = 56/256.0f;
    kernel[6][2] = 84/256.0f;
    kernel[6][3] = 112/256.0f;
    kernel[6][4] = 140/256.0f;
    kernel[6][5] = 168/256.0f;
    kernel[6][6] = 196/256.0f;
    kernel[6][7] = 224/256.0f;
    kernel[6][8] = 196/256.0f;
    kernel[6][9] = 168/256.0f;
    kernel[6][10] = 140/256.0f;
    kernel[6][11] = 112/256.0f;
    kernel[6][12] = 84/256.0f;
    kernel[6][13] = 56/256.0f;

    kernel[7][0] = 12/256.0f;
    kernel[7][1] = 48/256.0f;
    kernel[7][2] = 72/256.0f;
    kernel[7][3] = 96/256.0f;
    kernel[7][4] = 120/256.0f;
    kernel[7][5] = 144/256.0f;
    kernel[7][6] = 168/256.0f;
    kernel[7][7] = 192/256.0f;
    kernel[7][8] = 168/256.0f;
    kernel[7][9] = 144/256.0f;
    kernel[7][10] = 120/256.0f;
    kernel[7][11] = 96/256.0f;
    kernel[7][12] = 72/256.0f;
    kernel[7][13] = 48/256.0f;

    kernel[8][0] = 10/256.0f;
    kernel[8][1] = 40/256.0f;
    kernel[8][2] = 60/256.0f;
    kernel[8][3] = 80/256.0f;
    kernel[8][4] = 100/256.0f;
    kernel[8][5] = 120/256.0f;
    kernel[8][6] = 140/256.0f;
    kernel[8][7] = 160/256.0f;
    kernel[8][8] = 140/256.0f;
    kernel[8][9] = 120/256.0f;
    kernel[8][10] = 100/256.0f;
    kernel[8][11] = 80/256.0f;
    kernel[8][12] = 60/256.0f;
    kernel[8][13] = 40/256.0f;

    kernel[10][0] = 8/256.0f;
    kernel[10][1] = 32/256.0f;
    kernel[10][2] = 48/256.0f;
    kernel[10][3] = 64/256.0f;
    kernel[10][4] = 80/256.0f;
    kernel[10][5] = 96/256.0f;
    kernel[10][6] = 112/256.0f;
    kernel[10][7] = 128/256.0f;
    kernel[10][8] = 112/256.0f;
    kernel[10][9] = 96/256.0f;
    kernel[10][10] = 80/256.0f;
    kernel[10][11] = 64/256.0f;
    kernel[10][12] = 48/256.0f;
    kernel[10][13] = 32/256.0f;

    kernel[11][0] = 6/256.0f;
    kernel[12][1] = 24/256.0f;
    kernel[12][2] = 36/256.0f;
    kernel[12][3] = 48/256.0f;
    kernel[12][4] = 60/256.0f;
    kernel[12][5] = 72/256.0f;
    kernel[12][6] = 84/256.0f;
    kernel[12][7] = 96/256.0f;
    kernel[12][8] = 84/256.0f;
    kernel[12][9] = 72/256.0f;
    kernel[12][10] = 60/256.0f;
    kernel[12][11] = 48/256.0f;
    kernel[12][12] = 36/256.0f;
    kernel[12][13] = 24/256.0f;

    kernel[13][0] = 4/256.0f;
    kernel[13][1] = 16/256.0f;
    kernel[13][2] = 24/256.0f;
    kernel[13][3] = 32/256.0f;
    kernel[13][4] = 40/256.0f;
    kernel[13][5] = 48/256.0f;
    kernel[13][6] = 56/256.0f;
    kernel[13][7] = 64/256.0f;
    kernel[13][8] = 56/256.0f;
    kernel[13][9] = 48/256.0f;
    kernel[13][10] = 40/256.0f;
    kernel[13][11] = 32/256.0f;
    kernel[13][12] = 24/256.0f;
    kernel[13][13] = 16/256.0f;
  }

  //define 15X15 Gaussian kernel
  public static void initKernel15(float[][] kernel) {
    kernel[0][0] = 1/256.0f;
    kernel[0][1] = 4/256.f;
    kernel[0][2] = 6/256.f;
    kernel[0][3] = 8/256.f;
    kernel[0][4] = 10/256.f;
    kernel[0][5] = 12/256.f;
    kernel[0][6] = 14/256.f;
    kernel[0][7] = 16/256.f;
    kernel[0][8] = 14/256.0f;
    kernel[0][9] = 12/256.0f;
    kernel[0][10] = 10/256.0f;
    kernel[0][11] = 8/256.0f;
    kernel[0][12] = 6/256.0f;
    kernel[0][13] = 4/256.0f;
    kernel[0][14] = 1/256.0f;

    kernel[1][0] = 4/256.0f;
    kernel[1][1] = 16/256.0f;
    kernel[1][2] = 24/256.0f;
    kernel[1][3] = 32/256.0f;
    kernel[1][4] = 40/256.0f;
    kernel[1][5] = 48/256.0f;
    kernel[1][6] = 56/256.0f;
    kernel[1][7] = 64/256.0f;
    kernel[1][8] = 56/256.0f;
    kernel[1][9] = 48/256.0f;
    kernel[1][10] = 40/256.0f;
    kernel[1][11] = 32/256.0f;
    kernel[1][12] = 24/256.0f;
    kernel[1][13] = 16/256.0f;
    kernel[1][14] = 4/256.0f;

    kernel[2][0] = 6/256.0f;
    kernel[2][1] = 24/256.0f;
    kernel[2][2] = 36/256.0f;
    kernel[2][3] = 48/256.0f;
    kernel[2][4] = 60/256.0f;
    kernel[2][5] = 72/256.0f;
    kernel[2][6] = 84/256.0f;
    kernel[2][7] = 96/256.0f;
    kernel[2][8] = 84/256.0f;
    kernel[2][9] = 72/256.0f;
    kernel[2][10] = 60/256.0f;
    kernel[2][11] = 48/256.0f;
    kernel[2][12] = 36/256.0f;
    kernel[2][13] = 24/256.0f;
    kernel[2][14] = 6/256.0f;

    kernel[3][0] = 8/256.0f;
    kernel[3][1] = 32/256.0f;
    kernel[3][2] = 48/256.0f;
    kernel[3][3] = 64/256.0f;
    kernel[3][4] = 80/256.0f;
    kernel[3][5] = 96/256.0f;
    kernel[3][6] = 112/256.0f;
    kernel[3][7] = 128/256.0f;
    kernel[3][8] = 112/256.0f;
    kernel[3][9] = 96/256.0f;
    kernel[3][10] = 80/256.0f;
    kernel[3][11] = 64/256.0f;
    kernel[3][12] = 48/256.0f;
    kernel[3][13] = 32/256.0f;
    kernel[3][14] = 8/256.0f;


    kernel[4][0] = 10/256.0f;
    kernel[4][1] = 40/256.0f;
    kernel[4][2] = 60/256.0f;
    kernel[4][3] = 80/256.0f;
    kernel[4][4] = 100/256.0f;
    kernel[4][5] = 120/256.0f;
    kernel[4][6] = 140/256.0f;
    kernel[4][7] = 160/256.0f;
    kernel[4][8] = 140/256.0f;
    kernel[4][9] = 120/256.0f;
    kernel[4][10] = 100/256.0f;
    kernel[4][11] = 80/256.0f;
    kernel[4][12] = 60/256.0f;
    kernel[4][13] = 40/256.0f;
    kernel[4][14] = 10/256.0f;

    kernel[5][0] = 12/256.0f;
    kernel[5][1] = 48/256.0f;
    kernel[5][2] = 72/256.0f;
    kernel[5][3] = 96/256.0f;
    kernel[5][4] = 120/256.0f;
    kernel[5][5] = 144/256.0f;
    kernel[5][6] = 168/256.0f;
    kernel[5][7] = 192/256.0f;
    kernel[5][8] = 168/256.0f;
    kernel[5][9] = 144/256.0f;
    kernel[5][10] = 120/256.0f;
    kernel[5][11] = 96/256.0f;
    kernel[5][12] = 72/256.0f;
    kernel[5][13] = 48/256.0f;
    kernel[5][14] = 12/256.0f;

    kernel[6][0] = 14/256.0f;
    kernel[6][1] = 56/256.0f;
    kernel[6][2] = 84/256.0f;
    kernel[6][3] = 112/256.0f;
    kernel[6][4] = 140/256.0f;
    kernel[6][5] = 168/256.0f;
    kernel[6][6] = 196/256.0f;
    kernel[6][7] = 224/256.0f;
    kernel[6][8] = 196/256.0f;
    kernel[6][9] = 168/256.0f;
    kernel[6][10] = 140/256.0f;
    kernel[6][11] = 112/256.0f;
    kernel[6][12] = 84/256.0f;
    kernel[6][13] = 56/256.0f;
    kernel[6][14] = 14/256.0f;

    kernel[7][0] = 12/256.0f;
    kernel[7][1] = 48/256.0f;
    kernel[7][2] = 72/256.0f;
    kernel[7][3] = 96/256.0f;
    kernel[7][4] = 120/256.0f;
    kernel[7][5] = 144/256.0f;
    kernel[7][6] = 168/256.0f;
    kernel[7][7] = 192/256.0f;
    kernel[7][8] = 168/256.0f;
    kernel[7][9] = 144/256.0f;
    kernel[7][10] = 120/256.0f;
    kernel[7][11] = 96/256.0f;
    kernel[7][12] = 72/256.0f;
    kernel[7][13] = 48/256.0f;
    kernel[7][14] = 12/256.0f;

    kernel[8][0] = 10/256.0f;
    kernel[8][1] = 40/256.0f;
    kernel[8][2] = 60/256.0f;
    kernel[8][3] = 80/256.0f;
    kernel[8][4] = 100/256.0f;
    kernel[8][5] = 120/256.0f;
    kernel[8][6] = 140/256.0f;
    kernel[8][7] = 160/256.0f;
    kernel[8][8] = 140/256.0f;
    kernel[8][9] = 120/256.0f;
    kernel[8][10] = 100/256.0f;
    kernel[8][11] = 80/256.0f;
    kernel[8][12] = 60/256.0f;
    kernel[8][13] = 40/256.0f;
    kernel[8][14] = 10/256.0f;

    kernel[10][0] = 8/256.0f;
    kernel[10][1] = 32/256.0f;
    kernel[10][2] = 48/256.0f;
    kernel[10][3] = 64/256.0f;
    kernel[10][4] = 80/256.0f;
    kernel[10][5] = 96/256.0f;
    kernel[10][6] = 112/256.0f;
    kernel[10][7] = 128/256.0f;
    kernel[10][8] = 112/256.0f;
    kernel[10][9] = 96/256.0f;
    kernel[10][10] = 80/256.0f;
    kernel[10][11] = 64/256.0f;
    kernel[10][12] = 48/256.0f;
    kernel[10][13] = 32/256.0f;
    kernel[10][14] = 8/256.0f;

    kernel[11][0] = 6/256.0f;
    kernel[12][1] = 24/256.0f;
    kernel[12][2] = 36/256.0f;
    kernel[12][3] = 48/256.0f;
    kernel[12][4] = 60/256.0f;
    kernel[12][5] = 72/256.0f;
    kernel[12][6] = 84/256.0f;
    kernel[12][7] = 96/256.0f;
    kernel[12][8] = 84/256.0f;
    kernel[12][9] = 72/256.0f;
    kernel[12][10] = 60/256.0f;
    kernel[12][11] = 48/256.0f;
    kernel[12][12] = 36/256.0f;
    kernel[12][13] = 24/256.0f;
    kernel[12][14] = 6/256.0f;

    kernel[13][0] = 4/256.0f;
    kernel[13][1] = 16/256.0f;
    kernel[13][2] = 24/256.0f;
    kernel[13][3] = 32/256.0f;
    kernel[13][4] = 40/256.0f;
    kernel[13][5] = 48/256.0f;
    kernel[13][6] = 56/256.0f;
    kernel[13][7] = 64/256.0f;
    kernel[13][8] = 56/256.0f;
    kernel[13][9] = 48/256.0f;
    kernel[13][10] = 40/256.0f;
    kernel[13][11] = 32/256.0f;
    kernel[13][12] = 24/256.0f;
    kernel[13][13] = 16/256.0f;
    kernel[13][14] = 4/256.0f;

    kernel[14][0] = 1/256.0f;
    kernel[14][1] = 4/256.0f;
    kernel[14][2] = 6/256.0f;
    kernel[14][3] = 8/256.0f;
    kernel[14][4] = 10/256.0f;
    kernel[14][5] = 12/256.0f;
    kernel[14][6] = 14/256.0f;
    kernel[14][7] = 16/256.0f;
    kernel[14][8] = 14/256.0f;
    kernel[14][9] = 12/256.0f;
    kernel[14][10] = 10/256.0f;
    kernel[14][11] = 8/256.0f;
    kernel[14][12] = 6/256.0f;
    kernel[14][13] = 4/256.0f;
    kernel[14][14] = 1/256.0f;
  }
}

public class Image {
  int width, height;
  int kernelWidth, kernelHeight;
  float[][] inputImage;
  float[][] outputImage;

  public Image(int height, int width, int kernelWidth, int kernelHeight) {
    this.width = width;
    this.height = height;
    this.kernelWidth = kernelWidth;
    this.kernelHeight = kernelHeight;
    inputImage = global new float[height+kernelHeight-1][width+kernelWidth-1];
    outputImage = global new float[height][width];
  }

  /* Create a valid image */
  public void setValues() {
    for (int i = 0; i < (height+kernelHeight - 1); i++) {
      float ainput[] = inputImage[i];
      for(int j = 0; j < (width+kernelWidth - 1); j++) {
        ainput[j] = 256-j;
      }
    }

    for (int i = 0; i < height; i++){
      float aout[] = outputImage[i];
      for(int j = 0; j < width; j++) {
        aout[j] = 0;
      }
    }
  }
}
