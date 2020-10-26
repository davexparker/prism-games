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

/**
 * (region, strategy) pair for a player in a turn-based game (TG).
 */
public class RegionStrategy
{

	/**
	 * The region.
	 */
	protected BitSet region = new BitSet();
	/**
	 * The strategy.
	 * 
	 * TreeMap is used to print the strategy in linear order of the vertices.
	 */
	protected Map<Integer, Integer> strategy = new TreeMap<>();

	/**
	 * Get the region for the player.
	 */
	public BitSet getRegion()
	{
		return region;
	}

	/**
	 * Set the region for the player.
	 * @param region region
	 */
	public void setRegion(BitSet region)
	{
		this.region = region;
	}

	/**
	 * Get the strategy for the player.
	 */
	public Map<Integer, Integer> getStrategy()
	{
		return strategy;
	}

	/**
	 * Set the strategy for the player.
	 * @param strategy strategy
	 */
	public void setStrategy(Map<Integer, Integer> strategy)
	{
		this.strategy = strategy;
	}

}