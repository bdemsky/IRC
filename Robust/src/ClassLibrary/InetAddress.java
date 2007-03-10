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
	return addresses[0];
    }
    
    public byte[] getAddress() {
	return address;
    }

    public static InetAddress[] getAllByName(String hostname) {
	InetAddress[] addresses;
	
	byte[][] iplist = InetAddress.getHostByName(hostname.getBytes());
	
	addresses = new InetAddress[iplist.length];
	
	for (int i = 0; i < iplist.length; i++) {
	    addresses[i] = new InetAddress(iplist[i], hostname);
	}
      	return addresses;
    }

    public static native byte[][] getHostByName(byte[] hostname);
}
