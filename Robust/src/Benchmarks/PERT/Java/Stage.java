import java.util.Random;

public class Stage {
    int ID;

    int[] samplings;
    int optime;
    int nortime;
    int petime;
    int time;
    double variance2;

    public Stage(int id) {
	this.ID = id;

	this.samplings = new int[10];
	for(int i = 0; i < this.samplings.length; ++i) {
	    this.samplings[i] = 0;
	}

	this.optime = 0;
	this.nortime = 0;
	this.petime = 0;
	this.time = 0;
	this.variance2 = 0;
    }

    public void sampling() {
	/*if(ID % 2 == 1) {
			int tmp = samplings[samplings.length];
		}*/

	/*Random r = new Random();
	int tint = 0;
	for(int i = 0; i < this.samplings.length; ++i) {
	    do {
		tint = r.nextInt()%50;
	    } while(tint <= 0);
	    this.samplings[i] = tint;
	    //System./*out.print*///printString(tint + "; ");
//	}
    int tint = ID * 3;
	for(int i = 0; i < this.samplings.length; ++i) {
		this.samplings[i] = tint + i;
		//System.printString(tint + "; ");
	}
	//System.printString("\n");//out.println();
    }

    public void estimate() {
	/*if(ID % 2 == 1) {
			int tmp = samplings[samplings.length];
		}*/

	int highest = this.samplings[0];
	int lowest = this.samplings[0];
	int sum = this.samplings[0];
	for(int i = 1; i < this.samplings.length; ++i) {
	    int temp = this.samplings[i];
	    if(temp > highest) {
		highest = temp;
	    } else if(temp < lowest) {
		lowest = temp;
	    }
	    sum += temp;
	}
	sum  = sum - highest - lowest;
	int ordinary = sum / (this.samplings.length - 2);
	this.optime = lowest;;
	this.petime = highest;
	this.nortime = ordinary;
	this.time = (this.optime + 4 * this.nortime + this.petime) / 6;
	this.variance2 = (double)(this.optime - this.petime) * (double)(this.optime - this.petime) / 36.0;
	//System.out.println("Op time: " + this.optime + "; Nor time: " + this.nortime + "; Pe time: " + this.petime + "; variance2: " + (int)(this.variance2*100) + "(/100)");
    }

    public int getAntTime() {
	return this.time;
    }

    public double getAntVariance2() {
	return this.variance2;
    }

}
