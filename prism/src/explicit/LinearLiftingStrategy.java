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

import prism.PrismComponent;

/**
 * Linear lifting strategy for the small progress measures algorithm.
 * 
 * This is a naive lifting strategy in which vertices are simply lifted in linear order.
 */
public class LinearLiftingStrategy extends LiftingStrategy
{

	/**
	 * How many states have failed to lift
	 */
	protected int numFailed = 0;
	/**
	 * Next state to lift
	 */
	protected int nextState = 0;

	/**
	 * Create a LinearLiftingStrategy.
	 */
	public LinearLiftingStrategy(PrismComponent parent, PG pg)
	{
		super(parent, pg);
	}

	@Override
	public void lifted(int s)
	{
		numFailed = 0;
	}

	@Override
	public int next()
	{
		if (numFailed == pg.getTG().getNumStates()) {
			return LiftingStrategy.NO_STATE;
		} else {
			numFailed = numFailed + 1;
			nextState = (nextState + 1) % pg.getTG().getNumStates();
			return nextState;
		}
	}

}
