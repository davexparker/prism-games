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

import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import prism.Pair;
import prism.PrismComponent;

/**
 * Solve parity games using the small progress measures algorithm.
 * We treat null as our T element. 
 */
public class SmallProgressMeasuresSolver extends PGSolver
{

	/**
	 * Measure comparator for lexicographic order
	 */
	private static final Comparator<int[]> measureComparator = SmallProgressMeasuresSolver::compare;

	/**
	 * Create a new parity game solver.
	 */
	public SmallProgressMeasuresSolver(PrismComponent parent)
	{
		super(parent);
	}

	@Override
	public TGSolution solve(PG pg)
	{
		// Convert PG to the min-parity definition as SPM assumes this
		PG parityGame = new PG(pg);
		parityGame.convertMaxToMin();

		TGSolution soln = jurdzinksi(parityGame);
		return soln;
	}

	private TGSolution jurdzinksi(PG pg)
	{
		TGSolution soln = new TGSolution();

		// Maximum priority
		int d = pg.maxPriority();
		// Number of states for a priority
		// This is a measure just less than the top measure
		int[] max = new int[d + 1];
		for (Map.Entry<Integer, BitSet> entry : pg.getPriorityMap().entrySet()) {
			if (entry.getKey() % 2 != 0) {
				max[entry.getKey()] = entry.getValue().cardinality();
			}
		}
		// Progress measure
		int[][] rho = new int[pg.getTG().getNumStates()][d + 1];

		// Lifting strategies can be swapped here
		// LiftingStrategy liftingStrategy = new LinearLiftingStrategy(parent, pg);
		LiftingStrategy liftingStrategy = new PredecessorLiftingStrategy(parent, pg, rho);
		int v = liftingStrategy.next();

		while (v != LiftingStrategy.NO_STATE) {
			// (For benchmarking)
			if (Thread.currentThread().isInterrupted()) {
				return null;
			}

			Pair<int[], Integer> liftedPair = lift(pg, rho, max, d, v);

			// SPM computes Player 1's winning strategy
			if (pg.getTG().getPlayer(v) == 0) {
				soln.get(0).getStrategy().put(v, liftedPair.getValue());
			}

			int[] lifted = liftedPair.getKey();
			if (measureComparator.compare(rho[v], lifted) < 0) {
				rho[v] = lifted;
				liftingStrategy.lifted(v);
			}

			v = liftingStrategy.next();
		}

		for (int s = 0; s < rho.length; s++) {
			// SPM computes Player 2's winning region
			if (rho[s] == null) {
				soln.get(1).getRegion().set(s);
			} else { // Remaining are Player 1's
				soln.get(0).getRegion().set(s);
			}
		}

		return soln;
	}

	// Return the lifted measure as well as the vertex (min/max)imising it
	// This is useful in the case of Player 1 to compute her strategy.
	private static Pair<int[], Integer> lift(PG pg, int[][] rho, int[] max, int d, int v)
	{
		Map<int[], Integer> progs = new HashMap<>();
		pg.getTG().getSuccessors(v).stream().forEach(w -> {
			progs.put(prog(pg, rho, max, d, v, w), w);
		});

		int[] prog;
		if (pg.getTG().getPlayer(v) == 0) {
			prog = Collections.min(progs.keySet(), measureComparator);
		} else {
			prog = Collections.max(progs.keySet(), measureComparator);
		}

		return new Pair<>(Collections.max(Arrays.asList(rho[v], prog), measureComparator), progs.get(prog));
	}

	private static int[] prog(PG pg, int[][] rho, int[] max, int d, int v, int w)
	{
		if (rho[w] == null) {
			return null;
		}

		int vPriority = pg.getPriorities().get(v);
		int[] measure = new int[d + 1];

		for (int i = 1; i <= vPriority; i += 2) {
			measure[i] = rho[w][i];
		}
		if (vPriority % 2 != 0) {
			measure = increase(measure, max, vPriority);
		}

		return measure;
	}

	private static int[] increase(int[] measure, int[] max, int p)
	{
		if (measure != null) {
			for (int i = p; i >= 0; i--) {
				if (measure[i] < max[i]) {
					measure[i] = measure[i] + 1;
					return measure;
				} else {
					measure[i] = 0;
				}
			}
		}

		return null;
	}

	// Lexicographic order
	private static int compare(int[] v, int[] w)
	{
		if (v == null) {
			if (w == null) {
				return 0;
			} else {
				return 1;
			}
		} else if (w == null) {
			return -1;
		}

		for (int i = 0; i < v.length; i++) {
			if (v[i] == w[i]) {
				continue;
			}
			return Integer.compare(v[i], w[i]);
		}

		return 0;
	}

}
