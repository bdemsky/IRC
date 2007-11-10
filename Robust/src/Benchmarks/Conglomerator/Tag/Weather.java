public class Weather extends Lookup{
	public Weather() {
		url="warnings.php?wfo=sgx&zone=CAZ042&pil=XXXHWOSGX&productType=Hazardous+Weather+Outlook";
		hostname="www.wrh.noaa.gov";
		start="Hazardous Weather Outlook";
		end="$$";
	}

}
