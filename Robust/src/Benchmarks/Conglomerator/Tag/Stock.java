public class Stock extends Lookup {
	public Stock() {
		url="q?s=%5EDJI";
		//url="";
		hostname="finance.yahoo.com";
		start="</title>";
		end="</html>";
		exclusive=true;
	}

}
