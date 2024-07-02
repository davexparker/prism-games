//==============================================================================
//
//	Copyright (c) 2024-
//	Authors:
//	* Dave Parker <david.parker@cs.ox.ac.uk> (University of Oxford)
//
//------------------------------------------------------------------------------
//
//	This file is part of PRISM.
//
//	PRISM is free software; you can redistribute it and/or modify
//	it under the terms of the GNU General Public License as published by
//	the Free Software Foundation; either version 2 of the License, or
//	(at your option) any later version.
//
//	PRISM is distributed in the hope that it will be useful,
//	but WITHOUT ANY WARRANTY; without even the implied warranty of
//	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//	GNU General Public License for more details.
//
//	You should have received a copy of the GNU General Public License
//	along with PRISM; if not, write to the Free Software Foundation,
//	Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//
//==============================================================================

package explicit;

import prism.ModelType;
import prism.PrismLog;
import prism.PrismUtils;

import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

/**
 * Interface for classes that provide (read) access to an explicit-state POSMG,
 * i.e., a partially observable (turn-based) stochastic multi-player game.
 */
public interface POSMG<Value> extends SMG<Value>, PartiallyObservableModel<Value>
{
	// Accessors (for Model) - default implementations

	@Override
	default ModelType getModelType()
	{
		return ModelType.POSMG;
	}

	@Override
	default void exportToPrismExplicitTra(PrismLog out, int precision)
	{
		// Output transitions to .tra file
		int numStates = getNumStates();
		out.print(numStates + ":" + getNumPlayers() + " " + getNumChoices() + " " + getNumTransitions() + " " + getNumObservations() + "\n");
		TreeMap<Integer, Value> sorted = new TreeMap<Integer, Value>();
		for (int i = 0; i < numStates; i++) {
			int numChoices = getNumChoices(i);
			for (int j = 0; j < numChoices; j++) {
				// Extract transitions and sort by destination state index (to match PRISM-exported files)
				Iterator<Map.Entry<Integer, Value>> iter = getTransitionsIterator(i, j);
				while (iter.hasNext()) {
					Map.Entry<Integer, Value> e = iter.next();
					sorted.put(e.getKey(), e.getValue());
				}
				// Print out (sorted) transitions
				for (Map.Entry<Integer, Value> e : sorted.entrySet()) {
					out.print(i + ":" + getPlayer(i) + " " + j + " " + e.getKey() + " " + getEvaluator().toStringExport(e.getValue(), precision) + " " + getObservation(e.getKey()));
					Object action = getAction(i, j);
					out.print(action == null ? "\n" : (" " + action + "\n"));
				}
				sorted.clear();
			}
		}
	}
}
