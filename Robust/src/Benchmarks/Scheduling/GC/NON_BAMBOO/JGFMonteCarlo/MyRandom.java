package JGFMonteCarlo;

public class MyRandom {

  public int iseed;
  public float v1,v2;

  public MyRandom(int iseed, float v1, float v2) {
    this.iseed = iseed;
    this.v1 = v1;
    this.v2 = v2;
  }

  public float update() {
    float rand;
    float scale= (float)4.656612875e-10;

    int is1,is2,iss2;
    int imult= 16807;
    int imod = 2147483647;
    int seed = this.iseed;

    if (seed<=0) { 
      iseed = seed = 1; 
    }

    is2 = seed % 32768;
    is1 = seed / 32768;
    iss2 = is2 * imult;
    is2 = iss2 % 32768;
    is1 = (is1 * imult + iss2 / 32768) % (65536);

    iseed = seed = (is1 * 32768 + is2) % imod;

    rand = scale * seed;

    return rand;

  }

  public float seed() {

    float s,u1,u2,r;
    s = (float)1.0;
    //do {
    u1 = update();
    u2 = update();

    v1 = (float)2.0 * u1 - (float)1.0;
    v2 = (float)2.0 * u2 - (float)1.0;
    s = v1*v1 + v2*v2;
    //} while (s >= (float)1.0);
    s = s - (int)s;
    //System.printI(0xb4);
    r = Math.sqrtf((float)(-2.0*Math.logf(s))/(float)s);
    //System.printI(0xb5);
    return r;

  }
}
