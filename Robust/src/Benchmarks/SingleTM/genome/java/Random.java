public class Random {
  long[] mt; 
  int mti;
  long RANDOM_DEFAULT_SEED;
  /* period parameter */
  int N;
  int M;
  long MATRIX_A;
  long UPPER_MASK;
  long LOWER_MASK;

  public Random() {
    RANDOM_DEFAULT_SEED = 0L;
    N = 624;
    M = 397;
    mt = new long[N];
    mti = 0;
    MATRIX_A = 0x9908b0dfL;   /* constant vector a */
    UPPER_MASK = 0x80000000L; /* most significant w-r bits */
    LOWER_MASK = 0x7fffffffL; /* least significant r bits */
  }

  public Random random_alloc(Random rand) {
    init_genrand(rand, rand.RANDOM_DEFAULT_SEED);
    return rand;
  }

  /* initializes mt[N] with a seed */
  public void init_genrand(Random rand, long s) {
    int mti;

    rand.mt[0]= s & 0xFFFFFFFFL;
    for (mti=1; mti<rand.N; mti++) {
     rand.mt[mti] = (1812433253L * (rand.mt[mti-1] ^ (rand.mt[mti-1] >> 30)) + mti);
      /* See Knuth TAOCP Vol2. 3rd Ed. P.106 for multiplier. */
      /* In the previous versions, MSBs of the seed affect   */
      /* only MSBs of the array mt[].                        */
      /* 2002/01/09 modified by Makoto Matsumoto             */
      rand.mt[mti] &= 0xFFFFFFFFL;
      /* for >32 bit machines */
    }
  
    rand.mti = mti;
  }

  public void random_seed(Random rand, long seed) {
    init_genrand(rand, seed);
  }

  public long random_generate(Random rand) {
    return genrand_int32(rand);
  }

  //public static long genrand_int32(long[] mt, long mtiPtr) {
  public long genrand_int32(Random rand) {
    long y;
    long[] mag01= new long[2];
    mag01[0] = 0x0L;
    mag01[1] = rand.MATRIX_A;
    int mti = rand.mti;

    /* mag01[x] = x * MATRIX_A  for x=0,1 */

    if (mti >= rand.N) { /* generate N words at one time */
      int kk;

      if (mti == rand.N+1)   /* if init_genrand() has not been called, */
        init_genrand(rand, 5489L); /* a default initial seed is used */

      for (kk=0;kk<rand.N-rand.M;kk++) {
        y = (rand.mt[kk]&rand.UPPER_MASK)|(rand.mt[kk+1]&LOWER_MASK);
        rand.mt[kk] = rand.mt[kk+M] ^ (y >> 1) ^ mag01[(int)(y & 0x1L)];
      }
      for (;kk<rand.N-1;kk++) {
        y = (rand.mt[kk]&rand.UPPER_MASK)|(rand.mt[kk+1]&LOWER_MASK);
        rand.mt[kk] = rand.mt[kk+(M-N)] ^ (y >> 1) ^ mag01[(int)(y & 0x1L)];
      }
      y = (rand.mt[N-1]&rand.UPPER_MASK)|(rand.mt[0]&LOWER_MASK);
      rand.mt[N-1] = rand.mt[M-1] ^ (y >> 1) ^ mag01[(int)(y & 0x1L)];

      mti = 0;
    }

    y = rand.mt[mti++];

    /* Tempering */
    y ^= (y >> 11);
    y ^= (y << 7) & 0x9d2c5680L;
    y ^= (y << 15) & 0xefc60000L;
    y ^= (y >> 18);

    rand.mti = mti;

    return y;
  }
}
