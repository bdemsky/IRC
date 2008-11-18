public class PSFADemo {
    flag tosum;
    flag flushghost;
    flag tosum2;
    flag flushghost2;
    flag tosum3;
    flag flushghost3;
    flag tosum4;
    flag flushghost4;
    
    public int m_numGrids;
    public int m_numCells;
    public int m_numParticles;

    public HashMap m_borderCells;    // shared border cells
    
    public int m_nx;			// number of grid cells in each dimension
    public int m_ny;
    public int m_nz;
    public float m_viscosityCoeff;
    public Vec3 m_delta;				// cell dimensions
    public Vec3 m_domainMin;
    public Vec3 m_domainMax;
    
    public int m_counter;
    
    public PSFADemo(int _numgrids, int _numcells, int _numparticles, 
	    int _nx, int _ny, int _nz, float _viscosityCoeff, Vec3 _delta) {
	this.m_numGrids = _numgrids;
	this.m_numCells = _numcells;
	this.m_numParticles = _numparticles;
	this.m_borderCells = new HashMap();
	this.m_nx = _nx;
	this.m_ny = _ny;
	this.m_nz = _nz;
	this.m_viscosityCoeff = _viscosityCoeff;
	this.m_delta = _delta;
	this.m_domainMin = new Vec3(-0.065f, -0.08f, -0.065f);
	this.m_domainMax = new Vec3(0.065f, 0.1f, 0.065f);
	this.m_counter = 0;
    }
    
    public void addBorderCells(int index) {
	Integer in = new Integer(index);
	if(!this.m_borderCells.containsKey(in)) {
	    this.m_borderCells.put(in, new Cell(index));
	}
    }
    
    public boolean isFinish() {
	this.m_counter++;
	return (this.m_counter == this.m_numGrids);
    }
    
    public void resetCounter() {
	this.m_counter = 0;
    }
    
    public void resetBorderCells() {
	HashMapIterator it_values = this.m_borderCells.iterator(1);
	while(it_values.hasNext()) {
	    Cell cell= (Cell)it_values.next();
	    cell.m_numPars = 0;
	}
    }
    
    public void resetBorderCellsHV() {
	HashMapIterator it_values = this.m_borderCells.iterator(1);
	while(it_values.hasNext()) {
	    Cell cell= (Cell)it_values.next();
	    for(int i = 0; i <cell.m_numPars; i++) {
		cell.m_v[i].m_x = 0;
		cell.m_v[i].m_y = 0;
		cell.m_v[i].m_z = 0;
	    }
	}
    }
    
    public void sum(Grid g) {
	// ghost cells of g
	HashMap visited = new HashMap();
	HashMapIterator it_keys = g.m_neighCells.iterator(0);
	while(it_keys.hasNext()) {
	    Integer key = (Integer)it_keys.next();
	    if(!visited.containsKey(key)) {
		Cell cell = (Cell)(this.m_borderCells.get(key));
		int np = cell.m_numPars;
		Cell toadd = (Cell)(g.m_neighCells.get(key));
		int toaddnp = toadd.m_numPars;
		for(int j = 0; j < toaddnp; ++j) {
		    if(np < cell.m_p.length) {
			if(cell.m_p[np] == null) {
			    cell.m_p[np] = new Vec3();
			    cell.m_hv[np] = new Vec3();
			    cell.m_v[np] = new Vec3();
			    cell.m_a[np] = new Vec3();
			}
			cell.m_p[np].m_x = toadd.m_p[j].m_x;
			cell.m_p[np].m_y = toadd.m_p[j].m_y;
			cell.m_p[np].m_z = toadd.m_p[j].m_z;
			cell.m_hv[np].m_x = toadd.m_hv[j].m_x;
			cell.m_hv[np].m_y = toadd.m_hv[j].m_y;
			cell.m_hv[np].m_z = toadd.m_hv[j].m_z;
			cell.m_v[np].m_x = toadd.m_v[j].m_x;
			cell.m_v[np].m_y = toadd.m_v[j].m_y;
			cell.m_v[np].m_z = toadd.m_v[j].m_z;
			cell.m_a[np].m_x = toadd.m_a[j].m_x;
			cell.m_a[np].m_y = toadd.m_a[j].m_y;
			cell.m_a[np].m_z = toadd.m_a[j].m_z;
			cell.m_density[np] = toadd.m_density[j];
			np++;
		    }
		}
		cell.m_numPars += toaddnp;
		visited.put(key, key);
	    }
	}
	// border cells of g
	for(int iz = 0; iz < g.m_ez - g.m_sz;) {
	    for(int iy = 0; iy < g.m_ey - g.m_sy; iy++) {
		for(int ix = 0; ix < g.m_ex - g.m_sx; ix++) {
		    Integer index = new Integer(((iz + g.m_sz)*this.m_ny + (iy 
			          + g.m_sy))*this.m_nx + (ix + g.m_sx));
		    if((this.m_borderCells.containsKey(index)) && 
			    !visited.containsKey(index)){
			Cell cell = (Cell)(this.m_borderCells.get(index));
			int np = cell.m_numPars;
			Cell toadd = g.m_cells[ix][iy][iz];
			int toaddnp = toadd.m_numPars;
			for(int j = 0; j < toaddnp; ++j) {
			    if(np < cell.m_p.length) {
				if(cell.m_p[np] == null) {
				    cell.m_p[np] = new Vec3();
				    cell.m_hv[np] = new Vec3();
				    cell.m_v[np] = new Vec3();
				    cell.m_a[np] = new Vec3();
				}
				cell.m_p[np].m_x = toadd.m_p[j].m_x;
				cell.m_p[np].m_y = toadd.m_p[j].m_y;
				cell.m_p[np].m_z = toadd.m_p[j].m_z;
				cell.m_hv[np].m_x = toadd.m_hv[j].m_x;
				cell.m_hv[np].m_y = toadd.m_hv[j].m_y;
				cell.m_hv[np].m_z = toadd.m_hv[j].m_z;
				cell.m_v[np].m_x = toadd.m_v[j].m_x;
				cell.m_v[np].m_y = toadd.m_v[j].m_y;
				cell.m_v[np].m_z = toadd.m_v[j].m_z;
				cell.m_a[np].m_x = toadd.m_a[j].m_x;
				cell.m_a[np].m_y = toadd.m_a[j].m_y;
				cell.m_a[np].m_z = toadd.m_a[j].m_z;
				cell.m_density[np] = toadd.m_density[j];
				np++;
			    }
			}
			cell.m_numPars += toaddnp;
			visited.put(index, index);
		    }
		}
	    }
	    if(g.m_ez - g.m_sz - 1 > 0) {
		iz += g.m_ez - g.m_sz - 1;
	    } else {
		iz++;
	    }
	}

	for(int ix = 0; ix < g.m_ex - g.m_sx;) {
	    for(int iy = 0; iy < g.m_ey - g.m_sy; iy++) {
		for(int iz = 0; iz < g.m_ez - g.m_sz; iz++) {
		    Integer index = new Integer(((iz + g.m_sz)*this.m_ny + (iy 
			          + g.m_sy))*this.m_nx + (ix + g.m_sx));
		    if((this.m_borderCells.containsKey(index))
			    && !visited.containsKey(index)){
			Cell cell = (Cell)(this.m_borderCells.get(index));
			int np = cell.m_numPars;
			Cell toadd = g.m_cells[ix][iy][iz];
			int toaddnp = toadd.m_numPars;
			for(int j = 0; j < toaddnp; ++j) {
			    if(np < cell.m_p.length) {
				if(cell.m_p[np] == null) {
				    cell.m_p[np] = new Vec3();
				    cell.m_hv[np] = new Vec3();
				    cell.m_v[np] = new Vec3();
				    cell.m_a[np] = new Vec3();
				}
				cell.m_p[np].m_x = toadd.m_p[j].m_x;
				cell.m_p[np].m_y = toadd.m_p[j].m_y;
				cell.m_p[np].m_z = toadd.m_p[j].m_z;
				cell.m_hv[np].m_x = toadd.m_hv[j].m_x;
				cell.m_hv[np].m_y = toadd.m_hv[j].m_y;
				cell.m_hv[np].m_z = toadd.m_hv[j].m_z;
				cell.m_v[np].m_x = toadd.m_v[j].m_x;
				cell.m_v[np].m_y = toadd.m_v[j].m_y;
				cell.m_v[np].m_z = toadd.m_v[j].m_z;
				cell.m_a[np].m_x = toadd.m_a[j].m_x;
				cell.m_a[np].m_y = toadd.m_a[j].m_y;
				cell.m_a[np].m_z = toadd.m_a[j].m_z;
				cell.m_density[np] = toadd.m_density[j];
				np++;
			    }
			}
			cell.m_numPars += toaddnp;
			visited.put(index, index);
		    }
		}
	    }
	    if(g.m_ex - g.m_sx - 1 > 0) {
		ix += g.m_ex - g.m_sx - 1;
	    } else {
		ix++;
	    }
	}
    }
    
    public void flush(Grid g) {
	HashMap visited = new HashMap();
	// ghost cells of g
	HashMapIterator it_keys = g.m_neighCells.iterator(0);
	while(it_keys.hasNext()) {
	    Integer key = (Integer)it_keys.next();
	    if(!visited.containsKey(key)) {
		Cell toflush = (Cell)(this.m_borderCells.get(key));
		int toflushnp = toflush.m_numPars;
		Cell cell = (Cell)(g.m_neighCells.get(key));
		cell.m_numPars = 0;
		int np = 0;
		for(int j = 0; j < toflushnp; ++j) {
		    if(np < cell.m_p.length) {
			if(cell.m_p[np] == null) {
			    cell.m_p[np] = new Vec3();
			    cell.m_hv[np] = new Vec3();
			    cell.m_v[np] = new Vec3();
			    cell.m_a[np] = new Vec3();
			}
			cell.m_p[np].m_x = toflush.m_p[j].m_x;
			cell.m_p[np].m_y = toflush.m_p[j].m_y;
			cell.m_p[np].m_z = toflush.m_p[j].m_z;
			cell.m_hv[np].m_x = toflush.m_hv[j].m_x;
			cell.m_hv[np].m_y = toflush.m_hv[j].m_y;
			cell.m_hv[np].m_z = toflush.m_hv[j].m_z;
			cell.m_v[np].m_x = toflush.m_v[j].m_x;
			cell.m_v[np].m_y = toflush.m_v[j].m_y;
			cell.m_v[np].m_z = toflush.m_v[j].m_z;
			cell.m_a[np].m_x = toflush.m_a[j].m_x;
			cell.m_a[np].m_y = toflush.m_a[j].m_y;
			cell.m_a[np].m_z = toflush.m_a[j].m_z;
			cell.m_density[np] = toflush.m_density[j];
			np++;
		    }
		}
		cell.m_numPars += toflushnp;
		visited.put(key, key);
	    }
	}
	
	// border cells of g
	for(int iz = 0; iz < g.m_ez - g.m_sz;) {
	    for(int iy = 0; iy < g.m_ey - g.m_sy; iy++) {
		for(int ix = 0; ix < g.m_ex - g.m_sx; ix++) {
		    Integer index = new Integer(((iz + g.m_sz)*this.m_ny + (iy 
			          + g.m_sy))*this.m_nx + (ix + g.m_sx));
		    if((this.m_borderCells.containsKey(index)) 
			    && !visited.containsKey(index)) {
			Cell toflush = (Cell)(this.m_borderCells.get(index));
			int toflushnp = toflush.m_numPars;
			Cell cell = g.m_cells[ix][iy][iz];
			cell.m_numPars = 0;
			int np = 0;
			for(int j = 0; j < toflushnp; ++j) {
			    if(np < cell.m_p.length) {
				if(cell.m_p[np] == null) {
				    cell.m_p[np] = new Vec3();
				    cell.m_hv[np] = new Vec3();
				    cell.m_v[np] = new Vec3();
				    cell.m_a[np] = new Vec3();
				}
				cell.m_p[np].m_x = toflush.m_p[j].m_x;
				cell.m_p[np].m_y = toflush.m_p[j].m_y;
				cell.m_p[np].m_z = toflush.m_p[j].m_z;
				cell.m_hv[np].m_x = toflush.m_hv[j].m_x;
				cell.m_hv[np].m_y = toflush.m_hv[j].m_y;
				cell.m_hv[np].m_z = toflush.m_hv[j].m_z;
				cell.m_v[np].m_x = toflush.m_v[j].m_x;
				cell.m_v[np].m_y = toflush.m_v[j].m_y;
				cell.m_v[np].m_z = toflush.m_v[j].m_z;
				cell.m_a[np].m_x = toflush.m_a[j].m_x;
				cell.m_a[np].m_y = toflush.m_a[j].m_y;
				cell.m_a[np].m_z = toflush.m_a[j].m_z;
				cell.m_density[np] = toflush.m_density[j];
				np++;
			    }
			}
			cell.m_numPars += toflushnp;
			visited.put(index, index);
		    }
		}
	    }
	    if(g.m_ez - g.m_sz - 1 > 0) {
		iz += g.m_ez - g.m_sz - 1;
	    } else {
		iz++;
	    }
	}
	
	for(int ix = 0; ix < g.m_ex - g.m_sx;) {
	    for(int iy = 0; iy < g.m_ey - g.m_sy; iy++) {
		for(int iz = 0; iz < g.m_ez - g.m_sz; iz++) {
		    Integer index = new Integer(((iz + g.m_sz)*this.m_ny + (iy 
			          + g.m_sy))*this.m_nx + (ix + g.m_sx));
		    if((this.m_borderCells.containsKey(index)) 
			    && !visited.containsKey(index)) {
			Cell toflush = (Cell)(this.m_borderCells.get(index));
			int toflushnp = toflush.m_numPars;
			Cell cell = g.m_cells[ix][iy][iz];
			cell.m_numPars = 0;
			int np = 0;
			for(int j = 0; j < toflushnp; ++j) {
			    if(np < cell.m_p.length) {
				if(cell.m_p[np] == null) {
				    cell.m_p[np] = new Vec3();
				    cell.m_hv[np] = new Vec3();
				    cell.m_v[np] = new Vec3();
				    cell.m_a[np] = new Vec3();
				}
				cell.m_p[np].m_x = toflush.m_p[j].m_x;
				cell.m_p[np].m_y = toflush.m_p[j].m_y;
				cell.m_p[np].m_z = toflush.m_p[j].m_z;
				cell.m_hv[np].m_x = toflush.m_hv[j].m_x;
				cell.m_hv[np].m_y = toflush.m_hv[j].m_y;
				cell.m_hv[np].m_z = toflush.m_hv[j].m_z;
				cell.m_v[np].m_x = toflush.m_v[j].m_x;
				cell.m_v[np].m_y = toflush.m_v[j].m_y;
				cell.m_v[np].m_z = toflush.m_v[j].m_z;
				cell.m_a[np].m_x = toflush.m_a[j].m_x;
				cell.m_a[np].m_y = toflush.m_a[j].m_y;
				cell.m_a[np].m_z = toflush.m_a[j].m_z;
				cell.m_density[np] = toflush.m_density[j];
				np++;
			    }
			}
			cell.m_numPars += toflushnp;
			visited.put(index, index);
		    }
		}
	    }
	    if(g.m_ex - g.m_sx - 1 > 0) {
		ix += g.m_ex - g.m_sx - 1;
	    } else {
		ix++;
	    }
	}
    }
    
    public void sum2(Grid g) {
	HashMap visited = new HashMap();
	// ghost cells of g
	HashMapIterator it_keys = g.m_neighCells.iterator(0);
	while(it_keys.hasNext()) {
	    Integer key = (Integer)it_keys.next();
	    if(!visited.containsKey(key)) {
		Cell cell = (Cell)(this.m_borderCells.get(key));
		int np = cell.m_numPars;
		Cell toadd = (Cell)(g.m_neighCells.get(key));
		for(int j = 0; j < np; ++j) {
		    cell.m_density[j] += toadd.m_density[j];
		}
		visited.put(key, key);
	    }
	}
	// border cells of g
	for(int iz = 0; iz < g.m_ez - g.m_sz;) {
	    for(int iy = 0; iy < g.m_ey - g.m_sy; iy++) {
		for(int ix = 0; ix < g.m_ex - g.m_sx; ix++) {
		    Integer index = new Integer(((iz + g.m_sz)*this.m_ny + (iy 
			          + g.m_sy))*this.m_nx + (ix + g.m_sx));
		    if((this.m_borderCells.containsKey(index)) 
			    && !visited.containsKey(index)) {
			Cell cell = (Cell)(this.m_borderCells.get(index));
			int np = cell.m_numPars;
			Cell toadd = g.m_cells[ix][iy][iz];
			for(int j = 0; j < np; ++j) {
			    cell.m_density[j] += toadd.m_density[j];
			}
			visited.put(index, index);
		    }
		}
	    }
	    if(g.m_ez - g.m_sz - 1 > 0) {
		iz += g.m_ez - g.m_sz - 1;
	    } else {
		iz++;
	    }
	}
	for(int ix = 0; ix < g.m_ex - g.m_sx;) {
	    for(int iy = 0; iy < g.m_ey - g.m_sy; iy++) {
		for(int iz = 0; iz < g.m_ez - g.m_sz; iz++) {
		    Integer index = new Integer(((iz + g.m_sz)*this.m_ny + (iy 
			          + g.m_sy))*this.m_nx + (ix + g.m_sx));
		    if((this.m_borderCells.containsKey(index)) 
			    && !visited.containsKey(index)) {
			Cell cell = (Cell)(this.m_borderCells.get(index));
			int np = cell.m_numPars;
			Cell toadd = g.m_cells[ix][iy][iz];
			for(int j = 0; j < np; ++j) {
			    cell.m_density[j] += toadd.m_density[j];
			}
			visited.put(index, index);
		    }
		}
	    }
	    if(g.m_ex - g.m_sx - 1 > 0) {
		ix += g.m_ex - g.m_sx - 1;
	    } else {
		ix++;
	    }
	}
    }
    
    public void flush2(Grid g) {
	HashMap visited = new HashMap();
	// ghost cells of g
	HashMapIterator it_keys = g.m_neighCells.iterator(0);
	while(it_keys.hasNext()) {
	    Integer key = (Integer)it_keys.next();
	    if(!visited.containsKey(key)) {
		Cell toflush = (Cell)(this.m_borderCells.get(key));
		Cell cell = (Cell)(g.m_neighCells.get(key));
		int np = cell.m_numPars;
		for(int j = 0; j < np; ++j) {
		    cell.m_density[j] = toflush.m_density[j];
		}
	    }
	}
	
	// border cells of g
	for(int iz = 0; iz < g.m_ez - g.m_sz;) {
	    for(int iy = 0; iy < g.m_ey - g.m_sy; iy++) {
		for(int ix = 0; ix < g.m_ex - g.m_sx; ix++) {
		    Integer index = new Integer(((iz + g.m_sz)*this.m_ny + (iy 
			          + g.m_sy))*this.m_nx + (ix + g.m_sx));
		    if((this.m_borderCells.containsKey(index)) 
			    && !visited.containsKey(index)) {
			Cell toflush = (Cell)(this.m_borderCells.get(index));
			Cell cell = g.m_cells[ix][iy][iz];
			int np = cell.m_numPars;
			for(int j = 0; j < np; ++j) {
			    cell.m_density[j] = toflush.m_density[j];
			}
			visited.put(index, index);
		    }
		}
	    }
	    if(g.m_ez - g.m_sz - 1 > 0) {
		iz += g.m_ez - g.m_sz - 1;
	    } else {
		iz++;
	    }
	}
	
	for(int ix = 0; ix < g.m_ex - g.m_sx;) {
	    for(int iy = 0; iy < g.m_ey - g.m_sy; iy++) {
		for(int iz = 0; iz < g.m_ez - g.m_sz; iz++) {
		    Integer index = new Integer(((iz + g.m_sz)*this.m_ny + (iy 
			          + g.m_sy))*this.m_nx + (ix + g.m_sx));
		    if((this.m_borderCells.containsKey(index)) 
			    && !visited.containsKey(index)) {
			Cell toflush = (Cell)(this.m_borderCells.get(index));
			Cell cell = g.m_cells[ix][iy][iz];
			int np = cell.m_numPars;
			for(int j = 0; j < np; ++j) {
			    cell.m_density[j] = toflush.m_density[j];
			}
			visited.put(index, index);
		    }
		}
	    }
	    if(g.m_ex - g.m_sx - 1 > 0) {
		ix += g.m_ex - g.m_sx - 1;
	    } else {
		ix++;
	    }
	}
    }
    
    public void sum3(Grid g) {
	HashMap visited = new HashMap();
	// border cells of g
	for(int iz = 0; iz < g.m_ez - g.m_sz;) {
	    for(int iy = 0; iy < g.m_ey - g.m_sy; iy++) {
		for(int ix = 0; ix < g.m_ex - g.m_sx; ix++) {
		    Integer index = new Integer(((iz + g.m_sz)*this.m_ny + (iy 
			          + g.m_sy))*this.m_nx + (ix + g.m_sx));
		    if((this.m_borderCells.containsKey(index)) 
			    && !visited.containsKey(index)) {
			Cell cell = (Cell)(this.m_borderCells.get(index));
			int np = cell.m_numPars;
			Cell toadd = g.m_cells[ix][iy][iz];
			for(int j = 0; j < np; ++j) {
			    cell.m_density[j] = toadd.m_density[j];
			}
			visited.put(index, index);
		    }
		}
	    }
	    if(g.m_ez - g.m_sz - 1 > 0) {
		iz += g.m_ez - g.m_sz - 1;
	    } else {
		iz++;
	    }
	}
	
	for(int ix = 0; ix < g.m_ex - g.m_sx;) {
	    for(int iy = 0; iy < g.m_ey - g.m_sy; iy++) {
		for(int iz = 0; iz < g.m_ez - g.m_sz; iz++) {
		    Integer index = new Integer(((iz + g.m_sz)*this.m_ny + (iy 
			          + g.m_sy))*this.m_nx + (ix + g.m_sx));
		    if((this.m_borderCells.containsKey(index)) 
			    && !visited.containsKey(index)) {
			Cell cell = (Cell)(this.m_borderCells.get(index));
			int np = cell.m_numPars;
			Cell toadd = g.m_cells[ix][iy][iz];
			for(int j = 0; j < np; ++j) {
			    cell.m_density[j] = toadd.m_density[j];
			}
			visited.put(index, index);
		    }
		}
	    }
	    if(g.m_ex - g.m_sx - 1 > 0) {
		ix += g.m_ex - g.m_sx - 1;
	    } else {
		ix++;
	    }
	}
    }
    
    public void flush3(Grid g) {
	// ghost cells of g
	HashMapIterator it_keys = g.m_neighCells.iterator(0);
	while(it_keys.hasNext()) {
	    Integer key = (Integer)it_keys.next();
	    Cell toflush = (Cell)(this.m_borderCells.get(key));
	    Cell cell = (Cell)(g.m_neighCells.get(key));
	    int np = cell.m_numPars;
	    for(int j = 0; j < np; ++j) {
		cell.m_density[j] = toflush.m_density[j];
	    }
	}
    }
    
    public void sum4(Grid g) {
	HashMap visited = new HashMap();
	// ghost cells of g
	HashMapIterator it_keys = g.m_neighCells.iterator(0);
	while(it_keys.hasNext()) {
	    Integer key = (Integer)it_keys.next();
	    if(!visited.containsKey(key)) {
		Cell cell = (Cell)(this.m_borderCells.get(key));
		int np = cell.m_numPars;
		Cell toadd = (Cell)(g.m_neighCells.get(key));
		for(int j = 0; j < np; ++j) {
		    cell.m_v[j].m_x += toadd.m_v[j].m_x - cell.m_a[j].m_x;
		    cell.m_v[j].m_y += toadd.m_v[j].m_y - cell.m_a[j].m_y;
		    cell.m_v[j].m_z += toadd.m_v[j].m_z - cell.m_a[j].m_z;
		}
	    }
	}
	
	// border cells of g
	for(int iz = 0; iz < g.m_ez - g.m_sz;) {
	    for(int iy = 0; iy < g.m_ey - g.m_sy; iy++) {
		for(int ix = 0; ix < g.m_ex - g.m_sx; ix++) {
		    Integer index = new Integer(((iz + g.m_sz)*this.m_ny + (iy 
			          + g.m_sy))*this.m_nx + (ix + g.m_sx));
		    if((this.m_borderCells.containsKey(index)) 
			    && !visited.containsKey(index)) {
			Cell cell = (Cell)(this.m_borderCells.get(index));
			int np = cell.m_numPars;
			Cell toadd = g.m_cells[ix][iy][iz];
			for(int j = 0; j < np; ++j) {
			    cell.m_v[j].m_x = toadd.m_v[j].m_x - 
			                       cell.m_a[j].m_x;
			    cell.m_v[j].m_y = toadd.m_v[j].m_y - 
			                       cell.m_a[j].m_y;
			    cell.m_v[j].m_z = toadd.m_v[j].m_z - 
			                       cell.m_a[j].m_z;
			}
			visited.put(index, index);
		    }
		}
	    }
	    if(g.m_ez - g.m_sz - 1 > 0) {
		iz += g.m_ez - g.m_sz - 1;
	    } else {
		iz++;
	    }
	}
	
	for(int ix = 0; ix < g.m_ex - g.m_sx;) {
	    for(int iy = 0; iy < g.m_ey - g.m_sy; iy++) {
		for(int iz = 0; iz < g.m_ez - g.m_sz; iz++) {
		    Integer index = new Integer(((iz + g.m_sz)*this.m_ny + (iy 
			          + g.m_sy))*this.m_nx + (ix + g.m_sx));
		    if((this.m_borderCells.containsKey(index)) 
			    && !visited.containsKey(index)) {
			Cell cell = (Cell)(this.m_borderCells.get(index));
			int np = cell.m_numPars;
			Cell toadd = g.m_cells[ix][iy][iz];
			for(int j = 0; j < np; ++j) {
			    cell.m_v[j].m_x = toadd.m_v[j].m_x - 
			                      cell.m_a[j].m_x;
			    cell.m_v[j].m_y = toadd.m_v[j].m_y - 
			                      cell.m_a[j].m_y;
			    cell.m_v[j].m_z = toadd.m_v[j].m_z - 
			                      cell.m_a[j].m_z;
			}
			visited.put(index, index);
		    }
		}
	    }
	    if(g.m_ex - g.m_sx - 1 > 0) {
		ix += g.m_ex - g.m_sx - 1;
	    } else {
		ix++;
	    }
	}
	
	if(this.m_counter == this.m_numGrids - 1) {
	    HashMapIterator it_values = this.m_borderCells.iterator(1);
	    while(it_values.hasNext()) {
		Cell cell = (Cell)it_values.next();
		int np = cell.m_numPars;
		for(int j = 0; j < np; ++j) {
		    cell.m_a[j].m_x += cell.m_v[j].m_x;
		    cell.m_a[j].m_y += cell.m_v[j].m_y;
		    cell.m_a[j].m_z += cell.m_v[j].m_z;
		}
	    }
	}
    }
    
    public void flush4(Grid g) {
	HashMap visited = new HashMap();
	// ghost cells of g
	HashMapIterator it_keys = g.m_neighCells.iterator(0);
	while(it_keys.hasNext()) {
	    Integer key = (Integer)it_keys.next();
	    if(!visited.containsKey(key)) {
		Cell toflush = (Cell)(this.m_borderCells.get(key));
		Cell cell = (Cell)(g.m_neighCells.get(key));
		int np = cell.m_numPars;
		for(int j = 0; j < np; ++j) {
		    cell.m_a[j].m_x = toflush.m_a[j].m_x;
		    cell.m_a[j].m_y = toflush.m_a[j].m_y;
		    cell.m_a[j].m_z = toflush.m_a[j].m_z;
		}
		visited.put(key, key);
	    }
	}
	
	// border cells of g
	for(int iz = 0; iz < g.m_ez - g.m_sz;) {
	    for(int iy = 0; iy < g.m_ey - g.m_sy; iy++) {
		for(int ix = 0; ix < g.m_ex - g.m_sx; ix++) {
		    Integer index = new Integer(((iz + g.m_sz)*this.m_ny + (iy 
			          + g.m_sy))*this.m_nx + (ix + g.m_sx));
		    if((this.m_borderCells.containsKey(index)) 
			    && !visited.containsKey(index)) {
			Cell toflush = (Cell)(this.m_borderCells.get(index));
			Cell cell = g.m_cells[ix][iy][iz];
			int np = cell.m_numPars;
			for(int j = 0; j < np; ++j) {
			    cell.m_a[j].m_x = toflush.m_a[j].m_x;
			    cell.m_a[j].m_y = toflush.m_a[j].m_y;
			    cell.m_a[j].m_z = toflush.m_a[j].m_z;
			}
			visited.put(index, index);
		    }
		}
	    }
	    if(g.m_ez - g.m_sz - 1 > 0) {
		iz += g.m_ez - g.m_sz - 1;
	    } else {
		iz++;
	    }
	}
	
	for(int ix = 0; ix < g.m_ex - g.m_sx;) {
	    for(int iy = 0; iy < g.m_ey - g.m_sy; iy++) {
		for(int iz = 0; iz < g.m_ez - g.m_sz; iz++) {
		    Integer index = new Integer(((iz + g.m_sz)*this.m_ny + (iy 
			          + g.m_sy))*this.m_nx + (ix + g.m_sx));
		    if((this.m_borderCells.containsKey(index)) 
			    && !visited.containsKey(index)) {
			Cell toflush = (Cell)(this.m_borderCells.get(index));
			Cell cell = g.m_cells[ix][iy][iz];
			int np = cell.m_numPars;
			for(int j = 0; j < np; ++j) {
			    cell.m_a[j].m_x = toflush.m_a[j].m_x;
			    cell.m_a[j].m_y = toflush.m_a[j].m_y;
			    cell.m_a[j].m_z = toflush.m_a[j].m_z;
			}
			visited.put(index, index);
		    }
		}
	    }
	    if(g.m_ex - g.m_sx - 1 > 0) {
		ix += g.m_ex - g.m_sx - 1;
	    } else {
		ix++;
	    }
	}
    }
}