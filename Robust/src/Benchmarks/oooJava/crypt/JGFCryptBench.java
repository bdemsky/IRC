/**************************************************************************
 *                                                                         *
 *         Java Grande Forum Benchmark Suite - Thread Version 1.0          *
 *                                                                         *
 *                            produced by                                  *
 *                                                                         *
 *                  Java Grande Benchmarking Project                       *
 *                                                                         *
 *                                at                                       *
 *                                                                         *
 *                Edinburgh Parallel Computing Centre                      *
 *                                                                         * 
 *                email: epcc-javagrande@epcc.ed.ac.uk                     *
 *                                                                         *
 *                  Original version of this code by                       *
 *                 Gabriel Zachmann (zach@igd.fhg.de)                      *
 *                                                                         *
 *      This version copyright (c) The University of Edinburgh, 2001.      *
 *                         All rights reserved.                            *
 *                                                                         *
 **************************************************************************/

public class JGFCryptBench {

  private int nWorker;
  private int size;
  private int datasizes[];
  int array_rows;

  byte[] plain1; // Buffer for plaintext data.

  short[] userkey; // Key for encryption/decryption.
  int[] Z; // Encryption subkey (userkey derived).
  int[] DK; // Decryption subkey (userkey derived).
  
  public boolean validationTest;

  // buildTestData
  // Builds the data used for the test -- each time the test is run.
  void buildTestData() {

    // Create three byte arrays that will be used (and reused) for
    // encryption/decryption operations.

    plain1 = new byte[array_rows];

    Random rndnum = new Random(136506717L); // Create random number generator.

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

      userkey[i] = (short) rndnum.nextInt();
    }

    // Compute encryption and decryption subkeys.

    calcEncryptKey();
    calcDecryptKey();

    // Fill plain1 with "text."
    for (int i = 0; i < array_rows; i++) {
      plain1[i] = (byte) i;

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

  private void calcEncryptKey() {
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
        Z[i] = ((Z[i - 7] >>> 9) | (Z[i - 6] << 7)) // Shift and combine.
            & 0xFFFF; // Just 16 bits.
        // continue; // Next iteration.
        flag1 = 1;
      }

      if (flag1 == 0) {
        int flag2 = 0;

        if (j == 6) // Wrap to beginning for second chunk.
        {
          Z[i] = ((Z[i - 7] >>> 9) | (Z[i - 14] << 7)) & 0xFFFF;
          // continue;
          flag2 = 1;
        }

        if (flag2 == 0) {
          // j == 7 so wrap to beginning for both chunks.
          Z[i] = ((Z[i - 15] >>> 9) | (Z[i - 14] << 7)) & 0xFFFF;
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

  private void calcDecryptKey() {
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

  private int mul(int a, int b) {
    int ret;
    long p; // Large enough to catch 16-bit multiply
    // without hitting sign bit.
    if (a != 0) {
      if (b != 0) {
        p = (long) a * b;
        b = (int) p & 0xFFFF; // Lower 16 bits.
        a = (int) p >>> 16; // Upper 16 bits.
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

  private int inv(int x) {
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

  public JGFCryptBench() {
    datasizes = new int[3];
    datasizes[0] = 3000000;
    datasizes[1] = 20000000;
    datasizes[2] = 1000000000;
    validationTest=false;
  }

  public void JGFsetsize(int size, int nWorker) {
    this.size = size;
    this.nWorker = nWorker;
  }

  public void JGFinitialise() {
    array_rows = datasizes[size];
    buildTestData();
  }

  public void JGFkernel(){

    byte [] crypt1 =  new byte [array_rows];
    byte [] plain2 =  new byte [array_rows];

    long startT=System.currentTimeMillis();

    int nW=nWorker;
    // Encrypt plain1.    
    int  slice, tslice, ttslice; 
    tslice = plain1.length / 8;
    ttslice = (tslice + nWorker-1) / nWorker;
    slice = ttslice*8;
 
    for(int i=0;i<nW;i++) {
      // setup worker
      sese parallel_e{
        int ilow = i*slice;
        int iupper = (i+1)*slice;
        if(iupper > plain1.length) iupper = plain1.length;
        int localSize=iupper-ilow;
        byte local_crypt1[] =  new byte [localSize]; 
        IDEARunner runner=new IDEARunner(i,plain1,local_crypt1,localSize,Z,nWorker);
        runner.run();
      }
      
      sese serial_e{
        if(true){
          System.arraycopy(runner.text2, 0, crypt1, ilow, runner.local_size);
        }else{
          for(int idx=0;idx<runner.local_size;idx++){
          crypt1[ilow+idx]=runner.text2[idx];
          }
        }
      }      
      
    }       
    
    // Decrypt.
    for(int i=0;i<nW;i++) {

      sese parallel_d{
        int ilow = i*slice;
        int iupper = (i+1)*slice;
        if(iupper > crypt1.length) iupper = crypt1.length;
        int localSize=iupper-ilow;
        byte local_plain2[] =  new byte [localSize];     
        IDEARunner runner=new IDEARunner(i,crypt1,local_plain2,localSize,DK,nWorker);       
        runner.run();
      }
      
      sese serial_d{
        if(true){
          System.arraycopy(runner.text2, 0, plain2, ilow, runner.local_size);
        }else{
          for(int idx=0;idx<runner.local_size;idx++){
            plain2[ilow+idx]=runner.text2[idx];
          }
        }        
      }
      
    }   
    int p=plain2[0];
    long endT=System.currentTimeMillis();
    if(!validationTest){
      System.out.println(p+"runningtime="+(endT-startT));
    }
    
    if(validationTest){
      boolean error = false; 
      for (int i = 0; i < array_rows; i++){
        error = (plain1 [i] != plain2 [i]); 
        if (error){
          System.out.println("Validation failed");
          System.out.println("Original Byte " + i + " = " + plain1[i]); 
          System.out.println("Encrypted Byte " + i + " = " + crypt1[i]); 
          System.out.println("Decrypted Byte " + i + " = " + plain2[i]); 
          return;
        }
      }
      System.out.println("VALID");
    }

  }

  public void JGFrun(int size, int nWorker) {

    JGFsetsize(size, nWorker);
    long startT=System.currentTimeMillis();
    JGFinitialise();
    long endT=System.currentTimeMillis();
    JGFkernel();
    
    if(!validationTest){
      System.out.println("init="+(endT-startT));
    }
  }

  public static void main(String argv[]) {
    

    JGFCryptBench cb = new JGFCryptBench();

    int problem_size = 2;
    int nWorker = 2;
    if (argv.length > 0) {
      problem_size = Integer.parseInt(argv[0]);
    }

    if (argv.length > 1) {
      nWorker = Integer.parseInt(argv[1]);
    }
    
    if(argv.length > 2){
     cb.validationTest=true;
    }

    cb.JGFrun(problem_size, nWorker);

  }

}
