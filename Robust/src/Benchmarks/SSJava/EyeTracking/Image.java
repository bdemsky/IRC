public class Image {

  int width;
  int height;
  long pixel[][];

  public Image(int width, int height) {
    this.width = width;
    this.height = height;
    pixel = new long[width][height];
  }

  public void setPixel(int x, int y, long p) {
    pixel[x][y] = p;
  }

  public long getPixel(int x, int y) {
    return pixel[x][y];
  }

  public int getWidth() {
    return width;
  }

  public int getHeight() {
    return height;
  }

}
