package Analysis.Scheduling;

import java.util.Vector;
import java.lang.String;

public abstract class RuntimeSchedule {
    String rsAlgorithm; 
    
    public RuntimeSchedule(String rsAlgorithm) {
	super();
	this.rsAlgorithm = rsAlgorithm;
    }

    public abstract TaskSimulator schedule(Vector<TaskSimulator> tasks);
    
    public String algorithm() {
	return rsAlgorithm;
    }
}