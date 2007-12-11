public class Math {
	//public static final double PI=3.14159265358979323846;

	public double fabs(double x) {
		if (x < 0) {
			return -x;
		} else {
			return x;
		}
	}

	public double sin(double rad) {
		double app;
		double diff;
		int inc = 1;
		double PI=3.14159265358979323846;

		while( rad > 2*PI ) rad -= 2*PI;
		while( rad < -2*PI ) rad += 2*PI;
		app = diff = rad;
		diff = (diff * (-(rad*rad))) / ((2.0 * inc) * (2.0 * inc + 1.0));
		app = app + diff;
		inc++;
		while( fabs(diff) >= 0.00001 ) {
			diff = (diff * (-(rad*rad))) / ((2.0 * inc) * (2.0 * inc + 1.0));
			app = app + diff;
			inc++;
		}
		return app;
	}

	public double cos(double rad) {
		double app;
		double diff;
		int inc = 1;
		double PI=3.14159265358979323846;

		rad += PI/2.0;
		while( rad > 2*PI ) rad -= 2*PI;
		while( rad < -2*PI ) rad += 2*PI;
		app = diff = rad;
		diff = (diff * (-(rad*rad))) / ((2.0 * inc) * (2.0 * inc + 1.0));
		app = app + diff;
		inc++;
		while( fabs(diff) >= 0.00001 ) {
			diff = (diff * (-(rad*rad))) / ((2.0 * inc) * (2.0 * inc + 1.0));
			app = app + diff;
			inc++;
		}
		return app;
	}
}
