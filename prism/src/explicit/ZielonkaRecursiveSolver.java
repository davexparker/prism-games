//==============================================================================
//	
//	Copyright (c) 2020-
//	Authors:
//	* Shahram Javed <msj812@student.bham.ac.uk> (University of Birmingham)
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

import java.util.BitSet;
import java.util.Map;
import java.util.TreeMap;

import prism.PrismComponent;

/**
 * Solve parity games using the Zielonka recursive algorithm.
 */
public class ZielonkaRecursiveSolver extends PGSolver
{

	/**
	 * Create a new parity game solver.
	 */
	public ZielonkaRecursiveSolver(PrismComponent parent)
	{
		super(parent);
	}

	@Override
	public TGSolution solve(PG pg)
	{
		TGSolution soln = zielonka(pg);
		return soln;
	}

	private TGSolution zielonka(PG pg)
	{
		// (For benchmarking)
		if (Thread.currentThread().isInterrupted()) {
			return null;
		}

		TGSolution W = new TGSolution();
		if (pg.getTG().getActiveStates().isEmpty()) {
			return W;
		}

		int d = pg.maxPriority();
		BitSet U = pg.getPriorityMap().get(d);
		int i = d % 2;
		int j = 1 - i;
		// Arbitrary initial strategy for player p on U
		Map<Integer, Integer> tau = new TreeMap<>();
		U.stream().forEach(s -> {
			if (pg.getTG().getPlayer(s) == i) {
				tau.put(s, pg.getTG().getSuccessors(s).next());
			}
		});
		RegionStrategy A = pg.getTG().attractor(i, U, parent);
		TGSolution W1 = zielonka(pg.difference(A.getRegion()));

		if (W1.get(j).getRegion().isEmpty()) {
			W1.get(i).getRegion().or(A.getRegion());
			W1.get(i).getStrategy().putAll(tau);
			W1.get(i).getStrategy().putAll(A.getStrategy());
			W.set(i, W1.get(i));
			W.set(j, new RegionStrategy());
		} else {
			RegionStrategy B = pg.getTG().attractor(j, W1.get(j).getRegion(), parent);
			TGSolution W2 = zielonka(pg.difference(B.getRegion()));
			W.set(i, W2.get(i));
			W2.get(j).getRegion().or(B.getRegion());
			W2.get(j).getStrategy().putAll(B.getStrategy());
			W2.get(j).getStrategy().putAll(W1.get(j).getStrategy());
			W.set(j, W2.get(j));
		}

		return W;
	}

}
