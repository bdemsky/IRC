package Analysis.MLP;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;
import java.util.Map.Entry;

import Analysis.OwnershipAnalysis.HeapRegionNode;
import Analysis.OwnershipAnalysis.OwnershipGraph;
import Analysis.OwnershipAnalysis.ReachabilitySet;
import Analysis.OwnershipAnalysis.TokenTuple;
import IR.Flat.FlatMethod;
import IR.Flat.FlatSESEEnterNode;
import IR.Flat.TempDescriptor;

public class ConflictGraph {

	static private int uniqueCliqueIDcount = 100;

	public Hashtable<String, ConflictNode> id2cn;
	private OwnershipGraph og;

	public ConflictGraph(OwnershipGraph og) {
		id2cn = new Hashtable<String, ConflictNode>();
		this.og = og;
	}

	public boolean hasConflictEdge() {

		Set<String> keySet = id2cn.keySet();
		for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
			String key = (String) iterator.next();
			ConflictNode node = id2cn.get(key);
			if (node.getEdgeSet().size() > 0) {
				return true;
			}
		}
		return false;
	}

	public void analyzeConflicts() {

		Set<String> keySet = id2cn.keySet();
		Set<String> analyzedIDSet = new HashSet<String>();

		for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
			String nodeID = (String) iterator.next();
			ConflictNode node = id2cn.get(nodeID);
			analyzePossibleConflicts(analyzedIDSet, node);
		}
	}

	private boolean compareHRNSet(Set<HeapRegionNode> setA,
			Set<HeapRegionNode> setB) {
		boolean found = false;
		for (Iterator iterator = setA.iterator(); iterator.hasNext();) {
			HeapRegionNode heapRegionNode = (HeapRegionNode) iterator.next();
			String gID = heapRegionNode.getGloballyUniqueIdentifier();
			for (Iterator iterator2 = setB.iterator(); iterator2.hasNext();) {
				HeapRegionNode heapRegionNode2 = (HeapRegionNode) iterator2
						.next();
				if (heapRegionNode2.getGloballyUniqueIdentifier().equals(gID)) {
					found = true;
				}
			}
		}
		if (!found) {
			return false;
		}
		return true;
	}

	public String addStallNode(TempDescriptor td, FlatMethod fm,
			StallSite stallSite, Set<Set> reachabilitySet) {

		String stallNodeID = td + "_" + fm.getMethod().getSymbol();

		if (!id2cn.containsKey(stallNodeID)) {
			StallSiteNode newNode = new StallSiteNode(stallNodeID, td,
					stallSite, reachabilitySet);
			id2cn.put(stallNodeID, newNode);
			// it add new new stall node to conflict graph
			return stallNodeID;
		}
		// it doesn't add new stall node because stall node has already been
		// added.
		return null;
	}

	public StallSiteNode getStallNode(String stallNodeID) {
		ConflictNode node = id2cn.get(stallNodeID);
		if (node instanceof StallSiteNode) {
			return (StallSiteNode) node;
		} else {
			return null;
		}
	}

	public void addLiveInNode(TempDescriptor td, Set<HeapRegionNode> hrnSet,
			FlatSESEEnterNode fsen, Set<SESEEffectsKey> readEffectsSet,
			Set<SESEEffectsKey> writeEffectsSet,
			Set<SESEEffectsKey> strongUpdateSet, Set<Set> reachabilitySet) {

		String liveinNodeID = td + "_" + fsen.getIdentifier();

		LiveInNode newNode = new LiveInNode(liveinNodeID, td, hrnSet,
				readEffectsSet, writeEffectsSet, strongUpdateSet,
				reachabilitySet, fsen.getIdentifier());
		id2cn.put(liveinNodeID, newNode);

	}

	public void addConflictEdge(int type, ConflictNode nodeU, ConflictNode nodeV) {

		// if there are two edges between the same node pair, coarse has a
		// priority
		HashSet<ConflictEdge> set = nodeU.getEdgeSet();
		ConflictEdge toBeRemoved = null;
		for (Iterator iterator = set.iterator(); iterator.hasNext();) {
			ConflictEdge conflictEdge = (ConflictEdge) iterator.next();

			if ((conflictEdge.getVertexU().equals(nodeU) && conflictEdge
					.getVertexV().equals(nodeV))
					|| (conflictEdge.getVertexU().equals(nodeV) && conflictEdge
							.getVertexV().equals(nodeU))) {
				if (conflictEdge.getType() == ConflictEdge.FINE_GRAIN_EDGE
						&& type == ConflictEdge.COARSE_GRAIN_EDGE) {
					toBeRemoved = conflictEdge;
					break;
				} else if (conflictEdge.getType() == ConflictEdge.COARSE_GRAIN_EDGE
						&& type == ConflictEdge.FINE_GRAIN_EDGE) {
					// ignore
					return;
				}
			}
		}

		if (toBeRemoved != null) {
			nodeU.getEdgeSet().remove(toBeRemoved);
			nodeV.getEdgeSet().remove(toBeRemoved);
		}

		ConflictEdge newEdge = new ConflictEdge(nodeU, nodeV, type);
		nodeU.addEdge(newEdge);
		nodeV.addEdge(newEdge);

	}

	public Set<WaitingElement> getStallSiteWaitingElementSet(
			ParentChildConflictsMap conflictsMap, HashSet<SESELock> seseLockSet) {

		HashSet<WaitingElement> waitingElementSet = new HashSet<WaitingElement>();
		Set<Entry<String, ConflictNode>> s = id2cn.entrySet();
		Collection<StallSite> stallSites = conflictsMap.getStallMap().values();

		for (Iterator iterator = stallSites.iterator(); iterator.hasNext();) {

			StallSite stallSite = (StallSite) iterator.next();
			Iterator<Entry<String, ConflictNode>> i = s.iterator();
			while (i.hasNext()) {
				Entry<String, ConflictNode> entry = i.next();
				ConflictNode node = entry.getValue();

				if (node instanceof StallSiteNode) {
					StallSiteNode stallSiteNode = (StallSiteNode) node;
					if (stallSiteNode.getStallSite().equals(stallSite)) {
						HashSet<ConflictEdge> edgeSet = stallSiteNode
								.getEdgeSet();
						for (Iterator iter2 = edgeSet.iterator(); iter2
								.hasNext();) {
							ConflictEdge conflictEdge = (ConflictEdge) iter2
									.next();

							for (Iterator<SESELock> seseLockIter = seseLockSet
									.iterator(); seseLockIter.hasNext();) {
								SESELock seseLock = seseLockIter.next();
								if (seseLock
										.containsConflictNode(stallSiteNode)
										&& seseLock
												.containsConflictEdge(conflictEdge)) {
									WaitingElement newElement = new WaitingElement();
									newElement.setQueueID(seseLock.getID());
									if (isFineElement(newElement.getStatus())) {
										newElement
												.setDynID(node
														.getTempDescriptor()
														.toString());
									}
									newElement.setStatus(seseLock
											.getNodeType(stallSiteNode));
									waitingElementSet.add(newElement);
								}
							}
						}
					}
				}
			}

		}

		return waitingElementSet;
	}

	private Set<Integer> getConnectedConflictNode(ConflictEdge conflictEdge,
			int seseID) {

		HashSet<Integer> nodeIDSet = new HashSet<Integer>();

		if (conflictEdge.getVertexU() instanceof LiveInNode) {
			LiveInNode lin = (LiveInNode) conflictEdge.getVertexU();
			if (lin.getSESEIdentifier() != seseID) {
				nodeIDSet.add(new Integer(lin.getSESEIdentifier()));
			}
		} else {
			// it is stall site
			nodeIDSet.add(new Integer(-1));
		}
		if (conflictEdge.getVertexV() instanceof LiveInNode) {
			LiveInNode lin = (LiveInNode) conflictEdge.getVertexV();
			if (lin.getSESEIdentifier() != seseID) {
				nodeIDSet.add(new Integer(lin.getSESEIdentifier()));
			}
		} else {
			// it is stall site
			nodeIDSet.add(new Integer(-1));
		}

		// self-edge case
		if (conflictEdge.getVertexU() instanceof LiveInNode
				&& conflictEdge.getVertexV() instanceof LiveInNode) {
			if (((LiveInNode) conflictEdge.getVertexU()).getSESEIdentifier() == seseID
					&& ((LiveInNode) conflictEdge.getVertexV())
							.getSESEIdentifier() == seseID) {
				nodeIDSet.add(seseID);
			}
		}

		return nodeIDSet;
	}

	public Set<Integer> getConnectedConflictNodeSet(int seseID) {

		HashSet<Integer> nodeIDSet = new HashSet<Integer>();

		Set<Entry<String, ConflictNode>> s = id2cn.entrySet();
		Iterator<Entry<String, ConflictNode>> i = s.iterator();

		while (i.hasNext()) {
			Entry<String, ConflictNode> entry = i.next();
			ConflictNode node = entry.getValue();

			if (node instanceof LiveInNode) {
				LiveInNode liveInNode = (LiveInNode) node;
				if (liveInNode.getSESEIdentifier() == seseID) {
					HashSet<ConflictEdge> edgeSet = liveInNode.getEdgeSet();
					for (Iterator iterator = edgeSet.iterator(); iterator
							.hasNext();) {
						ConflictEdge conflictEdge = (ConflictEdge) iterator
								.next();
						//
						nodeIDSet.addAll(getConnectedConflictNode(conflictEdge,
								seseID));
						//
					}
				}
			}
		}

		return nodeIDSet;

	}

	public SESEWaitingQueue getWaitingElementSetBySESEID(int seseID,
			HashSet<SESELock> seseLockSet) {
		HashSet<WaitingElement> waitingElementSet = new HashSet<WaitingElement>();

		Set<Entry<String, ConflictNode>> s = id2cn.entrySet();
		Iterator<Entry<String, ConflictNode>> i = s.iterator();

		while (i.hasNext()) {
			Entry<String, ConflictNode> entry = i.next();
			ConflictNode node = entry.getValue();

			if (node instanceof LiveInNode) {
				LiveInNode liveInNode = (LiveInNode) node;
				if (liveInNode.getSESEIdentifier() == seseID) {

					HashSet<ConflictEdge> edgeSet = liveInNode.getEdgeSet();

					for (Iterator iterator = edgeSet.iterator(); iterator
							.hasNext();) {
						ConflictEdge conflictEdge = (ConflictEdge) iterator
								.next();

						for (Iterator<SESELock> seseLockIter = seseLockSet
								.iterator(); seseLockIter.hasNext();) {
							SESELock seseLock = seseLockIter.next();
							if (seseLock.containsConflictNode(liveInNode)
									&& seseLock
											.containsConflictEdge(conflictEdge)) {
								WaitingElement newElement = new WaitingElement();
								newElement.setQueueID(seseLock.getID());
								newElement.setStatus(seseLock
										.getNodeType(liveInNode));
								if (isFineElement(newElement.getStatus())) {
									newElement.setDynID(node
											.getTempDescriptor().toString());
									newElement.setTempDesc(node.getTempDescriptor());
								}
								if (!waitingElementSet.contains(newElement)) {
									waitingElementSet.add(newElement);
								}

							}
						}
					}

				}
			}

		}
		
		//handle the case that multiple enqueues by an SESE for different live-in into the same queue
		return refineQueue(waitingElementSet);
//		return waitingElementSet;
		
	}
	
	public SESEWaitingQueue refineQueue(Set<WaitingElement> waitingElementSet) {

		Set<WaitingElement> refinedSet=new HashSet<WaitingElement>();
		HashMap<Integer, Set<WaitingElement>> map = new HashMap<Integer, Set<WaitingElement>>();
		SESEWaitingQueue seseDS=new SESEWaitingQueue();

		for (Iterator iterator = waitingElementSet.iterator(); iterator
				.hasNext();) {
			WaitingElement waitingElement = (WaitingElement) iterator.next();
			Set<WaitingElement> set=map.get(new Integer(waitingElement.getQueueID()));
			if(set==null){
				set=new HashSet<WaitingElement>();
			}
			set.add(waitingElement);
			map.put(new Integer(waitingElement.getQueueID()), set);
		}
		
		Set<Integer> keySet=map.keySet();
		for (Iterator iterator = keySet.iterator(); iterator.hasNext();) {
			Integer queueID = (Integer) iterator.next();
			Set<WaitingElement> queueWEset=map.get(queueID);
			refineQueue(queueID.intValue(),queueWEset,seseDS);			
		}
		
		return seseDS;
	}
	
	private void refineQueue(int queueID,
			Set<WaitingElement> waitingElementSet, SESEWaitingQueue seseDS) {

		if (waitingElementSet.size() > 1) {
			//only consider there is more than one element submitted by same SESE
			Set<WaitingElement> refinedSet = new HashSet<WaitingElement>();

			int numCoarse = 0;
			int numRead = 0;
			int numWrite = 0;
			int total=waitingElementSet.size();
			WaitingElement SCCelement = null;
			WaitingElement coarseElement = null;

			for (Iterator iterator = waitingElementSet.iterator(); iterator
					.hasNext();) {
				WaitingElement waitingElement = (WaitingElement) iterator
						.next();
				if (waitingElement.getStatus() == ConflictNode.FINE_READ) {
					numRead++;
				} else if (waitingElement.getStatus() == ConflictNode.FINE_WRITE) {
					numWrite++;
				} else if (waitingElement.getStatus() == ConflictNode.COARSE) {
					numCoarse++;
					coarseElement = waitingElement;
				} else if (waitingElement.getStatus() == ConflictNode.SCC) {
					SCCelement = waitingElement;
				} 
			}

			if (SCCelement != null) {
				// if there is at lease one SCC element, just enqueue SCC and
				// ignore others.
				refinedSet.add(SCCelement);
			} else if (numCoarse == 1 && (numRead + numWrite == total)) {
				// if one is a coarse, the othere are reads/write, enqueue SCC.
				WaitingElement we = new WaitingElement();
				we.setQueueID(queueID);
				we.setStatus(ConflictNode.SCC);
				refinedSet.add(we);
			} else if (numCoarse == total) {
				// if there are multiple coarses, enqueue just one coarse.
				refinedSet.add(coarseElement);
			} else if(numWrite==total || (numRead+numWrite)==total){
				// code generator is going to handle the case for multiple writes & read/writes.
				seseDS.setType(queueID, SESEWaitingQueue.EXCEPTION);
				refinedSet.addAll(waitingElementSet);
			} else{
				// otherwise, enqueue everything.
				refinedSet.addAll(waitingElementSet);
			}
			seseDS.setWaitingElementSet(queueID, refinedSet);
		} else {
			seseDS.setWaitingElementSet(queueID, waitingElementSet);
		}
		
	}

	public boolean isFineElement(int type) {
		if (type == ConflictNode.FINE_READ || type == ConflictNode.FINE_WRITE
				|| type == ConflictNode.PARENT_READ
				|| type == ConflictNode.PARENT_WRITE) {
			return true;
		} else {
			return false;
		}
	}

	public HashSet<ConflictEdge> getEdgeSet() {

		HashSet<ConflictEdge> returnSet = new HashSet<ConflictEdge>();

		Collection<ConflictNode> nodes = id2cn.values();
		for (Iterator iterator = nodes.iterator(); iterator.hasNext();) {
			ConflictNode conflictNode = (ConflictNode) iterator.next();
			returnSet.addAll(conflictNode.getEdgeSet());
		}

		return returnSet;
	}

	public void writeGraph(String graphName, boolean filter)
			throws java.io.IOException {

		graphName = graphName.replaceAll("[\\W]", "");

		BufferedWriter bw = new BufferedWriter(new FileWriter(graphName
				+ ".dot"));
		bw.write("graph " + graphName + " {\n");

		HashSet<HeapRegionNode> visited = new HashSet<HeapRegionNode>();
		// then visit every heap region node
		Set<Entry<String, ConflictNode>> s = id2cn.entrySet();
		Iterator<Entry<String, ConflictNode>> i = s.iterator();

		HashSet<ConflictEdge> addedSet = new HashSet<ConflictEdge>();

		while (i.hasNext()) {
			Entry<String, ConflictNode> entry = i.next();
			ConflictNode node = entry.getValue();

			if (filter) {
				if (node.getID().startsWith("___dst")
						|| node.getID().startsWith("___srctmp")
						|| node.getID().startsWith("___neverused")
						|| node.getID().startsWith("___temp")) {

					continue;
				}
			}

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

				if (filter) {
					String uID = u.getID();
					String vID = v.getID();
					if (uID.startsWith("___dst") || uID.startsWith("___srctmp")
							|| uID.startsWith("___neverused")
							|| uID.startsWith("___temp")
							|| vID.startsWith("___dst")
							|| vID.startsWith("___srctmp")
							|| vID.startsWith("___neverused")
							|| vID.startsWith("___temp")) {
						continue;
					}
				}

				if (!addedSet.contains(conflictEdge)) {
					bw.write(" " + u.getID() + "--" + v.getID() + "[label="
							+ conflictEdge.toGraphEdgeString()
							+ ",decorate];\n");
					addedSet.add(conflictEdge);
				}

			}
		}

		bw.write("  graphTitle[label=\"" + graphName + "\",shape=box];\n");

		bw.write("}\n");
		bw.close();

	}
	
	private int calculateConflictType(StallSiteNode nodeA, LiveInNode nodeB) {

		StallSite stallSite = nodeA.getStallSite();
		Set<SESEEffectsKey> writeEffectsSet = nodeB.getWriteEffectsSet();
		Set<SESEEffectsKey> readEffectsSet = nodeB.getReadEffectsSet();

		int conflictType = 0;

		if (writeEffectsSet != null) {
			Iterator<SESEEffectsKey> writeIter = writeEffectsSet.iterator();
			while (writeIter.hasNext()) {
				SESEEffectsKey seseEffectsKey = (SESEEffectsKey) writeIter
						.next();
				String writeHeapRegionID = seseEffectsKey.getHRNUniqueId();
				String writeFieldName = seseEffectsKey.getFieldDescriptor();

				HashSet<HeapRegionNode> stallSiteHRNSet = nodeA.getHRNSet();
				for (Iterator iterator = stallSiteHRNSet.iterator(); iterator
						.hasNext();) {
					HeapRegionNode stallHRN = (HeapRegionNode) iterator.next();
					if (stallHRN.getGloballyUniqueIdentifier().equals(
							writeHeapRegionID)) {
						// check whether there are read or write effects of
						// stall sites
						HashSet<Effect> effectSet = stallSite.getEffectSet();
						for (Iterator iterator2 = effectSet.iterator(); iterator2
								.hasNext();) {
							Effect effect = (Effect) iterator2.next();
							String stallEffectfieldName = effect.getField();

							if (stallEffectfieldName.equals(writeFieldName)) {
								int newType = determineConflictType(nodeA,
										effect, nodeB, seseEffectsKey);
								if (newType > conflictType) {
									// coarse-grain conflict overrides
									// fine-grain conflict
									conflictType = newType;
								}
							}
						}
					}
				}

			}
		}

		if (readEffectsSet != null) {
			Iterator<SESEEffectsKey> readIter = readEffectsSet.iterator();
			while (readIter.hasNext()) {
				SESEEffectsKey seseEffectsKey = (SESEEffectsKey) readIter
						.next();
				String readHeapRegionID = seseEffectsKey.getHRNUniqueId();
				String readFieldName = seseEffectsKey.getFieldDescriptor();

				HashSet<HeapRegionNode> stallSiteHRNSet = nodeA.getHRNSet();
				for (Iterator iterator = stallSiteHRNSet.iterator(); iterator
						.hasNext();) {
					HeapRegionNode stallHRN = (HeapRegionNode) iterator.next();
					if (stallHRN.getGloballyUniqueIdentifier().equals(
							readHeapRegionID)) {

						HashSet<Effect> effectSet = stallSite.getEffectSet();
						for (Iterator iterator2 = effectSet.iterator(); iterator2
								.hasNext();) {
							Effect effect = (Effect) iterator2.next();
							String stallEffectfieldName = effect.getField();

							if (effect.getEffectType().equals(
									StallSite.WRITE_EFFECT)) {
								if (stallEffectfieldName.equals(readFieldName)) {
									int newType = determineConflictType(nodeA,
											effect, nodeB, seseEffectsKey);
									if (newType > conflictType) {
										// coarse-grain conflict overrides
										// fine-grain conflict
										conflictType = newType;
									}
								}
							}
						}
					}
				}
			}
		}
//System.out.println("%%%%%%%%%%%%%% RETURN conflictType="+conflictType);
		return conflictType;
	}
	
	private int calculateConflictType(LiveInNode nodeA, LiveInNode nodeB) {

		Set<SESEEffectsKey> readEffectsSetA = nodeA.getReadEffectsSet();
		Set<SESEEffectsKey> writeEffectsSetA = nodeA.getWriteEffectsSet();
		Set<SESEEffectsKey> strongUpdateSetA = nodeA.getStrongUpdateSet();

		Set<SESEEffectsKey> readEffectsSetB = nodeB.getReadEffectsSet();
		Set<SESEEffectsKey> writeEffectsSetB = nodeB.getWriteEffectsSet();
		Set<SESEEffectsKey> strongUpdateSetB = nodeB.getStrongUpdateSet();

		int conflictType = 0;
		
		// if node A has write effects on reading/writing regions of node B
		if (writeEffectsSetA != null) {
			Iterator<SESEEffectsKey> writeIterA = writeEffectsSetA.iterator();
			while (writeIterA.hasNext()) {
				SESEEffectsKey seseEffectsKey = (SESEEffectsKey) writeIterA
						.next();
				String writeHeapRegionID = seseEffectsKey.getHRNUniqueId();
				String writeFieldName = seseEffectsKey.getFieldDescriptor();

				if (readEffectsSetB != null) {

					Iterator<SESEEffectsKey> readIterB = readEffectsSetB
							.iterator();
					while (readIterB.hasNext()) {
						SESEEffectsKey readingEffect = (SESEEffectsKey) readIterB
								.next();

						if (readingEffect.getHRNUniqueId().equals(
								writeHeapRegionID)
								&& readingEffect.getFieldDescriptor().equals(
										writeFieldName)) {
							int newType = determineConflictType(nodeA,
									seseEffectsKey, nodeB, readingEffect);
							if (newType > conflictType) {
								// coarse-grain conflict overrides fine-grain
								// conflict
								conflictType = newType;
							}
						}
					}

				}

				if (writeEffectsSetB != null) {
					Iterator<SESEEffectsKey> writeIterB = writeEffectsSetB
							.iterator();
					while (writeIterB.hasNext()) {
						SESEEffectsKey writingEffect = (SESEEffectsKey) writeIterB
								.next();

						if (writingEffect.getHRNUniqueId().equals(
								writeHeapRegionID)
								&& writingEffect.getFieldDescriptor().equals(
										writeFieldName)) {
							int newType = determineConflictType(nodeA,
									seseEffectsKey, nodeB, writingEffect);
							if (newType > conflictType) {
								// coarse-grain conflict overrides fine-grain
								// conflict
								conflictType = newType;
							}
						}

					}
				}

			}
		}

		// if node B has write effects on reading regions of node A
		if (writeEffectsSetB != null) {
			Iterator<SESEEffectsKey> writeIterB = writeEffectsSetB.iterator();
			while (writeIterB.hasNext()) {
				SESEEffectsKey seseEffectsKey = (SESEEffectsKey) writeIterB
						.next();

				// if (!hasStrongUpdate(seseEffectsKey, strongUpdateSetB)) {

				String writeHeapRegionID = seseEffectsKey.getHRNUniqueId();
				String writeFieldName = seseEffectsKey.getFieldDescriptor();

				if (readEffectsSetA != null) {
					Iterator<SESEEffectsKey> readIterA = readEffectsSetA
							.iterator();
					while (readIterA.hasNext()) {
						SESEEffectsKey readingEffect = (SESEEffectsKey) readIterA
								.next();

						if (readingEffect.getHRNUniqueId().equals(
								writeHeapRegionID)
								&& readingEffect.getFieldDescriptor().equals(
										writeFieldName)) {
							int newType = determineConflictType(nodeA,
									readingEffect, nodeB, seseEffectsKey);
							if (newType > conflictType) {
								// coarse-grain conflict overrides fine-grain
								// conflict
								conflictType = newType;
							}
						}

					}
				}

				if (writeEffectsSetA != null) {
					Iterator<SESEEffectsKey> writeIterA = writeEffectsSetA
							.iterator();
					while (writeIterA.hasNext()) {
						SESEEffectsKey writingEffect = (SESEEffectsKey) writeIterA
								.next();

						if (writingEffect.getHRNUniqueId().equals(
								writeHeapRegionID)
								&& writingEffect.getFieldDescriptor().equals(
										writeFieldName)) {
							int newType = determineConflictType(nodeA,
									writingEffect, nodeB, seseEffectsKey);
							if (newType > conflictType) {
								// coarse-grain conflict overrides fine-grain
								// conflict
								conflictType = newType;
							}
						}

					}
				}

			}
		}
		return conflictType;
	}

	private Set<HeapRegionNode> getSameHeapRoot(Set<HeapRegionNode> setA,
			Set<HeapRegionNode> setB) {

		Set<HeapRegionNode> retSet = new HashSet<HeapRegionNode>();

		if (compareHRNSet(setA, setB)) {
			for (Iterator iterator = setA.iterator(); iterator.hasNext();) {
				HeapRegionNode heapRegionNode = (HeapRegionNode) iterator
						.next();
				String gID = heapRegionNode.getGloballyUniqueIdentifier();
				for (Iterator iterator2 = setB.iterator(); iterator2.hasNext();) {
					HeapRegionNode heapRegionNode2 = (HeapRegionNode) iterator2
							.next();
					if (heapRegionNode2.getGloballyUniqueIdentifier().equals(
							gID)) {
						retSet.add(heapRegionNode2);
					}
				}
			}
		}

		return retSet;

	}
	
	private boolean isReachableFrom(HeapRegionNode root1, HeapRegionNode root2,
			ReachabilitySet rset) {
		
		boolean reachable=false;

		TokenTuple h1 = new TokenTuple(root1.getID(), !root1.isSingleObject(),
				TokenTuple.ARITY_ONE).makeCanonical();

		TokenTuple h1plus = new TokenTuple(root1.getID(), !root1
				.isSingleObject(), TokenTuple.ARITY_ONEORMORE).makeCanonical();

		TokenTuple h1star = new TokenTuple(root1.getID(), !root1
				.isSingleObject(), TokenTuple.ARITY_ZEROORMORE).makeCanonical();

		TokenTuple h2 = new TokenTuple(root2.getID(), !root2.isSingleObject(),
				TokenTuple.ARITY_ONE).makeCanonical();

		TokenTuple h2plus = new TokenTuple(root2.getID(), !root2
				.isSingleObject(), TokenTuple.ARITY_ONEORMORE).makeCanonical();

		TokenTuple h2star = new TokenTuple(root2.getID(), !root2
				.isSingleObject(), TokenTuple.ARITY_ZEROORMORE).makeCanonical();

		 // only do this one if they are different tokens
	    if( h1 != h2 &&
	    		rset.containsTupleSetWithBoth(h1,     h2) ) {
	    	reachable = true;
	    }
	    if( rset.containsTupleSetWithBoth(h1plus, h2) ) {
	    	reachable = true;
	    }
	    if( rset.containsTupleSetWithBoth(h1star, h2) ) {
	    	reachable = true;
	    }
	    if( rset.containsTupleSetWithBoth(h1,     h2plus) ) {
	    	reachable = true;
	    }
	    if( rset.containsTupleSetWithBoth(h1plus, h2plus) ) {
	    	reachable = true;
	    }
	    if( rset.containsTupleSetWithBoth(h1star, h2plus) ) {
	    	reachable = true;
	    }
	    if( rset.containsTupleSetWithBoth(h1,     h2star) ) {
	    	reachable = true;
	    }
	    if( rset.containsTupleSetWithBoth(h1plus, h2star) ) {
	    	reachable = true;
	    }
	    if( rset.containsTupleSetWithBoth(h1star, h2star) ) {
	    	reachable = true;
	    }
		
		return reachable;

	}
	
	private int determineConflictType(StallSiteNode stallSiteNodeA,
			Effect effect, LiveInNode liveInNodeB,
			SESEEffectsKey effectB) {
		
		Set<HeapRegionNode> liveInHrnSetA = stallSiteNodeA.getStallSite().getHRNSet();
		Set<HeapRegionNode> liveInHrnSetB = liveInNodeB.getHRNSet();
		
		// check whether alloc site is reached from both  heap roots 
		boolean isDisjoint=true;
		HeapRegionNode effectHrn=og.gid2hrn.get(effectB.getHRNUniqueId());
		if(effectHrn.isSingleObject()){
			for (Iterator iterator = liveInHrnSetA.iterator(); iterator.hasNext();) {
				HeapRegionNode r1 = (HeapRegionNode) iterator.next();
				for (Iterator iterator2 = liveInHrnSetB.iterator(); iterator2.hasNext();) {
					HeapRegionNode r2 = (HeapRegionNode) iterator2.next();
					r1=og.gid2hrn.get(r1.getGloballyUniqueIdentifier());
					r2=og.gid2hrn.get(r2.getGloballyUniqueIdentifier());
					if(isReachableFrom(r1,r2,effectB.getRSet())){
						isDisjoint=false;
					}
				}
			}
			if(isDisjoint){
				return ConflictEdge.NON_WRITE_CONFLICT;
			}
		}
		
		/*
		HeapRegionNode r1=liveInHrnSetA.iterator().next();
		HeapRegionNode r2=liveInHrnSetB.iterator().next();
		
		r1=og.gid2hrn.get(r1.getGloballyUniqueIdentifier());
		r2=og.gid2hrn.get(r2.getGloballyUniqueIdentifier());
		
		System.out.println("r1="+r1);
		System.out.println("r2="+r2);
		System.out.println("effectB="+effectB.getRSet());
		System.out.println("###STALL calculateConflictType2");
		if(!isReachableFrom(r1,r2,effectB.getRSet())){
			System.out.println("###STALL calculateConflictType3");
			return ConflictEdge.NON_WRITE_CONFLICT;
		}
		*/
		Set<HeapRegionNode> entryHRNSet = getSameHeapRoot(liveInHrnSetA,
				liveInHrnSetB);
		if (entryHRNSet.size() == 0) {
			return ConflictEdge.COARSE_GRAIN_EDGE;
		}

		for (Iterator iterator = entryHRNSet.iterator(); iterator.hasNext();) {
			HeapRegionNode hrn = (HeapRegionNode) iterator.next();

			String entryIdentifier = hrn.getGloballyUniqueIdentifier();
			HeapRegionNode entryHRN = og.gid2hrn.get(entryIdentifier);

			TokenTuple h1 = new TokenTuple(entryHRN.getID(), !entryHRN
					.isSingleObject(), TokenTuple.ARITY_ONE).makeCanonical();
			
			TokenTuple h1star = new TokenTuple(entryHRN.getID(), true,
					TokenTuple.ARITY_ONEORMORE).makeCanonical();
			
			if (effectB.getRSet().containsTuple(h1star)) {
				return ConflictEdge.COARSE_GRAIN_EDGE;
			}else if (effectB.getRSet().containsTuple(h1)) {
				// rechability states contain heap root with arity 1
				return ConflictEdge.FINE_GRAIN_EDGE;
			}
		}
		return ConflictEdge.NON_WRITE_CONFLICT;
	}

	private int determineConflictType(LiveInNode liveInNodeA,
			SESEEffectsKey effectA, LiveInNode liveInNodeB,
			SESEEffectsKey effectB) {

		if (liveInNodeA.getSESEIdentifier() == liveInNodeB.getSESEIdentifier()) {
			return ConflictEdge.NON_WRITE_CONFLICT;
		}

		Set<HeapRegionNode> liveInHrnSetA = liveInNodeA.getHRNSet();
		Set<HeapRegionNode> liveInHrnSetB = liveInNodeB.getHRNSet();
		
		// check whether alloc site is reached from both  heap roots
		boolean isDisjoint=true;
		HeapRegionNode effectHrn=og.gid2hrn.get(effectB.getHRNUniqueId());
		if(effectHrn.isSingleObject()){
			for (Iterator iterator = liveInHrnSetA.iterator(); iterator.hasNext();) {
				HeapRegionNode r1 = (HeapRegionNode) iterator.next();
				for (Iterator iterator2 = liveInHrnSetB.iterator(); iterator2.hasNext();) {
					HeapRegionNode r2 = (HeapRegionNode) iterator2.next();
					r1=og.gid2hrn.get(r1.getGloballyUniqueIdentifier());
					r2=og.gid2hrn.get(r2.getGloballyUniqueIdentifier());
					
					if(isReachableFrom(r1,r2,effectB.getRSet())){
						isDisjoint=false;
					}else{
					}
				}
			}
			if(isDisjoint){
				return ConflictEdge.NON_WRITE_CONFLICT;
			}
		}
		
		/*
		HeapRegionNode r1=liveInHrnSetA.iterator().next();
		HeapRegionNode r2=liveInHrnSetB.iterator().next();
		
//		r1=og.gid2hrn.get(r1.getGloballyUniqueIdentifier());
//		r2=og.gid2hrn.get(r2.getGloballyUniqueIdentifier());
		System.out.println("@@r1="+r1);
		System.out.println("@@r2="+r2);
		System.out.println("@@effectB="+effectA.getRSet());
		
		if(!isReachableFrom(r1,r2,effectA.getRSet())){
			// two heap root are disjoint
			return ConflictEdge.NON_WRITE_CONFLICT;
		}
		*/
		
		Set<HeapRegionNode> entryHRNSet = getSameHeapRoot(liveInHrnSetA,
				liveInHrnSetB);
		if (entryHRNSet.size() == 0 ) {
			return ConflictEdge.COARSE_GRAIN_EDGE;
		}
		if(entryHRNSet.size()!=liveInHrnSetA.size() || entryHRNSet.size()!=liveInHrnSetB.size()){
			return ConflictEdge.COARSE_GRAIN_EDGE;
		}

		int count=0;
		for (Iterator iterator = entryHRNSet.iterator(); iterator.hasNext();) {
			HeapRegionNode hrn = (HeapRegionNode) iterator.next();
			if(hrn.getType()!=null && hrn.getType().isImmutable()){
				count++;
			}
		}
		if(count==entryHRNSet.size()){
			return ConflictEdge.FINE_GRAIN_EDGE;
		}
		
		for (Iterator iterator = entryHRNSet.iterator(); iterator.hasNext();) {
			HeapRegionNode hrn = (HeapRegionNode) iterator.next();

			String entryIdentifier = hrn.getGloballyUniqueIdentifier();
			HeapRegionNode entryHRN = og.gid2hrn.get(entryIdentifier);

			TokenTuple h1 = new TokenTuple(entryHRN.getID(), !entryHRN
					.isSingleObject(), TokenTuple.ARITY_ONE).makeCanonical();

			TokenTuple h1star = new TokenTuple(entryHRN.getID(), true,
					TokenTuple.ARITY_ONEORMORE).makeCanonical();

			if (effectA.getRSet().containsTuple(h1star)) {
				return ConflictEdge.COARSE_GRAIN_EDGE;
			} else if (effectA.getRSet().containsTuple(h1)) {
				// rechability states contain heap root with arity 1
				return ConflictEdge.FINE_GRAIN_EDGE;
			}

		}

		return ConflictEdge.NON_WRITE_CONFLICT;

	}
	
	private int calculateSelfConflictType(LiveInNode liveInNode) {
		
		// if strong update effect exists, it conflicts every effects of objects that are reachable from same heap root
		Set<SESEEffectsKey> strongUpdateSet = liveInNode.getStrongUpdateSet();
		if(strongUpdateSet!=null && strongUpdateSet.size()>0){
			return ConflictEdge.FINE_GRAIN_EDGE;
		}		
		
		if (liveInNode.getWriteEffectsSet() != null
				&& liveInNode.getWriteEffectsSet().size() > 0) {
			
			Set<SESEEffectsKey> writeEffectsSet=liveInNode.getWriteEffectsSet();
			
			int immuntableCount = 0;
			for (Iterator<HeapRegionNode> iterator = liveInNode.getHRNSet()
					.iterator(); iterator.hasNext();) {
				HeapRegionNode root = iterator.next();
				if(root.getType()!=null && root.getType().isImmutable()){
					immuntableCount++;
				}
			}
			if (immuntableCount == liveInNode.getHRNSet().size()) {
				// in this case, heap root is a parameter heap region
				return ConflictEdge.FINE_GRAIN_EDGE;
			}
			
			
			int paramCount = 0;
			for (Iterator<HeapRegionNode> iterator = liveInNode.getHRNSet()
					.iterator(); iterator.hasNext();) {
				HeapRegionNode root = iterator.next();
				if (root.isParameter()) {
					paramCount++;
				}
			}

			if (paramCount == liveInNode.getHRNSet().size()) {
				// in this case, heap root is a parameter heap region
				return ConflictEdge.FINE_GRAIN_EDGE;
			}

			if (liveInNode.getHRNSet().size()==1) {
				HeapRegionNode hrn = liveInNode.getHRNSet().iterator().next();
					String entryIdentifier = hrn.getGloballyUniqueIdentifier();
					HeapRegionNode entryHRN = og.gid2hrn.get(entryIdentifier);
					
					boolean containsStar=false;
					for (Iterator iterator = writeEffectsSet.iterator(); iterator
							.hasNext();) {
						SESEEffectsKey effect = (SESEEffectsKey) iterator.next();
						TokenTuple h1 = new TokenTuple(entryHRN.getID(), !entryHRN
								.isSingleObject(), TokenTuple.ARITY_ONE).makeCanonical();
						TokenTuple h1star = new TokenTuple(entryHRN.getID(), true, TokenTuple.ARITY_ZEROORMORE).makeCanonical();
						if (effect.getRSet().containsTuple(h1star)) {
							// rechability states contain heap root with arity star
							containsStar=true;
						}						
					}
					if(containsStar){
						return ConflictEdge.COARSE_GRAIN_EDGE;
					}else{
						return ConflictEdge.FINE_GRAIN_EDGE;
					}
			}else{
				return ConflictEdge.COARSE_GRAIN_EDGE;
			}
					
			
			/*
			boolean containsAllTuple=true;
			for (Iterator iterator2 = writeEffectsSet.iterator(); iterator2
					.hasNext();) {
				SESEEffectsKey seseEffectsKey = (SESEEffectsKey) iterator2
						.next();
				ReachabilitySet rset = seseEffectsKey.getRSet();
				Iterator<TokenTupleSet> tsetIter=rset.iterator();
				int countNotContained=0;
				while (tsetIter.hasNext()) {
					TokenTupleSet tokenTupleSet = (TokenTupleSet) tsetIter
							.next();
					boolean found=true;
					for (Iterator iterator = rootIDSet.iterator(); iterator
							.hasNext();) {
						Integer rootID = (Integer) iterator.next();
						if(tokenTupleSet.containsToken(rootID)==null){
							found=false;
						}
					}
					if(!found){
						countNotContained++;
					}
				}
				if(countNotContained==rset.size()){
					containsAllTuple=false;
				}
			}
			
			if (containsAllTuple && liveInNode.getHRNSet().size() > 1) {
				return ConflictEdge.COARSE_GRAIN_EDGE;
			} else {
				return ConflictEdge.FINE_GRAIN_EDGE;
			}
			*/
			
			
			
		}
		
		return ConflictEdge.NON_WRITE_CONFLICT;

	}

	public void analyzePossibleConflicts(Set<String> analyzedIDSet,
			ConflictNode currentNode) {

		// compare with all nodes
		// examine the case where self-edge exists
		if (currentNode instanceof LiveInNode) {
			LiveInNode liveInNode = (LiveInNode) currentNode;
			int conflictType=calculateSelfConflictType(liveInNode);
			if(conflictType>0){
				addConflictEdge(conflictType, currentNode,
						currentNode);
			}
		}

		Set<Entry<String, ConflictNode>> set = id2cn.entrySet();
		for (Iterator iterator = set.iterator(); iterator.hasNext();) {
			Entry<String, ConflictNode> entry = (Entry<String, ConflictNode>) iterator
					.next();

			String entryNodeID = entry.getKey();
			ConflictNode entryNode = entry.getValue();

			if ((!currentNode.getID().equals(entryNodeID))
					&& !(analyzedIDSet.contains(currentNode.getID()
							+ entryNodeID) || analyzedIDSet
							.contains(entryNodeID + currentNode.getID()))) {

				if (currentNode instanceof StallSiteNode
						&& entryNode instanceof LiveInNode) {
					
					int conflictType = calculateConflictType((StallSiteNode) currentNode,	(LiveInNode) entryNode);
					if (conflictType > 0) {
						addConflictEdge(conflictType, currentNode, entryNode);
					}
					
					analyzedIDSet.add(currentNode.getID() + entryNodeID);

				} else if (currentNode instanceof LiveInNode
						&& entryNode instanceof LiveInNode) {

					int conflictType = calculateConflictType(
							(LiveInNode) currentNode, (LiveInNode) entryNode);
					if (conflictType > 0) {
						addConflictEdge(conflictType, currentNode, entryNode);
					}
					analyzedIDSet.add(currentNode.getID() + entryNodeID);
				}

			}

		}

	}

}

class ConflictEdge {

	private ConflictNode u;
	private ConflictNode v;
	private int type;

	public static final int NON_WRITE_CONFLICT = 0;
	public static final int FINE_GRAIN_EDGE = 1;
	public static final int COARSE_GRAIN_EDGE = 2;

	public ConflictEdge(ConflictNode u, ConflictNode v, int type) {
		this.u = u;
		this.v = v;
		this.type = type;
	}

	public String toGraphEdgeString() {
		if (type == FINE_GRAIN_EDGE) {
			return "\"F_CONFLICT\"";
		} else if (type == COARSE_GRAIN_EDGE) {
			return "\"C_CONFLICT\"";
		} else {
			return "CONFLICT\"";
		}
	}

	public ConflictNode getVertexU() {
		return u;
	}

	public ConflictNode getVertexV() {
		return v;
	}

	public int getType() {
		return type;
	}

	public String toString() {
		return getVertexU() + "-" + getVertexV();
	}

}
