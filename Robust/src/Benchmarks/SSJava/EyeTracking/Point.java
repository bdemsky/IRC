@LATTICE("POS")
@METHODDEFAULT("OUT<THIS,THIS<IN,THISLOC=THIS,RETURNLOC=OUT")
public class Point {

  @LOC("POS") public int x;
  @LOC("POS") public int y;

  public Point(int x, int y) {
    this.x = x;
    this.y = y;
  }

  public Point() {
  }
  
  public String toString(){
    return "("+x+","+y+")";
  }

}
