//==============================================================================
//	
//	Copyright (c) 2020-
//	Authors:
//	* Dave Parker <d.a.parker@cs.bham.ac.uk> (University of Birmingham)
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

import prism.ModelType;
import prism.PlayerInfoOwner;
import prism.PrismException;

import java.util.BitSet;
import java.util.Iterator;
import java.util.Map.Entry;

/**
 * Interface for classes that provide (read) access to an explicit-state turn-based (multi-player) game (TG).
 */
public interface TG<Value> extends LTS<Value>, PlayerInfoOwner, TurnBasedGame
{
	// Accessors (for Model) - default implementations

	@Override
	default ModelType getModelType()
	{
		return ModelType.TG;
	}

	@Override
	default void exportToPrismLanguage(final String filename) throws PrismException
	{
		throw new UnsupportedOperationException();
	}

	// Accessors

	/**
	 * Get the active states.
	 * This is useful to see what states are still present in subgames.
	 */
	public BitSet getActiveStates();

	/**
	 * Compute the subgame with the given states.
	 * @param states states
	 */
	public TG<Value> subgame(BitSet states);

	/**
	 * Compute the subgame without the given states.
	 * @param states states
	 */
	public TG<Value> difference(BitSet states);

	/**
	 * Get an iterator over the transitions from choice {@code i} of state {@code s}.
	 */
	public Iterator<Entry<Integer, Value>> getTransitionsIterator(int s, int i);

}
