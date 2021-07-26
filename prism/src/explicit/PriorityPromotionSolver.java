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
import java.util.HashMap;
import java.util.Map;

import prism.Pair;

/**
 * Solve parity games using the priority promotion algorithm.
 */
public class PriorityPromotionSolver extends PGSolver
{

	/**
	 * Create a new parity game solver.
	 */
	public PriorityPromotionSolver(TGModelChecker mc)
	{
		super(mc);
	}

	@Override
	public TGSolution solve(PG pg)
	{
		PG parityGame = pg;
		TGSolution soln = new TGSolution();

		while (!parityGame.getTG().getActiveStates().isEmpty()) {
			// (For benchmarking)
			if (Thread.currentThread().isInterrupted()) {
				return null;
			}

			Pair<Integer, RegionStrategy> dominion = searchDominion(parityGame);
			soln.get(dominion.getKey()).getRegion().or(dominion.getValue().getRegion());
			soln.get(dominion.getKey()).getStrategy().putAll(dominion.getValue().getStrategy());
			parityGame = parityGame.difference(dominion.getValue().getRegion());
		}

		return soln;
	}

	private Pair<Integer, RegionStrategy> searchDominion(PG pg)
	{
		Map<Integer, Integer> r = new HashMap<>();
		pg.getTG().getActiveStates().stream().forEach(s -> {
			r.put(s, -1); // -1 is our bottom
		});
		int p = pg.maxPriority();

		while (true) {
			// (For benchmarking)
			if (Thread.currentThread().isInterrupted()) {
				return null;
			}
			
			int alpha = p % 2;
            int alphaBar = 1 - alpha;

			int finalP = p;

			BitSet Subgame = (BitSet) pg.getTG().getActiveStates().clone();
			r.entrySet().stream()
			.filter(entry -> entry.getValue() > finalP)
			.map(Map.Entry::getKey)
			.forEach(s -> Subgame.set(s, false));

			BitSet A = (BitSet) pg.getPriorityMap().getOrDefault(p, new BitSet()).clone();
			A.and(Subgame);
			r.entrySet().stream()
			.filter(entry -> entry.getValue() == finalP)
			.map(Map.Entry::getKey)
			.forEach(s -> A.set(s));

			RegionStrategy Z = mc.computeAttractor(pg.getTG().subgame(Subgame), alpha, A);

			BitSet Open = Z.getRegion().stream()
					.filter(s -> pg.getTG().getPlayer(s) == alpha && 
								 !pg.getTG().someSuccessorsInSet(s, Z.getRegion()))
					.collect(BitSet::new, BitSet::set, BitSet::or);

			BitSet Esc = Z.getRegion().stream()
					.filter(s -> pg.getTG().getPlayer(s) == alphaBar)
					.flatMap(s -> pg.getTG().getSuccessors(s).stream())
					.filter(s -> !Z.getRegion().get(s))
					.collect(BitSet::new, BitSet::set, BitSet::or);

			BitSet EscAndSubgame = (BitSet) Esc.clone();
			EscAndSubgame.and(Subgame);

			if (!Open.isEmpty() || !EscAndSubgame.isEmpty()) {
				int finalP1 = p;
				Z.getRegion().stream().forEach(s -> r.put(s, finalP1));

				p = Subgame.stream()
						.filter(s -> !Z.getRegion().get(s))
						.map(s -> pg.getPriorities().get(s))
						.max().getAsInt();
			} else if (!Esc.isEmpty()) {
				p = r.entrySet().stream()
						.filter(entry -> Esc.get(entry.getKey()))
						.map(Map.Entry::getValue)
						.min(Integer::compare).get();
				int finalP1 = p;

				Z.getRegion().stream().forEach(s -> r.put(s, finalP1));
				r.entrySet().stream()
				.filter(entry -> entry.getValue() < finalP1)
				.map(Map.Entry::getKey)
				.forEach(s -> r.put(s, -1));
			} else {
				RegionStrategy Z1 = mc.computeAttractor(pg.getTG(), alpha, Z.getRegion());
				Z1.getStrategy().putAll(Z.getStrategy());
				return new Pair<>(alpha, Z1);
			}
		}
	}

}
