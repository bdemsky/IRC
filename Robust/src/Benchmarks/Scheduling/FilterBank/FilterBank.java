task t1(StartupObject s{initialstate}) {
	//System.printString("task t1\n");

	int N_sim=1200;
	int N_samp=8;
	int N_ch=16;
	int N_col=128;
	int i,j;

	/*float r[] = new float[N_sim];
	for (i=0;i<N_sim;i++) {
		r[i]=i+1;
	}*/
	
	for(j = 0; j < N_ch; j++) {
		FilterBankAtom fba = new FilterBankAtom(j, 
			                                N_ch, 
			                                N_col, 
			                                N_sim, 
			                                N_samp/*, 
			                                r*/){tosamp};
	}
	FilterBank fb = new FilterBank(N_sim, N_ch){!finish, !print};

	taskexit(s{!initialstate});
}

task t2(FilterBankAtom fba{tosamp}) {
	//System.printString("task t2\n");
	
	//fba.init();
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
	    float[] ty = this.y;
	    int Nsim = this.N_sim;
	    for(int i = 0; i < Nsim; i++) {
		ty[i] += result[i];
	    }
	    this.counter--;

	    return this.counter == 0;
	}

	public void print() {
		/*for(int i = 0; i < this.N_sim; i++) {
			System.printI((int)(this.y[i] * 10000));
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
	//float[] r;
	/*float[] H;
	float[] F;
	float[] vH;
	float[] vDn;
	float[] vUp;*/
	public float[] vF;

	public FilterBankAtom(int cindex, 
		              int N_ch, 
		              int N_col, 
		              int N_sim, 
		              int N_samp/*, 
		              float[] r*/) {
	    this.ch_index = cindex;
	    this.N_ch = N_ch;
	    this.N_col = N_col;
	    this.N_sim = N_sim;
	    this.N_samp = N_samp;
	    /*this.r = r;
	    //this.H = null;
	    //this.F = null;
	    this.vH = new float[this.N_sim];
	    this.vDn = new float[(int) this.N_sim/this.N_samp];
	    this.vUp = new float[this.N_sim];*/
	    this.vF = new float[this.N_sim];
	    /*this.H[] = new float[N_col];
	    this.F[] = new float[N_col];
	    for(int i = 0; i < N_col; i++) {
		H[i]=i*N_col+j*N_ch+j+i+j+1;
		F[i]=i*j+j*j+j+i;
	    }*/
	}
	
	/*public void init() {
	    int j = this.ch_index;
	    this.H = new float[this.N_col];
	    this.F = new float[this.N_col];
	    for(int i = 0; i < this.N_col; i++) {
		this.H[i]=i*this.N_col+j*this.N_ch+j+i+j+1;
		this.F[i]=i*j+j*j+j+i;
	    }
	}*/

	public void FBCore() {
		int i,j,k;
		int chindex = this.ch_index;
		int Ncol = this.N_col;
		int Nsim = this.N_sim;
		int Nch = this.N_ch;
		int Nsamp = this.N_samp;
		float[] tvF = this.vF;
		
		float r[] = new float[Nsim];
		for (i=0;i<Nsim;i++) {
			r[i]=i+1;
		}

		float[] H = new float[Ncol];
		float[] F = new float[Ncol];
		for(i = 0; i < Ncol; i++) {
		    H[i]=i*Ncol+chindex*Nch+chindex+i+chindex+1;
		    F[i]=i*chindex+chindex*chindex+chindex+i;
		}
		float[] vH = new float[Nsim];
		float[] vDn = new float[(int)(Nsim/Nsamp)];
		float[] vUp = new float[Nsim];

		//convolving H
		for (j=0; j< Nsim; j++) {
			/*for (k=0; ((k<Ncol) & ((j-k)>=0)); k++) {
				vH[j]+=H[k]*r[j-k];
			}*/
		    k = 0;
		    boolean stat = false;
		    int diff = j;
		    do{
			float tmp = H[k]*r[j-k];
			k++;
			diff--;
			stat = (k<Ncol) & (diff >= 0);
			vH[j]+=tmp;
		    }while(stat);
		}

		//Down Samplin
		for (j=0; j < Nsim/Nsamp; j++) {
			vDn[j]=vH[j*Nsamp];
		}

		//Up Sampling
		for (j=0; j < Nsim/Nsamp; j++) {
			vUp[j*Nsamp]=vDn[j];
		}

		//convolving F
		for (j=0; j< Nsim; j++) {
			/*for (k=0; ((k<Ncol) & ((j-k)>=0)); k++) {
				tvF[j]+=F[k]*vUp[j-k];
			}*/
		    k = 0;
		    boolean stat = false;
		    int diff = j;
		    do{
			float tmp = F[k]*vUp[j-k];
			k++;
			diff--;
			stat = (k<Ncol) & (diff >= 0);
			tvF[j]+=tmp;
		    }while(stat);
		}
	}
}
