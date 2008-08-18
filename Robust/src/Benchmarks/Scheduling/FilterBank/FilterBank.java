task t1(StartupObject s{initialstate}) {
	//System.printString("task t1\n");

	int N_sim=1200;
	int N_samp=8;
	int N_ch=16;
	int N_col=256;
	int i,j;

	float r[] = new float[N_sim];
	for (i=0;i<N_sim;i++) {
		r[i]=i+1;
	}
	
	for(j = 0; j < N_ch; j++) {
		FilterBankAtom fba = new FilterBankAtom(j, N_ch, N_col, N_sim, N_samp, r/*, H, F*/){tosamp};
	}
	FilterBank fb = new FilterBank(N_sim, N_ch){!finish};

	taskexit(s{!initialstate});
}

task t2(FilterBankAtom fba{tosamp}) {
	//System.printString("task t2\n");
	
	fba.init();
	fba.FBCore();

	taskexit(fba{!tosamp, tomerge});
}

task t3(FilterBank fb{!finish}, FilterBankAtom fba{tomerge}) {
	//System.printString("task t3\n");

	boolean finish = fb.merge(fba.vF);

	if(finish) {
		taskexit(fb{finish, print}, fba{!tomerge});
	} else {
		taskexit(fba{!tomerge});
	}
}

task t4(FilterBank fb{print}) {
	//System.printString("task t4\n");

	fb.print();

	taskexit(fb{!print});
}

public class FilterBank {
	flag finish;
	flag print;
	
	int N_sim;
	float[] y;
	int counter;

	public FilterBank(int snum, int cnum) {
		this.N_sim = snum;
		this.y = new float[N_sim];
		for(int i=0; i < N_sim; i++) {
			y[i]=0;
		}
		counter = cnum;
	}

	public boolean merge(float[] result) {
		for(int i = 0; i < this.N_sim; i++) {
			this.y[i] += result[i];
		}
		this.counter--;

		return this.counter == 0;
	}

	public void print() {
		/*for(int i = 0; i < this.N_sim; i++) {
			//System.printI((int)this.y[i]);
		}*/
	}
}

public class FilterBankAtom {

	flag tosamp;
	flag tomerge;

	int ch_index;
	int N_ch;
	int N_col;
	int N_sim;
	int N_samp;
	float[] r;
	float[] H;
	float[] F;
	float[] vH;
	float[] vDn;
	float[] vUp;
	public float[] vF;

	public FilterBankAtom(int cindex, int N_ch, int N_col, int N_sim, int N_samp, float[] r/*, float[] H, float[] F*/) {
	    this.ch_index = cindex;
	    this.N_ch = N_ch;
	    this.N_col = N_col;
	    this.N_sim = N_sim;
	    this.N_samp = N_samp;
	    this.r = r;
	    //this.H = null;
	    //this.F = null;
	    this.vH = new float[this.N_sim];
	    this.vDn = new float[(int) this.N_sim/this.N_samp];
	    this.vUp = new float[this.N_sim];
	    this.vF = new float[this.N_sim];
	    /*this.H[] = new float[N_col];
	    this.F[] = new float[N_col];
	    for(int i = 0; i < N_col; i++) {
		H[i]=i*N_col+j*N_ch+j+i+j+1;
		F[i]=i*j+j*j+j+i;
	    }*/
	}
	
	public void init() {
	    int j = this.ch_index;
	    this.H = new float[this.N_col];
	    this.F = new float[this.N_col];
	    for(int i = 0; i < this.N_col; i++) {
		this.H[i]=i*this.N_col+j*this.N_ch+j+i+j+1;
		this.F[i]=i*j+j*j+j+i;
	    }
	}

	public void FBCore() {
		int j,k;

		//convolving H
		for (j=0; j< N_sim; j++) {
			this.vH[j]=0;
			for (k=0; ((k<this.N_col) && ((j-k)>=0)); k++) {
				this.vH[j]+=this.H[k]*this.r[j-k];
			}
		}

		//Down Samplin
		for (j=0; j < this.N_sim/this.N_samp; j++) {
			this.vDn[j]=this.vH[j*this.N_samp];
		}

		//Up Sampling
		for (j=0; j < this.N_sim; j++) {
			this.vUp[j]=0;
		}
		for (j=0; j < this.N_sim/this.N_samp; j++) {
			this.vUp[j*this.N_samp]=this.vDn[j];
		}

		//convolving F
		for (j=0; j< this.N_sim; j++) {
			this.vF[j]=0;
			for (k=0; ((k<this.N_col) && ((j-k)>=0)); k++) {
				this.vF[j]+=this.F[k]*this.vUp[j-k];
			}
		}
	}
}
