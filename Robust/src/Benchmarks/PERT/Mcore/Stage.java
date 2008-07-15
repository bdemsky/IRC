public class Stage {
    flag sampling;
    flag estimate;
    flag merge;

    int ID;

    int[] samplings;
    int optime;
    int nortime;
    int petime;
    int time;
    double variance2;

    public Stage(int id) {
	System.printI(0xff20);
	this.ID = id;

	this.samplings = new int[10];
	System.printI(0xff21);
	for(int i = 0; i < this.samplings.length; ++i) {
		System.printI(0xff22);
	    this.samplings[i] = 0;
	}
	System.printI(0xff23);

	this.optime = 0;
	this.nortime = 0;
	this.petime = 0;
	this.time = 0;
	this.variance2 = 0;
	System.printI(0xff24);
    }

    public void sampling() {
	System.printI(0xff00);
	Random r = new Random(ID);
	System.printI(0xff01);
	int tint = 0;
	System.printI(this.samplings.length);
	for(int i = 0; i < this.samplings.length; ++i) {
	    do {
		tint = r.nextInt()%50;
	    } while(tint <= 0);
		System.printI(0xff02);
	    this.samplings[i] = tint;
	    //System.printString(tint + "; ");
	}
	System.printI(0xff03);
    }

    public void estimate() {
	System.printI(0xff10);
	int highest = this.samplings[0];
	System.printI(0xff12);
	int lowest = this.samplings[0];
	int sum = this.samplings[0];
	System.printI(0xff13);
	System.printI(this.samplings.length);
	for(int i = 1; i < this.samplings.length; ++i) {
		System.printI(0xff14);
	    int temp = this.samplings[i];
	    if(temp > highest) {
		highest = temp;
	    } else if(temp < lowest) {
		lowest = temp;
	    }
	    sum += temp;
	}
	System.printI(0xff15);
	sum  = sum - highest - lowest;
	int ordinary = sum / (this.samplings.length - 2);
	this.optime = lowest;;
	this.petime = highest;
	this.nortime = ordinary;
	System.printI(0xff16);
	this.time = (this.optime + 4 * this.nortime + this.petime) / 6;
	System.printI(0xff17);
	this.variance2 = (double)(this.optime - this.petime) * (double)(this.optime - this.petime) / 36.0;
	System.printI(0xff18);
	//System.printString("Op time: " + this.optime + "; Nor time: " + this.nortime + "; Pe time: " + this.petime + "; variance2: " + (int)(this.variance2*100) + "(/100)\n");
    }

    public int getAntTime() {
	return this.time;
    }

    public double getAntVariance2() {
	return this.variance2;
    }

}
