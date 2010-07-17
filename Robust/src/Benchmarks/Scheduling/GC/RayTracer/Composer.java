public class Composer {

  flag compose;

  int numCore;
  int num_composed;
  int image[][];
  int heightPerCore;

  public Composer(int numCore,
                  int size) {
    this.numCore = numCore;
    this.num_composed = 0;
    heightPerCore = size/this.numCore;

    // set image size
    this.image=new int[size][];
  }
  
  public boolean compose(TestRunner tr) {
    this.num_composed++;
    int startidx=heightPerCore * tr.id;
    int endidx=startidx + heightPerCore;
    for(int i = startidx; i < endidx; i++) {
      //this.image[i] = tr.image[i];
    }
    return this.num_composed == this.numCore;
  }
}