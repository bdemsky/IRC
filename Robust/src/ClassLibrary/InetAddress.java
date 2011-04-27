public class InetAddress {
  String hostname;
  byte[] address;

  public InetAddress(byte[] addr, String hostname) {
    this.hostname=hostname;
    this.address=addr;
  }

  public static InetAddress getByAddress(String host, byte[] addr) {
    return new InetAddress(addr, host);
  }

  public static InetAddress getByName(String hostname) {
    InetAddress[] addresses=getAllByName(hostname);
    if (addresses != null)
      return addresses[0];
    else
      return null;
  }

  public byte[] getAddress() {
    return address;
  }

  public static InetAddress getLocalHost() {
    return getByName("localhost");
  }

  public boolean equals(InetAddress ia) {
    if (ia==null)
      return false;
    if (ia.address.length!=address.length)
      return false;
    for(int i=0; i<address.length; i++)
      if (ia.address[i]!=address[i])
	return false;
    return true;
  }

  public static InetAddress[] getAllByName(String hostname) {
    InetAddress[] addresses;

    byte[][] iplist = InetAddress.getHostByName(hostname.getBytes());

    if (iplist != null) {
      addresses = new InetAddress[iplist.length];

      for (int i = 0; i < iplist.length; i++) {
	addresses[i] = new InetAddress(iplist[i], hostname);
      }
      return addresses;
    } else
      return null;
  }

  public static native byte[][] getHostByName(byte[] hostname);

  public String toString() {
    String h=hostname+" ";
    for (int i=0; i<address.length; i++) {
      if (i>0)
	h+=".";
      h+=(int)address[i];
    }
    return h;
  }
}
