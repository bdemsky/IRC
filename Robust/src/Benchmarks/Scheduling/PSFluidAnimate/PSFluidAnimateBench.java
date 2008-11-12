//Ported for Parsec 1.0

task t1(StartupObject s{initialstate}) {
    //System.printString("task t1\n");
    
    int threadnum = 16;
    int framenum = 4;
    
    int XDIVS = 4;// number of partitions in X
    int ZDIVS = 4;// number of partitions in Z
    int NUM_GRIDS = XDIVS * ZDIVS;
    
    // initialize the simulation
    float doubleRestDensity = (float)2000.0;
    float kernelRadiusMultiplier = (float)1.695;
    float stiffness = (float)1.5;
    float viscosity = (float)0.4;
    
    float restParticlesPerMeter, h, hSq;
    float densityCoeff, pressureCoeff, viscosityCoeff;
    
    int nx, ny, nz;
    Vec3 delta = new Vec3();

    restParticlesPerMeter = (float)60.0;
    int origNumParticles = 48;//0;//0;
    int numParticles = origNumParticles;
    
    h = kernelRadiusMultiplier / restParticlesPerMeter;
    hSq = h*h;
    
    float pi = (float)3.14159265358979;
    float coeff1 = (float)315.0 / ((float)64.0*pi*Math.powf(h,(float)9.0));
    float coeff2 = (float)15.0 / (pi*Math.powf(h,(float)6.0));
    float coeff3 = (float)45.0 / (pi*Math.powf(h,(float)6.0));
    float particleMass = ((float)0.5)*doubleRestDensity / (restParticlesPerMeter*restParticlesPerMeter*restParticlesPerMeter);
    densityCoeff = particleMass * coeff1;
    pressureCoeff = ((float)3)*coeff2 * ((float)0.5)*stiffness * particleMass;
    viscosityCoeff = viscosity * coeff3 * particleMass;
    
    Vec3 domainMin = new Vec3((float)-0.065, (float)-0.08, (float)-0.065);
    Vec3 domainMax = new Vec3((float)0.065, (float)0.1, (float)0.065);
    Vec3 range = domainMax.sub1(domainMin);
    nx = (int)(range.m_x / h);
    ny = (int)(range.m_y / h);
    nz = (int)(range.m_z / h);
    //System.printString("h: " + (int)(h*10000) + "\n");
    //System.printString("x: " + nx + "; y: " + ny + ", z: " + nz + "\n");
    
    int numCells = nx*ny*nz;
    delta.m_x = range.m_x / nx;
    delta.m_y = range.m_y / ny;
    delta.m_z = range.m_z / nz;
    
    PSFADemo demo = new PSFADemo(NUM_GRIDS, numCells, numParticles, nx, ny, nz, 
	    viscosityCoeff, new Vec3(delta.m_x, delta.m_y, delta.m_z)){tosum};

    float px, py, pz, hvx, hvy, hvz, vx, vy, vz;
    long seed = 12345678;
    Random r = new Random(seed);
    int maxint = (1<<31) - 1;
    float dx = (float)0.005;
    float dy = (float)0.005;
    float dz = (float)0.005;
    px = domainMin.m_x;
    py = domainMin.m_y;
    pz = domainMin.m_z;
    hvx = (float)0.2;
    hvy = (float)0.5;
    hvz = (float)0.3;
    vx = (float)0.2;
    vy = (float)0.5;
    vz = (float)0.3;
    //int p = 0;
    int xn = 0;
    int yn = 0;
    int zn = 0;
    Cell[][][] cells = new Cell[nx][ny][nz];
    int p=0;
    for(int i = 0; i < nx; i++) {
	for(int j = 0; j < ny; j++) {
	    for(int k = 0; k < nz; k++) {
		int index = (k*ny + j)*nx + i;
		cells[i][j][k] = new Cell(index);
		if (p<numParticles) {
		    Cell cell = cells[i][j][k];
		    int np = cell.m_numPars;
		    cell.m_p[np].m_x = (float)((i+0.5)*delta.m_x+px);
		    cell.m_p[np].m_y = (float)((j+0.5)*delta.m_y+py);
		    cell.m_p[np].m_z = (float)((k+0.5)*delta.m_z+pz);
		    cell.m_hv[np].m_x = hvx;
		    cell.m_hv[np].m_y = hvy;
		    cell.m_hv[np].m_z = hvz;
		    cell.m_v[np].m_x = vx;
		    cell.m_v[np].m_y = vy;
		    cell.m_v[np].m_z = vz;
		    ++cell.m_numPars;
		    p++;
		}
	    }
	}
    }

    int gi = 0;
    int sx, sz, ex, ez;
    ex = 0;
    for(int i = 0; i < XDIVS; ++i) {
	sx = ex;
	ex = (int)((float)nx/(float)XDIVS * (i+1) + (float)0.5);
	ez = 0;
	for(int j = 0; j < ZDIVS; ++j, ++gi) {
	    sz = ez;
	    ez = (int)((float)nz/(float)ZDIVS * (j+1) + (float)0.5);
	    //System.printString("(" + i + ", " + j + "); " + "sx: " + sx + "; ex: " + ex + "; sy: " + 0 + "; ey: " + ny + "; sz: " + sz + "; ez: " + ez+ "\n");
	    
	    Grid grid = new Grid(gi, sx, 0, sz, ex, ny, ez, h, hSq, 
		    new Vec3(delta.m_x, delta.m_y, delta.m_z),
		    densityCoeff, pressureCoeff, viscosityCoeff, nx, ny, nz,
		    framenum){rebuild};
	    // initialize the cells
	    grid.initCells(cells);
	    grid.initNeighCells(cells, demo);
	}
    }
    demo.resetBorderCells();
    taskexit(s{!initialstate});
}

task t2(Grid g{rebuild}) {
    //System.printString("task t2\n");
    
    g.ClearParticlesMT();
    g.RebuildGridMT();
    g.InitDensitiesAndForcesMT();
    taskexit(g{!rebuild, tosum});
}

task t3(PSFADemo demo{tosum}, Grid g{tosum}) {
    //System.printString("task t3\n");
    
    demo.sum(g);
    if(demo.isFinish()) {
	demo.resetCounter();
	taskexit(demo{!tosum, flushghost}, g{!tosum, flushghost});
    } else {
	taskexit(g{!tosum, flushghost});
    }
}

task t4(PSFADemo demo{flushghost}, Grid g{flushghost}) {
    //System.printString("task t4\n");
    
    demo.flush(g);
    if(demo.isFinish()) {
	demo.resetCounter();
	taskexit(demo{!flushghost, tosum2}, g{!flushghost, computeDens});
    } else {
	taskexit(g{!flushghost, computeDens});
    }
}

task t5(Grid g{computeDens}) {
    //System.printString("task t5\n");
    
    g.ComputeDensitiesMT();
    taskexit(g{!computeDens, tosum2});
}

task t6(PSFADemo demo{tosum2}, Grid g{tosum2}) {
    //System.printString("task t6\n");
    
    demo.sum2(g);
    if(demo.isFinish()) {
	demo.resetCounter();
	taskexit(demo{!tosum2, flushghost2}, g{!tosum2, flushghost2});
    } else {
	taskexit(g{!tosum2, flushghost2});
    }
}

task t7(PSFADemo demo{flushghost2}, Grid g{flushghost2}) {
    //System.printString("task t7\n");
    
    demo.flush2(g);
    if(demo.isFinish()) {
	demo.resetCounter();
	taskexit(demo{!flushghost2, tosum3}, g{!flushghost2, computeDens2});
    } else {
	taskexit(g{!flushghost2, computeDens2});
    }
}

task t8(Grid g{computeDens2}) {
    //System.printString("task t8\n");
    
    g.ComputeDensities2MT();
    taskexit(g{!computeDens2, tosum3});
}

task t9(PSFADemo demo{tosum3}, Grid g{tosum3}) {
    //System.printString("task t9\n");
    
    demo.sum3(g);
    if(demo.isFinish()) {
	demo.resetCounter();
	taskexit(demo{!tosum3, flushghost3}, g{!tosum3, flushghost3});
    } else {
	taskexit(g{!tosum3, flushghost3});
    }
}

task t10(PSFADemo demo{flushghost3}, Grid g{flushghost3}) {
    //System.printString("task t10\n");
    
    demo.flush3(g);
    if(demo.isFinish()) {
	demo.resetBorderCellsHV();
	demo.resetCounter();
	taskexit(demo{!flushghost3, tosum4}, g{!flushghost3, computeForces});
    } else {
	taskexit(g{!flushghost3, computeForces});
    }
}

task t11(Grid g{computeForces}) {
    //System.printString("task t11\n");
    
    g.ComputeForcesMT();
    taskexit(g{!computeForces, tosum4});
}

task t12(PSFADemo demo{tosum4}, Grid g{tosum4}) {
    //System.printString("task t12\n");
    
    demo.sum4(g);
    if(demo.isFinish()) {
	demo.resetCounter();
	taskexit(demo{!tosum4, flushghost4}, g{!tosum4, flushghost4});
    } else {
	taskexit(g{!tosum4, flushghost4});
    }
}

task t13(PSFADemo demo{flushghost4}, Grid g{flushghost4}) {
    //System.printString("task t13\n");
    
    demo.flush4(g);
    if(demo.isFinish()) {
	demo.resetCounter();
	demo.resetBorderCells();
	taskexit(demo{!flushghost4, tosum}, g{!flushghost4, procCollision});
    } else {
	taskexit(g{!flushghost4, procCollision});
    }
}

task t14(Grid g{procCollision}) {
    //System.printString("task t14\n");
    
    g.ProcessCollisionsMT();
    g.AdvanceParticlesMT();
    if(g.isFinish()) {
	taskexit(g{!procCollision, finish});
    } else {
	taskexit(g{!procCollision, rebuild});
    }
}
