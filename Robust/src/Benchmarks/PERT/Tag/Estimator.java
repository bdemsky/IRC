public class Estimator {
    flag estimate;
    flag prob;

    int stages;
    int time;
    double variance;
    double[] probtable;

    boolean partial;

    public Estimator(int stages) {
	this.stages = stages;
	this.time = 0;
	this.variance = 0;

	this.probtable = new double[31];
	int i = 0;
	this.probtable[i++] = 0.5000;
	this.probtable[i++] = 0.5398;
	this.probtable[i++] = 0.5793;
	this.probtable[i++] = 0.6179;
	this.probtable[i++] = 0.6554;
	this.probtable[i++] = 0.6915;
	this.probtable[i++] = 0.7257;
	this.probtable[i++] = 0.7580;
	this.probtable[i++] = 0.7881;
	this.probtable[i++] = 0.8159;
	this.probtable[i++] = 0.8413;
	this.probtable[i++] = 0.8643;
	this.probtable[i++] = 0.8849;
	this.probtable[i++] = 0.9032;
	this.probtable[i++] = 0.9192;
	this.probtable[i++] = 0.9332;
	this.probtable[i++] = 0.9452;
	this.probtable[i++] = 0.9554;
	this.probtable[i++] = 0.9641;
	this.probtable[i++] = 0.9713;
	this.probtable[i++] = 0.9772;
	this.probtable[i++] = 0.9821;
	this.probtable[i++] = 0.9861;
	this.probtable[i++] = 0.9893;
	this.probtable[i++] = 0.9918;
	this.probtable[i++] = 0.9938;
	this.probtable[i++] = 0.9953;
	this.probtable[i++] = 0.9965;
	this.probtable[i++] = 0.9974;
	this.probtable[i++] = 0.9981;
	this.probtable[i++] = 0.9987;

	this.partial = false;
    }

    public boolean estimate(int time, double variance2, boolean fake) {
	if(!fake) {
	    this.time += time;
	    this.variance += variance2;
	} else {
	    this.partial = true;
	}
	--this.stages;
	if(this.stages == 0) {
	    //System.printString("variance2: " + (int)(this.variance*100) + "(/100); ");
	    this.variance = Math.sqrt(this.variance);
	    //System.printString("variance: " + (int)(this.variance*100) + "(/100)\n");
	    return true;
	}
	return false;
    }

    public double getProbability(int x, int y) {
	int l = x;
	int r = y;
	if(x > y) {
	    l = y;
	    r = x;
	}

	double prob = prob(r) - prob(l);
	return prob;
    }

    private double prob(int s) {
	int tmp = (int)((s - this.time) * 10 / this.variance);
	//System.printString(tmp + "\n");
	int abs = (int)Math.abs(tmp);
	double prob = 0;
	if(abs > this.probtable.length - 1) {
	    prob = 1;
	} else {
	    prob = this.probtable[abs];
	}
	if(tmp < 0) {
	    return 1.0 - prob;
	} else {
	    return prob;
	}
    }

    public int getTime() {
	return this.time;
    }

    public double getVariance() {
	return this.variance;
    }

    public boolean isPartial() {
	return this.partial;
    }
}
