//==============================================================================
//
//	Copyright (c) 2024-
//	Authors:
//	* Dave Parker <david.parker@cs.ox.ac.uk> (University of Oxford)
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import explicit.rewards.MDPRewards;
import parser.State;
import prism.PrismException;
import prism.PrismUtils;

/**
 * Simple explicit-state representation of a POSMG.
 * Basically an {@link SMGSimple} with observability info,
 * added in the same way as for a {@link POMDPSimple}.
 */
public class POSMGSimple<Value> extends SMGSimple<Value> implements POSMG<Value>
{
	// Observations
	protected ObservationsSimple observations;

	// Constructors

	/**
	 * Constructor: empty POSMG.
	 */
	public POSMGSimple()
	{
		super();
		observations = new ObservationsSimple();
	}

	/**
	 * Constructor: new POSMG with fixed number of states.
	 */
	public POSMGSimple(int numStates)
	{
		super(numStates);
		observations = new ObservationsSimple(numStates);
	}

	/**
	 * Copy constructor.
	 */
	public POSMGSimple(POSMGSimple<Value> posmg)
	{
		super(posmg);
		observations = new ObservationsSimple(posmg.observations);
	}

	/**
	 * Construct a POSMG from an existing one and a state index permutation,
	 * i.e. in which state index i becomes index permut[i].
	 */
	public POSMGSimple(POSMGSimple<Value> posmg, int permut[])
	{
		super(posmg, permut);
		observations = new ObservationsSimple(posmg.observations, permut);
	}

	// Mutators (for ModelSimple)

	@Override
	public void clearState(int s)
	{
		super.clearState(s);
		observations.clearState(s);
	}

	@Override
	public void addStates(int numToAdd)
	{
		super.addStates(numToAdd);
		observations.addStates(numToAdd);
	}

	// Mutators (other)

	/**
	 * Set the associated (read-only) observation list.
	 */
	public void setObservationsList(List<State> observationsList)
	{
		observations.setObservationsList(observationsList);
	}

	/**
	 * Set the associated (read-only) unobservation list.
	 */
	public void setUnobservationsList(List<State> unobservationsList)
	{
		observations.setUnobservationsList(unobservationsList);
	}

	/**
	 * Set the observation info for a state.
	 * If the actions for existing states with this observation do not match,
	 * an explanatory exception is thrown (so this should be done after transitions
	 * have been added to the state). Optionally, a list of names of the
	 * observables can be passed for error reporting.
	 * @param s State
	 * @param observ Observation
	 * @param unobserv Unobservation
	 * @param observableNames Names of observables (optional)
	 */
	public void setObservation(int s, State observ, State unobserv, List<String> observableNames) throws PrismException
	{
		observations.setObservation(s, observ, unobserv, observableNames, this);
	}

	/**
	 * Assign observation with index o to state s.
	 * (assumes observation has already been added to the list)
	 * If the actions for existing states with this observation do not match,
	 * an explanatory exception is thrown (so this should be done after transitions
	 * have been added to the state).
	 */
	protected void setObservation(int s, int o) throws PrismException
	{
		observations.setObservation(s, o, this);
	}

	// Accessors (for PartiallyObservableModel)
	@Override
	public List<State> getObservationsList()
	{
		return observations.getObservationsList();
	}

	@Override
	public List<State> getUnobservationsList()
	{
		return observations.getUnobservationsList();
	}

	@Override
	public int getObservation(int s)
	{
		return observations.getObservation(s);
	}

	@Override
	public int getUnobservation(int s)
	{
		return observations.getUnobservation(s);
	}

	@Override
	public int getNumChoicesForObservation(int o)
	{
		return getNumChoices(observations.getObservationState(o));
	}

	// Standard methods

	@Override
	public String toString()
	{
		int i, j, n;
		Object o;
		String s = "";
		s = "[ ";
		for (i = 0; i < numStates; i++) {
			if (i > 0)
				s += ", ";
			s += i + "(" + getObservation(i) + "/" + getUnobservation(i) + "): ";
			if (statesList.size() > i)
				s += i + "(P-" + (stateOwners.getPlayer(i)+1) + " " + statesList.get(i) + "): ";
			else
				s += i + "(P-" + (stateOwners.getPlayer(i)+1) + "): ";
			s += "[";
			n = getNumChoices(i);
			for (j = 0; j < n; j++) {
				if (j > 0)
					s += ",";
				o = getAction(i, j);
				if (o != null)
					s += o + ":";
				s += trans.get(i).get(j);
			}
			s += "]";
		}
		s += " ]\n";
		return s;
	}
}
