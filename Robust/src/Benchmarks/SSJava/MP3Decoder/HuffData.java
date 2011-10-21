// need to have this class for liner type system
@LATTICE("V")
public class HuffData {

  @LOC("V") public int x;
  @LOC("V") public int y;
  @LOC("V") public int w;
  @LOC("V") public int v;
  @LOC("V") public BitReserve br;

  public HuffData(int x, int y, int w, int v, BitReserve br) {
    this.x = x;
    this.y = y;
    this.w = w;
    this.v = v;
    this.br = br;
  }

}
