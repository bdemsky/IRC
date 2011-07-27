/*
 * 11/19/04 1.0 moved to LGPL.
 * 
 * 04/01/00 Fixes for running under build 23xx Microsoft JVM. mdm.
 * 
 * 19/12/99 Performance improvements to compute_pcm_samples().  
 *			Mat McGowan. mdm@techie.com. 
 *
 * 16/02/99 Java Conversion by E.B , javalayer@javazoom.net
 *
 *  @(#) synthesis_filter.h 1.8, last edit: 6/15/94 16:52:00
 *  @(#) Copyright (C) 1993, 1994 Tobias Bading (bading@cs.tu-berlin.de)
 *  @(#) Berlin University of Technology
 *
 *-----------------------------------------------------------------------
 *   This program is free software; you can redistribute it and/or modify
 *   it under the terms of the GNU Library General Public License as published
 *   by the Free Software Foundation; either version 2 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Library General Public License for more details.
 *
 *   You should have received a copy of the GNU Library General Public
 *   License along with this program; if not, write to the Free Software
 *   Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *----------------------------------------------------------------------
 */

//import java.io.IOException; //Compiler does not support imports

/**
 * A class for the synthesis filter bank.
 * This class does a fast downsampling from 32, 44.1 or 48 kHz to 8 kHz, if ULAW is defined.
 * Frequencies above 4 kHz are removed by ignoring higher subbands.
 */
@LATTICE("TMP<PCM,PCM<AV,AV<V2,V2<V1,V1<L1,L1<L2,L2<L3,L3<L4,L4<LSH,LSH<S,S<SA,SA<EQ,EQ<SH,SH*,LSH*,V1*")
@METHODDEFAULT("OUT<V,V<SH,SH<IN,IN<GLOBAL,SH*,THISLOC=IN,GLOBALLOC=GLOBAL")
final class SynthesisFilter
{
  @LOC("V1") private float[] 			 v1;
  @LOC("V2") private float[]		 	 v2;
//  @LOC("AV") private float[]			 actual_v;			// v1 or v2
  @LOC("SH") private int 			 actual_write_pos;	// 0-15
  @LOC("SA") private float[]			 samples;			// 32 new subband samples
  @LOC("V2") private int				 channel;
  @LOC("V2") private float 			 scalefactor;
  @LOC("EQ") private float[]			 eq;
	
	/**
	 * Quality value for controlling CPU usage/quality tradeoff. 
	 */
	/*
	private int				quality;
	
	private int				v_inc;
	
	
	
	public static final int	HIGH_QUALITY = 1;
	public static final int MEDIUM_QUALITY = 2;
	public static final int LOW_QUALITY = 4;
	*/
	
  /**
   * Contructor.
   * The scalefactor scales the calculated float pcm samples to short values
   * (raw pcm samples are in [-1.0, 1.0], if no violations occur).
   */
  public SynthesisFilter(int channelnumber, float factor, float[] eq0)
  {  	 
      d16 = splitArray(d, 16);
	  
	  v1 = new float[512];
	 v2 = new float[512];
	 samples = new float[32];
     channel = channelnumber;
	 scalefactor = factor;
	 setEQ(eq);	 
	 //setQuality(HIGH_QUALITY);
	 
     reset();
  }
  
  public void setEQ(float[] eq0)
  {
	 this.eq = eq0;	 
	 if (eq==null)
	 {
		 eq = new float[32];
		 for (int i=0; i<32; i++)
			 eq[i] = 1.0f;
	 }
	 if (eq.length<32)
	 {
		throw new IllegalArgumentException("eq0");	 
	 }
	  
  }
  
	/*
	private void setQuality(int quality0)
	{
	  	switch (quality0)
	  	{		
		case HIGH_QUALITY:
		case MEDIUM_QUALITY:
		case LOW_QUALITY:						  
			v_inc = 16 * quality0;			
			quality = quality0;
			break;	
		default :
			throw new IllegalArgumentException("Unknown quality value");
	  	}				
	}
	
	public int getQuality()
	{
		return quality;	
	}
	*/
  
  /**
   * Reset the synthesis filter.
   */
  public void reset()
  {
     //float[] floatp;
	 // float[] floatp2;

     // initialize v1[] and v2[]:
     //for (floatp = v1 + 512, floatp2 = v2 + 512; floatp > v1; )
	 //   *--floatp = *--floatp2 = 0.0;
	 for (int p=0;p<512;p++) 
		 v1[p] = v2[p] = 0.0f;

     // initialize samples[]:
     //for (floatp = samples + 32; floatp > samples; )
	 //  *--floatp = 0.0;
	 for (int p2=0;p2<32;p2++) 
		 samples[p2] = 0.0f;

//     actual_v = v1;
     actual_write_pos = 15;
  }


  /**
   * Inject Sample.
   */
    public void input_sample(@LOC("V") float sample, @LOC("V") int subbandnumber)
  {	 	 		  
	  samples[subbandnumber] = eq[subbandnumber]*sample;
  }

  @LATTICE("TH<IN,IN<C,C*,THISLOC=TH")
  public void input_samples(@LOC("IN") float[] s)
  {
	  for (@LOC("C") int i=31; i>=0; i--)
	  {		
		 samples[i] = s[i]*eq[i];
	  }
  }
  
  /**
   * Compute new values via a fast cosine transform.
   */
  private void compute_new_v()
  {
    // p is fully initialized from x1
    //float[] p = _p;
    // pp is fully initialized from p
    //float[] pp = _pp; 

    //float[] new_v = _new_v;

    //float[] new_v = new float[32]; // new V[0-15] and V[33-48] of Figure 3-A.2 in ISO DIS 11172-3
    //float[] p = new float[16];
    //float[] pp = new float[16];

    /*
	 for (int i=31; i>=0; i--)
	 {
		 new_v[i] = 0.0f;
	 }
     */

    @LOC("IN,SynthesisFilter.L4") float new_v0 = 0.0f;
    @LOC("IN,SynthesisFilter.L2") float new_v1 = 0.0f;
    @LOC("IN,SynthesisFilter.L4") float new_v2 = 0.0f;
    @LOC("IN,SynthesisFilter.L2") float new_v3 = 0.0f;
    @LOC("IN,SynthesisFilter.L3") float new_v4 = 0.0f;
    @LOC("IN,SynthesisFilter.L4") float new_v5 = 0.0f;
    @LOC("IN,SynthesisFilter.L2") float new_v6 = 0.0f;
    @LOC("IN,SynthesisFilter.L3") float new_v7 = 0.0f;
    @LOC("IN,SynthesisFilter.L4") float new_v8 = 0.0f;
    @LOC("IN,SynthesisFilter.L4") float new_v9 = 0.0f;
    @LOC("IN,SynthesisFilter.L3") float new_v10 = 0.0f;
    @LOC("IN,SynthesisFilter.L2") float new_v11 = 0.0f;
    @LOC("IN,SynthesisFilter.L4") float new_v12 = 0.0f;
    @LOC("IN,SynthesisFilter.L3") float new_v13 = 0.0f;
    @LOC("IN,SynthesisFilter.L4") float new_v14 = 0.0f;
    @LOC("IN,SynthesisFilter.L4") float new_v15 = 0.0f;
    @LOC("IN,SynthesisFilter.L1") float new_v16 = 0.0f;
    @LOC("IN,SynthesisFilter.L3") float new_v17 = 0.0f;
    @LOC("IN,SynthesisFilter.L1") float new_v18 = 0.0f;
    @LOC("IN,SynthesisFilter.L2") float new_v19 = 0.0f;
    @LOC("IN,SynthesisFilter.L2") float new_v20 = 0.0f;
    @LOC("IN,SynthesisFilter.L2") float new_v21 = 0.0f;
    @LOC("IN,SynthesisFilter.L2") float new_v22 = 0.0f;
    @LOC("IN,SynthesisFilter.L3") float new_v23 = 0.0f;
    @LOC("IN,SynthesisFilter.L2") float new_v24 = 0.0f;
    @LOC("IN,SynthesisFilter.L2") float new_v25 = 0.0f;
    @LOC("IN,SynthesisFilter.L2") float new_v26 = 0.0f;
    @LOC("IN,SynthesisFilter.L4") float new_v27 = 0.0f;
    @LOC("IN,SynthesisFilter.L2") float new_v28 = 0.0f;
    @LOC("IN,SynthesisFilter.L4") float new_v29 = 0.0f;
    @LOC("IN,SynthesisFilter.L2") float new_v30 = 0.0f;
    @LOC("IN,SynthesisFilter.L4") float new_v31 = 0.0f;

//    new_v0 = new_v1 = new_v2 = new_v3 = new_v4 = new_v5 = new_v6 = new_v7 = new_v8 = new_v9 = 
//      new_v10 = new_v11 = new_v12 = new_v13 = new_v14 = new_v15 = new_v16 = new_v17 = new_v18 = new_v19 = 
//        new_v20 = new_v21 = new_v22 = new_v23 = new_v24 = new_v25 = new_v26 = new_v27 = new_v28 = new_v29 = 
//          new_v30 = new_v31 = 0.0f;


    //	float[] new_v = new float[32]; // new V[0-15] and V[33-48] of Figure 3-A.2 in ISO DIS 11172-3
    //	float[] p = new float[16];
    //	float[] pp = new float[16];

    //float[] s = samples; // subbed in samples directly below to reduce uneccesary areas

    @LOC("IN,SynthesisFilter.S") float s0 = samples[0];
    @LOC("IN,SynthesisFilter.S") float s1 = samples[1];
    @LOC("IN,SynthesisFilter.S") float s2 = samples[2];
    @LOC("IN,SynthesisFilter.S") float s3 = samples[3];
    @LOC("IN,SynthesisFilter.S") float s4 = samples[4];
    @LOC("IN,SynthesisFilter.S") float s5 = samples[5];
    @LOC("IN,SynthesisFilter.S") float s6 = samples[6];
    @LOC("IN,SynthesisFilter.S") float s7 = samples[7];
    @LOC("IN,SynthesisFilter.S") float s8 = samples[8];
    @LOC("IN,SynthesisFilter.S") float s9 = samples[9];
    @LOC("IN,SynthesisFilter.S") float s10 = samples[10];	
    @LOC("IN,SynthesisFilter.S") float s11 = samples[11];
    @LOC("IN,SynthesisFilter.S") float s12 = samples[12];
    @LOC("IN,SynthesisFilter.S") float s13 = samples[13];
    @LOC("IN,SynthesisFilter.S") float s14 = samples[14];
    @LOC("IN,SynthesisFilter.S") float s15 = samples[15];
    @LOC("IN,SynthesisFilter.S") float s16 = samples[16];
    @LOC("IN,SynthesisFilter.S") float s17 = samples[17];
    @LOC("IN,SynthesisFilter.S") float s18 = samples[18];
    @LOC("IN,SynthesisFilter.S") float s19 = samples[19];
    @LOC("IN,SynthesisFilter.S") float s20 = samples[20];	
    @LOC("IN,SynthesisFilter.S") float s21 = samples[21];
    @LOC("IN,SynthesisFilter.S") float s22 = samples[22];
    @LOC("IN,SynthesisFilter.S") float s23 = samples[23];
    @LOC("IN,SynthesisFilter.S") float s24 = samples[24];
    @LOC("IN,SynthesisFilter.S") float s25 = samples[25];
    @LOC("IN,SynthesisFilter.S") float s26 = samples[26];
    @LOC("IN,SynthesisFilter.S") float s27 = samples[27];
    @LOC("IN,SynthesisFilter.S") float s28 = samples[28];
    @LOC("IN,SynthesisFilter.S") float s29 = samples[29];
    @LOC("IN,SynthesisFilter.S") float s30 = samples[30];	
    @LOC("IN,SynthesisFilter.S") float s31 = samples[31];

    @LOC("IN,SynthesisFilter.LSH") float p0 = s0 + s31;
    @LOC("IN,SynthesisFilter.LSH") float p1 = s1 + s30;
    @LOC("IN,SynthesisFilter.LSH") float p2 = s2 + s29;
    @LOC("IN,SynthesisFilter.LSH") float p3 = s3 + s28;
    @LOC("IN,SynthesisFilter.LSH") float p4 = s4 + s27;
    @LOC("IN,SynthesisFilter.LSH") float p5 = s5 + s26;
    @LOC("IN,SynthesisFilter.LSH") float p6 = s6 + s25;
    @LOC("IN,SynthesisFilter.LSH") float p7 = s7 + s24;
    @LOC("IN,SynthesisFilter.LSH") float p8 = s8 + s23;
    @LOC("IN,SynthesisFilter.LSH") float p9 = s9 + s22;
    @LOC("IN,SynthesisFilter.LSH") float p10 = s10 + s21;
    @LOC("IN,SynthesisFilter.LSH") float p11 = s11 + s20;
    @LOC("IN,SynthesisFilter.LSH") float p12 = s12 + s19;
    @LOC("IN,SynthesisFilter.LSH") float p13 = s13 + s18;
    @LOC("IN,SynthesisFilter.LSH") float p14 = s14 + s17;
    @LOC("IN,SynthesisFilter.LSH") float p15 = s15 + s16;

    @LOC("IN,SynthesisFilter.LSH") float pp0 = p0 + p15;
    @LOC("IN,SynthesisFilter.LSH") float pp1 = p1 + p14;
    @LOC("IN,SynthesisFilter.LSH") float pp2 = p2 + p13;
    @LOC("IN,SynthesisFilter.LSH") float pp3 = p3 + p12;
    @LOC("IN,SynthesisFilter.LSH") float pp4 = p4 + p11;
    @LOC("IN,SynthesisFilter.LSH") float pp5 = p5 + p10;
    @LOC("IN,SynthesisFilter.LSH") float pp6 = p6 + p9;
    @LOC("IN,SynthesisFilter.LSH") float pp7 = p7 + p8;
    @LOC("IN,SynthesisFilter.LSH") float pp8 = (p0 - p15) * cos1_32;
    @LOC("IN,SynthesisFilter.LSH") float pp9 = (p1 - p14) * cos3_32;
    @LOC("IN,SynthesisFilter.LSH") float pp10 = (p2 - p13) * cos5_32;
    @LOC("IN,SynthesisFilter.LSH") float pp11 = (p3 - p12) * cos7_32;
    @LOC("IN,SynthesisFilter.LSH") float pp12 = (p4 - p11) * cos9_32;
    @LOC("IN,SynthesisFilter.LSH") float pp13 = (p5 - p10) * cos11_32;
    @LOC("IN,SynthesisFilter.LSH") float pp14 = (p6 - p9) * cos13_32;
    @LOC("IN,SynthesisFilter.LSH") float pp15 = (p7 - p8) * cos15_32;

    p0 = pp0 + pp7;
    p1 = pp1 + pp6;
    p2 = pp2 + pp5;
    p3 = pp3 + pp4;
    p4 = (pp0 - pp7) * cos1_16;
    p5 = (pp1 - pp6) * cos3_16;
    p6 = (pp2 - pp5) * cos5_16;
    p7 = (pp3 - pp4) * cos7_16;
    p8 = pp8 + pp15;
    p9 = pp9 + pp14;
    p10 = pp10 + pp13;
    p11 = pp11 + pp12;
    p12 = (pp8 - pp15) * cos1_16;
    p13 = (pp9 - pp14) * cos3_16;
    p14 = (pp10 - pp13) * cos5_16;
    p15 = (pp11 - pp12) * cos7_16;


    pp0 = p0 + p3;
    pp1 = p1 + p2;
    pp2 = (p0 - p3) * cos1_8;
    pp3 = (p1 - p2) * cos3_8;
    pp4 = p4 + p7;
    pp5 = p5 + p6;
    pp6 = (p4 - p7) * cos1_8;
    pp7 = (p5 - p6) * cos3_8;
    pp8 = p8 + p11;
    pp9 = p9 + p10;
    pp10 = (p8 - p11) * cos1_8;
    pp11 = (p9 - p10) * cos3_8;
    pp12 = p12 + p15;
    pp13 = p13 + p14;
    pp14 = (p12 - p15) * cos1_8;
    pp15 = (p13 - p14) * cos3_8;

    p0 = pp0 + pp1;
    p1 = (pp0 - pp1) * cos1_4;
    p2 = pp2 + pp3;
    p3 = (pp2 - pp3) * cos1_4;
    p4 = pp4 + pp5;
    p5 = (pp4 - pp5) * cos1_4;
    p6 = pp6 + pp7;
    p7 = (pp6 - pp7) * cos1_4;
    p8 = pp8 + pp9;
    p9 = (pp8 - pp9) * cos1_4;

    p10 = pp10 + pp11;
    p11 = (pp10 - pp11) * cos1_4;
    p12 = pp12 + pp13;
    p13 = (pp12 - pp13) * cos1_4;
    p14 = pp14 + pp15;
    p15 = (pp14 - pp15) * cos1_4;

    // this is pretty insane coding
    @LOC("IN,SynthesisFilter.L3") float tmp1;
    new_v19/*36-17*/ = -(new_v4 = (new_v12 = p7) + p5) - p6;
    new_v27/*44-17*/ = -p6 - p7 - p4;
    new_v6 = (new_v10 = (new_v14 = p15) + p11) + p13;
    new_v17/*34-17*/ = -(new_v2 = p15 + p13 + p9) - p14;
    new_v21/*38-17*/ = (tmp1 = -p14 - p15 - p10 - p11) - p13;
    new_v29/*46-17*/ = -p14 - p15 - p12 - p8;
    new_v25/*42-17*/ = tmp1 - p12;
    new_v31/*48-17*/ = -p0;
    new_v0 = p1;
    new_v23/*40-17*/ = -(new_v8 = p3) - p2;

    p0 = (s0 - s31) * cos1_64;
    p1 = (s1 - s30) * cos3_64;
    p2 = (s2 - s29) * cos5_64;
    p3 = (s3 - s28) * cos7_64;
    p4 = (s4 - s27) * cos9_64;
    p5 = (s5 - s26) * cos11_64;
    p6 = (s6 - s25) * cos13_64;
    p7 = (s7 - s24) * cos15_64;
    p8 = (s8 - s23) * cos17_64;
    p9 = (s9 - s22) * cos19_64;
    p10 = (s10 - s21) * cos21_64;
    p11 = (s11 - s20) * cos23_64;
    p12 = (s12 - s19) * cos25_64;
    p13 = (s13 - s18) * cos27_64;
    p14 = (s14 - s17) * cos29_64;
    p15 = (s15 - s16) * cos31_64;


    pp0 = p0 + p15;
    pp1 = p1 + p14;
    pp2 = p2 + p13;
    pp3 = p3 + p12;
    pp4 = p4 + p11;
    pp5 = p5 + p10;
    pp6 = p6 + p9;
    pp7 = p7 + p8;
    pp8 = (p0 - p15) * cos1_32;
    pp9 = (p1 - p14) * cos3_32;
    pp10 = (p2 - p13) * cos5_32;
    pp11 = (p3 - p12) * cos7_32;
    pp12 = (p4 - p11) * cos9_32;
    pp13 = (p5 - p10) * cos11_32;
    pp14 = (p6 - p9) * cos13_32;
    pp15 = (p7 - p8) * cos15_32;


    p0 = pp0 + pp7;
    p1 = pp1 + pp6;
    p2 = pp2 + pp5;
    p3 = pp3 + pp4;
    p4 = (pp0 - pp7) * cos1_16;
    p5 = (pp1 - pp6) * cos3_16;
    p6 = (pp2 - pp5) * cos5_16;
    p7 = (pp3 - pp4) * cos7_16;
    p8 = pp8 + pp15;
    p9 = pp9 + pp14;
    p10 = pp10 + pp13;
    p11 = pp11 + pp12;
    p12 = (pp8 - pp15) * cos1_16;
    p13 = (pp9 - pp14) * cos3_16;
    p14 = (pp10 - pp13) * cos5_16;
    p15 = (pp11 - pp12) * cos7_16;


    pp0 = p0 + p3;
    pp1 = p1 + p2;
    pp2 = (p0 - p3) * cos1_8;
    pp3 = (p1 - p2) * cos3_8;
    pp4 = p4 + p7;
    pp5 = p5 + p6;
    pp6 = (p4 - p7) * cos1_8;
    pp7 = (p5 - p6) * cos3_8;
    pp8 = p8 + p11;
    pp9 = p9 + p10;
    pp10 = (p8 - p11) * cos1_8;
    pp11 = (p9 - p10) * cos3_8;
    pp12 = p12 + p15;
    pp13 = p13 + p14;
    pp14 = (p12 - p15) * cos1_8;
    pp15 = (p13 - p14) * cos3_8;


    p0 = pp0 + pp1;
    p1 = (pp0 - pp1) * cos1_4;
    p2 = pp2 + pp3;
    p3 = (pp2 - pp3) * cos1_4;
    p4 = pp4 + pp5;
    p5 = (pp4 - pp5) * cos1_4;
    p6 = pp6 + pp7;
    p7 = (pp6 - pp7) * cos1_4;
    p8 = pp8 + pp9;
    p9 = (pp8 - pp9) * cos1_4;
    p10 = pp10 + pp11;
    p11 = (pp10 - pp11) * cos1_4;
    p12 = pp12 + pp13;
    p13 = (pp12 - pp13) * cos1_4;
    p14 = pp14 + pp15;
    p15 = (pp14 - pp15) * cos1_4;


    // manually doing something that a compiler should handle sucks
    // coding like this is hard to read
    @LOC("IN,SynthesisFilter.L4") float tmp2;
//    new_v5 = (new_v11 = (new_v13 = (new_v15 = p15) + p7) + p11)
//    + p5 + p13;
    new_v15 = p15;
    new_v13 = p15 + p7;
    new_v11 = p15 + p7 + p11;
    new_v5 = p15 + p7 + p11 + p5 + p13;
    
//    new_v7 = (new_v9 = p15 + p11 + p3) + p13;
    new_v9 = p15 + p11 + p3;
    new_v7 = new_v9 + p13;
    
    new_v16/*33-17*/ = -(new_v1 = (tmp1 = p13 + p15 + p9) + p1) - p14;
    new_v18/*35-17*/ = -(new_v3 = tmp1 + p5 + p7) - p6 - p14;

    new_v22/*39-17*/ = (tmp1 = -p10 - p11 - p14 - p15)
    - p13 - p2 - p3;
    new_v20/*37-17*/ = tmp1 - p13 - p5 - p6 - p7;
    new_v24/*41-17*/ = tmp1 - p12 - p2 - p3;
    new_v26/*43-17*/ = tmp1 - p12 - (tmp2 = p4 + p6 + p7);
    new_v30/*47-17*/ = (tmp1 = -p8 - p12 - p14 - p15) - p0;
    new_v28/*45-17*/ = tmp1 - tmp2;

    // insert V[0-15] (== new_v[0-15]) into actual v:	
    // float[] x2 = actual_v + actual_write_pos;
    //float dest[] = actual_v; //actual_v subbed in so as not to create a new area

    //int pos = actual_write_pos; //substituted to simplify location relations

    v1[0 + actual_write_pos] = new_v0;
    v1[16 + actual_write_pos] = new_v1;
    v1[32 + actual_write_pos] = new_v2;
    v1[48 + actual_write_pos] = new_v3;
    v1[64 + actual_write_pos] = new_v4;
    v1[80 + actual_write_pos] = new_v5;
    v1[96 + actual_write_pos] = new_v6;
    v1[112 + actual_write_pos] = new_v7;
    v1[128 + actual_write_pos] = new_v8;
    v1[144 + actual_write_pos] = new_v9;
    v1[160 + actual_write_pos] = new_v10;
    v1[176 + actual_write_pos] = new_v11;
    v1[192 + actual_write_pos] = new_v12;
    v1[208 + actual_write_pos] = new_v13;
    v1[224 + actual_write_pos] = new_v14;
    v1[240 + actual_write_pos] = new_v15;

    // V[16] is always 0.0:
    v1[256 + actual_write_pos] = 0.0f;

    // insert V[17-31] (== -new_v[15-1]) into actual v:
    v1[272 + actual_write_pos] = -new_v15;
    v1[288 + actual_write_pos] = -new_v14;
    v1[304 + actual_write_pos] = -new_v13;
    v1[320 + actual_write_pos] = -new_v12;
    v1[336 + actual_write_pos] = -new_v11;
    v1[352 + actual_write_pos] = -new_v10;
    v1[368 + actual_write_pos] = -new_v9;
    v1[384 + actual_write_pos] = -new_v8;
    v1[400 + actual_write_pos] = -new_v7;
    v1[416 + actual_write_pos] = -new_v6;
    v1[432 + actual_write_pos] = -new_v5;
    v1[448 + actual_write_pos] = -new_v4;
    v1[464 + actual_write_pos] = -new_v3;
    v1[480 + actual_write_pos] = -new_v2;
    v1[496 + actual_write_pos] = -new_v1;

    // insert V[32] (== -new_v[0]) into other v:
    //dest = (actual_v==v1) ? v2 : v1;  //assignment replaced with if statement so that new areas are not created

    v2[0 + actual_write_pos] = -new_v0;
    // insert V[33-48] (== new_v[16-31]) into other v:
    v2[16 + actual_write_pos] = new_v16;
    v2[32 + actual_write_pos] = new_v17;
    v2[48 + actual_write_pos] = new_v18;
    v2[64 + actual_write_pos] = new_v19;
    v2[80 + actual_write_pos] = new_v20;
    v2[96 + actual_write_pos] = new_v21;
    v2[112 + actual_write_pos] = new_v22;
    v2[128 + actual_write_pos] = new_v23;
    v2[144 + actual_write_pos] = new_v24;
    v2[160 + actual_write_pos] = new_v25;
    v2[176 + actual_write_pos] = new_v26;
    v2[192 + actual_write_pos] = new_v27;
    v2[208 + actual_write_pos] = new_v28;
    v2[224 + actual_write_pos] = new_v29;
    v2[240 + actual_write_pos] = new_v30;
    v2[256 + actual_write_pos] = new_v31;

    // insert V[49-63] (== new_v[30-16]) into other v:
    v2[272 + actual_write_pos] = new_v30;
    v2[288 + actual_write_pos] = new_v29;
    v2[304 + actual_write_pos] = new_v28;
    v2[320 + actual_write_pos] = new_v27;
    v2[336 + actual_write_pos] = new_v26;
    v2[352 + actual_write_pos] = new_v25;
    v2[368 + actual_write_pos] = new_v24;
    v2[384 + actual_write_pos] = new_v23;
    v2[400 + actual_write_pos] = new_v22;
    v2[416 + actual_write_pos] = new_v21;
    v2[432 + actual_write_pos] = new_v20;
    v2[448 + actual_write_pos] = new_v19;
    v2[464 + actual_write_pos] = new_v18;
    v2[480 + actual_write_pos] = new_v17;
    v2[496 + actual_write_pos] = new_v16;
  }
	
  /**
   * Compute PCM Samples.
   */
  
  @LOC("TMP") private float[] _tmpOut = new float[32];
  
  @LATTICE("THIS<DVP,DVP<I,DVP*,I*,THISLOC=THIS")
  private void compute_pcm_samples0(@LOC("THIS") Obuffer buffer)
  {
      //final float[] vp = v1;  //subbed in variable name instead to reduce areas	
	//int inc = v_inc;
	//final float[] tmpOut = _tmpOut; //subbed in variable name instread to reduce areas
	 @LOC("DVP") int dvp =0;
	
			// fat chance of having this loop unroll
	        for(@LOC("I") int i=0; i<32; i++)
		{
		@LOC("THIS,SynthesisFilter.PCM") float pcm_sample;
		//final float[] dp = d16[i]; //subbed in variable name instead to reduce areas
		pcm_sample = (float)(((v1[0 + dvp] * d16[i][0]) +
			(v1[15 + dvp] * d16[i][1]) +
			(v1[14 + dvp] * d16[i][2]) +
			(v1[13 + dvp] * d16[i][3]) +
			(v1[12 + dvp] * d16[i][4]) +
			(v1[11 + dvp] * d16[i][5]) +
			(v1[10 + dvp] * d16[i][6]) +
			(v1[9 + dvp] * d16[i][7]) +
			(v1[8 + dvp] * d16[i][8]) +
			(v1[7 + dvp] * d16[i][9]) +
			(v1[6 + dvp] * d16[i][10]) +
			(v1[5 + dvp] * d16[i][11]) +
			(v1[4 + dvp] * d16[i][12]) +
			(v1[3 + dvp] * d16[i][13]) +
			(v1[2 + dvp] * d16[i][14]) +
			(v1[1 + dvp] * d16[i][15])
			) * scalefactor);

            _tmpOut[i] = pcm_sample;
			
			dvp += 16;
	} // for
  }
  @LATTICE("THIS<DVP,DVP<I,DVP*,I*,THISLOC=THIS")
  private void compute_pcm_samples1(@LOC("THIS") Obuffer buffer)
  {
      //final float[] vp = v1;	
	//int inc = v_inc;
	//final float[] tmpOut = _tmpOut;
	 @LOC("DVP") int dvp =0;
	
			// fat chance of having this loop unroll
			for(@LOC("I") int i=0; i<32; i++)
			{
			    //final float[] dp = d16[i];
				@LOC("THIS,SynthesisFilter.PCM") float pcm_sample;

				pcm_sample = (float)(((v1[1 + dvp] * d16[i][0]) +
						      (v1[0 + dvp] * d16[i][1]) +
					(v1[15 + dvp] * d16[i][2]) +
					(v1[14 + dvp] * d16[i][3]) +
					(v1[13 + dvp] * d16[i][4]) +
					(v1[12 + dvp] * d16[i][5]) +
					(v1[11 + dvp] * d16[i][6]) +
					(v1[10 + dvp] * d16[i][7]) +
					(v1[9 + dvp] * d16[i][8]) +
					(v1[8 + dvp] * d16[i][9]) +
					(v1[7 + dvp] * d16[i][10]) +
					(v1[6 + dvp] * d16[i][11]) +
					(v1[5 + dvp] * d16[i][12]) +
					(v1[4 + dvp] * d16[i][13]) +
					(v1[3 + dvp] * d16[i][14]) +
					(v1[2 + dvp] * d16[i][15])
					) * scalefactor);

            _tmpOut[i] = pcm_sample;
//			v1
			dvp += 16;
         } // for
  }
  @LATTICE("THIS<DVP,DVP<I,DVP*,I*,THISLOC=THIS")
  private void compute_pcm_samples2(@LOC("THIS") Obuffer buffer)
  {
      //final float[] vp = v1;
	
	//int inc = v_inc;
	//final float[] tmpOut = _tmpOut;
	 @LOC("DVP") int dvp =0;
	
			// fat chance of having this loop unroll
			for(@LOC("I") int i=0; i<32; i++)
			{
			    //final float[] dp = d16[i];
				@LOC("THIS,SynthesisFilter.PCM") float pcm_sample;

				pcm_sample = (float)(((v1[2 + dvp] * d16[i][0]) +
					(v1[1 + dvp] * d16[i][1]) +
					(v1[0 + dvp] * d16[i][2]) +
					(v1[15 + dvp] * d16[i][3]) +
					(v1[14 + dvp] * d16[i][4]) +
					(v1[13 + dvp] * d16[i][5]) +
					(v1[12 + dvp] * d16[i][6]) +
					(v1[11 + dvp] * d16[i][7]) +
					(v1[10 + dvp] * d16[i][8]) +
					(v1[9 + dvp] * d16[i][9]) +
					(v1[8 + dvp] * d16[i][10]) +
					(v1[7 + dvp] * d16[i][11]) +
					(v1[6 + dvp] * d16[i][12]) +
					(v1[5 + dvp] * d16[i][13]) +
					(v1[4 + dvp] * d16[i][14]) +
					(v1[3 + dvp] * d16[i][15])
					) * scalefactor);

            _tmpOut[i] = pcm_sample;
			
			dvp += 16;
			} // for
  }
  @LATTICE("THIS<DVP,DVP<I,DVP*,I*,THISLOC=THIS")	
  private void compute_pcm_samples3(@LOC("THIS") Obuffer buffer)
  {
      //final float[] vp = v1;
	
	int idx = 0;
	//int inc = v_inc;
	//final float[] tmpOut = _tmpOut;
	 @LOC("DVP") int dvp =0;
	
			// fat chance of having this loop unroll
			for(@LOC("I") int i=0; i<32; i++)
			{
			    //final float[] dp = d16[i];
				@LOC("THIS,SynthesisFilter.PCM") float pcm_sample;

				pcm_sample = (float) ( ( (v1[3 + dvp] * d16[i][0]) +
					(v1[2 + dvp] * d16[i][1]) +
					(v1[1 + dvp] * d16[i][2]) +
					(v1[0 + dvp] * d16[i][3]) +
					(v1[15 + dvp] * d16[i][4]) +
					(v1[14 + dvp] * d16[i][5]) +
					(v1[13 + dvp] * d16[i][6]) +
					(v1[12 + dvp] * d16[i][7]) +
					(v1[11 + dvp] * d16[i][8]) +
					(v1[10 + dvp] * d16[i][9]) +
					(v1[9 + dvp] * d16[i][10]) +
					(v1[8 + dvp] * d16[i][11]) +
					(v1[7 + dvp] * d16[i][12]) +
					(v1[6 + dvp] * d16[i][13]) +
					(v1[5 + dvp] * d16[i][14]) +
					(v1[4 + dvp] * d16[i][15])
					) * scalefactor);

            _tmpOut[i] = pcm_sample;
			
			dvp += 16;
			} // for
  }
  @LATTICE("THIS<DVP,DVP<I,DVP*,I*,THISLOC=THIS")			
  private void compute_pcm_samples4(@LOC("THIS") Obuffer buffer)
  {
      //final float[] vp = v1;
	
	//int inc = v_inc;
	//final float[] tmpOut = _tmpOut;
	 @LOC("DVP") int dvp =0;
	
			// fat chance of having this loop unroll
			for(@LOC("I") int i=0; i<32; i++)
			{
			    //final float[] dp = d16[i];
				@LOC("THIS,SynthesisFilter.PCM") float pcm_sample;

				pcm_sample = (float)(((v1[4 + dvp] * d16[i][0]) +
					(v1[3 + dvp] * d16[i][1]) +
					(v1[2 + dvp] * d16[i][2]) +
					(v1[1 + dvp] * d16[i][3]) +
					(v1[0 + dvp] * d16[i][4]) +
					(v1[15 + dvp] * d16[i][5]) +
					(v1[14 + dvp] * d16[i][6]) +
					(v1[13 + dvp] * d16[i][7]) +
					(v1[12 + dvp] * d16[i][8]) +
					(v1[11 + dvp] * d16[i][9]) +
					(v1[10 + dvp] * d16[i][10]) +
					(v1[9 + dvp] * d16[i][11]) +
					(v1[8 + dvp] * d16[i][12]) +
					(v1[7 + dvp] * d16[i][13]) +
					(v1[6 + dvp] * d16[i][14]) +
					(v1[5 + dvp] * d16[i][15])
					) * scalefactor);

            _tmpOut[i] = pcm_sample;
			
			dvp += 16;
			} // for
  }
  @LATTICE("THIS<DVP,DVP<I,DVP*,I*,THISLOC=THIS")
  private void compute_pcm_samples5(@LOC("THIS") Obuffer buffer)
  {
      //final float[] vp = v1;
	
	//int inc = v_inc;
	//final float[] tmpOut = _tmpOut;
	 @LOC("DVP") int dvp =0;
	
			// fat chance of having this loop unroll
			for(@LOC("I") int i=0; i<32; i++)
			{
			    //	final float[] dp = d16[i];
				@LOC("THIS,SynthesisFilter.PCM") float pcm_sample;

				pcm_sample = (float)(((v1[5 + dvp] * d16[i][0]) +
					(v1[4 + dvp] * d16[i][1]) +
					(v1[3 + dvp] * d16[i][2]) +
					(v1[2 + dvp] * d16[i][3]) +
					(v1[1 + dvp] * d16[i][4]) +
					(v1[0 + dvp] * d16[i][5]) +
					(v1[15 + dvp] * d16[i][6]) +
					(v1[14 + dvp] * d16[i][7]) +
					(v1[13 + dvp] * d16[i][8]) +
					(v1[12 + dvp] * d16[i][9]) +
					(v1[11 + dvp] * d16[i][10]) +
					(v1[10 + dvp] * d16[i][11]) +
					(v1[9 + dvp] * d16[i][12]) +
					(v1[8 + dvp] * d16[i][13]) +
					(v1[7 + dvp] * d16[i][14]) +
					(v1[6 + dvp] * d16[i][15])
					) * scalefactor);

            _tmpOut[i] = pcm_sample;
			
			dvp += 16;
			} // for
  }
  @LATTICE("THIS<DVP,DVP<I,DVP*,I*,THISLOC=THIS")
  private void compute_pcm_samples6(@LOC("THIS") Obuffer buffer)
  {
      //final float[] vp = v1;	
	//int inc = v_inc;
	//final float[] tmpOut = _tmpOut;
	 @LOC("DVP") int dvp =0;
	
			// fat chance of having this loop unroll
			for(@LOC("I") int i=0; i<32; i++)
			{
			    //final float[] dp = d16[i];
				@LOC("THIS,SynthesisFilter.PCM") float pcm_sample;

				pcm_sample = (float)(((v1[6 + dvp] * d16[i][0]) +
					(v1[5 + dvp] * d16[i][1]) +
					(v1[4 + dvp] * d16[i][2]) +
					(v1[3 + dvp] * d16[i][3]) +
					(v1[2 + dvp] * d16[i][4]) +
					(v1[1 + dvp] * d16[i][5]) +
					(v1[0 + dvp] * d16[i][6]) +
					(v1[15 + dvp] * d16[i][7]) +
					(v1[14 + dvp] * d16[i][8]) +
					(v1[13 + dvp] * d16[i][9]) +
					(v1[12 + dvp] * d16[i][10]) +
					(v1[11 + dvp] * d16[i][11]) +
					(v1[10 + dvp] * d16[i][12]) +
					(v1[9 + dvp] * d16[i][13]) +
					(v1[8 + dvp] * d16[i][14]) +
					(v1[7 + dvp] * d16[i][15])
					) * scalefactor);

            _tmpOut[i] = pcm_sample;
			
			dvp += 16;
			} // for
  }
  @LATTICE("THIS<DVP,DVP<I,DVP*,I*,THISLOC=THIS")
  private void compute_pcm_samples7(@LOC("THIS") Obuffer buffer)
  {
      //final float[] vp = v1;
	
	//int inc = v_inc;
	//final float[] tmpOut = _tmpOut;
	 @LOC("DVP") int dvp =0;
	
			// fat chance of having this loop unroll
			for(@LOC("I") int i=0; i<32; i++)
			{
			    //final float[] dp = d16[i];
				@LOC("THIS,SynthesisFilter.PCM") float pcm_sample;

				pcm_sample = (float)(((v1[7 + dvp] * d16[i][0]) +
					(v1[6 + dvp] * d16[i][1]) +
					(v1[5 + dvp] * d16[i][2]) +
					(v1[4 + dvp] * d16[i][3]) +
					(v1[3 + dvp] * d16[i][4]) +
					(v1[2 + dvp] * d16[i][5]) +
					(v1[1 + dvp] * d16[i][6]) +
					(v1[0 + dvp] * d16[i][7]) +
					(v1[15 + dvp] * d16[i][8]) +
					(v1[14 + dvp] * d16[i][9]) +
					(v1[13 + dvp] * d16[i][10]) +
					(v1[12 + dvp] * d16[i][11]) +
					(v1[11 + dvp] * d16[i][12]) +
					(v1[10 + dvp] * d16[i][13]) +
					(v1[9 + dvp] * d16[i][14]) +
					(v1[8 + dvp] * d16[i][15])
					) * scalefactor);

            _tmpOut[i] = pcm_sample;
			
			dvp += 16;
			} // for
  }
  @LATTICE("THIS<DVP,DVP<I,DVP*,I*,THISLOC=THIS")
  private void compute_pcm_samples8(@LOC("THIS") Obuffer buffer)
  {
      //final float[] vp = v1;
	
	//int inc = v_inc;
	//final float[] tmpOut = _tmpOut;
	 @LOC("DVP") int dvp =0;
	
			// fat chance of having this loop unroll
			for(@LOC("I") int i=0; i<32; i++)
			{
			    //final float[] dp = d16[i];
				@LOC("THIS,SynthesisFilter.PCM") float pcm_sample;

				pcm_sample = (float)(((v1[8 + dvp] * d16[i][0]) +
					(v1[7 + dvp] * d16[i][1]) +
					(v1[6 + dvp] * d16[i][2]) +
					(v1[5 + dvp] * d16[i][3]) +
					(v1[4 + dvp] * d16[i][4]) +
					(v1[3 + dvp] * d16[i][5]) +
					(v1[2 + dvp] * d16[i][6]) +
					(v1[1 + dvp] * d16[i][7]) +
					(v1[0 + dvp] * d16[i][8]) +
					(v1[15 + dvp] * d16[i][9]) +
					(v1[14 + dvp] * d16[i][10]) +
					(v1[13 + dvp] * d16[i][11]) +
					(v1[12 + dvp] * d16[i][12]) +
					(v1[11 + dvp] * d16[i][13]) +
					(v1[10 + dvp] * d16[i][14]) +
					(v1[9 + dvp] * d16[i][15])
					) * scalefactor);

            _tmpOut[i] = pcm_sample;
			
			dvp += 16;
			} // for
  }
  @LATTICE("THIS<DVP,DVP<I,DVP*,I*,THISLOC=THIS")
  private void compute_pcm_samples9(@LOC("THIS") Obuffer buffer)
  {
      //final float[] vp = v1;
	
	//int inc = v_inc;
	//final float[] tmpOut = _tmpOut;
	 @LOC("DVP") int dvp =0;
	
			// fat chance of having this loop unroll
			for(@LOC("I") int i=0; i<32; i++)
			{
			    //final float[] dp = d16[i];
				@LOC("THIS,SynthesisFilter.PCM") float pcm_sample;

				pcm_sample = (float)(((v1[9 + dvp] * d16[i][0]) +
					(v1[8 + dvp] * d16[i][1]) +
					(v1[7 + dvp] * d16[i][2]) +
					(v1[6 + dvp] * d16[i][3]) +
					(v1[5 + dvp] * d16[i][4]) +
					(v1[4 + dvp] * d16[i][5]) +
					(v1[3 + dvp] * d16[i][6]) +
					(v1[2 + dvp] * d16[i][7]) +
					(v1[1 + dvp] * d16[i][8]) +
					(v1[0 + dvp] * d16[i][9]) +
					(v1[15 + dvp] * d16[i][10]) +
					(v1[14 + dvp] * d16[i][11]) +
					(v1[13 + dvp] * d16[i][12]) +
					(v1[12 + dvp] * d16[i][13]) +
					(v1[11 + dvp] * d16[i][14]) +
					(v1[10 + dvp] * d16[i][15])
					) * scalefactor);

            _tmpOut[i] = pcm_sample;
			
			dvp += 16;
			} // for
  }
  @LATTICE("THIS<DVP,DVP<I,DVP*,I*,THISLOC=THIS")
  private void compute_pcm_samples10(@LOC("THIS") Obuffer buffer)
  {
      //final float[] vp = v1;	
	//int inc = v_inc;
	//final float[] tmpOut = _tmpOut;
	 @LOC("DVP") int dvp =0;
	
			// fat chance of having this loop unroll
			for(@LOC("I") int i=0; i<32; i++)
			{
			    //final float[] dp = d16[i];
				@LOC("THIS,SynthesisFilter.PCM") float pcm_sample;

				pcm_sample = (float)(((v1[10 + dvp] * d16[i][0]) +
					(v1[9 + dvp] * d16[i][1]) +
					(v1[8 + dvp] * d16[i][2]) +
					(v1[7 + dvp] * d16[i][3]) +
					(v1[6 + dvp] * d16[i][4]) +
					(v1[5 + dvp] * d16[i][5]) +
					(v1[4 + dvp] * d16[i][6]) +
					(v1[3 + dvp] * d16[i][7]) +
					(v1[2 + dvp] * d16[i][8]) +
					(v1[1 + dvp] * d16[i][9]) +
					(v1[0 + dvp] * d16[i][10]) +
					(v1[15 + dvp] * d16[i][11]) +
					(v1[14 + dvp] * d16[i][12]) +
					(v1[13 + dvp] * d16[i][13]) +
					(v1[12 + dvp] * d16[i][14]) +
					(v1[11 + dvp] * d16[i][15])
					) * scalefactor);

            _tmpOut[i] = pcm_sample;
			
			dvp += 16;
			} // for
  }
  @LATTICE("THIS<DVP,DVP<I,DVP*,I*,THISLOC=THIS")
  private void compute_pcm_samples11(@LOC("THIS") Obuffer buffer)
  {
      //final float[] vp = v1;
	
	//int inc = v_inc;
	//final float[] tmpOut = _tmpOut;
	 @LOC("DVP") int dvp =0;
	
			// fat chance of having this loop unroll
			for(@LOC("I") int i=0; i<32; i++)
			{
			    //final float[] dp = d16[i];
				@LOC("THIS,SynthesisFilter.PCM") float pcm_sample;

				pcm_sample = (float)(((v1[11 + dvp] * d16[i][0]) +
					(v1[10 + dvp] * d16[i][1]) +
					(v1[9 + dvp] * d16[i][2]) +
					(v1[8 + dvp] * d16[i][3]) +
					(v1[7 + dvp] * d16[i][4]) +
					(v1[6 + dvp] * d16[i][5]) +
					(v1[5 + dvp] * d16[i][6]) +
					(v1[4 + dvp] * d16[i][7]) +
					(v1[3 + dvp] * d16[i][8]) +
					(v1[2 + dvp] * d16[i][9]) +
					(v1[1 + dvp] * d16[i][10]) +
					(v1[0 + dvp] * d16[i][11]) +
					(v1[15 + dvp] * d16[i][12]) +
					(v1[14 + dvp] * d16[i][13]) +
					(v1[13 + dvp] * d16[i][14]) +
					(v1[12 + dvp] * d16[i][15])
					) * scalefactor);

            _tmpOut[i] = pcm_sample;
			
			dvp += 16;
			} // for
  }
  @LATTICE("THIS<DVP,DVP<I,DVP*,I*,THISLOC=THIS")
  private void compute_pcm_samples12(@LOC("THIS") Obuffer buffer)
  {
      //final float[] vp = v1;	
	//int inc = v_inc;
	//final float[] tmpOut = _tmpOut;
	 @LOC("DVP") int dvp =0;
	
			// fat chance of having this loop unroll
			for(@LOC("I") int i=0; i<32; i++)
			{
			    //final float[] dp = d16[i];
				@LOC("THIS,SynthesisFilter.PCM") float pcm_sample;

				pcm_sample = (float)(((v1[12 + dvp] * d16[i][0]) +
					(v1[11 + dvp] * d16[i][1]) +
					(v1[10 + dvp] * d16[i][2]) +
					(v1[9 + dvp] * d16[i][3]) +
					(v1[8 + dvp] * d16[i][4]) +
					(v1[7 + dvp] * d16[i][5]) +
					(v1[6 + dvp] * d16[i][6]) +
					(v1[5 + dvp] * d16[i][7]) +
					(v1[4 + dvp] * d16[i][8]) +
					(v1[3 + dvp] * d16[i][9]) +
					(v1[2 + dvp] * d16[i][10]) +
					(v1[1 + dvp] * d16[i][11]) +
					(v1[0 + dvp] * d16[i][12]) +
					(v1[15 + dvp] * d16[i][13]) +
					(v1[14 + dvp] * d16[i][14]) +
					(v1[13 + dvp] * d16[i][15])
					) * scalefactor);

            _tmpOut[i] = pcm_sample;
			
			dvp += 16;
			} // for
  }
  @LATTICE("THIS<DVP,DVP<I,DVP*,I*,THISLOC=THIS")
  private void compute_pcm_samples13(@LOC("THIS") Obuffer buffer)
  {
      //final float[] vp = v1;
	
	//int inc = v_inc;
	//final float[] tmpOut = _tmpOut;
	 @LOC("DVP") int dvp =0;
	
			// fat chance of having this loop unroll
			for(@LOC("I") int i=0; i<32; i++)
			{
			    //final float[] dp = d16[i];
				@LOC("THIS,SynthesisFilter.PCM") float pcm_sample;

				pcm_sample = (float)(((v1[13 + dvp] * d16[i][0]) +
					(v1[12 + dvp] * d16[i][1]) +
					(v1[11 + dvp] * d16[i][2]) +
					(v1[10 + dvp] * d16[i][3]) +
					(v1[9 + dvp] * d16[i][4]) +
					(v1[8 + dvp] * d16[i][5]) +
					(v1[7 + dvp] * d16[i][6]) +
					(v1[6 + dvp] * d16[i][7]) +
					(v1[5 + dvp] * d16[i][8]) +
					(v1[4 + dvp] * d16[i][9]) +
					(v1[3 + dvp] * d16[i][10]) +
					(v1[2 + dvp] * d16[i][11]) +
					(v1[1 + dvp] * d16[i][12]) +
					(v1[0 + dvp] * d16[i][13]) +
					(v1[15 + dvp] * d16[i][14]) +
					(v1[14 + dvp] * d16[i][15])
					) * scalefactor);

            _tmpOut[i] = pcm_sample;
			
			dvp += 16;
			} // for
  }
  @LATTICE("THIS<DVP,DVP<I,DVP*,I*,THISLOC=THIS")
  private void compute_pcm_samples14(@LOC("THIS") Obuffer buffer)
  {
      //final float[] vp = v1;
	
	//int inc = v_inc;
	//final float[] tmpOut = _tmpOut;
	 @LOC("DVP") int dvp =0;
	
			// fat chance of having this loop unroll
			for(@LOC("I") int i=0; i<32; i++)
			{
			    //final float[] dp = d16[i];
				@LOC("THIS,SynthesisFilter.PCM") float pcm_sample;

				pcm_sample = (float)(((v1[14 + dvp] * d16[i][0]) +
					(v1[13 + dvp] * d16[i][1]) +
					(v1[12 + dvp] * d16[i][2]) +
					(v1[11 + dvp] * d16[i][3]) +
					(v1[10 + dvp] * d16[i][4]) +
					(v1[9 + dvp] * d16[i][5]) +
					(v1[8 + dvp] * d16[i][6]) +
					(v1[7 + dvp] * d16[i][7]) +
					(v1[6 + dvp] * d16[i][8]) +
					(v1[5 + dvp] * d16[i][9]) +
					(v1[4 + dvp] * d16[i][10]) +
					(v1[3 + dvp] * d16[i][11]) +
					(v1[2 + dvp] * d16[i][12]) +
					(v1[1 + dvp] * d16[i][13]) +
					(v1[0 + dvp] * d16[i][14]) +
					(v1[15 + dvp] * d16[i][15])
					) * scalefactor);

            _tmpOut[i] = pcm_sample;
			
			dvp += 16;
			} // for
  }
  @LATTICE("THIS<DVP,DVP<I,DVP*,I*,THISLOC=THIS")
  private void compute_pcm_samples15(@LOC("THIS") Obuffer buffer)
  {
      //final float[] vp = v1;
		
	//int inc = v_inc;
	//final float[] tmpOut = _tmpOut;
      @LOC("DVP") int dvp =0;
	
			// fat chance of having this loop unroll
			for(@LOC("I") int i=0; i<32; i++)
			{
				@LOC("THIS,SynthesisFilter.PCM") float pcm_sample;
				//final float dp[] = d16[i];
				pcm_sample = (float)(((v1[15 + dvp] * d16[i][0]) +
					(v1[14 + dvp] * d16[i][1]) +
					(v1[13 + dvp] * d16[i][2]) +
					(v1[12 + dvp] * d16[i][3]) +
					(v1[11 + dvp] * d16[i][4]) +
					(v1[10 + dvp] * d16[i][5]) +
					(v1[9 + dvp] * d16[i][6]) +
					(v1[8 + dvp] * d16[i][7]) +
					(v1[7 + dvp] * d16[i][8]) +
					(v1[6 + dvp] * d16[i][9]) +
					(v1[5 + dvp] * d16[i][10]) +
					(v1[4 + dvp] * d16[i][11]) +
					(v1[3 + dvp] * d16[i][12]) +
					(v1[2 + dvp] * d16[i][13]) +
					(v1[1 + dvp] * d16[i][14]) +
					(v1[0 + dvp] * d16[i][15])
					) * scalefactor);

            _tmpOut[i] = pcm_sample;			
			dvp += 16;
			} // for
		}
	 	 	 	 
private void compute_pcm_samples(@LOC("GLOBAL") Obuffer buffer)
{
	
	switch (actual_write_pos)
	{
	case 0: 
		compute_pcm_samples0(buffer);
		break;
	case 1: 
		compute_pcm_samples1(buffer);
		break;
	case 2: 
		compute_pcm_samples2(buffer);
		break;
	case 3: 
		compute_pcm_samples3(buffer);
		break;
	case 4: 
		compute_pcm_samples4(buffer);
		break;
	case 5: 
		compute_pcm_samples5(buffer);
		break;
	case 6: 
		compute_pcm_samples6(buffer);
		break;
	case 7: 
		compute_pcm_samples7(buffer);
		break;
	case 8: 
		compute_pcm_samples8(buffer);
		break;
	case 9: 
		compute_pcm_samples9(buffer);
		break;
	case 10: 
		compute_pcm_samples10(buffer);
		break;
	case 11: 
		compute_pcm_samples11(buffer);
		break;
	case 12: 
		compute_pcm_samples12(buffer);
		break;
	case 13: 
		compute_pcm_samples13(buffer);
		break;
	case 14: 
		compute_pcm_samples14(buffer);
		break;
	case 15: 
		compute_pcm_samples15(buffer);
		break;
	}
		
	if (buffer!=null)
	{		
		buffer.appendSamples(channel, _tmpOut);
	}
	 
/*
	 // MDM: I was considering putting in quality control for
	 // low-spec CPUs, but the performance gain (about 10-15%) 
	 // did not justify the considerable drop in audio quality.
		switch (inc)
		{
		case 16:		 
		    buffer.appendSamples(channel, tmpOut);
		    break;
		case 32:
			for (int i=0; i<16; i++)
			{
				buffer.append(channel, (short)tmpOut[i]);
				buffer.append(channel, (short)tmpOut[i]); 
			}
			break;			
		case 64:
			for (int i=0; i<8; i++)
			{
				buffer.append(channel, (short)tmpOut[i]);
				buffer.append(channel, (short)tmpOut[i]);
				buffer.append(channel, (short)tmpOut[i]);
				buffer.append(channel, (short)tmpOut[i]); 
			}
			break;			
	
		}
*/	 
  }

  /**
   * Calculate 32 PCM samples and put the into the Obuffer-object.
   */
  
  @LATTICE("V<THIS,THIS<SH,SH*,THISLOC=THIS")
  public void calculate_pcm_samples(@LOC("V") Obuffer buffer) {
    compute_new_v();
    compute_pcm_samples(buffer);

    actual_write_pos = (actual_write_pos + 1) & 0xf;
    // actual_v = (actual_v == v1) ? v2 : v1;

    // initialize samples[]:
    // for (register float *floatp = samples + 32; floatp > samples; )
    // *--floatp = 0.0f;

    // MDM: this may not be necessary. The Layer III decoder always
    // outputs 32 subband samples, but I haven't checked layer I & II.
    for (@LOC("SH") int p = 0; p < 32; p++)
      samples[p] = 0.0f;
  }
  
  
  @LOC("EQ") private static final double MY_PI = 3.14159265358979323846;
  @LOC("SA") private static final float cos1_64  =(float) (1.0 / (2.0 * Math.cos(MY_PI        / 64.0)));
  @LOC("SA") private static final float cos3_64  =(float) (1.0 / (2.0 * Math.cos(MY_PI * 3.0  / 64.0)));
  @LOC("SA") private static final float cos5_64  =(float) (1.0 / (2.0 * Math.cos(MY_PI * 5.0  / 64.0)));
  @LOC("SA") private static final float cos7_64  =(float) (1.0 / (2.0 * Math.cos(MY_PI * 7.0  / 64.0)));
  @LOC("SA") private static final float cos9_64  =(float) (1.0 / (2.0 * Math.cos(MY_PI * 9.0  / 64.0)));
  @LOC("SA") private static final float cos11_64 =(float) (1.0 / (2.0 * Math.cos(MY_PI * 11.0 / 64.0)));
  @LOC("SA") private static final float cos13_64 =(float) (1.0 / (2.0 * Math.cos(MY_PI * 13.0 / 64.0)));
  @LOC("SA") private static final float cos15_64 =(float) (1.0 / (2.0 * Math.cos(MY_PI * 15.0 / 64.0)));
  @LOC("SA") private static final float cos17_64 =(float) (1.0 / (2.0 * Math.cos(MY_PI * 17.0 / 64.0)));
  @LOC("SA") private static final float cos19_64 =(float) (1.0 / (2.0 * Math.cos(MY_PI * 19.0 / 64.0)));
  @LOC("SA") private static final float cos21_64 =(float) (1.0 / (2.0 * Math.cos(MY_PI * 21.0 / 64.0)));
  @LOC("SA") private static final float cos23_64 =(float) (1.0 / (2.0 * Math.cos(MY_PI * 23.0 / 64.0)));
  @LOC("SA") private static final float cos25_64 =(float) (1.0 / (2.0 * Math.cos(MY_PI * 25.0 / 64.0)));
  @LOC("SA") private static final float cos27_64 =(float) (1.0 / (2.0 * Math.cos(MY_PI * 27.0 / 64.0)));
  @LOC("SA") private static final float cos29_64 =(float) (1.0 / (2.0 * Math.cos(MY_PI * 29.0 / 64.0)));
  @LOC("SA") private static final float cos31_64 =(float) (1.0 / (2.0 * Math.cos(MY_PI * 31.0 / 64.0)));
  @LOC("SA") private static final float cos1_32  =(float) (1.0 / (2.0 * Math.cos(MY_PI        / 32.0)));
  @LOC("SA") private static final float cos3_32  =(float) (1.0 / (2.0 * Math.cos(MY_PI * 3.0  / 32.0)));
  @LOC("SA") private static final float cos5_32  =(float) (1.0 / (2.0 * Math.cos(MY_PI * 5.0  / 32.0)));
  @LOC("SA") private static final float cos7_32  =(float) (1.0 / (2.0 * Math.cos(MY_PI * 7.0  / 32.0)));
  @LOC("SA") private static final float cos9_32  =(float) (1.0 / (2.0 * Math.cos(MY_PI * 9.0  / 32.0)));
  @LOC("SA") private static final float cos11_32 =(float) (1.0 / (2.0 * Math.cos(MY_PI * 11.0 / 32.0)));
  @LOC("SA") private static final float cos13_32 =(float) (1.0 / (2.0 * Math.cos(MY_PI * 13.0 / 32.0)));
  @LOC("SA") private static final float cos15_32 =(float) (1.0 / (2.0 * Math.cos(MY_PI * 15.0 / 32.0)));
  @LOC("SA") private static final float cos1_16  =(float) (1.0 / (2.0 * Math.cos(MY_PI        / 16.0)));
  @LOC("SA") private static final float cos3_16  =(float) (1.0 / (2.0 * Math.cos(MY_PI * 3.0  / 16.0)));
  @LOC("SA") private static final float cos5_16  =(float) (1.0 / (2.0 * Math.cos(MY_PI * 5.0  / 16.0)));
  @LOC("SA") private static final float cos7_16  =(float) (1.0 / (2.0 * Math.cos(MY_PI * 7.0  / 16.0)));
  @LOC("SA") private static final float cos1_8   =(float) (1.0 / (2.0 * Math.cos(MY_PI        / 8.0)));
  @LOC("SA") private static final float cos3_8   =(float) (1.0 / (2.0 * Math.cos(MY_PI * 3.0  / 8.0)));
  @LOC("SA") private static final float cos1_4   =(float) (1.0 / (2.0 * Math.cos(MY_PI / 4.0)));
  
  // Note: These values are not in the same order
  // as in Annex 3-B.3 of the ISO/IEC DIS 11172-3 
  // private float d[] = {0.000000000, -4.000442505};
  
//  @LOC("V2") private static float d[] = null;
  
  /** 
   * d[] split into subarrays of length 16. This provides for
   * more faster access by allowing a block of 16 to be addressed
   * with constant offset. 
   **/
  @LOC("V2") private static float d16[][] = null;	
  
	/**
	 * Converts a 1D array into a number of smaller arrays. This is used
	 * to achieve offset + constant indexing into an array. Each sub-array
	 * represents a block of values of the original array. 
	 * @param array			The array to split up into blocks.
	 * @param blockSize		The size of the blocks to split the array
	 *						into. This must be an exact divisor of
	 *						the length of the array, or some data
	 *						will be lost from the main array.
	 * 
	 * @return	An array of arrays in which each element in the returned
	 *			array will be of length <code>blockSize</code>.
	 */
	static private float[][] splitArray(final float[] array, final int blockSize)
	{
		int size = array.length / blockSize;
		float[][] split = new float[size][];
		for (int i=0; i<size; i++)
		{
			split[i] = subArray(array, i*blockSize, blockSize);
		}
		return split;
	}
	
	/**
	 * Returns a subarray of an existing array.
	 * 
	 * @param array	The array to retrieve a subarra from.
	 * @param offs	The offset in the array that corresponds to
	 *				the first index of the subarray.
	 * @param len	The number of indeces in the subarray.
	 * @return The subarray, which may be of length 0.
	 */
	static private float[] subArray(final float[] array, final int offs, int len)
	{
		if (offs+len > array.length)
		{
			len = array.length-offs;
		}
		
		if (len < 0)
			len = 0;
		
		float[] subarray = new float[len];
		for (int i=0; i<len; i++)
		{
			subarray[i] = array[offs+i];
		}
		
		return subarray;
	}
	
	// The original data for d[]. This data is loaded from a file
	// to reduce the overall package size and to improve performance. 

  static final float d[] = {
  	0.000000000f, -0.000442505f,  0.003250122f, -0.007003784f,
  	0.031082153f, -0.078628540f,  0.100311279f, -0.572036743f,
  	1.144989014f,  0.572036743f,  0.100311279f,  0.078628540f,
  	0.031082153f,  0.007003784f,  0.003250122f,  0.000442505f,
   -0.000015259f, -0.000473022f,  0.003326416f, -0.007919312f,
  	0.030517578f, -0.084182739f,  0.090927124f, -0.600219727f,
  	1.144287109f,  0.543823242f,  0.108856201f,  0.073059082f,
  	0.031478882f,  0.006118774f,  0.003173828f,  0.000396729f,
   -0.000015259f, -0.000534058f,  0.003387451f, -0.008865356f,
  	0.029785156f, -0.089706421f,  0.080688477f, -0.628295898f,
  	1.142211914f,  0.515609741f,  0.116577148f,  0.067520142f,
    0.031738281f,  0.005294800f,  0.003082275f,  0.000366211f,
   -0.000015259f, -0.000579834f,  0.003433228f, -0.009841919f,
    0.028884888f, -0.095169067f,  0.069595337f, -0.656219482f,
  	1.138763428f,  0.487472534f,  0.123474121f,  0.061996460f,
    0.031845093f,  0.004486084f,  0.002990723f,  0.000320435f,
   -0.000015259f, -0.000625610f,  0.003463745f, -0.010848999f,
    0.027801514f, -0.100540161f,  0.057617188f, -0.683914185f,
  	1.133926392f,  0.459472656f,  0.129577637f,  0.056533813f,
  	0.031814575f,  0.003723145f,  0.002899170f,  0.000289917f,
   -0.000015259f, -0.000686646f,  0.003479004f, -0.011886597f,
  	0.026535034f, -0.105819702f,  0.044784546f, -0.711318970f,
  	1.127746582f,  0.431655884f,  0.134887695f,  0.051132202f,
  	0.031661987f,  0.003005981f,  0.002792358f,  0.000259399f,
   -0.000015259f, -0.000747681f,  0.003479004f, -0.012939453f,
  	0.025085449f, -0.110946655f,  0.031082153f, -0.738372803f,
    1.120223999f,  0.404083252f,  0.139450073f,  0.045837402f,
    0.031387329f,  0.002334595f,  0.002685547f,  0.000244141f,
   -0.000030518f, -0.000808716f,  0.003463745f, -0.014022827f,
    0.023422241f, -0.115921021f,  0.016510010f, -0.765029907f,
  	1.111373901f,  0.376800537f,  0.143264771f,  0.040634155f,
    0.031005859f,  0.001693726f,  0.002578735f,  0.000213623f,
   -0.000030518f, -0.000885010f,  0.003417969f, -0.015121460f,
  	0.021575928f, -0.120697021f,  0.001068115f, -0.791213989f,
    1.101211548f,  0.349868774f,  0.146362305f,  0.035552979f,
  	0.030532837f,  0.001098633f,  0.002456665f,  0.000198364f,
   -0.000030518f, -0.000961304f,  0.003372192f, -0.016235352f,
    0.019531250f, -0.125259399f, -0.015228271f, -0.816864014f,
  	1.089782715f,  0.323318481f,  0.148773193f,  0.030609131f,
  	0.029937744f,  0.000549316f,  0.002349854f,  0.000167847f,
   -0.000030518f, -0.001037598f,  0.003280640f, -0.017349243f,
  	0.017257690f, -0.129562378f, -0.032379150f, -0.841949463f,
    1.077117920f,  0.297210693f,  0.150497437f,  0.025817871f,
    0.029281616f,  0.000030518f,  0.002243042f,  0.000152588f,
   -0.000045776f, -0.001113892f,  0.003173828f, -0.018463135f,
  	0.014801025f, -0.133590698f, -0.050354004f, -0.866363525f,
  	1.063217163f,  0.271591187f,  0.151596069f,  0.021179199f,
  	0.028533936f, -0.000442505f,  0.002120972f,  0.000137329f,
   -0.000045776f, -0.001205444f,  0.003051758f, -0.019577026f,
  	0.012115479f, -0.137298584f, -0.069168091f, -0.890090942f,
  	1.048156738f,  0.246505737f,  0.152069092f,  0.016708374f,
  	0.027725220f, -0.000869751f,  0.002014160f,  0.000122070f,
   -0.000061035f, -0.001296997f,  0.002883911f, -0.020690918f,
    0.009231567f, -0.140670776f, -0.088775635f, -0.913055420f,
  	1.031936646f,  0.221984863f,  0.151962280f,  0.012420654f,
    0.026840210f, -0.001266479f,  0.001907349f,  0.000106812f,
   -0.000061035f, -0.001388550f,  0.002700806f, -0.021789551f,
  	0.006134033f, -0.143676758f, -0.109161377f, -0.935195923f,
    1.014617920f,  0.198059082f,  0.151306152f,  0.008316040f,
  	0.025909424f, -0.001617432f,  0.001785278f,  0.000106812f,
   -0.000076294f, -0.001480103f,  0.002487183f, -0.022857666f,
  	0.002822876f, -0.146255493f, -0.130310059f, -0.956481934f,
  	0.996246338f,  0.174789429f,  0.150115967f,  0.004394531f,
    0.024932861f, -0.001937866f,  0.001693726f,  0.000091553f,
   -0.000076294f, -0.001586914f,  0.002227783f, -0.023910522f,
   -0.000686646f, -0.148422241f, -0.152206421f, -0.976852417f,
    0.976852417f,  0.152206421f,  0.148422241f,  0.000686646f,
  	0.023910522f, -0.002227783f,  0.001586914f,  0.000076294f,
   -0.000091553f, -0.001693726f,  0.001937866f, -0.024932861f,
   -0.004394531f, -0.150115967f, -0.174789429f, -0.996246338f,
    0.956481934f,  0.130310059f,  0.146255493f, -0.002822876f,
    0.022857666f, -0.002487183f,  0.001480103f,  0.000076294f,
   -0.000106812f, -0.001785278f,  0.001617432f, -0.025909424f,
   -0.008316040f, -0.151306152f, -0.198059082f, -1.014617920f,
    0.935195923f,  0.109161377f,  0.143676758f, -0.006134033f,
    0.021789551f, -0.002700806f,  0.001388550f,  0.000061035f,
   -0.000106812f, -0.001907349f,  0.001266479f, -0.026840210f,
   -0.012420654f, -0.151962280f, -0.221984863f, -1.031936646f,
  	0.913055420f,  0.088775635f,  0.140670776f, -0.009231567f,
  	0.020690918f, -0.002883911f,  0.001296997f,  0.000061035f,
   -0.000122070f, -0.002014160f,  0.000869751f, -0.027725220f,
   -0.016708374f, -0.152069092f, -0.246505737f, -1.048156738f,
    0.890090942f,  0.069168091f,  0.137298584f, -0.012115479f,
  	0.019577026f, -0.003051758f,  0.001205444f,  0.000045776f,
   -0.000137329f, -0.002120972f,  0.000442505f, -0.028533936f,
   -0.021179199f, -0.151596069f, -0.271591187f, -1.063217163f,
    0.866363525f,  0.050354004f,  0.133590698f, -0.014801025f,
    0.018463135f, -0.003173828f,  0.001113892f,  0.000045776f,
   -0.000152588f, -0.002243042f, -0.000030518f, -0.029281616f,
   -0.025817871f, -0.150497437f, -0.297210693f, -1.077117920f,
  	0.841949463f,  0.032379150f,  0.129562378f, -0.017257690f,
  	0.017349243f, -0.003280640f,  0.001037598f,  0.000030518f,
   -0.000167847f, -0.002349854f, -0.000549316f, -0.029937744f,
   -0.030609131f, -0.148773193f, -0.323318481f, -1.089782715f,
  	0.816864014f,  0.015228271f,  0.125259399f, -0.019531250f,
    0.016235352f, -0.003372192f,  0.000961304f,  0.000030518f,
   -0.000198364f, -0.002456665f, -0.001098633f, -0.030532837f,
   -0.035552979f, -0.146362305f, -0.349868774f, -1.101211548f,
  	0.791213989f, -0.001068115f,  0.120697021f, -0.021575928f,
  	0.015121460f, -0.003417969f,  0.000885010f,  0.000030518f,
   -0.000213623f, -0.002578735f, -0.001693726f, -0.031005859f,
   -0.040634155f, -0.143264771f, -0.376800537f, -1.111373901f,
    0.765029907f, -0.016510010f,  0.115921021f, -0.023422241f,
    0.014022827f, -0.003463745f,  0.000808716f,  0.000030518f,
   -0.000244141f, -0.002685547f, -0.002334595f, -0.031387329f,
   -0.045837402f, -0.139450073f, -0.404083252f, -1.120223999f,
    0.738372803f, -0.031082153f,  0.110946655f, -0.025085449f,
  	0.012939453f, -0.003479004f,  0.000747681f,  0.000015259f,
   -0.000259399f, -0.002792358f, -0.003005981f, -0.031661987f,
   -0.051132202f, -0.134887695f, -0.431655884f, -1.127746582f,
  	0.711318970f, -0.044784546f,  0.105819702f, -0.026535034f,
    0.011886597f, -0.003479004f,  0.000686646f,  0.000015259f,
   -0.000289917f, -0.002899170f, -0.003723145f, -0.031814575f,
   -0.056533813f, -0.129577637f, -0.459472656f, -1.133926392f,
    0.683914185f, -0.057617188f,  0.100540161f, -0.027801514f,
  	0.010848999f, -0.003463745f,  0.000625610f,  0.000015259f,
   -0.000320435f, -0.002990723f, -0.004486084f, -0.031845093f,
   -0.061996460f, -0.123474121f, -0.487472534f, -1.138763428f,
  	0.656219482f, -0.069595337f,  0.095169067f, -0.028884888f,
  	0.009841919f, -0.003433228f,  0.000579834f,  0.000015259f,
   -0.000366211f, -0.003082275f, -0.005294800f, -0.031738281f,
   -0.067520142f, -0.116577148f, -0.515609741f, -1.142211914f,
  	0.628295898f, -0.080688477f,  0.089706421f, -0.029785156f,
  	0.008865356f, -0.003387451f,  0.000534058f,  0.000015259f,
   -0.000396729f, -0.003173828f, -0.006118774f, -0.031478882f,
   -0.073059082f, -0.108856201f, -0.543823242f, -1.144287109f,
  	0.600219727f, -0.090927124f,  0.084182739f, -0.030517578f,
	0.007919312f, -0.003326416f,  0.000473022f,  0.000015259f
	};
  
  
}
