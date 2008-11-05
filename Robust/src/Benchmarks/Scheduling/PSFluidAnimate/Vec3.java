public class Vec3 {
    public float m_x;
    public float m_y;
    public float m_z;

    public Vec3() {
    }
    
    public Vec3(float _x, float _y, float _z) {
	this.m_x = _x;
	this.m_y = _y;
	this.m_z = _z;
    }

    public float GetLengthSq() { 
	return this.m_x*this.m_x + this.m_y*this.m_y + this.m_z*this.m_z; 
    }
    
    public float GetLength() {
	return Math.sqrtf(GetLengthSq());
    }
    
    void Normalize() {
	this.m_x /= GetLength();
	this.m_y /= GetLength();
	this.m_z /= GetLength();
    }

    void add0(Vec3 v) { 
	this.m_x += v.m_x;  
	this.m_y += v.m_y; 
	this.m_z += v.m_z; 
    }
    
    void sub0(Vec3 v) { 
	this.m_x -= v.m_x;  
	this.m_y -= v.m_y; 
	this.m_z -= v.m_z; 
    }
    
    void mul0(float s) { 
	this.m_x *= s;  
	this.m_y *= s; 
	this.m_z *= s; 
    }
    
    void div0(float s) { 
	this.m_x /= s;  
	this.m_y /= s; 
	this.m_z /= s; 
    }

    Vec3 add1(Vec3 v) { 
	return new Vec3(this.m_x+v.m_x, this.m_y+v.m_y, this.m_z+v.m_z); 
    }
    
    Vec3 reverse1() { 
	return new Vec3(-this.m_x, -this.m_y, -this.m_z); 
    }
    
    Vec3 sub1(Vec3 v) {
	return new Vec3(this.m_x-v.m_x, this.m_y-v.m_y, this.m_z-v.m_z); 
    }
    
    Vec3 mul1(float s) {
	return new Vec3(this.m_x*s, this.m_y*s, this.m_z*s); 
    }
    
    Vec3 div1(float s) {
	return new Vec3(this.m_x/s, this.m_y/s, this.m_z/s); 
    }

    float mul2(Vec3 v) { 
	return this.m_x*v.m_x + this.m_y*v.m_y + this.m_z*v.m_z; 
    }
    
    Vec3 clone() {
	return new Vec3(this.m_x, this.m_y, this.m_z); 
    }
    
    boolean isLess(Vec3 v) {
	if((this.m_x > v.m_x) || (this.m_y > v.m_y) || (this.m_z > v.m_z)) {
	    return false;
	} else {
	    return true;
	}
    }
}