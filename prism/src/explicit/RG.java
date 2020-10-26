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

/**
 * Representation of a reachability game (RG).
 */
public class RG
{

	/**
	 * Turn-based game (TG)
	 */
	protected TGSimple tg;
	/**
	 * Target set
	 */
	protected BitSet target;

	/**
	 * Create a new reachability game (RG).
	 */
	public RG(TG tg, BitSet target)
	{
		this.tg = (TGSimple) tg;
		this.target = target;
	}

	/** 
	 * Get the turn-based game (TG). 
	 */
	public TG getTG()
	{
		return tg;
	}

	/** 
	 * Set the turn-based game (TG).
	 * @param tg TG
	 */
	public void setTG(TG tg)
	{
		this.tg = (TGSimple) tg;
	}

	/** 
	 * Get the target set.
	 */
	public BitSet getTarget()
	{
		return target;
	}

	/** 
	 * Set the target set. 
	 * @param target target set
	 */
	public void setTarget(BitSet target)
	{
		this.target = target;
	}

}
