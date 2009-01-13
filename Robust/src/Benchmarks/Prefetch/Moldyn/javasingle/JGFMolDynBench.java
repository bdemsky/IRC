/**************************************************************************
 *                                                                         *
 *         Java Grande Forum Benchmark Suite - Thread Version 1.0          *
 *                                                                         *
 *                            produced by                                  *
 *                                                                         *
 *                  Java Grande Benchmarking Project                       *
 *                                                                         *
 *                                at                                       *
 *                                                                         *
 *                Edinburgh Parallel Computing Centre                      *
 *                                                                         * 
 *                email: epcc-javagrande@epcc.ed.ac.uk                     *
 *                                                                         *
 *                                                                         *
 *      This version copyright (c) The University of Edinburgh, 2001.      *
 *                         All rights reserved.                            *
 *                                                                         *
 **************************************************************************/
public class JGFMolDynBench {
  public int ITERS;
  public double LENGTH;
  public double m;
  public double mu;
  public double kb;
  public double TSIM;
  public double deltat;

  public int PARTSIZE;

  public DoubleWrapper[] epot;
  public DoubleWrapper[] vir;
  public DoubleWrapper[] ek;

  int size,mm;
  int[] datasizes;

  public int interactions;
  public IntWrapper[] interacts;

  public int nthreads;
  public JGFInstrumentor instr;

  public JGFMolDynBench(int nthreads) {
    this.nthreads=nthreads;
  }

  public void JGFsetsize(int size){
    this.size = size;
  }

  public void JGFinitialise(){
    interactions = 0;
    datasizes = new int[2];
    datasizes[0] = 8;
    datasizes[1] = 13;

    mm = datasizes[size];
    PARTSIZE = mm*mm*mm*4;
    ITERS = 100;
    LENGTH = 50e-10;
    m = 4.0026;
    mu = 1.66056e-27;
    kb = 1.38066e-23;
    TSIM = 50;
    deltat = 5e-16;
  }

  public static void JGFapplication(JGFMolDynBench mold) { 
    // Create new arrays 
    //BarrierServer mybarr;
    /*
       int[] mid = new int[8];
       mid[0] = (128<<24)|(195<<16)|(175<<8)|84;//dw-10
       mid[1] = (128<<24)|(195<<16)|(175<<8)|85;//dw-11
       mid[2] = (128<<24)|(195<<16)|(175<<8)|86;//dw-12
       mid[3] = (128<<24)|(195<<16)|(175<<8)|87;//dw-13
       mid[4] = (128<<24)|(195<<16)|(175<<8)|88;//dw-14
       mid[5] = (128<<24)|(195<<16)|(175<<8)|89;//dw-15
       mid[6] = (128<<24)|(195<<16)|(175<<8)|90;//dw-16
       mid[7] = (128<<24)|(195<<16)|(175<<8)|91;//dw-17
       */

    double sh_force [][];
    double sh_force2 [][][];
    int partsize, numthreads;
    partsize = mold.PARTSIZE;
    numthreads = mold.nthreads;
    //mybarr = new BarrierServer(numthreads);
    //mybarr.start(mid[0]);

    sh_force = new double[3][partsize];
    sh_force2 = new double[3][numthreads][partsize];
    mold.epot = new DoubleWrapper[numthreads];
    mold.vir  = new DoubleWrapper[numthreads];
    mold.ek   = new DoubleWrapper[numthreads];
    mold.interacts = new IntWrapper[numthreads];
    for(int i=0;i<numthreads;i++) {
      mold.epot[i]=new DoubleWrapper();
      mold.vir[i]=new DoubleWrapper();
      mold.ek[i]=new DoubleWrapper();
      mold.interacts[i]=new IntWrapper();
    }

    // spawn threads 
    MDWrap[] thobjects = new MDWrap[numthreads];

    for(int i=0;i<numthreads;i++) {
      thobjects[i] = new MDWrap(new mdRunner(i,mold.mm,sh_force,sh_force2,mold.nthreads,mold));
    }

    /*
       boolean waitfordone=true;
       while(waitfordone) {
       if (mybarr.done)
       waitfordone=false;
       }
       */

    for(int i=0;i<numthreads;i++) {
      //thobjects[i].md.start(mid[i]);
      thobjects[i].md.run();
    }
  }

  public void JGFvalidate(){
    double[] refval = new double[2];
    refval[0] = 1731.4306625334357;
    refval[1] = 7397.392307839352;
    double dev = Math.fabs(ek[0].d - refval[size]);
    if (dev > 1.0e-10 ){
      //System.printString("Validation failed\n");
      //System.printString("Kinetic Energy = " + (long)ek[0] + "  " + (long)dev + "  " + size + "\n");
    }
  }
}

class mdRunner {

  double count;
  int id,i,j,k,lg,mm;
  double l,rcoff,rcoffs,side,sideh,hsq,hsq2,vel,velt;
  double a,r,sum,tscale,sc,ekin,ts,sp;
  double den;
  double tref;
  double h;
  double vaver,vaverh,rand;
  double etot,temp,pres,rp;
  double u1, u2, s, xx, yy, zz;
  double xvelocity, yvelocity, zvelocity;

  double [][] sh_force;
  double [][][] sh_force2;

  int ijk,npartm,iseed,tint;
  int irep;
  int istop;
  int iprint;

  JGFMolDynBench mymd;
  int nthreads;

  public mdRunner(int id, int mm, double [][] sh_force, double [][][] sh_force2, 
      int nthreads, JGFMolDynBench mymd) {
    this.id=id;
    this.mm=mm;
    this.sh_force=sh_force;
    this.sh_force2=sh_force2;
    this.nthreads = nthreads;
    this.mymd = mymd;
    count = 0.0;
    den = 0.83134;
    tref = 0.722;
    h = 0.064;
    irep = 10;
    istop = 19;
    iprint = 10;
  } 

  public void init(particle[] one, int mdsize) {
    int id=this.id;
    for (int lg=0; lg<=1; lg++) {
      for (int i=0; i<mm; i++) {
        for (int j=0; j<mm; j++) {
          for (int k=0; k<mm; k++) {
            one[ijk] = new particle((i*a+lg*a*0.5),(j*a+lg*a*0.5),(k*a),
                xvelocity,yvelocity,zvelocity,sh_force,sh_force2,id,one);
            ijk = ijk + 1;
          }
        }
      }
    }

    for (int lg=1; lg<=2; lg++) {
      for (int i=0; i<mm; i++) {
        for (int j=0; j<mm; j++) {
          for (int k=0; k<mm; k++) {
            one[ijk] = new particle((i*a+(2-lg)*a*0.5),(j*a+(lg-1)*a*0.5),
                (k*a+a*0.5),xvelocity,yvelocity,zvelocity,sh_force,sh_force2,id,one);
            ijk = ijk + 1;
          }
        }
      }
    }

    /* Initialise velocities */

    iseed = 0;
    double v1 = 0.0;
    double v2 = 0.0;
    random randnum = new random(iseed,v1,v2);


    for (int i=0; i<mdsize; i+=2) {
      r  = randnum.seed();
      one[i].xvelocity = r*randnum.v1;
      one[i+1].xvelocity  = r*randnum.v2;
    }

    for (int i=0; i<mdsize; i+=2) {
      r  = randnum.seed();
      one[i].yvelocity = r*randnum.v1;
      one[i+1].yvelocity  = r*randnum.v2;
    }

    for (int i=0; i<mdsize; i+=2) {
      r  = randnum.seed();
      one[i].zvelocity = r*randnum.v1;
      one[i+1].zvelocity  = r*randnum.v2;
    }


    /* velocity scaling */

    ekin = 0.0;
    sp = 0.0;

    for(int i=0;i<mdsize;i++) {
      sp = sp + one[i].xvelocity;
    }
    sp = sp / mdsize;

    for(int i=0;i<mdsize;i++) {
      one[i].xvelocity = one[i].xvelocity - sp;
      ekin = ekin + one[i].xvelocity*one[i].xvelocity;
    }

    sp = 0.0;
    for(int i=0;i<mdsize;i++) {
      sp = sp + one[i].yvelocity;
    }
    sp = sp / mdsize;

    for(int i=0;i<mdsize;i++) {
      one[i].yvelocity = one[i].yvelocity - sp;
      ekin = ekin + one[i].yvelocity*one[i].yvelocity;
    }


    sp = 0.0;
    for(int i=0;i<mdsize;i++) {
      sp = sp + one[i].zvelocity;
    }
    sp = sp / mdsize;

    for(int i=0;i<mdsize;i++) {
      one[i].zvelocity = one[i].zvelocity - sp;
      ekin = ekin + one[i].zvelocity*one[i].zvelocity;
    }

    ts = tscale * ekin;
    sc = h * Math.sqrt(tref/ts);


    for(int i=0;i<mdsize;i++) {

      one[i].xvelocity = one[i].xvelocity * sc;     
      one[i].yvelocity = one[i].yvelocity * sc;     
      one[i].zvelocity = one[i].zvelocity * sc;     

    }

  }

  public void doinit(int mdsize) {
    for(int j=0;j<3;j++) {
      double[] sh=sh_force[j];
      for (int i=0;i<mdsize;i++) {
        sh[i] = 0.0;
      }
    }
  }


  public void doinit2(int mdsize) {
    for(int k=0;k<3;k++) {
      double[] sh=sh_force[k];
      double [][] sha=sh_force2[k];
      for(int j=0;j<nthreads;j++) {
        double[] sha2=sha[j];
        for(int i=0;i<mdsize;i++) {
          sh[i] += sha2[i];
        }
      }
    }

    for(int k=0;k<3;k++) {
      double [][] sh1=sh_force2[k];
      for(int j=0;j<nthreads;j++) {
        double[] sh2=sh1[j];
        for(int i=0;i<mdsize;i++) {


          sh2[i] = 0.0;
        }
      }
    }

    for(int j=1;j<nthreads;j++) {
      mymd.epot[0].d += mymd.epot[j].d;
      mymd.vir[0].d += mymd.vir[j].d;
    }
    for(int j=1;j<nthreads;j++) {       
      mymd.epot[j].d = mymd.epot[0].d;
      mymd.vir[j].d = mymd.vir[0].d;
    }
    for(int j=0;j<nthreads;j++) {
      mymd.interactions += mymd.interacts[j].i; 
    }

    for (int j=0;j<3;j++) {
      double sh[]=sh_force[j];
      for (int i=0;i<mdsize;i++) {
        sh[i] = sh[i] * hsq2;
      }
    }
  }

  public void run() {
    /* Parameter determination */

    int mdsize;
    double tmpden;
    int movemx=50;
    //Barrier barr=new Barrier("128.195.175.84");
    particle[] one;
    int id;
    id=this.id;
    mdsize = mymd.PARTSIZE;
    one=new particle[mdsize];
    l = mymd.LENGTH;
    tmpden = den;
    side = Math.pow((mdsize/tmpden),0.3333333);
    rcoff = mm/4.0;

    a = side/mm;
    sideh = side*0.5;
    hsq = h*h;
    hsq2 = hsq*0.5;
    npartm = mdsize - 1;
    rcoffs = rcoff * rcoff;
    tscale = 16.0 / (1.0 * mdsize - 1.0);
    vaver = 1.13 * Math.sqrt(tref / 24.0);
    vaverh = vaver * h;

    /* Particle Generation */

    xvelocity = 0.0;
    yvelocity = 0.0;
    zvelocity = 0.0;
    ijk = 0;
    init(one, mdsize);


    /* Synchronise threads and start timer before MD simulation */

    //Barrier.enterBarrier(barr);

    /* MD simulation */

    for (int move=0;move<movemx;move++) {
      /* move the particles and update velocities */
      for (int i=0;i<mdsize;i++) {
        one[i].domove(side,i);       
      }

      /* Barrier */
      //Barrier.enterBarrier(barr);

      if(id==0) {
        doinit(mdsize);
      }

      mymd.epot[id].d = 0.0;
      mymd.vir[id].d = 0.0;
      mymd.interacts[id].i = 0;


      /* Barrier */
      //Barrier.enterBarrier(barr);

      /* compute forces */

      for (int i=0+id;i<mdsize;i+=nthreads) {
        one[i].force(side,rcoff,mdsize,i,xx,yy,zz,mymd); 
      }

      /* Barrier */
      //Barrier.enterBarrier(barr);

      /* update force arrays */
      if(id == 0) {
        doinit2(mdsize);
      }

      /* Barrier */
      //Barrier.enterBarrier(barr);

      /*scale forces, update velocities */
      sum = 0.0;
      for (int i=0;i<mdsize;i++) {
        sum = sum + one[i].mkekin(hsq2,i);  
      }

      ekin = sum/hsq;

      vel = 0.0;
      count = 0.0;

      /* average velocity */

      for (int i=0;i<mdsize;i++) {
        velt = one[i].velavg(vaverh,h);
        if(velt > vaverh) { count = count + 1.0; }
        vel = vel + velt;                    
      }

      vel = vel / h;

      /* temperature scale if required */

      if((move < istop) && (((move+1) % irep) == 0)) {
        sc = Math.sqrt(tref / (tscale*ekin));
        for (int i=0;i<mdsize;i++) {
          one[i].dscal(sc,1);
        }
        ekin = tref / tscale;
      }

      /* sum to get full potential energy and virial */

      if(((move+1) % iprint) == 0) {
        mymd.ek[id].d = 24.0*ekin;
        mymd.epot[id].d = 4.0*mymd.epot[id].d;
        etot = mymd.ek[id].d + mymd.epot[id].d;
        temp = tscale * ekin;
        pres = tmpden * 16.0 * (ekin - mymd.vir[id].d) / mdsize;
        vel = vel / mdsize; 
        rp = (count / mdsize) * 100.0;
      }
      //Barrier.enterBarrier(barr);

      //if (id == 0) JGFInstrumentor.stopTimer("Section3:MolDyn:Run", instr.timers);
    }
  }
}

  class particle {

    public double xcoord, ycoord, zcoord;
    public double xvelocity,yvelocity,zvelocity;
    int part_id;
    int id;
    double [][] sh_force;
    double [][][] sh_force2;
    particle[] one;

    public particle(double xcoord, double ycoord, double zcoord, double xvelocity,
        double yvelocity,double zvelocity, double [][] sh_force, 
        double [][][] sh_force2,int id, particle[] one) {

      this.xcoord = xcoord; 
      this.ycoord = ycoord; 
      this.zcoord = zcoord;
      this.xvelocity = xvelocity;
      this.yvelocity = yvelocity;
      this.zvelocity = zvelocity;
      this.sh_force = sh_force;
      this.sh_force2 = sh_force2;
      this.id=id;
      this.one=one;
    }

    public void domove(double side,int part_id) {

      xcoord = xcoord + xvelocity + sh_force[0][part_id];
      ycoord = ycoord + yvelocity + sh_force[1][part_id];
      zcoord = zcoord + zvelocity + sh_force[2][part_id];

      if(xcoord < 0) { xcoord = xcoord + side; } 
      if(xcoord > side) { xcoord = xcoord - side; }
      if(ycoord < 0) { ycoord = ycoord + side; }
      if(ycoord > side) { ycoord = ycoord - side; }
      if(zcoord < 0) { zcoord = zcoord + side; }
      if(zcoord > side) { zcoord = zcoord - side; }

      xvelocity = xvelocity + sh_force[0][part_id];
      yvelocity = yvelocity + sh_force[1][part_id];
      zvelocity = zvelocity + sh_force[2][part_id];

    }

    public void force(double side, double rcoff,int mdsize,int x, double xx, double yy, double zz, JGFMolDynBench mymd) {

      double sideh;
      double rcoffs;

      double fxi,fyi,fzi;
      double rd,rrd,rrd2,rrd3,rrd4,rrd6,rrd7,r148;
      double forcex,forcey,forcez;
      int id=this.id;
      sideh = 0.5*side; 
      rcoffs = rcoff*rcoff;

      fxi = 0.0;
      fyi = 0.0;
      fzi = 0.0;

      for (int i=x+1;i<mdsize;i++) {
        xx = this.xcoord - one[i].xcoord;
        yy = this.ycoord - one[i].ycoord;
        zz = this.zcoord - one[i].zcoord;

        if(xx < (-sideh)) { xx = xx + side; }
        if(xx > (sideh))  { xx = xx - side; }
        if(yy < (-sideh)) { yy = yy + side; }
        if(yy > (sideh))  { yy = yy - side; }
        if(zz < (-sideh)) { zz = zz + side; }
        if(zz > (sideh))  { zz = zz - side; }


        rd = xx*xx + yy*yy + zz*zz;

        if(rd <= rcoffs) {
          rrd = 1.0/rd;
          rrd2 = rrd*rrd;
          rrd3 = rrd2*rrd;
          rrd4 = rrd2*rrd2;
          rrd6 = rrd2*rrd4;
          rrd7 = rrd6*rrd;
          mymd.epot[id].d += (rrd6 - rrd3);
          r148 = rrd7 - 0.5*rrd4;
          mymd.vir[id].d += - rd*r148;
          forcex = xx * r148;
          fxi = fxi + forcex;

          sh_force2[0][id][i] = sh_force2[0][id][i] - forcex;

          forcey = yy * r148;
          fyi = fyi + forcey;

          sh_force2[1][id][i] = sh_force2[1][id][i] - forcey;

          forcez = zz * r148;
          fzi = fzi + forcez;

          sh_force2[2][id][i] = sh_force2[2][id][i] - forcez;

          mymd.interacts[id].i++;
        }

      }

      sh_force2[0][id][x] = sh_force2[0][id][x] + fxi;
      sh_force2[1][id][x] = sh_force2[1][id][x] + fyi;
      sh_force2[2][id][x] = sh_force2[2][id][x] + fzi;

    }

    public double mkekin(double hsq2,int part_id) {

      double sumt = 0.0; 

      xvelocity = xvelocity + sh_force[0][part_id]; 
      yvelocity = yvelocity + sh_force[1][part_id]; 
      zvelocity = zvelocity + sh_force[2][part_id]; 

      sumt = (xvelocity*xvelocity)+(yvelocity*yvelocity)+(zvelocity*zvelocity);
      return sumt;
    }

    public double velavg(double vaverh,double h) {

      double velt;
      double sq;

      sq = Math.sqrt(xvelocity*xvelocity + yvelocity*yvelocity +
          zvelocity*zvelocity);

      velt = sq;
      return velt;
    }

    public void dscal(double sc,int incx) {
      xvelocity = xvelocity * sc;
      yvelocity = yvelocity * sc;   
      zvelocity = zvelocity * sc;   
    }
  }

  class random {

    public int iseed;
    public double v1,v2;

    public random(int iseed,double v1,double v2) {
      this.iseed = iseed;
      this.v1 = v1;
      this.v2 = v2;
    }

    public double update() {

      double rand;
      double scale= 4.656612875e-10;

      int is1,is2,iss2;
      int imult=16807;
      int imod = 2147483647;

      if (iseed<=0) { iseed = 1; }

      is2 = iseed % 32768;
      is1 = (iseed-is2)/32768;
      iss2 = is2 * imult;
      is2 = iss2 % 32768;
      is1 = (is1*imult+(iss2-is2)/32768) % (65536);

      iseed = (is1*32768+is2) % imod;

      rand = scale * iseed;

      return rand;

    }

    public double seed() {

      double s,u1,u2,r;
      s = 1.0;
      do {
        u1 = update();
        u2 = update();

        v1 = 2.0 * u1 - 1.0;
        v2 = 2.0 * u2 - 1.0;
        s = v1*v1 + v2*v2;

      } while (s >= 1.0);

      r = Math.sqrt(-2.0*Math.log(s)/s);

      return r;

    }
  }


