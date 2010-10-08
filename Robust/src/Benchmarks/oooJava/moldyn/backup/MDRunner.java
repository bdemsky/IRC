public class MDRunner {
    
    public int id;  
    int mm;
    int mdsize;
    public int group;
    public int ilow, iupper;
    //float l;
    float rcoff;
    //rcoffs,
    float side/*sideh,*/;
    //float rand;
    public float [][] sh_force2;
    public float epot;
    public float vir;
    public float ek;
    //int npartm,tint;
    //int iprint = 10;
    MD md;
    Particle[] one;

    public MDRunner(int id, MD m) {
	this.id=id;
	this.md = m;
	this.mm=this.md.mm;
	//this.l = 50e-10;
	this.mdsize = this.md.mdsize;
	this.group = this.md.group;
	this.side = this.md.side;
	this.rcoff = this.mm/(float)4.0;
//	this.rcoffs = this.rcoff * this.rcoff;
	//this.sideh = this.side * 0.5;
	//this.npartm = this.mdsize - 1;
	//this.iprint = 10;
	int slice = (this.mdsize - 1) / this.group + 1; 
	this.ilow = this.id * slice; 
	this.iupper = (this.id+1) * slice; 
	if (this.iupper > this.mdsize )  {
	    iupper = this.mdsize; 
	}
	sh_force2 = new float[3][this.mdsize];
	
	this.one = this.md.one;
	this.epot = (float)0.0;//this.md.epot[id+1];
	this.vir = (float)0.0;//this.md.vir[id+1];
	this.ek = (float)0.0;//this.md.ek[id+1];
    }
    
    public void init() {
	this.epot = (float)0.0;
	this.vir = (float)0.0;
	for(int i = 0; i < this.mdsize; i++) {
	    for(int j = 0; j < 3; j++) {
		this.sh_force2[j][i] = (float)0.0;
	    }
	}
    }

    public void run() {
	/* compute forces */
	//System.printString("here 1: " + this.id + "\n");
	for (int i = this.ilow; i < this.iupper; i++) {
	    one[i].force(side,rcoff,mdsize,i,this); 
	}
	//System.printString("here 2: " + this.id + "\n");
    }

}