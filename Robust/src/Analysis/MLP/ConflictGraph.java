package Analysis.MLP;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;
import java.util.Map.Entry;

import Analysis.OwnershipAnalysis.HeapRegionNode;
import IR.Flat.FlatMethod;
import IR.Flat.FlatSESEEnterNode;
import IR.Flat.TempDescriptor;

public class ConflictGraph {

	public Hashtable<String, ConflictNode> td2cn;

	public ConflictGraph() {
		td2cn = new Hashtable<String, ConflictNode>();
	}

	public String addStallNode(TempDescriptor td, FlatMethod fm,
			StallSite stallSite) {

		String stallNodeID = td + "_" + fm.getMethod().getSymbol();

		if (!td2cn.containsKey(stallNodeID)) {
			StallSiteNode newNode = new StallSiteNode(stallNodeID, td,
					stallSite);
			td2cn.put(stallNodeID, newNode);
			// it add new new stall node to conflict graph
			return stallNodeID;
		}
		// it doesn't add new stall node because stall node has already been
		// added.
		return null;
	}

	public StallSiteNode getStallNode(String stallNodeID) {
		ConflictNode node = td2cn.get(stallNodeID);
		if (node instanceof StallSiteNode) {
			return (StallSiteNode) node;
		} else {
			return null;
		}
	}

	public void addLiveInNode(TempDescriptor td, FlatSESEEnterNode fsen,
			Set<SESEEffectsKey> readEffectsSet,
			Set<SESEEffectsKey> writeEffectsSet) {

		String liveinNodeID = td + "_" + fsen.getIdentifier();

		LiveInNode newNode = new LiveInNode(liveinNodeID, td, readEffectsSet,
				writeEffectsSet);
		td2cn.put(liveinNodeID, newNode);

	}

	public void addWriteConflictEdge(StallSiteNode stallNode,
			LiveInNode liveInNode) {
		ConflictEdge newEdge = new ConflictEdge(stallNode, liveInNode,
				ConflictEdge.WRITE_CONFLICT);
		stallNode.addEdge(newEdge);
		liveInNode.addEdge(newEdge);
	}

	public HashSet<LiveInNode> getLiveInNodeSet() {
		HashSet<LiveInNode> resultSet = new HashSet<LiveInNode>();

		Set<String> keySet = td2cn.keySet();
		for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
			String key = (String) iterator.next();
			ConflictNode node = td2cn.get(key);

			if (node instanceof LiveInNode) {
				resultSet.add((LiveInNode) node);
			}
		}

		return resultSet;
	}

	public void writeGraph(String graphName) throws java.io.IOException {

		graphName = graphName.replaceAll("[\\W]", "");

		BufferedWriter bw = new BufferedWriter(new FileWriter(graphName
				+ ".dot"));
		bw.write("graph " + graphName + " {\n");

		HashSet<HeapRegionNode> visited = new HashSet<HeapRegionNode>();
		// then visit every heap region node
		Set<Entry<String, ConflictNode>> s = td2cn.entrySet();
		Iterator<Entry<String, ConflictNode>> i = s.iterator();
		
		HashSet<ConflictEdge> addedSet=new HashSet<ConflictEdge>();
		
		while (i.hasNext()) {
			Entry<String, ConflictNode> entry = i.next();
			ConflictNode node = entry.getValue();
			String attributes = "[";

			attributes += "label=\"ID" + node.getID() + "\\n";

			if (node instanceof StallSiteNode) {
				attributes += "STALL SITE" + "\\n" + "\"]";
			} else {
				attributes += "LIVE-IN" + "\\n" + "\"]";
			}
			bw.write(entry.getKey() + attributes + ";\n");

			HashSet<ConflictEdge> edgeSet = node.getEdgeSet();
			for (Iterator iterator = edgeSet.iterator(); iterator.hasNext();) {
				ConflictEdge conflictEdge = (ConflictEdge) iterator.next();

				ConflictNode u = conflictEdge.getVertexU();
				ConflictNode v = conflictEdge.getVertexV();
				
				if (conflictEdge.getType() == ConflictEdge.WRITE_CONFLICT) {
					if(!addedSet.contains(conflictEdge)){
						bw.write(" " + u.getID() + "--" + v.getID()
								+ "[label=\""
								+ conflictEdge.toGraphEdgeString()
								+ "\",decorate];\n");
						addedSet.add(conflictEdge);
					}
				}
			}
		}

		bw.write("  graphTitle[label=\"" + graphName + "\",shape=box];\n");

		bw.write("}\n");
		bw.close();

	}

}

class ConflictEdge {

	private ConflictNode u;
	private ConflictNode v;
	private int type;

	public static final int WRITE_CONFLICT = 0;
	public static final int REACH_CONFLICT = 1;

	public ConflictEdge(ConflictNode u, ConflictNode v, int type) {
		this.u = u;
		this.v = v;
		this.type = type;
	}

	public String toGraphEdgeString() {
		if (type == WRITE_CONFLICT) {
			return "W_CONFLICT";
		} else {
			return "R_CONFLICT";
		}
	}

	public ConflictNode getVertexU() {
		return u;
	}

	public ConflictNode getVertexV() {
		return v;
	}
	
	public int getType(){
		return type;
	}

}