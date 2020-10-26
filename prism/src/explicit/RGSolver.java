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

import prism.PrismComponent;

/**
 * Solve reachability games (RGs).
 */
public class RGSolver extends TGSolver
{

	/**
	 * Create a new reachability game solver.
	 */
	public RGSolver(PrismComponent parent)
	{
		super(parent);
	}

	/**
	 * Compute the solution.
	 * 
	 * @param tg Turn-based game (TG)
	 * @param target Target set
	 */
	public TGSolution solve(RG rg)
	{
		TGSolution soln = new TGSolution();

		RegionStrategy rs = rg.getTG().attractor(0, rg.target, parent);
		soln.set(0, rs);
		soln.get(1).setRegion((BitSet) rs.getRegion().clone());
		soln.get(1).getRegion().flip(0, rg.getTG().getNumStates());

		// Complete the winning strategies
		rg.getTG().getActiveStates().stream().forEach(s -> {
			// Arbitrary choice for Player 1
			if (rg.getTG().getPlayer(s) == 0 && !rs.getStrategy().containsKey(s)) {
				rs.getStrategy().put(s, rg.getTG().getSuccessors(s).next());
			}

			// For Player 2, keep the token out of the attractor
			// Arbitrary choice if unable to.
			if (rg.getTG().getPlayer(s) == 1) {
				SuccessorsIterator successors = rg.getTG().getSuccessors(s);
				while (successors.hasNext()) {
					int succ = successors.next();
					if (!rs.getRegion().get(succ)) {
						soln.get(1).getStrategy().put(s, succ);
						break;
					}
				}

				if (!rs.getStrategy().containsKey(s)) {
					soln.get(1).getStrategy().put(s, rg.getTG().getSuccessors(s).next());
				}
			}
		});

		return soln;
	}

}
