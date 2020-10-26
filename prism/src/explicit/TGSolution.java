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

/**
 * Solution to a 2-player turn-based game (TG).
 */
public class TGSolution
{

	/**
	 * Winning pair for player 1. 
	 */
	protected RegionStrategy w1 = new RegionStrategy();
	/**
	 * Winning pair for player 2. 
	 */
	protected RegionStrategy w2 = new RegionStrategy();

	/**
	 * Get the winning pair for a player
	 * @param player player (0-indexed)
	 */
	public RegionStrategy get(int player)
	{
		if (player == 0) {
			return w1;
		} else {
			return w2;
		}
	}

	/**
	 * Set the winning pair for a player
	 * @param player player (0-indexed)
	 * @param pair Winning pair
	 */
	public void set(int player, RegionStrategy pair)
	{
		if (player == 0) {
			w1 = pair;
		} else {
			w2 = pair;
		}
	}

	@Override
	public String toString()
	{
		// Add a new line before and after.
		String s = "\nTGSolution:\n";
		s += "Player 1:\n";
		s += "Region: " + w1.getRegion() + "\n";
		s += "Strategy: " + w1.getStrategy() + "\n";
		s += "Player 2:\n";
		s += "Region: " + w2.getRegion() + "\n";
		s += "Strategy: " + w2.getStrategy() + "\n";
		return s;
	}

}