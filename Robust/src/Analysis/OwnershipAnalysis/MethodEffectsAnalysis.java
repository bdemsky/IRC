package Analysis.OwnershipAnalysis;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;

import IR.FieldDescriptor;
import IR.MethodDescriptor;
import IR.Flat.FlatCall;
import IR.Flat.TempDescriptor;

public class MethodEffectsAnalysis {

	private Hashtable<MethodContext, MethodEffects> mapMethodContextToMethodEffects;
	boolean methodeffects = false;

	public MethodEffectsAnalysis(boolean methodeffects) {
		this.methodeffects = methodeffects;
		mapMethodContextToMethodEffects = new Hashtable<MethodContext, MethodEffects>();
	}

	public void createNewMapping(MethodContext mcNew) {
		if(!methodeffects) return;
		if (!mapMethodContextToMethodEffects.containsKey(mcNew)) {
			MethodEffects meNew = new MethodEffects();
			mapMethodContextToMethodEffects.put(mcNew, meNew);
		}
	}

	public void analyzeFlatCall(OwnershipGraph calleeOG,
			MethodContext calleeMC, MethodContext callerMC, FlatCall fc) {
		if(!methodeffects) return;
		MethodEffects me = mapMethodContextToMethodEffects.get(callerMC);
		MethodEffects meFlatCall = mapMethodContextToMethodEffects
				.get(calleeMC);
		me.analyzeFlatCall(calleeOG, fc, callerMC, meFlatCall);
		mapMethodContextToMethodEffects.put(callerMC, me);
	}

	public void analyzeFlatFieldNode(MethodContext mc, OwnershipGraph og,
			TempDescriptor srcDesc, FieldDescriptor fieldDesc) {
		if(!methodeffects) return;
		MethodEffects me = mapMethodContextToMethodEffects.get(mc);
		me.analyzeFlatFieldNode(og, srcDesc, fieldDesc);
		mapMethodContextToMethodEffects.put(mc, me);
	}

	public void analyzeFlatSetFieldNode(MethodContext mc, OwnershipGraph og,
			TempDescriptor dstDesc, FieldDescriptor fieldDesc) {
		if(!methodeffects) return;
		MethodEffects me = mapMethodContextToMethodEffects.get(mc);
		me.analyzeFlatSetFieldNode(og, dstDesc, fieldDesc);
		mapMethodContextToMethodEffects.put(mc, me);
	}

	public void writeMethodEffectsResult() throws IOException {

		try {
			BufferedWriter bw = new BufferedWriter(new FileWriter(
					"MethodEffects_resport.txt"));

			Set<MethodContext> mcSet = mapMethodContextToMethodEffects.keySet();
			Iterator<MethodContext> mcIter = mcSet.iterator();
			while (mcIter.hasNext()) {
				MethodContext mc = mcIter.next();
				MethodDescriptor md = (MethodDescriptor) mc.getDescriptor();

				int startIdx = 0;
				if (!md.isStatic()) {
					startIdx = 1;
				}

				MethodEffects me = mapMethodContextToMethodEffects.get(mc);
				EffectsSet effectsSet = me.getEffects();

				bw.write("Method " + mc + " :\n");
				for (int i = startIdx; i < md.numParameters() + startIdx; i++) {

					String paramName = md.getParamName(i - startIdx);

					Set<EffectsKey> effectSet = effectsSet.getReadingSet(i);
					String keyStr = "{";
					if (effectSet != null) {
						Iterator<EffectsKey> effectIter = effectSet.iterator();
						while (effectIter.hasNext()) {
							EffectsKey key = effectIter.next();
							keyStr += " " + key;
						}
					}
					keyStr += " }";
					bw.write("  Paramter " + paramName + " ReadingSet="
							+ keyStr + "\n");

					effectSet = effectsSet.getWritingSet(new Integer(i));
					keyStr = "{";
					if (effectSet != null) {
						Iterator<EffectsKey> effectIter = effectSet.iterator();
						while (effectIter.hasNext()) {
							EffectsKey key = effectIter.next();
							keyStr += " " + key;
						}
					}

					keyStr += " }";
					bw.write("  Paramter " + paramName + " WritingngSet="
							+ keyStr + "\n");

				}
				bw.write("\n");

			}

			bw.close();
		} catch (IOException e) {
			System.err.println(e);
		}

	}

}
