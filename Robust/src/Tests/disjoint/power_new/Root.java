import com.enea.jcarder.transactionalinterfaces.HashMap;

import dstm2.AtomicSuperClass;

/**
 * The root node of the power system optimization tree. The root node represents
 * the power plant.
 **/
final class Root {
	/**
	 * Total system power demand.
	 **/
	Demand D;
	
	double P,Q;
	/**
	 * Lagrange multiplier for the global real power equality constraint
	 **/
	double theta_R;
	/**
	 * Lagrange multiplier for the global reactive power equality constraint
	 **/
	double theta_I;
	/**
	 * The power demand on the previous iteration
	 **/
	Demand last;
	/**
	 * The real power equality constraint on the previous iteration
	 **/
	double last_theta_R;
	/**
	 * The reactive power equality constraint on the previous iteration
	 **/
	double last_theta_I;
	/**
	 * A representation of the customers that feed off of the power plant.
	 **/
	Lateral[] feeders;

	/**
	 * Value used to compute convergence
	 **/
	private static double ROOT_EPSILON;

	/**
	 * Domain of thetaR->P map is 0.65 to 1.00 [index*0.01+0.65]
	 **/
	private static double map_P[];

	private static double MIN_THETA_R;
	private static double PER_INDEX_R;
	private static double MAX_THETA_R;

	/**
	 * Domain of thetaI->Q map is 0.130 to 0.200 [index*0.002+0.130]
	 **/
	private static double map_Q[];

	private static double MIN_THETA_I;
	private static double PER_INDEX_I;
	private static double MAX_THETA_I;
	
	/**
	 * Create the tree used by the power system optimization benchmark. Each
	 * root contains <tt>nfeeders</tt> feeders which each contain
	 * <tt>nlaterals</tt> lateral nodes, which each contain <tt>nbranches</tt>
	 * branch nodes, which each contain <tt>nleafs</tt> leaf nodes.
	 * 
	 * @param nfeeders
	 *            the number of feeders off of the root
	 * @param nlaterals
	 *            the number of lateral nodes per feeder
	 * @param nbranches
	 *            the number of branches per lateral
	 * @param nleaves
	 *            the number of leaves per branch
	 **/
	Root(int nfeeders, int nlaterals, int nbranches, int nleaves) {

		theta_R = 0.8;
		theta_I = 0.16;
		ROOT_EPSILON = 0.00001;
		map_P = new double[36];

		map_P[0] = 8752.218091048;
		map_P[1] = 8446.106670416;
		map_P[2] = 8107.990680283;
		map_P[3] = 7776.191574285;
		map_P[4] = 7455.920518777;
		map_P[5] = 7146.602181352;
		map_P[6] = 6847.709026813;
		map_P[7] = 6558.734204024;
		map_P[8] = 6279.213382291;
		map_P[9] = 6008.702199986;
		map_P[10] = 5746.786181029;
		map_P[11] = 5493.078256495;
		map_P[12] = 5247.206333097;
		map_P[13] = 5008.828069358;
		map_P[14] = 4777.615815166;
		map_P[15] = 4553.258735900;
		map_P[16] = 4335.470002316;
		map_P[17] = 4123.971545694;
		map_P[18] = 3918.501939675;
		map_P[19] = 3718.817618538;
		map_P[20] = 3524.683625800;
		map_P[21] = 3335.876573044;
		map_P[22] = 3152.188635673;
		map_P[23] = 2973.421417103;
		map_P[24] = 2799.382330486;
		map_P[25] = 2629.892542617;
		map_P[26] = 2464.782829705;
		map_P[27] = 2303.889031418;
		map_P[28] = 2147.054385395;
		map_P[29] = 1994.132771399;
		map_P[30] = 1844.985347313;
		map_P[31] = 1699.475053321;
		map_P[32] = 1557.474019598;
		map_P[33] = 1418.860479043;
		map_P[34] = 1283.520126656;
		map_P[35] = 1151.338004216;

		/*
		 map_P = { 8752.218091048, 8446.106670416,
		 8107.990680283, 7776.191574285, 7455.920518777, 7146.602181352,
		 6847.709026813, 6558.734204024, 6279.213382291, 6008.702199986,
		 5746.786181029, 5493.078256495, 5247.206333097, 5008.828069358,
		 4777.615815166, 4553.258735900, 4335.470002316, 4123.971545694,
		 3918.501939675, 3718.817618538, 3524.683625800, 3335.876573044,
		 3152.188635673, 2973.421417103, 2799.382330486, 2629.892542617,
		 2464.782829705, 2303.889031418, 2147.054385395, 1994.132771399,
		 1844.985347313, 1699.475053321, 1557.474019598, 1418.860479043,
		 1283.520126656, 1151.338004216 };
		 */

		MIN_THETA_R = 0.65;
		PER_INDEX_R = 0.01;
		MAX_THETA_R = 0.995;

		map_Q = new double[36];
		map_Q[0] = 1768.846590190;
		map_Q[1] = 1706.229490046;
		map_Q[2] = 1637.253873079;
		map_Q[3] = 1569.637451623;
		map_Q[4] = 1504.419525242;
		map_Q[5] = 1441.477913810;
		map_Q[6] = 1380.700660446;
		map_Q[7] = 1321.980440476;
		map_Q[8] = 1265.218982201;
		map_Q[9] = 1210.322424636;
		map_Q[10] = 1157.203306183;
		map_Q[11] = 1105.780028163;
		map_Q[12] = 1055.974296746;
		map_Q[13] = 1007.714103979;
		map_Q[14] = 960.930643875;
		map_Q[15] = 915.558722782;
		map_Q[16] = 871.538200178;
		map_Q[17] = 828.810882006;
		map_Q[18] = 787.322098340;
		map_Q[19] = 747.020941334;
		map_Q[20] = 707.858376214;
		map_Q[21] = 669.787829741;
		map_Q[22] = 632.765987756;
		map_Q[23] = 596.751545633;
		map_Q[24] = 561.704466609;
		map_Q[25] = 527.587580585;
		map_Q[26] = 494.365739051;
		map_Q[27] = 462.004890691;
		map_Q[28] = 430.472546686;
		map_Q[29] = 399.738429196;
		map_Q[30] = 369.773787595;
		map_Q[31] = 340.550287137;
		map_Q[32] = 312.041496095;
		map_Q[33] = 284.222260660;
		map_Q[34] = 257.068973074;
		map_Q[35] = 230.557938283;

		/*
		 * map_Q = { 1768.846590190, 1706.229490046, 1637.253873079,
		 * 1569.637451623, 1504.419525242, 1441.477913810, 1380.700660446,
		 * 1321.980440476, 1265.218982201, 1210.322424636, 1157.203306183,
		 * 1105.780028163, 1055.974296746, 1007.714103979, 960.930643875,
		 * 915.558722782, 871.538200178, 828.810882006, 787.322098340,
		 * 747.020941334, 707.858376214, 669.787829741, 632.765987756,
		 * 596.751545633, 561.704466609, 527.587580585, 494.365739051,
		 * 462.004890691, 430.472546686, 399.738429196, 369.773787595,
		 * 340.550287137, 312.041496095, 284.222260660, 257.068973074,
		 * 230.557938283 };
		 */

		MIN_THETA_I = 0.13;
		PER_INDEX_I = 0.002;
		MAX_THETA_I = 0.199;

		D = new Demand(0.0,0.0);
		last = new Demand(0.0,0.0);

		feeders = new Lateral[nfeeders];
		for (int i = 0; i < nfeeders; i++) {
			feeders[i] = new Lateral(nlaterals, nbranches, nleaves);
		}
	}

	/**
	 * Return true if we've reached a convergence.
	 * 
	 * @return true if we've finished.
	 **/
	boolean reachedLimit() {
		boolean rtr= (Math.abs(D.P / 10000.0 - theta_R) < ROOT_EPSILON && Math
				.abs(D.Q / 10000.0 - theta_I) < ROOT_EPSILON);
		return rtr;
	}

	/**
	 * Pass prices down and compute demand for the power system.
	 **/
	void compute() {
		D.P = 0.0;
		D.Q = 0.0;
		
		double pp=0.0;
		double qq=0.0;
		
		for (int i = 0; i < feeders.length; i++) {
			Lateral lateral=feeders[i];
			sese parallel{
				DemandResult result=compute(lateral);
				double p=result.P;
				double q=result.Q;
//				D.increment(feeders[i].compute(theta_R, theta_I, theta_R, theta_I));
			}
			sese serial{
				pp+=p;
				qq+=q;
			}
		} // end of for
		D.P=pp;
		D.Q=qq;
	}
	
	DemandResult compute(Lateral lateral){
		Demand demand= lateral.compute(theta_R, theta_I, theta_R, theta_I);
		DemandResult result=new DemandResult(demand.P,demand.Q);
		return result;
	}

	/**
	 * Set up the values for another pass of the algorithm.
	 **/
	void nextIter(boolean verbose, double new_theta_R, double new_theta_I) {
		last.P = D.P;
		last.Q = D.Q;
		last_theta_R = theta_R;
		last_theta_I = theta_I;
		theta_R = new_theta_R;
		theta_I = new_theta_I;
	}

	/**
	 * Set up the values for another pass of the algorithm.
	 * 
	 * @param verbose
	 *            is set to true to print the values of the system.
	 **/
	void nextIter(boolean verbose) {
		int i = (int) ((theta_R - MIN_THETA_R) / PER_INDEX_R);
		if (i < 0)
			i = 0;
		if (i > 35)
			i = 35;
		double d_theta_R = -(theta_R - D.P / 10000.0)
				/ (1 - (map_P[i + 1] - map_P[i]) / (PER_INDEX_R * 10000.0));

		i = (int) ((theta_I - MIN_THETA_I) / PER_INDEX_I);
		if (i < 0)
			i = 0;
		if (i > 35)
			i = 35;
		double d_theta_I = -(theta_I - D.Q / 10000.0)
				/ (1 - (map_Q[i + 1] - map_Q[i]) / (PER_INDEX_I * 10000.0));

		if (verbose) {
			System.out.println("D TR-" + d_theta_R + ", TI=" + d_theta_I);
		}

		last.P = D.P;
		last.Q = D.Q;
		last_theta_R = theta_R;
		last_theta_I = theta_I;
		theta_R += d_theta_R;
		theta_I += d_theta_I;
	}

	/**
	 * Create a string representation of the power plant.
	 **/
	public String toString() {
		return "TR=" + theta_R + ", TI=" + theta_I + ", P0=" + D.P + ", Q0="
				+ D.Q;
	}
}

class DemandResult{
	
		double P;
		double Q;
		
		public DemandResult(double P, double Q){
			this.P=P;
			this.Q=Q;
		}
		
}
