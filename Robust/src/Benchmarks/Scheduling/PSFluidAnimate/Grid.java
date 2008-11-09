public class Grid {
    flag tosum;
    flag flushghost;
    flag tosum2;
    flag flushghost2;
    flag tosum3;
    flag flushghost3;
    flag tosum4;
    flag flushghost4;
    flag rebuild;
    flag computeDens;
    flag computeDens2;
    flag computeForces;
    flag procCollision;
    flag finish;
    
    public int m_id;
    public int m_sx;
    public int m_sy;
    public int m_sz;
    public int m_ex;
    public int m_ey;
    public int m_ez;

    public Vec3 m_delta;				// cell dimensions
    
    private Vec3 m_externalAcceleration;
    public Vec3 m_domainMin;
    public Vec3 m_domainMax;
    
    public float m_h;
    public float m_hSq;
    public float m_densityCoeff;
    public float m_pressureCoeff;
    public float m_viscosityCoeff;
    public int m_nx;
    public int m_ny;
    public int m_nz;
    
    public int m_frameNum;
    
    public Cell[][][] m_cells;
    public Cell[][][] m_cells2;
    public HashMap m_neighCells;    // neighbour cells

    public Grid() {}

    public Grid(int _id, int _sx, int _sy, int _sz, int _ex, int _ey, int _ez, 
	    float _h, float _hSq, Vec3 _delta, 
	    float _densityCoeff, float _pressureCoeff, float _viscosityCoeff, 
	    int _nx, int _ny, int _nz, int _frameNum) {
	this.m_id = _id;
	this.m_sx = _sx;
	this.m_sy = _sy;
	this.m_sz = _sz;
	this.m_ex = _ex;
	this.m_ey = _ey;
	this.m_ez = _ez;
	this.m_delta = _delta;
	this.m_externalAcceleration = new Vec3(0.f, -9.8f, 0.f);
	this.m_domainMin = new Vec3(-0.065f, -0.08f, -0.065f);
	this.m_domainMax = new Vec3(0.065f, 0.1f, 0.065f);
	this.m_h = _h;
	this.m_hSq = _hSq;
	this.m_densityCoeff = _densityCoeff;
	this.m_pressureCoeff = _pressureCoeff;
	this.m_viscosityCoeff = _viscosityCoeff;
	this.m_nx = _nx;
	this.m_ny = _ny;
	this.m_nz = _nz;
	this.m_frameNum = _frameNum;
	this.m_cells = new Cell[_ex-_sx][_ey-_sy][_ez-_sz];
	this.m_cells2 = new Cell[_ex-_sx][_ey-_sy][_ez-_sz];
	this.m_neighCells = new HashMap();
    }
    
    public void initCells(Cell[][][] _cells) {
	for(int ix = 0; ix < this.m_ex - this.m_sx; ix++) {
	    for(int iy = 0; iy < this.m_ey - this.m_sy; iy++) {
		for(int iz = 0; iz < this.m_ez - this.m_sz; iz++) {
		    this.m_cells2[ix][iy][iz] = 
			_cells[ix+this.m_sx][iy+this.m_sy][iz+this.m_sz];
		    this.m_cells[ix][iy][iz] = 
			new Cell(this.m_cells2[ix][iy][iz].m_id);
		}
	    }
	}
    }
    
    public void initNeighCells(Cell[][][] _cells, PSFADemo _psfaDemo) {
	for(int iz = 0; iz < this.m_ez - this.m_sz; ) {
	    for(int iy = 0; iy < this.m_ey - this.m_sy; iy++) {
		for(int ix = 0; ix < this.m_ex - this.m_sx; ix++) {
		    for(int dk = -1; dk <= 1; ++dk) {
			for(int dj = -1; dj <= 1; ++dj) {
			    for(int di = -1; di <= 1; ++di) {
				int ci = ix + this.m_sx + di;
				int cj = iy + this.m_sy + dj;
				int ck = iz + this.m_sz + dk;
				
				if(ci < 0) {
                                    ci = 0;
                                } else if(ci > (this.m_nx-1)) {
                                    ci = this.m_nx-1;
                                }
                                if(cj < 0) {
                                    cj = 0;
                                } else if(cj > (this.m_ny-1)) {
                                    cj = this.m_ny-1;
                                }
                                if(ck < 0) {
                                    ck = 0;
                                } else if(ck > (this.m_nz-1)) {
                                    ck = this.m_nz-1;
                                }
 
				if( ci < this.m_sx || ci >= this.m_ex ||
					cj < this.m_sy || cj >= this.m_ey ||
					ck < this.m_sz || ck >= this.m_ez ) {
				    Integer index = new Integer((ck*this.m_ny 
					          + cj)*this.m_nx + ci);
				    if(!this.m_neighCells.containsKey(index)) {
					this.m_neighCells.put(index, 
						new Cell(index.intValue()));
				    }
				    _psfaDemo.addBorderCells(index.intValue());
				}
			    }
			}
		    }
		}
	    }
	    if(this.m_ez - this.m_sz - 1 > 0) {
		iz += this.m_ez - this.m_sz - 1;
	    } else {
		iz++;
	    }
	}

	for(int ix = 0; ix < this.m_ex - this.m_sx;) {
	    for(int iy = 0; iy < this.m_ey - this.m_sy; iy++) {
		for(int iz = 0; iz < this.m_ez - this.m_sz; iz++) {
		    for(int dk = -1; dk <= 1; ++dk) {
			for(int dj = -1; dj <= 1; ++dj) {
			    for(int di = -1; di <= 1; ++di) {
				int ci = ix + this.m_sx + di;
				int cj = iy + this.m_sy + dj;
				int ck = iz + this.m_sz + dk;
				
				if(ci < 0) {
                                    ci = 0;
                                } else if(ci > (this.m_nx-1)) {
                                    ci = this.m_nx-1;
                                }
                                if(cj < 0) {
                                    cj = 0;
                                } else if(cj > (this.m_ny-1)) {
                                    cj = this.m_ny-1;
                                }
                                if(ck < 0) {
                                    ck = 0;
                                } else if(ck > (this.m_nz-1)) {
                                    ck = this.m_nz-1;
                                }

				if( ci < this.m_sx || ci >= this.m_ex ||
					cj < this.m_sy || cj >= this.m_ey ||
					ck < this.m_sz || ck >= this.m_ez ) {
				    Integer index = new Integer((ck*this.m_ny 
					          + cj)*this.m_nx + ci);
				    if(!this.m_neighCells.containsKey(index)) {
					this.m_neighCells.put(index, 
						new Cell(index.intValue()));
				    }
				    _psfaDemo.addBorderCells(index.intValue());
				}
			    }
			}
		    }
		}
	    }
	    if(this.m_ex - this.m_sx - 1 > 0) {
		ix += this.m_ex - this.m_sx - 1;
	    } else {
		ix++;
	    }
	}

    }

    public void ClearParticlesMT() {
	for(int iz = 0; iz < this.m_ez - this.m_sz; ++iz) {
	    for(int iy = 0; iy < this.m_ey - this.m_sy; ++iy) {
		for(int ix = 0; ix < this.m_ex - this.m_sx; ++ix) {
		    this.m_cells[ix][iy][iz].m_numPars = 0;
		}
	    }
	}
	
	HashMapIterator it_values = this.m_neighCells.iterator(1);
	while(it_values.hasNext()) {
	    Cell value = (Cell)it_values.next();
	    value.m_numPars = 0;
	}
    }

    public void RebuildGridMT() {
	for(int iz = 0; iz < this.m_ez - this.m_sz; ++iz) {
	    for(int iy = 0; iy < this.m_ey - this.m_sy; ++iy) {
		for(int ix = 0; ix < this.m_ex - this.m_sx; ++ix) {
		    Cell cell2 = this.m_cells2[ix][iy][iz];
		    int np2 = cell2.m_numPars;
		    for(int j = 0; j < np2; ++j) {
			int ci = (int)((cell2.m_p[j].m_x - this.m_domainMin.m_x) 
				/ this.m_delta.m_x);
			int cj = (int)((cell2.m_p[j].m_y - this.m_domainMin.m_y) 
				/ this.m_delta.m_y);
			int ck = (int)((cell2.m_p[j].m_z - this.m_domainMin.m_z) 
				/ this.m_delta.m_z);

			if(ci < 0) {
			    ci = 0; 
			} else if(ci > (this.m_nx-1)) {
			    ci = this.m_nx-1;
			}
			if(cj < 0) {
			    cj = 0; 
			} else if(cj > (this.m_ny-1)) {
			    cj = this.m_ny-1;
			}
			if(ck < 0) {
			    ck = 0; 
			} else if(ck > (this.m_nz-1)) {
			    ck = this.m_nz-1;
			}
			
			int np = 0;
			Cell cell = null;
			if( ci < this.m_sx || ci >= this.m_ex ||
				cj < this.m_sy || cj >= this.m_ey ||
				ck < this.m_sz || ck >= this.m_ez ) {
			    // move to a neighbour cell
			    int index = (ck*this.m_ny + cj)*this.m_nx + ci;
			    // this assumes that particles cannot travel more than 
			    // one grid cell per time step
			    cell = (Cell)this.m_neighCells.get(
				    new Integer(index));
			} else {
			    // move to a inside cell
			    cell = this.m_cells[ix][iy][iz];
			}
			np = cell.m_numPars;
			cell.m_p[np].m_x = cell2.m_p[j].m_x;
			cell.m_p[np].m_y = cell2.m_p[j].m_y;
			cell.m_p[np].m_z = cell2.m_p[j].m_z;
			cell.m_hv[np].m_x = cell2.m_hv[j].m_x;
			cell.m_hv[np].m_y = cell2.m_hv[j].m_y;
			cell.m_hv[np].m_z = cell2.m_hv[j].m_z;
			cell.m_v[np].m_x = cell2.m_v[j].m_x;
			cell.m_v[np].m_y = cell2.m_v[j].m_y;
			cell.m_v[np].m_z = cell2.m_v[j].m_z;
			cell.m_numPars++;
		    }
		}
	    }
	}
    }

    private int InitNeighCellList(int ci, int cj, int ck, Vec3[] neighCells) {
	int numNeighCells = 0;

	for(int di = -1; di <= 1; ++di) {
	    for(int dj = -1; dj <= 1; ++dj) {
		for(int dk = -1; dk <= 1; ++dk) {
		    int ii = ci + di;
		    int jj = cj + dj;
		    int kk = ck + dk;
		    if(ii >= 0 && ii < this.m_nx && jj >= 0 && jj < this.m_ny 
			    && kk >= 0 && kk < this.m_nz) {
			if( ii < this.m_sx || ii >= this.m_ex ||
				jj < this.m_sy || jj >= this.m_ey ||
				kk < this.m_sz || kk >= this.m_ez ) {
			    Integer index = new Integer((kk*this.m_ny + jj)
				          *this.m_nx + ii);
			    if(((Cell)(this.m_neighCells.get(index))).m_numPars 
				    != 0) {
				neighCells[numNeighCells] = new Vec3(ii,jj,kk);
				++numNeighCells;
			    }
			} else {
			    if(this.m_cells[ii - this.m_sx]
			                    [jj - this.m_sy]
			                     [kk - this.m_sz].m_numPars != 0) {
				neighCells[numNeighCells] = new Vec3(ii,jj,kk);
				++numNeighCells;
			    }
			}
		    }
		}
	    }
	}

	return numNeighCells;
    }

    public void InitDensitiesAndForcesMT() {	
	for(int iz = 0; iz < this.m_ez - this.m_sz; ++iz) {
	    for(int iy = 0; iy < this.m_ey - this.m_sy; ++iy) {
		for(int ix = 0; ix < this.m_ex - this.m_sx; ++ix) {
		    Cell cell = this.m_cells[ix][iy][iz];
		    int np = cell.m_numPars;
		    for(int j = 0; j < np; ++j) {
			cell.m_density[j] = 0.f;
			cell.m_a[j].m_x = this.m_externalAcceleration.m_x;
			cell.m_a[j].m_y = this.m_externalAcceleration.m_y;
			cell.m_a[j].m_z = this.m_externalAcceleration.m_z;
		    }
		}
	    }
	}
	
	HashMapIterator it_values = this.m_neighCells.iterator(1);
	while(it_values.hasNext()) {
	    Cell value = (Cell)it_values.next();
	    int np = value.m_numPars;
	    for(int j = 0; j < np; ++j) {
		value.m_density[j] = 0.f;
		value.m_a[j].m_x = this.m_externalAcceleration.m_x;
		value.m_a[j].m_y = this.m_externalAcceleration.m_y;
		value.m_a[j].m_z = this.m_externalAcceleration.m_z;
	    }
	}
    }

    public void ComputeDensitiesMT() {
	Vec3[] neighCells = new Vec3[27];

	for(int iz = 0; iz < this.m_ez - this.m_sz; ++iz) {
	    for(int iy = 0; iy < this.m_ey - this.m_sy; ++iy) {
		for(int ix = 0; ix < this.m_ex- this.m_sx; ++ix) {
		    Cell cell = this.m_cells[ix][iy][iz];
		    int np = cell.m_numPars;
		    if(np != 0) {
			int numNeighCells = InitNeighCellList(ix + this.m_sx, 
				iy + this.m_sy, 
				iz + this.m_sz, 
				neighCells);

			for(int j = 0; j < np; ++j) {
			    for(int inc = 0; inc < numNeighCells; ++inc) {
				Vec3 indexNeigh = neighCells[inc];
				Cell neigh = null;
				if( indexNeigh.m_x < this.m_sx || 
					indexNeigh.m_x >= this.m_ex ||
					indexNeigh.m_y < this.m_sy || 
					indexNeigh.m_y >= this.m_ey ||
					indexNeigh.m_z < this.m_sz || 
					indexNeigh.m_z >= this.m_ez ) {
				    int index = (int)((indexNeigh.m_z*this.m_ny 
					    + indexNeigh.m_y)*this.m_nx 
					    + indexNeigh.m_x);
				    neigh = (Cell)(this.m_neighCells.get(
					    new Integer(index)));
				} else {
				    neigh = this.m_cells[(int)indexNeigh.m_x-this.m_sx]
				                         [(int)indexNeigh.m_y-this.m_sy]
				                          [(int)indexNeigh.m_z-this.m_sz];
				}
				int numNeighPars = neigh.m_numPars;
				for(int iparNeigh = 0; iparNeigh < numNeighPars; 
				++iparNeigh) {
				    if(neigh.m_p[iparNeigh].isLess(cell.m_p[j])) {
					float distSq = (cell.m_p[j].sub1
						(neigh.m_p[iparNeigh])).
						GetLengthSq();
					if(distSq < this.m_hSq) {
					    float t = this.m_hSq - distSq;
					    float tc = t*t*t;
					    cell.m_density[j] += tc;
					    neigh.m_density[iparNeigh] += tc;
					}
				    }
				}
			    }
			}
		    }
		}
	    }
	}
    }

    public void ComputeDensities2MT() {
	float tc = this.m_hSq*this.m_hSq*this.m_hSq;
	for(int iz = 0; iz < this.m_ez - this.m_sz; ++iz) {
	    for(int iy = 0; iy < this.m_ey - this.m_sy; ++iy) {
		for(int ix = 0; ix < this.m_ex - this.m_sx; ++ix) {
		    Cell cell = this.m_cells[ix][iy][iz];
		    int np = cell.m_numPars;
		    for(int j = 0; j < np; ++j) {
			cell.m_density[j] += tc;
			cell.m_density[j] *= this.m_densityCoeff;
		    }
		}
	    }
	}
    }

    public void ComputeForcesMT() {
	float doubleRestDensity = 2000.f;
	
	Vec3[] neighCells = new Vec3[27];

	for(int iz = 0; iz < this.m_ez - this.m_sz; ++iz) {
	    for(int iy = 0; iy < this.m_ey - this.m_sy; ++iy) {
		for(int ix = 0; ix < this.m_ex - this.m_sx; ++ix) {
		    Cell cell = this.m_cells[ix][iy][iz];
		    int np = cell.m_numPars;

		    if(np != 0) {
			int numNeighCells = InitNeighCellList(ix + this.m_sx, 
				iy + this.m_sy, 
				iz + this.m_sz, 
				neighCells);

			for(int j = 0; j < np; ++j) {
			    for(int inc = 0; inc < numNeighCells; ++inc) {
				Vec3 indexNeigh = neighCells[inc];
				Cell neigh = null;
				if( indexNeigh.m_x < this.m_sx || 
					indexNeigh.m_x >= this.m_ex ||
					indexNeigh.m_y < this.m_sy || 
					indexNeigh.m_y >= this.m_ey ||
					indexNeigh.m_z < this.m_sz || 
					indexNeigh.m_z >= this.m_ez ) {
				    int index = (int)((indexNeigh.m_z*this.m_ny 
					    + indexNeigh.m_y)*this.m_nx 
					    + indexNeigh.m_x);
				    neigh = (Cell)(this.m_neighCells.get(
					    new Integer(index)));
				} else {
				    neigh = this.m_cells[(int)indexNeigh.m_x-this.m_sx]
				                         [(int)indexNeigh.m_y-this.m_sy]
				                          [(int)indexNeigh.m_z-this.m_sz];
				}
				int numNeighPars = neigh.m_numPars;
				for(int iparNeigh = 0; iparNeigh < numNeighPars; 
				++iparNeigh) {
				    if(neigh.m_p[iparNeigh].isLess(cell.m_p[j])) {
					Vec3 disp = cell.m_p[j].sub1
					(neigh.m_p[iparNeigh]);
					float distSq = disp.GetLengthSq();
					if(distSq < this.m_hSq) {
					    float max = 1e-12f;
					    if(distSq > 1e-12f) {
						max = distSq;
					    }
					    float dist = Math.sqrtf(max);
					    float hmr = this.m_h - dist;

					    Vec3 acc = disp.mul1(
						    this.m_pressureCoeff).mul1(
							    hmr*hmr/dist).mul1(
								    cell.m_density[j]+
								    neigh.m_density[iparNeigh] - 
								    doubleRestDensity);
					    acc.add0(neigh.m_v[iparNeigh].sub1(
						    cell.m_v[j]).mul1(
							    this.m_viscosityCoeff * 
							    hmr));
					    acc.div0(cell.m_density[j] * 
						    neigh.m_density[iparNeigh]);
					    cell.m_a[j].add0(acc);
					    neigh.m_a[iparNeigh].sub0(acc);
					}
				    }
				}
			    }
			}
		    }
		}
	    }
	}
    }

    public void ProcessCollisionsMT() {
	float timeStep = 0.005f;
	float parSize = 0.0002f;
	float epsilon = 1e-10f;
	float stiffness = 30000.f;
	float damping = 128.f;

	for(int iz = 0; iz < this.m_ez - this.m_sz; ++iz) {
	    for(int iy = 0; iy < this.m_ey - this.m_sy; ++iy) {
		for(int ix = 0; ix < this.m_ex - this.m_sx; ++ix) {
		    Cell cell = this.m_cells[ix][iy][iz];
		    int np = cell.m_numPars;
		    for(int j = 0; j < np; ++j) {
			Vec3 pos = cell.m_p[j].add1(
				cell.m_hv[j].mul1(timeStep));

			float diff = parSize - (pos.m_x - this.m_domainMin.m_x);
			if(diff > epsilon) {
			    cell.m_a[j].m_x += stiffness*diff 
			                     - damping*cell.m_v[j].m_x;
			}

			diff = parSize - (this.m_domainMax.m_x - pos.m_x);
			if(diff > epsilon) {
			    cell.m_a[j].m_x -= stiffness*diff 
			                     + damping*cell.m_v[j].m_x;
			}

			diff = parSize - (pos.m_y - this.m_domainMin.m_y);
			if(diff > epsilon) {
			    cell.m_a[j].m_y += stiffness*diff 
			                     - damping*cell.m_v[j].m_y;
			}

			diff = parSize - (this.m_domainMax.m_y - pos.m_y);
			if(diff > epsilon) {
			    cell.m_a[j].m_y -= stiffness*diff 
			                     + damping*cell.m_v[j].m_y;
			}

			diff = parSize - (pos.m_z - this.m_domainMin.m_z);
			if(diff > epsilon) {
			    cell.m_a[j].m_z += stiffness*diff 
			                     - damping*cell.m_v[j].m_z;
			}

			diff = parSize - (this.m_domainMax.m_z - pos.m_z);
			if(diff > epsilon) {
			    cell.m_a[j].m_z -= stiffness*diff 
			                     + damping*cell.m_v[j].m_z;
			}
		    }
		}
	    }
	}
    }

    public void AdvanceParticlesMT() {
	float timeStep = 0.005f;
	
	for(int iz = 0; iz < this.m_ez - this.m_sz; ++iz) {
	    for(int iy = 0; iy < this.m_ey - this.m_sy; ++iy) {
		for(int ix = 0; ix < this.m_ex - this.m_sx; ++ix) {
		    Cell cell = this.m_cells[ix][iy][iz];
		    int np = cell.m_numPars;
		    for(int j = 0; j < np; ++j) {
			Vec3 v_half = cell.m_hv[j].add1(cell.m_a[j].mul1(
				timeStep));
			cell.m_p[j].add0(v_half.mul1(timeStep));
			cell.m_v[j] = cell.m_hv[j].add1(v_half);
			cell.m_v[j].mul0(0.5f);
			cell.m_hv[j] = v_half;
		    }
		}
	    }
	}
    }
    
    public boolean isFinish() {
	this.m_frameNum--;
	return (this.m_frameNum == 0);
    }
}