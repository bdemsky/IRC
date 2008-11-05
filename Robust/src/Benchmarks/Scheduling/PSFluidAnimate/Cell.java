// there is a current limitation of 16 particles per cell
// (this structure use to be a simple linked-list of particles but, due to
// improved cache locality, we get a huge performance increase by copying
// particles instead of referencing them)
public class Cell {
    public int m_id;
    public int m_numPars;
    public Vec3[] m_p;
    public Vec3[] m_hv;
    public Vec3[] m_v;
    public Vec3[] m_a;
    public float[] m_density;
	
    public Cell(int _id) {
	this.m_id = _id;
	this.m_numPars = 0;
	this.m_p = new Vec3[16];
	this.m_hv = new Vec3[16];
	this.m_v = new Vec3[16];
	this.m_a = new Vec3[16];
	this.m_density = new float[16];
	init();
    }
    
    public Cell clone() {
	Cell copy = new Cell(this.m_id);
	copy.m_numPars = this.m_numPars;
	copy.m_p = new Vec3[16];
	for(int i = 0; i < this.m_numPars; i++) {
	    copy.m_p[i] = this.m_p[i].clone();
	}
	copy.m_hv = new Vec3[16];
	for(int i = 0; i < this.m_numPars; i++) {
	    copy.m_hv[i] = this.m_hv[i].clone();
	}
	copy.m_v = new Vec3[16];
	for(int i = 0; i < this.m_numPars; i++) {
	    copy.m_v[i] = this.m_v[i].clone();
	}
	copy.m_a = new Vec3[16];
	for(int i = 0; i < this.m_numPars; i++) {
	    copy.m_a[i] = this.m_a[i].clone();
	}
	copy.m_density = new float[16];
	for(int i = 0; i < this.m_numPars; i++) {
	    copy.m_density[i] = this.m_density[i];
	}
	return copy;
    }
    
    private void init() {
	for(int i = 0; i < this.m_p.length; i++) {
	    this.m_p[i] = new Vec3();
	}
	for(int i = 0; i < this.m_hv.length; i++) {
	    this.m_hv[i] = new Vec3();
	}
	for(int i = 0; i < this.m_v.length; i++) {
	    this.m_v[i] = new Vec3();
	}
	for(int i = 0; i < this.m_a.length; i++) {
	    this.m_a[i] = new Vec3();
	}
	for(int i = 0; i < this.m_density.length; i++) {
	    this.m_density[i] = 0;
	}
    }
}