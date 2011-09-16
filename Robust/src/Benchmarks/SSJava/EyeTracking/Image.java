public class Image {

  int width;
  int height;
  int pixel[][];

  public Image(int width, int height) {
    this.width = width;
    this.height = height;
    pixel = new int[width][height];
  }

  public void setPixel(int x, int y, int R, int G, int B) {
    pixel[x][y] = (R << 16) | (G << 8) | B;
  }

  public int getRed(int x, int y) {
    return (pixel[x][y] >> 16) & 0xff;
  }

  public int getGreen(int x, int y) {
    return (pixel[x][y] >> 8) & 0xff;
  }

  public int getBlue(int x, int y) {
    return pixel[x][y] & 0xff;
  }

  public void setPixel(int x, int y, int p) {
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
