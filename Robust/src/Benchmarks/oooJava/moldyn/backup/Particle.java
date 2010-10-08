public class Particle {

    public float xcoord, ycoord, zcoord;
    public float xvelocity,yvelocity,zvelocity;
    int id;
    //float [][] sh_force;
    //float [][][] sh_force2;
    MD md;

    public Particle(float xcoord, float ycoord, float zcoord, float xvelocity,
	    float yvelocity,float zvelocity,/*float [][] sh_force, 
	    float [][][] sh_force2, int id, */MD m) {

	this.xcoord = xcoord; 
	this.ycoord = ycoord; 
	this.zcoord = zcoord;
	this.xvelocity = xvelocity;
	this.yvelocity = yvelocity;
	this.zvelocity = zvelocity;
	//this.sh_force = sh_force;
	//this.sh_force2 = sh_force2;
	this.id=id;
	this.md=m;
    }

    public void domove(float side,int part_id) {
	xcoord = xcoord + xvelocity + this.md.sh_force[0][part_id];
	ycoord = ycoord + yvelocity + this.md.sh_force[1][part_id];
	zcoord = zcoord + zvelocity + this.md.sh_force[2][part_id];

	if(xcoord < 0) { xcoord = xcoord + side; } 
	if(xcoord > side) { xcoord = xcoord - side; }
	if(ycoord < 0) { ycoord = ycoord + side; }
	if(ycoord > side) { ycoord = ycoord - side; }
	if(zcoord < 0) { zcoord = zcoord + side; }
	if(zcoord > side) { zcoord = zcoord - side; }

	xvelocity = xvelocity + this.md.sh_force[0][part_id];
	yvelocity = yvelocity + this.md.sh_force[1][part_id];
	zvelocity = zvelocity + this.md.sh_force[2][part_id];
	//System.printI(0xc0);
    }

    public void force(float side, float rcoff,int mdsize,int x, MDRunner runner) {
	float sideh;
	float rcoffs;

	float fxi,fyi,fzi;
	float rd,rrd,rrd2,rrd3,rrd4,rrd6,rrd7,r148;
	float forcex,forcey,forcez;
	
	float xx, yy, zz;

	sideh = (float)0.5*side; 
	rcoffs = rcoff*rcoff;

	fxi = (float)0.0;
	fyi = (float)0.0;
	fzi = (float)0.0;
	//System.printString("here 111: " + runner.id + "\n");
	for (int i=x+1;i<mdsize;i++) {
	    xx = this.xcoord - this.md.one[i].xcoord;
	    yy = this.ycoord - this.md.one[i].ycoord;
	    zz = this.zcoord - this.md.one[i].zcoord;

	    if(xx < (-sideh)) { xx = xx + side; }
	    if(xx > (sideh))  { xx = xx - side; }
	    if(yy < (-sideh)) { yy = yy + side; }
	    if(yy > (sideh))  { yy = yy - side; }
	    if(zz < (-sideh)) { zz = zz + side; }
	    if(zz > (sideh))  { zz = zz - side; }


	    rd = xx*xx + yy*yy + zz*zz;

	    if(rd <= rcoffs) {
		rrd = (float)1.0/rd;
		rrd2 = rrd*rrd;
		rrd3 = rrd2*rrd;
		rrd4 = rrd2*rrd2;
		rrd6 = rrd2*rrd4;
		rrd7 = rrd6*rrd;
		runner.epot = runner.epot + (rrd6 - rrd3);
		r148 = rrd7 - (float)0.5*rrd4;
		runner.vir = runner.vir - rd*r148;
		forcex = xx * r148;
		fxi = fxi + forcex;

		runner.sh_force2[0][i] = runner.sh_force2[0][i] - forcex;

		forcey = yy * r148;
		fyi = fyi + forcey;

		runner.sh_force2[1][i] = runner.sh_force2[1][i] - forcey;

		forcez = zz * r148;
		fzi = fzi + forcez;

		runner.sh_force2[2][i] = runner.sh_force2[2][i] - forcez;

		//this.md.interacts[id]++;
	    }

	}
	//System.printString("here 222: " + runner.id + "\n");
	runner.sh_force2[0][x] = runner.sh_force2[0][x] + fxi;
	runner.sh_force2[1][x] = runner.sh_force2[1][x] + fyi;
	runner.sh_force2[2][x] = runner.sh_force2[2][x] + fzi;
	//System.printString("here 333: " + runner.id + "\n");
    }

    public float mkekin(float hsq2,int part_id) {
	float sumt = (float)0.0; 

	xvelocity = xvelocity + this.md.sh_force[0][part_id]; 
	yvelocity = yvelocity + this.md.sh_force[1][part_id]; 
	zvelocity = zvelocity + this.md.sh_force[2][part_id]; 

	sumt = (xvelocity*xvelocity)+(yvelocity*yvelocity)+(zvelocity*zvelocity);
	return sumt;
    }

    public float velavg(float vaverh,float h) {
	float velt;
	float sq;

	sq = Math.sqrtf(xvelocity*xvelocity + yvelocity*yvelocity + zvelocity*zvelocity);

	velt = sq;
	return velt;
    }

    public void dscal(float sc,int incx) {
	xvelocity = xvelocity * sc;
	yvelocity = yvelocity * sc;   
	zvelocity = zvelocity * sc;   
    }

}