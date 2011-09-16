public class ImageReader {
  public ImageReader() {
  }

  public Image readImage(String file) {

    FileInputStream fs = new FileInputStream(file);
    int bflen = 14; // 14 byte BITMAPFILEHEADER
    byte bf[] = new byte[bflen];
    // fs.read(bf,0,bflen);
    fs.read(bf);
    int bilen = 40; // 40-byte BITMAPINFOHEADER
    byte bi[] = new byte[bilen];
    // fs.read(bi,0,bilen);
    fs.read(bi);
    // Interperet data.
    int nsize =
        (((int) bf[5] & 0xff) << 24) | (((int) bf[4] & 0xff) << 16) | (((int) bf[3] & 0xff) << 8)
            | (int) bf[2] & 0xff;
    // System.out.println("File type is :" + (char) bf[0] + (char) bf[1]);
    // System.out.println("Size of file is :" + nsize);
    int nbisize =
        (((int) bi[3] & 0xff) << 24) | (((int) bi[2] & 0xff) << 16) | (((int) bi[1] & 0xff) << 8)
            | (int) bi[0] & 0xff;
    // System.out.println("Size of bitmapinfoheader is :" + nbisize);
    int nwidth =
        (((int) bi[7] & 0xff) << 24) | (((int) bi[6] & 0xff) << 16) | (((int) bi[5] & 0xff) << 8)
            | (int) bi[4] & 0xff;
    // System.out.println("Width is :" + nwidth);
    int nheight =
        (((int) bi[11] & 0xff) << 24) | (((int) bi[10] & 0xff) << 16) | (((int) bi[9] & 0xff) << 8)
            | (int) bi[8] & 0xff;
    // System.out.println("Height is :" + nheight);
    int nplanes = (((int) bi[13] & 0xff) << 8) | (int) bi[12] & 0xff;
    // System.out.println("Planes is :" + nplanes);
    int nbitcount = (((int) bi[15] & 0xff) << 8) | (int) bi[14] & 0xff;
    // System.out.println("BitCount is :" + nbitcount);
    // Look for non-zero values to indicate compression
    int ncompression =
        (((int) bi[19]) << 24) | (((int) bi[18]) << 16) | (((int) bi[17]) << 8) | (int) bi[16];
    // System.out.println("Compression is :" + ncompression);
    int nsizeimage =
        (((int) bi[23] & 0xff) << 24) | (((int) bi[22] & 0xff) << 16)
            | (((int) bi[21] & 0xff) << 8) | (int) bi[20] & 0xff;
    // System.out.println("SizeImage is :" + nsizeimage);

    int nxpm =
        (((int) bi[27] & 0xff) << 24) | (((int) bi[26] & 0xff) << 16)
            | (((int) bi[25] & 0xff) << 8) | (int) bi[24] & 0xff;
    // System.out.println("X-Pixels per meter is :" + nxpm);
    int nypm =
        (((int) bi[31] & 0xff) << 24) | (((int) bi[30] & 0xff) << 16)
            | (((int) bi[29] & 0xff) << 8) | (int) bi[28] & 0xff;
    // System.out.println("Y-Pixels per meter is :" + nypm);
    int nclrused =
        (((int) bi[35] & 0xff) << 24) | (((int) bi[34] & 0xff) << 16)
            | (((int) bi[33] & 0xff) << 8) | (int) bi[32] & 0xff;
    // System.out.println("Colors used are :" + nclrused);
    int nclrimp =
        (((int) bi[39] & 0xff) << 24) | (((int) bi[38] & 0xff) << 16)
            | (((int) bi[37] & 0xff) << 8) | (int) bi[36] & 0xff;
    // System.out.println("Colors important are :" + nclrimp);

    Image image = new Image(nwidth, nheight);

    if (nbitcount == 24) {
      // No Palatte data for 24-bit format but scan lines are
      // padded out to even 4-byte boundaries.
      int npad = (nsizeimage / nheight) - nwidth * 3;
      // ndata = new int[(nheight * nwidth) + 4];
      byte brgb[] = new byte[(nwidth + npad) * 3 * nheight];
      // fs.read (brgb, 0, (nwidth + npad) * 3 * nheight);
      fs.read(brgb);
      int nindex = 0;
      int yPos = 0;
      for (int j = 0; j < nheight; j++) {
        for (int i = 0; i < nwidth; i++) {
          // ndata[nwidth * (nheight - j - 1) + i] =
          // (255 & 0xff) << 24 | (((int) brgb[nindex + 2] & 0xff) << 16)
          // | (((int) brgb[nindex + 1] & 0xff) << 8) | (int) brgb[nindex] &
          // 0xff;
          int ta =
              ((3 * ((int) (brgb[nindex + 2]) & 0xff) + 6 * ((int) brgb[nindex + 1] & 0xff) + ((int) brgb[nindex] & 0xff))) / 10;

          // ndata[nwidth * (nheight - j - 1) + i + 4] = ta;
          yPos = nheight - j - 1;
          // System.out.println("yPos=" + yPos + " nheight=" + nheight + " j=" +
          // j);
          // System.out.println("Encoded Color at (" + i + "," + yPos + ")is:" +
          // brgb + " (R,G,B)= ("
          // + ((int) (brgb[nindex + 2]) & 0xff) + "," + ((int) brgb[nindex + 1]
          // & 0xff) + ","
          // + ((int) brgb[nindex] & 0xff) + ")" + "cufoff=" + ta);
          image.setPixel(i, yPos, ((int) brgb[nindex + 2] & 0xff), ((int) brgb[nindex + 1] & 0xff),
              ((int) brgb[nindex] & 0xff));
          nindex += 3;
        }
        nindex += npad;
      }
      // image = createImage
      // ( new MemoryImageSource (nwidth, nheight,
      // ndata, 0, nwidth));

    } else if (nbitcount == 8) {
      // Have to determine the number of colors, the clrsused
      // parameter is dominant if it is greater than zero. If
      // zero, calculate colors based on bitsperpixel.
      int nNumColors = 0;
      if (nclrused > 0) {
        nNumColors = nclrused;
      } else {
        nNumColors = (1 & 0xff) << nbitcount;
      }
      System.out.println("The number of Colors is" + nNumColors);
      // Some bitmaps do not have the sizeimage field calculated
      // Ferret out these cases and fix 'em.
      if (nsizeimage == 0) {
        nsizeimage = ((((nwidth * nbitcount) + 31) & 31) >> 3);
        nsizeimage *= nheight;
        System.out.println("nsizeimage (backup) is" + nsizeimage);
      }
      // Read the palatte colors.
      int npalette[] = new int[nNumColors];
      byte bpalette[] = new byte[nNumColors * 4];
      // fs.read (bpalette, 0, nNumColors*4);
      fs.read(bpalette);
      int nindex8 = 0;
      for (int n = 0; n < nNumColors; n++) {
        npalette[n] =
            (255 & 0xff) << 24 | (((int) bpalette[nindex8 + 2] & 0xff) << 16)
                | (((int) bpalette[nindex8 + 1] & 0xff) << 8) | (int) bpalette[nindex8] & 0xff;
        // System.out.println ("Palette Color "+n
        // +" is:"+npalette[n]+" (res,R,G,B)= (" +((int)(bpalette[nindex8+3]) &
        // 0xff)+"," +((int)(bpalette[nindex8+2]) & 0xff)+","
        // +((int)bpalette[nindex8+1]&0xff)+","
        // +((int)bpalette[nindex8]&0xff)+")");

        nindex8 += 4;
      }
      // Read the image data (actually indices into the palette)
      // Scan lines are still padded out to even 4-byte
      // boundaries.
      int npad8 = (nsizeimage / nheight) - nwidth;
      // System.out.println("nPad is:" + npad8);
      // int ndata8[] = new int[nwidth * nheight];
      // ndata = new int[(nwidth * nheight) + 4];
      byte bdata[] = new byte[(nwidth + npad8) * nheight];
      // fs.read (bdata, 0, (nwidth+npad8)*nheight);
      fs.read(bdata);
      nindex8 = 0;
      for (int j8 = 0; j8 < nheight; j8++) {
        for (int i8 = 0; i8 < nwidth; i8++) {
          // ndata[nwidth * (nheight - j8 - 1) + i8 + 4] = npalette[((int)
          // bdata[nindex8] & 0xff)];
          image.setPixel(i8, j8, npalette[((int) bdata[nindex8] & 0xff)]);
          // System.out.println("Encoded Color at (" + i8 + "," + j8 + ")is: "
          // + ndata[nwidth * (nheight - j8 - 1) + i8 + 4]);
          nindex8++;
        }
        nindex8 += npad8;
      }
      // image = createImage ( new MemoryImageSource (nwidth, nheight,
      // ndata8, 0, nwidth));
    } else {
      System.out.println("Not a 24-bit or 8-bit Windows Bitmap, aborting...");
      // image = (Image)null;
    }
    fs.close();

    // ndata[0] = nheight;
    // ndata[1] = nwidth;
    // ndata[2] = nheight * nwidth;
    // ndata[3] = 2;

    return image;
    // return ndata;

  }
}