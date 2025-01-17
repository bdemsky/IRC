#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <sys/time.h>


long long currentTimeMillis() {
  struct timeval t;
  long long retval;
  gettimeofday( &t, NULL );
  retval=t.tv_sec;
  retval*=1000;
  retval+=(t.tv_usec/1000);
  return retval;
}


struct JGFCryptBench {
  int nWorker;
  int size;
  int* datasizes;
  int array_rows;

  char* plain1; // Buffer for plaintext data.

  short* userkey; // Key for encryption/decryption.
  int* Z; // Encryption subkey (userkey derived).
  int* DK; // Decryption subkey (userkey derived).

  void buildTestData();
  void calcEncryptKey();
  void calcDecryptKey();  

  int mul(int a, int b);
  int inv(int x);
  
  JGFCryptBench();

  void JGFsetsize(int size, int nWorker);
  void JGFinitialise();
  void JGFkernel();
  void JGFrun(int size, int nWorker);
};


struct IDEARunner {
  int id;
  int* key;
  char* text1;
  char* text2;
  int nthreads;
  int local_size;

  int array_rows;

  IDEARunner(int id, char* text1, char* text2, int local_size, int* key, int nthreads, int array_rows);
  void run();
};




int main( int argc, char** argv ) {
    
  JGFCryptBench cb;

  int problem_size = 2;
  int nWorker = 30;
  if (argc > 1) {
    problem_size = atoi(argv[1]);
  }

  if (argc > 2) {
    nWorker = atoi(argv[2]);
  }

  cb.JGFrun(problem_size, nWorker);

  return 0;
}





  // buildTestData
  // Builds the data used for the test -- each time the test is run.
void JGFCryptBench::buildTestData() {

  // Create three byte arrays that will be used (and reused) for
  // encryption/decryption operations.

  plain1 = new char[array_rows];

  srand( 136506717 );

  // Allocate three arrays to hold keys: userkey is the 128-bit key.
  // Z is the set of 16-bit encryption subkeys derived from userkey,
  // while DK is the set of 16-bit decryption subkeys also derived
  // from userkey. NOTE: The 16-bit values are stored here in
  // 32-bit int arrays so that the values may be used in calculations
  // as if they are unsigned. Each 64-bit block of plaintext goes
  // through eight processing rounds involving six of the subkeys
  // then a final output transform with four of the keys; (8 * 6)
  // + 4 = 52 subkeys.

  userkey = new short[8]; // User key has 8 16-bit shorts.
  Z = new int[52]; // Encryption subkey (user key derived).
  DK = new int[52]; // Decryption subkey (user key derived).

  // Generate user key randomly; eight 16-bit values in an array.

  for (int i = 0; i < 8; i++) {
    // Again, the random number function returns int. Converting
    // to a short type preserves the bit pattern in the lower 16
    // bits of the int and discards the rest.

    userkey[i] = (short) rand();
  }

  // Compute encryption and decryption subkeys.

  calcEncryptKey();
  calcDecryptKey();

  // Fill plain1 with "text."
  for (int i = 0; i < array_rows; i++) {
    plain1[i] = (char) i;

    // Converting to a byte
    // type preserves the bit pattern in the lower 8 bits of the
    // int and discards the rest.
  }
}


// calcEncryptKey

// Builds the 52 16-bit encryption subkeys Z[] from the user key and
// stores in 32-bit int array. The routing corrects an error in the
// source code in the Schnier book. Basically, the sense of the 7-
// and 9-bit shifts are reversed. It still works reversed, but would
// encrypted code would not decrypt with someone else's IDEA code.
//

void JGFCryptBench::calcEncryptKey() {
  int j; // Utility variable.

  for (int i = 0; i < 52; i++)
    // Zero out the 52-int Z array.
    Z[i] = 0;

  for (int i = 0; i < 8; i++) // First 8 subkeys are userkey itself.
    {
      Z[i] = userkey[i] & 0xffff; // Convert "unsigned"
      // short to int.
    }

  // Each set of 8 subkeys thereafter is derived from left rotating
  // the whole 128-bit key 25 bits to left (once between each set of
  // eight keys and then before the last four). Instead of actually
  // rotating the whole key, this routine just grabs the 16 bits
  // that are 25 bits to the right of the corresponding subkey
  // eight positions below the current subkey. That 16-bit extent
  // straddles two array members, so bits are shifted left in one
  // member and right (with zero fill) in the other. For the last
  // two subkeys in any group of eight, those 16 bits start to
  // wrap around to the first two members of the previous eight.

  for (int i = 8; i < 52; i++) {
    int flag1 = 0;
    j = i % 8;
    if (j < 6) {
      Z[i] = ((Z[i - 7] >> 9) | (Z[i - 6] << 7)) // Shift and combine.
        & 0xFFFF; // Just 16 bits.
      // continue; // Next iteration.
      flag1 = 1;
    }

    if (flag1 == 0) {
      int flag2 = 0;

      if (j == 6) // Wrap to beginning for second chunk.
        {
          Z[i] = ((Z[i - 7] >> 9) | (Z[i - 14] << 7)) & 0xFFFF;
          // continue;
          flag2 = 1;
        }

      if (flag2 == 0) {
        // j == 7 so wrap to beginning for both chunks.
        Z[i] = ((Z[i - 15] >> 9) | (Z[i - 14] << 7)) & 0xFFFF;
      }
    }
  }
}

//
// calcDecryptKey
//
// Builds the 52 16-bit encryption subkeys DK[] from the encryption-
// subkeys Z[]. DK[] is a 32-bit int array holding 16-bit values as
// unsigned.
//

void JGFCryptBench::calcDecryptKey() {
  int j, k; // Index counters.
  int t1, t2, t3; // Temps to hold decrypt subkeys.

  t1 = inv(Z[0]); // Multiplicative inverse (mod x10001).
  t2 = -Z[1] & 0xffff; // Additive inverse, 2nd encrypt subkey.
  t3 = -Z[2] & 0xffff; // Additive inverse, 3rd encrypt subkey.

  DK[51] = inv(Z[3]); // Multiplicative inverse (mod x10001).
  DK[50] = t3;
  DK[49] = t2;
  DK[48] = t1;

  j = 47; // Indices into temp and encrypt arrays.
  k = 4;
  for (int i = 0; i < 7; i++) {
    t1 = Z[k++];
    DK[j--] = Z[k++];
    DK[j--] = t1;
    t1 = inv(Z[k++]);
    t2 = -Z[k++] & 0xffff;
    t3 = -Z[k++] & 0xffff;
    DK[j--] = inv(Z[k++]);
    DK[j--] = t2;
    DK[j--] = t3;
    DK[j--] = t1;
  }

  t1 = Z[k++];
  DK[j--] = Z[k++];
  DK[j--] = t1;
  t1 = inv(Z[k++]);
  t2 = -Z[k++] & 0xffff;
  t3 = -Z[k++] & 0xffff;
  DK[j--] = inv(Z[k++]);
  DK[j--] = t3;
  DK[j--] = t2;
  DK[j--] = t1;
}

//
// mul
//
// Performs multiplication, modulo (2**16)+1. This code is structured
// on the assumption that untaken branches are cheaper than taken
// branches, and that the compiler doesn't schedule branches.
// Java: Must work with 32-bit int and one 64-bit long to keep
// 16-bit values and their products "unsigned." The routine assumes
// that both a and b could fit in 16 bits even though they come in
// as 32-bit ints. Lots of "& 0xFFFF" masks here to keep things 16-bit.
// Also, because the routine stores mod (2**16)+1 results in a 2**16
// space, the result is truncated to zero whenever the result would
// zero, be 2**16. And if one of the multiplicands is 0, the result
// is not zero, but (2**16) + 1 minus the other multiplicand (sort
// of an additive inverse mod 0x10001).

// NOTE: The java conversion of this routine works correctly, but
// is half the speed of using Java's modulus division function (%)
// on the multiplication with a 16-bit masking of the result--running
// in the Symantec Caje IDE. So it's not called for now; the test
// uses Java % instead.
//

int JGFCryptBench::mul(int a, int b) {
  int ret;
  long long p; // Large enough to catch 16-bit multiply
  // without hitting sign bit.
  if (a != 0) {
    if (b != 0) {
      p = (long long) a * b;
      b = (int) p & 0xFFFF; // Lower 16 bits.
      a = (int) p >> 16; // Upper 16 bits.
      if (b < a)
        return (b - a + 1) & 0xFFFF;
      else
        return (b - a) & 0xFFFF;
    } else
      return ((1 - a) & 0xFFFF); // If b = 0, then same as
    // 0x10001 - a.
  } else
    // If a = 0, then return
    return ((1 - b) & 0xFFFF); // same as 0x10001 - b.
}

//
// inv
//
// Compute multiplicative inverse of x, modulo (2**16)+1 using
// extended Euclid's GCD (greatest common divisor) algorithm.
// It is unrolled twice to avoid swapping the meaning of
// the registers. And some subtracts are changed to adds.
// Java: Though it uses signed 32-bit ints, the interpretation
// of the bits within is strictly unsigned 16-bit.
//

int JGFCryptBench::inv(int x) {
  int t0, t1;
  int q, y;

  if (x <= 1) // Assumes positive x.
    return (x); // 0 and 1 are self-inverse.

  t1 = 0x10001 / x; // (2**16+1)/x; x is >= 2, so fits 16 bits.
  y = 0x10001 % x;
  if (y == 1)
    return ((1 - t1) & 0xFFFF);

  t0 = 1;
  do {
    q = x / y;
    x = x % y;
    t0 += q * t1;
    if (x == 1)
      return (t0);
    q = y / x;
    y = y % x;
    t1 += q * t0;
  } while (y != 1);

  return ((1 - t1) & 0xFFFF);
}

JGFCryptBench::JGFCryptBench() {
  datasizes = new int[3];
  datasizes[0] = 3000000;
  datasizes[1] = 20000000;
  datasizes[2] = 1000000000;
}

void JGFCryptBench::JGFsetsize(int size, int nWorker) {
  this->size = size;
  this->nWorker = nWorker;
}

void JGFCryptBench::JGFinitialise() {
  array_rows = datasizes[size];
  buildTestData();
}

void JGFCryptBench::JGFkernel(){
  long long startT=currentTimeMillis();
  char* crypt1 =  new char [array_rows];
  char* plain2 =  new char [array_rows];

  long plain1_length = array_rows;

  int nW=nWorker;
  // Encrypt plain1.    
  int  slice, tslice, ttslice; 
  tslice = plain1_length / 8;
  ttslice = (tslice + nWorker-1) / nWorker;
  slice = ttslice*8;
 
  for(int i=0;i<nW;i++) {
    // setup worker
    int ilow = i*slice;
    int iupper = (i+1)*slice;
    if(iupper > plain1_length) iupper = plain1_length;
    int localSize=iupper-ilow;
    char* local_crypt1 =  new char [localSize]; 
    IDEARunner* runner=new IDEARunner(i,plain1,local_crypt1,localSize,Z,nWorker,array_rows);
    runner->run();
      
    if(true){
      memcpy( ((char*)crypt1)+ilow, (char*)runner->text2, runner->local_size );
    }else{
      for(int idx=0;idx<runner->local_size;idx++){
        crypt1[ilow+idx]=runner->text2[idx];
      }      
    }
  }       
    
  long crypt1_length = array_rows;

  // Decrypt.
  for(int i=0;i<nW;i++) {

    int ilow = i*slice;
    int iupper = (i+1)*slice;
    if(iupper > crypt1_length) iupper = crypt1_length;
    int localSize=iupper-ilow;
    char* local_plain2 =  new char [localSize];     
    IDEARunner* runner=new IDEARunner(i,crypt1,local_plain2,localSize,DK,nWorker,array_rows);       
    runner->run();
  
    if(true){
      memcpy( ((char*)plain2)+ilow, (char*)runner->text2, runner->local_size );
    }else{
      for(int idx=0;idx<runner->local_size;idx++){
        plain2[ilow+idx]=runner->text2[idx];
      }      
    }            
  }   
  int p=plain2[0];
  long long endT=currentTimeMillis();
  printf("%drunningtime=%d\n", p, (endT-startT));


  /*
  bool error = false; 
  for (int i = 0; i < array_rows; i++){
    error = (plain1 [i] != plain2 [i]); 
    if (error){
      printf("Validation failed\n");
      printf("Original Byte  %d = %c\n", i, plain1[i]); 
      printf("Encrypted Byte %d = %c\n", i, crypt1[i]); 
      printf("Decrypted Byte %d = %c\n", i, plain2[i]); 
      return;
    }
  }
  printf("Validation Success\n");
  */
}

void JGFCryptBench::JGFrun(int size, int nWorker) {

  JGFsetsize(size, nWorker);
  long long startT=currentTimeMillis();
  JGFinitialise();
  long long endT=currentTimeMillis();
  JGFkernel();
    
  printf("init=%d\n",(endT-startT));
}






IDEARunner::IDEARunner(int id, char* text1, char* text2, int local_size, int* key, int nthreads, int array_rows) {
  this->id = id;
  this->text1 = text1;
  this->text2 = text2;
  this->key = key;
  this->nthreads = nthreads;
  this->local_size = local_size;
  this->array_rows = array_rows;
}

  //
  // run()
  // 
  // IDEA encryption/decryption algorithm. It processes plaintext in
  // 64-bit blocks, one at a time, breaking the block into four 16-bit
  // unsigned subblocks. It goes through eight rounds of processing
  // using 6 new subkeys each time, plus four for last step. The source
  // text is in array text1, the destination text goes into array text2
  // The routine represents 16-bit subblocks and subkeys as type int so
  // that they can be treated more easily as unsigned. Multiplication
  // modulo 0x10001 interprets a zero sub-block as 0x10000; it must to
  // fit in 16 bits.
  //

void IDEARunner::run() {
  int ilow, iupper, slice, tslice, ttslice;

  long text1_length = array_rows;

  tslice = text1_length / 8;
  ttslice = (tslice + nthreads - 1) / nthreads;
  slice = ttslice * 8;

  ilow = id * slice;
  iupper = (id + 1) * slice;
  if (iupper > text1_length)
    iupper = text1_length;

  int i1 = ilow; // Index into first text array.
  // int i2 = ilow; // Index into second text array.
  int i2 = 0;

  int ik; // Index into key array.
  int x1, x2, x3, x4, t1, t2; // Four "16-bit" blocks, two temps.
  int r; // Eight rounds of processing.

  for (int i = ilow; i < iupper; i += 8) {

    ik = 0; // Restart key index.
    r = 8; // Eight rounds of processing.

    // Load eight plain1 bytes as four 16-bit "unsigned" integers.
    // Masking with 0xff prevents sign extension with cast to int.

    x1 = text1[i1++] & 0xff; // Build 16-bit x1 from 2 bytes,
    x1 |= (text1[i1++] & 0xff) << 8; // assuming low-order byte first.
    x2 = text1[i1++] & 0xff;
    x2 |= (text1[i1++] & 0xff) << 8;
    x3 = text1[i1++] & 0xff;
    x3 |= (text1[i1++] & 0xff) << 8;
    x4 = text1[i1++] & 0xff;
    x4 |= (text1[i1++] & 0xff) << 8;

    do {
      // 1) Multiply (modulo 0x10001), 1st text sub-block
      // with 1st key sub-block.

      x1 = (int) ((long long) x1 * key[ik++] % 0x10001L & 0xffff);

      // 2) Add (modulo 0x10000), 2nd text sub-block
      // with 2nd key sub-block.

      x2 = x2 + key[ik++] & 0xffff;

      // 3) Add (modulo 0x10000), 3rd text sub-block
      // with 3rd key sub-block.

      x3 = x3 + key[ik++] & 0xffff;

      // 4) Multiply (modulo 0x10001), 4th text sub-block
      // with 4th key sub-block.

      x4 = (int) ((long long) x4 * key[ik++] % 0x10001L & 0xffff);

      // 5) XOR results from steps 1 and 3.

      t2 = x1 ^ x3;

      // 6) XOR results from steps 2 and 4.
      // Included in step 8.

      // 7) Multiply (modulo 0x10001), result of step 5
      // with 5th key sub-block.

      t2 = (int) ((long long) t2 * key[ik++] % 0x10001L & 0xffff);

      // 8) Add (modulo 0x10000), results of steps 6 and 7.

      t1 = t2 + (x2 ^ x4) & 0xffff;

      // 9) Multiply (modulo 0x10001), result of step 8
      // with 6th key sub-block.

      t1 = (int) ((long long) t1 * key[ik++] % 0x10001L & 0xffff);

      // 10) Add (modulo 0x10000), results of steps 7 and 9.

      t2 = t1 + t2 & 0xffff;

      // 11) XOR results from steps 1 and 9.

      x1 ^= t1;

      // 14) XOR results from steps 4 and 10. (Out of order).

      x4 ^= t2;

      // 13) XOR results from steps 2 and 10. (Out of order).

      t2 ^= x2;

      // 12) XOR results from steps 3 and 9. (Out of order).

      x2 = x3 ^ t1;

      x3 = t2; // Results of x2 and x3 now swapped.

    } while (--r != 0); // Repeats seven more rounds.

    // Final output transform (4 steps).

    // 1) Multiply (modulo 0x10001), 1st text-block
    // with 1st key sub-block.

    x1 = (int) ((long long) x1 * key[ik++] % 0x10001L & 0xffff);

    // 2) Add (modulo 0x10000), 2nd text sub-block
    // with 2nd key sub-block. It says x3, but that is to undo swap
    // of subblocks 2 and 3 in 8th processing round.

    x3 = x3 + key[ik++] & 0xffff;

    // 3) Add (modulo 0x10000), 3rd text sub-block
    // with 3rd key sub-block. It says x2, but that is to undo swap
    // of subblocks 2 and 3 in 8th processing round.

    x2 = x2 + key[ik++] & 0xffff;

    // 4) Multiply (modulo 0x10001), 4th text-block
    // with 4th key sub-block.

    x4 = (int) ((long long) x4 * key[ik++] % 0x10001L & 0xffff);

    // Repackage from 16-bit sub-blocks to 8-bit byte array text2.

    text2[i2++] = (char) x1;
    text2[i2++] = (char) (x1 >> 8);
    text2[i2++] = (char) x3; // x3 and x2 are switched
    text2[i2++] = (char) (x3 >> 8); // only in name.
    text2[i2++] = (char) x2;
    text2[i2++] = (char) (x2 >> 8);
    text2[i2++] = (char) x4;
    text2[i2++] = (char) (x4 >> 8);
  } // End for loop.

} // End routine.
