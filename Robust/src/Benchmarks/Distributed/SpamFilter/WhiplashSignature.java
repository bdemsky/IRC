
/*
 Part of the Spamato project (www.spamato.net)
 Copyright (C) 2005 ETHZ, DCG
 contact by email: info@spamato.net

 This program is free software; you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation; either version 2 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program; if not, write to the Free Software
 Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA


 $Id: WhiplashSignature.java,v 1.2 2009/11/13 01:27:02 adash Exp $
 */
public class WhiplashSignature {
  char[] b64table;

  public WhiplashSignature() {
    System.out.println("Inside WhiplashSignature");
    b64table = new char[64];

    for (int i= 0; i <= 25; i++) {
      b64table[i] = (char) ((i + 65) & 0xff);
    }
    for (int i= 26; i <= 51; i++) {
      b64table[i] = (char) ((i + 71) & 0xff);
    }
    for (int i= 52; i <= 61; i++) {
      b64table[i] = (char) ((i - 4) & 0xff);
    }
    b64table[62]= '-';
    b64table[63]= '_';
  }

  public String[] computeSignature(String text) {

    System.out.println("Inside computeSignature");
    //Current: Simplify the host extraction and signature computation
    String[] sigs = whiplash(text);
    /*
      TODO: Extract canonical domain name and convert to Base64
    if(sigs != null) {
      for(int i = 0; i<sigs.length; i++) {
        sigs[i] = hexToBase64(sigs[i]);
        System.out.println("sigs[i]= " + sigs[i]);
      }
    }
    */
    return sigs;
  }

  /**
   * converts a hex-string in a base64-string exactly as it is done in razor.
   * @param hex a hex-value
   * @return a base64-equivalent of <code>hex</code>.
   */
  public String hexToBase64(String hex){
    if(hex == null)
      return null;
    int[] b64s = new int[hex.length()*2/3 + ((hex.length()*2)%3)];
    int i=0;
    int b64count = 0;

    while(i < hex.length()){
      //process 3 hex char chunks at a time
      int upperBorder = Math.imin(i+3,hex.length());
      String hex3 = hex.substring(i,upperBorder);
      i+=3;

      int bv = convertHexToRazorEncoding(hex3);
      //now the right endian encoding
      b64s[b64count++] = ((0xfc0 & bv)>>>6); //higher 6 bits
      b64s[b64count++] = (0x3f & bv) ;  //lower 6 bits

    }
    String bs = "";
    for (int j= 0; j < b64s.length; j++) {
      bs += b64table[ b64s[j] ];
    }
    return bs;
  }

  /**
   * razor does some special conversion using perl's <code>pack()</code> which
   * we must do manually in java.
   */
  private int convertHexToRazorEncoding(String hex3) {
    if((hex3 == null))
      return 0; //error
    int res = 0;
    int cur = Integer.parseInt(hex3.substring(0,1),16);
    cur = mirror4LSBits(cur);
    res |= ( (cur&0xf) << 8);
    if(hex3.length() >=2) {
      cur = Integer.parseInt(hex3.substring(1,2),16);
    } else {
      cur = 0;
    }
    //cur = ( hex3.length() >=2 ? Integer.parseInt(hex3.substring(1,2),16) : 0);
    cur = mirror4LSBits(cur);
    res |= ((cur & 0xf) << 4);
    if(hex3.length() >= 3) {
      cur = Integer.parseInt(hex3.substring(2,3),16);
    } else {
      cur = 0;
    }
    //cur = ( hex3.length() >= 3 ? Integer.parseInt(hex3.substring(2,3),16): 0);
    cur = mirror4LSBits(cur);
    res |= (cur & 0xf);

    return res;
  }

  /**
   * mirrors the 4 least significant bytes of an integer
   * @param cur an int containing 4 Least Singificant bytes like <code>00000...00abcd</code>
   * @return the mirrored 4 least significant bytes <code>00000...00dcba</code>. all bits except <code>a-b</code> are lost.
   */
  public int mirror4LSBits(int cur) {
    int res = 0;
    res |= (cur & 0x8)>>>3;
    res |= (cur & 0x4)>>>1;
    res |= (cur & 0x2)<<1;
    res |= (cur & 0x1)<<3;
    return res;
  }

  public String[] whiplash(String text) {
    
    //System.out.println("Inside whiplash");
    if (text == null) {
      return null;
    }
    String[] hosts = extractHosts(text);
    if (hosts == null || hosts.length < 1) {
      return null;
    }
    String[] sigs = new String[hosts.length];

    for (int i = 0; i < hosts.length; i++) {
      MD5 md = new MD5();
      String host = hosts[i];
      int len = host.length();
      byte buf[] = host.getBytes();
      byte sig[] = new byte[16];
      md.update(buf, len);
      md.md5final(sig);
      String signature = new String(sig);
      sigs[i] = signature;
    }
    return sigs;
  }

  public String[] extractHosts(String text) {
    //System.out.println("Inside extractHosts");
    Vector hosts = new Vector();
    String buf = new String(text);

    System.out.println("buf= " + buf);

    String strwww = new String("www.");
    int idx;
    while ((idx = buf.indexOf(strwww)) != -1) {
      int startidx = idx + strwww.length();
      //System.out.println("idx= " + idx + " startidx= " + startidx);
      String strcom = new String(".");
      buf = buf.substring(startidx);
      int endidx = buf.indexOf(strcom);
      String host = buf.substring(0, endidx);
      System.out.println(host);
      hosts.addElement(host);
      buf = buf.substring(endidx+strcom.length());
    }

    if (hosts.size() == 0) {
      return null;
    }

    String[] retbuf = new String[hosts.size()];
    for (int i = 0; i < hosts.size(); i++) {
      retbuf[i] = (String) (hosts.elementAt(i));
    }

    return retbuf;
  }

// Testing the signature computation
//  public static void main(String[] args) {
//    /*		String testVector = " Test Vectors: \n"+
//            "\n" +
//            "1. http:www.nodg.com@www.geocities.com/nxcisdsfdfdsy/off\n"+
//            "2. http:www.ksleybiuh.com@213.171.60.74/getoff/\n"+
//            "3. <http:links.verotel.com/cgi-bin/showsite.verotel?vercode=12372:9804000000374206>\n"+ 
//            "4. http:217.12.4.7/rmi/http:definethis.net/526/index.html\n"+
//            "5. http:magalygr8sex.free-host.com/h.html\n"+
//            "6. http:%3CVenkatrs%3E@218.80.74.102/thecard/4index.htm\n"+
//            "7. http:EBCDVKIGURGGCEOKXHINOCANVQOIDOXJWTWGPC@218.80.74.102/thecard/5in\n"+
//            "8. http:g.india2.bag.gs/remove_page.htm\n"+
//            "9. https:220.97.40.149\n"+
//            "10. http:&#109;j&#97;k&#101;d.b&#105;z/u&#110;&#115;&#117;bscr&#105;&#98;e&#46;d&#100;d?leaving\n"+
//            "11. http:g5j99m8@it.rd.yahoo.com/bassi/*http:www.lekobas.com/c/index.php\n"+
//            "12. <a href=\"http:Chettxuydyhv   vwyyrcmgbxzj  n as ecq kkurxtrvaug nfsygjjjwhfkpaklh t a qsc  exinscfjtxr\n"+
//            "    jobg @www.mmv9.org?affil=19\">look great / feel great</a>\n"+ 
//            "13. <A HREF=\"http:href=www.churchwomen.comhref=www.cairn.nethref=www.teeter.orghref=www.lefty.bizhref=wwwbehold.pitfall@www.mmstong5f.com/host/index.asp?ID=01910?href=www.corrode.comhref=www.ode.nethref=www.clergy.orghref=www.aberrate.biz\" >\n"+
//            "14.  www.pillzthatwork.com  # anything that starts with www. \n";
//     */
//    String testVector = "<html>\n"+
//      "<body>\n"+
//      "<p>Our first autolink: www.autolink1.com or another link like www.autolink2.co.uk or how about https:plaintextlink1.co.uk or http:plaintextlink2.com</p>\n"+
//      "<p>now a masked link <a    href=\"http://www.hiddenlink1.com\">http://www.coveringlink1.com</a> and another link http:plaintextlink3.net and how about https:plaintextlink4.to</p>\n"+
//      "<p>another masked link <A Href=\"http://www.hiddenlink2.com\">https:coveringlink2.com</A> and another link https:plaintextlink5.com</p>\n"+
//      "</body>\n"+
//      "</html>\n";
//    String test1 = "Our first autolink: www.autolink1.com or another link like www.autolink2.co.uk or how about https:plaintextlink1.co.uk or http:plaintextlink2.com</p>\n";
//    WhiplashSignature whiplash = new WhiplashSignature();
//    String[] hosts = whiplash.computeSignature(testVector);
//    //String[] hosts = whiplash.computeSignature(test1);
//    for (int i = 0; i < hosts.length; i++) {
//      String string = hosts[i];
//      System.out.println("host " + i + ":\t" + string);
//    }
//  }

}
