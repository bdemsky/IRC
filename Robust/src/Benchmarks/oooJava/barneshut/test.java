public class Test {



    public Test(){
	
    }
    
    static public void main( String[] args ) {
	int nbodies;
	int ntimesteps;
	double dtime;
	double eps;
	double tol;
	
	FileInputStream inputFile = new FileInputStream("./inputs/BarnesHut.in");
	
	nbodies = Integer.parseInt(inputFile.readLine());
	ntimesteps = Integer.parseInt(inputFile.readLine());
	dtime = Double.parseDouble(inputFile.readLine());
	eps = Double.parseDouble(inputFile.readLine());
	tol =Double.parseDouble(inputFile.readLine());

	System.out.println("nbodies="+nbodies);
	System.out.println("ntimesteps="+ntimesteps);
	System.out.println("dtime="+dtime);
	System.out.println("eps="+eps);
	System.out.println("tol="+tol);
	
	String line=null;
	line=inputFile.readLine();

	StringTokenizer token=new StringTokenizer(line);
	double mass=Double.parseDouble(token.nextToken());
	double posx=Double.parseDouble(token.nextToken());
	double posy=Double.parseDouble(token.nextToken());
	double posz=Double.parseDouble(token.nextToken());
	double vx=Double.parseDouble(token.nextToken());
	double vy=Double.parseDouble(token.nextToken());
	double vz=Double.parseDouble(token.nextToken());
	
	System.out.println("mass="+mass);
	System.out.println("posx="+posx);
	System.out.println("posy="+posy);
	System.out.println("posz="+posz);
	System.out.println("vx="+vx);
	System.out.println("vy="+vy);
	System.out.println("vz="+vz);
	
    }
    
}