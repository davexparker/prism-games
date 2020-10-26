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

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.function.Predicate;

import io.ExplicitModelImporter;
import prism.PlayerInfo;
import prism.PlayerInfoOwner;
import prism.PrismException;

/**
 * Simple explicit-state representation of a turn-based (multi-player) game (TG).
 */
public class TGSimple<Value> extends LTSSimple<Value> implements TG<Value>
{
	/**
	 * Which player owns each state
	 */
	protected StateOwnersSimple stateOwners;

	/**
	 * Player + coalition information
	 */
	protected PlayerInfo playerInfo;

	/**
	 * Active states
	 *
	 * The nature of LTS and storing states in a list (i.e. densely) presents
	 * difficulties in removing states without shifting and relabelling each
	 * state to reflect a state's removal. Instead we store the states which
	 * are still active without modifying the original list of states.
	 */
	protected BitSet activeStates;

	// Constructors

	/**
	 * Constructor: empty TG.
	 */
	public TGSimple()
	{
		super();
	}

	/**
	 * Constructor: new TG with fixed number of states.
	 */
	public TGSimple(int numStates)
	{
		super(numStates);
	}

	/**
	 * Copy constructor
	 */
	public TGSimple(TGSimple<Value> tg)
	{
		super(tg);
		stateOwners = new StateOwnersSimple(tg.stateOwners);
		playerInfo = new PlayerInfo(tg.playerInfo);
		activeStates = (BitSet) tg.activeStates.clone();
	}

	/**
	 * Construct a TG from an existing one and a state index permutation,
	 * i.e. in which state index i becomes index permut[i].
	 * Player and coalition info is also copied across.
	 */
	public TGSimple(TGSimple<Value> tg, int permut[])
	{
		super(tg, permut);
		stateOwners = new StateOwnersSimple(tg.stateOwners, permut);
		playerInfo = new PlayerInfo(tg.playerInfo);
		activeStates = (BitSet) tg.activeStates.clone();
	}

	// Mutators

	@Override
	public void initialise(int numStates)
	{
		super.initialise(numStates);
		stateOwners = new StateOwnersSimple(numStates);
		playerInfo = new PlayerInfo();
		activeStates = new BitSet(numStates);
	}

	@Override
	public void clearState(int s)
	{
		super.clearState(s);
		stateOwners.clearState(s);
	}

	@Override
	public void addStates(int numToAdd)
	{
		super.addStates(numToAdd);
		// Assume all player 1
		for (int i = 0; i < numToAdd; i++) {
			stateOwners.addState(0);
		}
		activeStates.set(numStates - numToAdd, numStates);
	}

	/**
	 * Add a new (player {@code p}) state and return its index.
	 * @param p Player who owns the new state (0-indexed)
	 */
	public int addState(int p)
	{
		int s = super.addState();
		stateOwners.setPlayer(s, p);
		activeStates.set(s);
		return s;
	}

	/**
	 * Set the player that owns state {@code s} to {@code p}.
	 * @param s State to be modified (0-indexed)
	 * @param p Player who owns the state (0-indexed)
	 */
	public void setPlayer(int s, int p)
	{
		stateOwners.setPlayer(s, p);
	}

	@Override
	public void buildFromExplicitImport(ExplicitModelImporter modelImporter) throws PrismException
	{
		super.buildFromExplicitImport(modelImporter);
		modelImporter.extractStateOwners(this::setPlayer);
	}

	/**
	 * Copy the player info from another model
	 */
	public void copyPlayerInfo(PlayerInfoOwner model)
	{
		playerInfo = new PlayerInfo(model.getPlayerInfo());
	}

	// Accessors (for Model)

	@Override
	public void checkForDeadlocks(BitSet except) throws PrismException
	{
		for (int i = 0; i < numStates; i++) {
			if (trans.get(i).isEmpty() && (except == null || !except.get(i)))
				throw new PrismException("Game has a deadlock in state " + i + (statesList == null ? "" : ": " + statesList.get(i)));
		}
	}

	// Accessors (for PlayerInfoOwner)

	@Override
	public PlayerInfo getPlayerInfo()
	{
		return playerInfo;
	}

	// Accessors (for TG)

	@Override
	public int getPlayer(int s)
	{
		return playerInfo.getPlayer(stateOwners.getPlayer(s));
	}

	@Override
	public BitSet getActiveStates()
	{
		return activeStates;
	}

	// Attractor

	// Attractor is computed through a backward breadth-first search.
	@Override
	public RegionStrategy attractor(int player, BitSet target, prism.PrismComponent parent)
	{
		Map<Integer, Integer> outdegree = new HashMap<>();
		getActiveStates().stream().forEach(s -> {
			if (getPlayer(s) != player) {
				outdegree.put(s, getNumTransitions(s));
			}
		});

		RegionStrategy attractor = new RegionStrategy();
		attractor.setRegion((BitSet) target.clone());

		Queue<Integer> queue = new LinkedList<Integer>();
		target.stream().forEach(s -> queue.add(s));
		PredecessorRelation pre = getPredecessorRelation(parent, true);

		while (!queue.isEmpty()) {
			int from = queue.poll();

			for (int to : pre.getPre(from)) {
				// Self-loop
				if (attractor.getRegion().get(to)) {
					if (getPlayer(to) == player) {
						attractor.getStrategy().put(to, from);
					}
					continue;
				}

				// Player whose goal it is to reach the target set
				if (getPlayer(to) == player) {
					if (attractor.getRegion().get(from)) {
						queue.add(to);
						attractor.getRegion().set(to);
						attractor.getStrategy().put(to, from);
					}
				} else { // Player trying to prevent this
					outdegree.put(to, outdegree.get(to) - 1);
					if (outdegree.get(to) == 0) {
						queue.add(to);
						attractor.getRegion().set(to);
					}
				}
			}
		}

		return attractor;
	}

	@Override
	public TG subgame(BitSet states)
	{
		return subgameByStateFilter(s -> states.get(s));
	}

	@Override
	public TG difference(BitSet states)
	{
		return subgameByStateFilter(s -> !states.get(s));
	}

	// Construct the subgame with a given filter of states
	private TG subgameByStateFilter(Predicate<Integer> pred)
	{
		TGSimple tg = new TGSimple(numStates);

		activeStates.stream().forEach(s -> {
			if (pred.test(s)) {
				tg.trans.set(s, new ArrayList<>());
				tg.setPlayer(s, getPlayer(s));
				tg.activeStates.set(s);

				SuccessorsIterator successors = getSuccessors(s);
				while (successors.hasNext()) {
					int succ = successors.next();
					if (pred.test(succ)) {
						tg.addTransition(s, succ);
					}
				}
			}
		});

		return tg;
	}

	@Override
	public Iterator<Entry<Integer, Double>> getTransitionsIterator(int s, int i)
	{
		return Collections.singletonMap(trans.get(s).get(i), 1D).entrySet().iterator();
	}

}
