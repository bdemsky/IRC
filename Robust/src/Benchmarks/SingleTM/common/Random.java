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
    mti = N;
    MATRIX_A = 0x9908b0dfL;   /* constant vector a */
    UPPER_MASK = 0x80000000L; /* most significant w-r bits */
    LOWER_MASK = 0x7fffffffL; /* least significant r bits */
  }

  public void random_alloc() {
    init_genrand(this.RANDOM_DEFAULT_SEED);
  }

  /* initializes mt[N] with a seed */
  public void init_genrand(long s) {
    int mti;
    mt[0]= s & 0xFFFFFFFFL;
    for (mti=1; mti<N; mti++) {
     mt[mti] = (1812433253L * (mt[mti-1] ^ (mt[mti-1] >> 30)) + mti);
      /* See Knuth TAOCP Vol2. 3rd Ed. P.106 for multiplier. */
      /* In the previous versions, MSBs of the seed affect   */
      /* only MSBs of the array mt[].                        */
      /* 2002/01/09 modified by Makoto Matsumoto             */
      mt[mti] &= 0xFFFFFFFFL;
      /* for >32 bit machines */
    }
    this.mti=mti;
  }

  public void random_seed(long seed) {
    init_genrand(seed);
  }

  public long random_generate() {
    return genrand_int32();
  }

  public long genrand_int32() {
    long y;
    long[] mag01= new long[2];
    mag01[0] = 0x0L;
    mag01[1] = MATRIX_A;
    int mti = this.mti;

    /* mag01[x] = x * MATRIX_A  for x=0,1 */

    if (mti >= N) { /* generate N words at one time */
      int kk;

      if (mti == N+1)   /* if init_genrand() has not been called, */
        init_genrand(5489L); /* a default initial seed is used */

      for (kk=0;kk<N-M;kk++) {
        y = (mt[kk]&UPPER_MASK)|(mt[kk+1]&LOWER_MASK);
        mt[kk] = mt[kk+M] ^ (y >> 1) ^ mag01[(int)(y & 0x1L)];
      }
      for (;kk<N-1;kk++) {
        y = (mt[kk]&UPPER_MASK)|(mt[kk+1]&LOWER_MASK);
        mt[kk] = mt[kk+(M-N)] ^ (y >> 1) ^ mag01[(int)(y & 0x1L)];
      }
      y = (mt[N-1]&UPPER_MASK)|(mt[0]&LOWER_MASK);
      mt[N-1] = mt[M-1] ^ (y >> 1) ^ mag01[(int)(y & 0x1L)];

      mti = 0;
    }

    y = mt[mti++];

    /* Tempering */
    y ^= (y >> 11);
    y ^= (y << 7) & 0x9d2c5680L;
    y ^= (y << 15) & 0xefc60000L;
    y ^= (y >> 18);

    this.mti = mti;

    return y;
  }
}
