package RayTracer;

public class Composer {

  int numCore;
  int num_composed;
  //int image[][];
  int heightPerCore;
  public long result;
  public long result1;

  public Composer(int numCore,
      int size) {
    this.numCore = numCore;
    this.num_composed = 0;
    heightPerCore = size/this.numCore;

    // set image size
    //this.image=new int[size][];
    this.result = 0;
    this.result1 = 0;
  }

  public boolean compose(TestRunner tr) {
    this.num_composed++;
    int startidx=0; //heightPerCore * tr.id;
    int endidx=this.heightPerCore; //startidx + heightPerCore;
    for(int i = startidx; i < endidx; i++) {
      //this.image[i] = tr.image[i];
      for(int j = 0; j < this.heightPerCore*this.numCore; j++) {
        this.result += tr.image[i][j];
      }
    }
    this.result1 += tr.checksum;
    return this.num_composed == this.numCore;
  }
}