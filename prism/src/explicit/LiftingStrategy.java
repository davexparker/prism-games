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
 * Lifting strategy for the small progress measures algorithm.
 */
public abstract class LiftingStrategy
{

	/**
	 * Indicator for there being no more states left to lift
	 */
	public static final int NO_STATE = -1;

	/**
	 * PrismComponent for obtaining the log
	 */
	protected final PrismComponent parent;
	/**
	 * Parity game (PG)
	 */
	protected final PG pg;

	/**
	 * Create a LiftingStrategy.
	 */
	public LiftingStrategy(PrismComponent parent, PG pg)
	{
		this.parent = parent;
		this.pg = pg;
	}

	/**
	 * Notify the lifting strategy a state has been successfully lifted.
	 * 
	 * @param s Index of state (0-indexed)
	 */
	public abstract void lifted(int s);

	/**
	 * Compute the next state to attempt to lift.
	 */
	public abstract int next();

}
