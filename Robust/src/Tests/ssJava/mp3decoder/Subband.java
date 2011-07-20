//package ssJava.mp3decoder;

/**
 * Abstract base class for subband classes of layer I and II
 */
@LATTICE("S<L,L<H,H<SH")
@METHODDEFAULT("OUT<V,V<SH,SH<THIS,THIS<IN,SH*,THISLOC=THIS,GLOBALLOC=IN")
static abstract class Subband
{
 /*
  *  Changes from version 1.1 to 1.2:
  *    - array size increased by one, although a scalefactor with index 63
  *      is illegal (to prevent segmentation faults)
  */
  // Scalefactors for layer I and II, Annex 3-B.1 in ISO/IEC DIS 11172:
  @LOC("H") public static final float scalefactors[] =
  {
  2.00000000000000f, 1.58740105196820f, 1.25992104989487f, 1.00000000000000f,
  0.79370052598410f, 0.62996052494744f, 0.50000000000000f, 0.39685026299205f,
  0.31498026247372f, 0.25000000000000f, 0.19842513149602f, 0.15749013123686f,
  0.12500000000000f, 0.09921256574801f, 0.07874506561843f, 0.06250000000000f,
  0.04960628287401f, 0.03937253280921f, 0.03125000000000f, 0.02480314143700f,
  0.01968626640461f, 0.01562500000000f, 0.01240157071850f, 0.00984313320230f,
  0.00781250000000f, 0.00620078535925f, 0.00492156660115f, 0.00390625000000f,
  0.00310039267963f, 0.00246078330058f, 0.00195312500000f, 0.00155019633981f,
  0.00123039165029f, 0.00097656250000f, 0.00077509816991f, 0.00061519582514f,
  0.00048828125000f, 0.00038754908495f, 0.00030759791257f, 0.00024414062500f,
  0.00019377454248f, 0.00015379895629f, 0.00012207031250f, 0.00009688727124f,
  0.00007689947814f, 0.00006103515625f, 0.00004844363562f, 0.00003844973907f,
  0.00003051757813f, 0.00002422181781f, 0.00001922486954f, 0.00001525878906f,
  0.00001211090890f, 0.00000961243477f, 0.00000762939453f, 0.00000605545445f,
  0.00000480621738f, 0.00000381469727f, 0.00000302772723f, 0.00000240310869f,
  0.00000190734863f, 0.00000151386361f, 0.00000120155435f, 0.00000000000000f /* illegal scalefactor */
  };

  public abstract void read_allocation (@LOC("IN")  Bitstream stream, @LOC("IN") Header header, @LOC("IN") Crc16 crc) throws DecoderException;
  public abstract void read_scalefactor (@LOC("IN") Bitstream stream, @LOC("IN") Header header);
  
  @RETURNLOC("OUT")
  public abstract boolean read_sampledata (@LOC("IN") Bitstream stream);
  
  @RETURNLOC("OUT")
  public abstract boolean put_next_sample (@LOC("IN") int channels,@LOC("IN")  SynthesisFilter filter1,@LOC("IN")  SynthesisFilter filter2);
};
