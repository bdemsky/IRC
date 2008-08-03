public class Estimator {
    flag estimate;
    flag prob;

    int stages;
    int time;
    float variance;
    float[] probtable;

    public Estimator(int stages) {
	this.stages = stages;
	this.time = 0;
	this.variance = 0;

	this.probtable = new float[31];
	int i = 0;
	this.probtable[i++] = (float)0.5000;
	this.probtable[i++] = (float)0.5398;
	this.probtable[i++] = (float)0.5793;
	this.probtable[i++] = (float)0.6179;
	this.probtable[i++] = (float)0.6554;
	this.probtable[i++] = (float)0.6915;
	this.probtable[i++] = (float)0.7257;
	this.probtable[i++] = (float)0.7580;
	this.probtable[i++] = (float)0.7881;
	this.probtable[i++] = (float)0.8159;
	this.probtable[i++] = (float)0.8413;
	this.probtable[i++] = (float)0.8643;
	this.probtable[i++] = (float)0.8849;
	this.probtable[i++] = (float)0.9032;
	this.probtable[i++] = (float)0.9192;
	this.probtable[i++] = (float)0.9332;
	this.probtable[i++] = (float)0.9452;
	this.probtable[i++] = (float)0.9554;
	this.probtable[i++] = (float)0.9641;
	this.probtable[i++] = (float)0.9713;
	this.probtable[i++] = (float)0.9772;
	this.probtable[i++] = (float)0.9821;
	this.probtable[i++] = (float)0.9861;
	this.probtable[i++] = (float)0.9893;
	this.probtable[i++] = (float)0.9918;
	this.probtable[i++] = (float)0.9938;
	this.probtable[i++] = (float)0.9953;
	this.probtable[i++] = (float)0.9965;
	this.probtable[i++] = (float)0.9974;
	this.probtable[i++] = (float)0.9981;
	this.probtable[i++] = (float)0.9987;
    }

    public boolean estimate(int time, float variance2) {
	//System.printI(0xff30);
	this.time += time;
	this.variance += variance2;
	--this.stages;
	//System.printI(0xff31);
	//System.printI(this.stages);
	//System.printI(this.time);
	//System.printI((int)this.variance);
	if(this.stages == 0) {
	    //System.printI(0xff32);
	    //System.printString("variance2: " + (int)(this.variance*100) + "(/100); ");
	    this.variance = Math.sqrtf(this.variance);
	    //System.printString("variance: " + (int)(this.variance*100) + "(/100)\n");
	    return true;
	}
	//System.printI(0xff33);
	return false;
    }

    public float getProbability(int x, int y) {
	int l = x;
	int r = y;
	if(x > y) {
	    l = y;
	    r = x;
	}

	float prob = prob(r) - prob(l);
	return prob;
    }

    private float prob(int s) {
	int tmp = (int)((s - this.time) * 10 / this.variance);
	//System.printString(tmp + "\n");
	int abs = (int)Math.abs(tmp);
	float prob = 0;
	if(abs > this.probtable.length - 1) {
	    prob = 1;
	} else {
	    prob = this.probtable[abs];
	}
	if(tmp < 0) {
	    return (float)1.0 - prob;
	} else {
	    return prob;
	}
    }

    public int getTime() {
	return this.time;
    }

    public float getVariance() {
	return this.variance;
    }

}
