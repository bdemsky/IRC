public class FilterStatistic {
	int unknown;
	int spam;
	int ham;

	// -------------------------------------------------------
	
	public FilterStatistic() {
      this(0,0,0);
	}

	public FilterStatistic(int spam, int ham, int unknown) {
		this.spam = spam;
		this.ham = ham;
		this.unknown = unknown;
	}

	public int getChecked() {
      //TODO Change this formula
		return getSpam() + getHam() + getUnknown();
	}

	public int getHam() {
		return ham;
	}

	public int getSpam() {
		return spam;
	}

	public String getName() {
		return name;
	}

	public void setHam(int i) {
		ham = i;
	}

	public void setSpam(int i) {
		spam = i;
	}

	public int getUnknown() {
		return unknown;
	}

	public void setUnknown(int u) {
		unknown = u;
	}

	public void increaseSpam() {
		setSpam(getSpam() + 1);
	}

	public void increaseHam() {
		setHam(getHam() + 1);
	}

	public void increaseUnknown() {
		setUnknown(getUnknown() + 1);
	}

	public void setName(String name) {
		this.name = name;
	}
}
