package Benchmarks.oooJava.moldyn;

/** Banboo Version */

/**************************************************************************
 * * Java Grande Forum Benchmark Suite - Version 2.0 * * produced by * * Java
 * Grande Benchmarking Project * * at * * Edinburgh Parallel Computing Centre *
 * * email: epcc-javagrande@epcc.ed.ac.uk * * Original version of this code by *
 * Dieter Heermann * converted to Java by * Lorna Smith (l.smith@epcc.ed.ac.uk)
 * * (see copyright notice below) * * This version copyright (c) The University
 * of Edinburgh, 2001. * All rights reserved. * *
 **************************************************************************/

public class MD {
  public int mm;
  public int group;
  public int mdsize;
  public int move;
  public int movemx;

  public float side;
  float hsq, hsq2, den, h, vaver, vaverh;
  float tref, tscale;
  int irep;
  int istop;
  int iprint;

  public Particle[] one;
  public float[][] sh_force;
  // public float[] epot;
  // public float[] vir;
  // public float[] ek;
  public float epot;
  public float vir;
  public float ek;
  float ekin;

  public int counter;

  public void MD(int d, int g) {
    this.mm = d;
    this.group = g;
    this.mdsize = this.mm * this.mm * this.mm * 4;
    this.one = new Particle[this.mdsize];
    this.sh_force = new float[3][this.mdsize];
    this.move = 0;
    this.movemx = 2;
    this.den = (float) 0.83134;
    this.h = (float) 0.064;
    this.side = Math.powf((float) (this.mdsize / this.den), (float) 0.3333333);
    this.hsq = this.h * this.h;
    this.hsq2 = this.hsq * (float) 0.5;
    this.vaver = (float) 1.13 * Math.sqrtf((float) this.tref / (float) 24.0);
    this.vaverh = this.vaver * this.h;
    this.irep = 10;
    this.istop = 19;
    // this.epot = (float)0.0;
    // this.vir = (float)0.0;
    this.tref = (float) 0.722;
    this.tscale = (float) 16.0 / ((float) 1.0 * (float) this.mdsize - (float) 1.0);
    this.iprint = 10;
    this.counter = 0;

    this.epot = (float) 0.0;// new float[this.group + 1];
    this.vir = (float) 0.0;// new float[this.group + 1];
    this.ek = (float) 0.0;// new float[this.group + 1];
  }

  public void init() {
    /*
     * for(int i = 0; i < this.group + 1; i++) { this.epot[i] = (float)0.0;
     * this.vir[i] = (float)0.0; }
     */
    this.epot = (float) 0.0;
    this.vir = (float) 0.0;
    for (int j = 0; j < 3; j++) {
      for (int i = 0; i < this.mdsize; i++) {
        this.sh_force[j][i] = (float) 0.0;
      }
    }
    this.counter = 0;
  }

  public void initialise() {

    /* Particle Generation */
    float xvelocity, yvelocity, zvelocity;
    int ijk, lg, i, j, k;
    float a = this.side / this.mm;
    xvelocity = (float) 0.0;
    yvelocity = (float) 0.0;
    zvelocity = (float) 0.0;
    // System.printString("here 1\n");
    // System.printI(0xa0);
    ijk = 0;
    for (lg = 0; lg <= 1; lg++) {
      for (i = 0; i < mm; i++) {
        for (j = 0; j < mm; j++) {
          for (k = 0; k < mm; k++) {
            one[ijk] =
                new Particle((float) (i * a + lg * a * (float) 0.5), (float) (j * a + lg * a
                    * (float) 0.5), (k * a), xvelocity, yvelocity, zvelocity, this);
            ijk = ijk + 1;
          }
        }
      }
    }
    for (lg = 1; lg <= 2; lg++) {
      for (i = 0; i < mm; i++) {
        for (j = 0; j < mm; j++) {
          for (k = 0; k < mm; k++) {
            one[ijk] =
                new Particle((float) (i * a + (2 - lg) * a * (float) 0.5),
                    (float) (j * a + (lg - 1) * a * (float) 0.5),
                    (float) (k * a + a * (float) 0.5), xvelocity, yvelocity, zvelocity, this);
            ijk = ijk + 1;
          }
        }
      }
    }
    // System.printString("here 2\n");
    // System.printI(0xa1);
    /* Initialise velocities */
    int iseed;
    float v1, v2, r;
    iseed = 0;
    v1 = (float) 0.0;
    v2 = (float) 0.0;

    MyRandom random = new MyRandom(iseed, v1, v2);
    // System.printString("here 3\n");
    // System.printI(0xa2);
    for (i = 0; i < this.mdsize; i += 2) {
      r = random.seed();
      one[i].xvelocity = r * random.v1;
      one[i + 1].xvelocity = r * random.v2;
    }
    // System.printString("here 4\n");
    // System.printI(0xa3);
    for (i = 0; i < this.mdsize; i += 2) {
      r = random.seed();
      one[i].yvelocity = r * random.v1;
      one[i + 1].yvelocity = r * random.v2;
    }
    // System.printString("here 5\n");
    // System.printI(0xa4);
    for (i = 0; i < this.mdsize; i += 2) {
      r = random.seed();
      one[i].zvelocity = r * random.v1;
      one[i + 1].zvelocity = r * random.v2;
    }

    /*
     * for(i = 0; i < this.mdsize; i++) { System.printString("xvel: " +
     * (int)(one[i].xvelocity*100000) + "; yvel: " +
     * (int)(one[i].yvelocity*100000) + "; zvel: " +
     * (int)(one[i].zvelocity*100000) + "\n"); }
     */

    // System.printString("here 6\n");
    // System.printI(0xa5);
    /* velocity scaling */
    float sp, ts, sc;
    ekin = (float) 0.0;
    sp = (float) 0.0;
    // System.printString("here 7\n");
    // System.printI(0xa6);
    for (i = 0; i < this.mdsize; i++) {
      sp = sp + one[i].xvelocity;
    }
    sp = sp / this.mdsize;
    // System.printString("here 8\n");
    // System.printI(0xa7);
    for (i = 0; i < this.mdsize; i++) {
      one[i].xvelocity = one[i].xvelocity - sp;
      ekin = ekin + one[i].xvelocity * one[i].xvelocity;
    }
    // System.printString("here 9\n");
    // System.printI(0xa8);
    sp = (float) 0.0;
    for (i = 0; i < this.mdsize; i++) {
      sp = sp + one[i].yvelocity;
    }
    sp = sp / this.mdsize;
    // System.printString("here 10\n");
    // System.printI(0xa9);
    for (i = 0; i < this.mdsize; i++) {
      one[i].yvelocity = one[i].yvelocity - sp;
      ekin = ekin + one[i].yvelocity * one[i].yvelocity;
    }
    // System.printString("here 11\n");
    // System.printI(0xa10);
    sp = (float) 0.0;
    for (i = 0; i < this.mdsize; i++) {
      sp = sp + one[i].zvelocity;
    }
    sp = sp / this.mdsize;
    // System.printString("here 12\n");
    // System.printI(0xa11);
    for (i = 0; i < this.mdsize; i++) {
      one[i].zvelocity = one[i].zvelocity - sp;
      ekin = ekin + one[i].zvelocity * one[i].zvelocity;
    }
    // System.printString("here 13\n");
    // System.printI(0xa12);
    ts = this.tscale * ekin;
    sc = h * Math.sqrtf(this.tref / ts);

    for (i = 0; i < this.mdsize; i++) {
      one[i].xvelocity = one[i].xvelocity * sc;
      one[i].yvelocity = one[i].yvelocity * sc;
      one[i].zvelocity = one[i].zvelocity * sc;
    }
    // System.printString("here 14\n");
    // System.printI(0xa13);
  }

  public void domove() {
    for (int i = 0; i < this.mdsize; i++) {
      one[i].domove(this.side, i);
    }

    /*
     * for(int j=0;j<3;j++) { for (int i=0;i<mdsize;i++) { sh_force[j][i] =
     * (float)0.0; } }
     */

    this.move++;
  }

  public boolean finish() {
    if (this.move == this.movemx) {
      return true;
    }
    return false;
  }

  public void update(MDRunner runner) {
    float sum, vel, velt, count, sc;
    float etot, temp, pres, rp;

    /* update force arrays */
    for (int k = 0; k < 3; k++) {
      for (int i = 0; i < this.mdsize; i++) {
        sh_force[k][i] += runner.sh_force2[k][i];
      }
    }

    // runner.init();

    // this.epot[runner.id + 1] = runner.epot;
    // this.vir[runner.id + 1] = runner.vir;
    this.epot += runner.epot;
    this.vir += runner.vir;
  }

  public void sum() {
    float sum = (float) 0.0;

    for (int j = 0; j < 3; j++) {
      for (int i = 0; i < this.mdsize; i++) {
        sh_force[j][i] = sh_force[j][i] * hsq2;
      }
    }

    sum = (float) 0.0;
    for (int i = 0; i < this.mdsize; i++) {
      sum = sum + this.one[i].mkekin(hsq2, i);
    }
    ekin = (float) (sum / hsq);
  }

  public void scale() {
    float sum, vel, velt, count, sc;
    float etot, temp, pres, rp;

    // runner.epot = this.epot[0];
    // runner.vir = this.vir[0];

    vel = (float) 0.0;
    count = (float) 0.0;

    /* average velocity */
    for (int i = 0; i < this.mdsize; i++) {
      velt = this.one[i].velavg(vaverh, h);
      if (velt > vaverh) {
        count = count + (float) 1.0;
      }
      vel = vel + velt;
    }
    vel = (float) (vel / h);

    /* temperature scale if required */
    if ((this.move < this.istop) && (((this.move + 1) % this.irep) == 0)) {
      sc = Math.sqrtf(this.tref / (this.tscale * ekin));
      for (int i = 0; i < mdsize; i++) {
        one[i].dscal(sc, 1);
      }
      ekin = (float) (this.tref / this.tscale);
    }

    /* sum to get full potential energy and virial */
    if (((this.move + 1) % this.iprint) == 0) {
      /*
       * this.ek[runner.id+1] = (float)24.0*ekin; this.epot[runner.id+1] =
       * (float)4.0*this.epot[runner.id+1]; etot = this.ek[runner.id+1] +
       * this.epot[runner.id+1]; temp = this.tscale * ekin; pres = den *
       * (float)16.0 * (ekin - this.vir[runner.id+1]) / mdsize; vel = vel /
       * this.mdsize; rp = (count / this.mdsize) * (float)100.0;
       * 
       * if(this.counter == this.group) {
       */
      this.ek = (float) 24.0 * ekin;
      // this.epot[0] = (float)4.0*this.epot[0];
      // etot = this.ek[0] + this.epot[0];
      // temp = this.tscale * ekin;
      // pres = den * (float)16.0 * (ekin - this.vir[0]) / mdsize;
      // vel = vel / this.mdsize;
      // rp = (count / this.mdsize) * (float)100.0;
      // System.printString("ek: " + (int)(this.ek*1000000) + " (" + this.move +
      // ")\n");
      // }
    }
  }

  public void validate() {
    float refval = (float) 1731.4306625334357;
    float dev = Math.abs(this.ek - refval);
    System.out.println("this.ek=" + this.ek);
    if (dev > 1.0e-10) {
      System.out.println("Validation failed\n");
      // System.printString("Kinetic Energy = " + (int)(this.ek*1000000) + ";  "
      // + (int)(refval*1000000) + "\n");
      // System.printI(0xdddf);
      // System.printI((int)(this.ek[0]*1000000));
      // System.printI((int)(refval*1000000));
      // System.printI((int)(ek*10000));
      // System.printI((int)(refval*10000));
    } else {
      System.out.println("Validation success");
    }
  }
}
