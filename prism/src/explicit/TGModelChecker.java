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

import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import acceptance.AcceptanceParity;
import acceptance.AcceptanceType;
import parser.ast.Coalition;
import parser.ast.Expression;
import parser.ast.ExpressionStrategy;
import parser.ast.ExpressionTemporal;
import parser.ast.ExpressionUnaryOp;
import prism.PrismComponent;
import prism.PrismException;
import prism.PrismNotSupportedException;

/**
 * Explicit-state model checker for turn-based games (TGs).
 */
public class TGModelChecker extends NonProbModelChecker
{
	// Flags/settings
	// (NB: defaults do not necessarily coincide with PRISM)

	// Method used for solving parity games
	protected ParityGameMethod parityGameMethod = ParityGameMethod.ZIELONKA;
	
	// Methods used for solving parity games
	public enum ParityGameMethod {
		ZIELONKA, PRIORITY_PROMOTION, SMALL_PROG_MEASURES;
		public String fullName()
		{
			switch (this) {
			case ZIELONKA:
				return "Zielonka recursive";
			case PRIORITY_PROMOTION:
				return "Priority promotion";
			case SMALL_PROG_MEASURES:
				return "Small progress measures";
			default:
				return this.toString();
			}
		}
	};

	/**
	 * Create a new TGModelChecker, inherit basic state from parent (unless null).
	 */
	public TGModelChecker(PrismComponent parent) throws PrismException
	{
		super(parent);
	}

	// Model checking functions

	@Override
	public StateValues checkExpression(Model model, Expression expr, BitSet statesOfInterest) throws PrismException
	{
		StateValues res;

		// <<>> or [[]] operator
		if (expr instanceof ExpressionStrategy) {
			return checkExpressionStrategy(model, (ExpressionStrategy) expr, statesOfInterest);
		}
		// Otherwise, use the superclass
		else {
			res = super.checkExpression(model, expr, statesOfInterest);
		}

		return res;
	}

	/**
	 * Model check a <<>> or [[]] operator expression and return the values for the statesOfInterest.
	 * * @param statesOfInterest the states of interest, see checkExpression()
	 */
	protected StateValues checkExpressionStrategy(Model model, ExpressionStrategy expr, BitSet statesOfInterest) throws PrismException
	{
		// Only support <<>> right now, not [[]]
		if (!expr.isThereExists())
			throw new PrismNotSupportedException("The " + expr.getOperatorString() + " operator is not yet supported");

		// Multiple (>2) coalitions not supported
		if (expr.getNumCoalitions() > 1) {
			throw new PrismNotSupportedException("The " + expr.getOperatorString() + " operator can only contain one coalition");
		}
		// Extract coalition info
		Coalition coalition = expr.getCoalition();

		// For now, just support a single path formula in parentheses for now
		List<Expression> exprs = expr.getOperands();
		if (exprs.size() > 1) {
			throw new PrismException("Cannot currently check strategy operators with lists of expressions");
		}
		Expression exprSub = exprs.get(0);
		if (!Expression.isParenth(exprSub)) {
			throw new PrismException("Cannot currently check this strategy formula");
		}
		exprSub = ((ExpressionUnaryOp) exprSub).getOperand();
		if (exprSub.isSimplePathFormula() && exprSub instanceof ExpressionTemporal && ((ExpressionTemporal) exprSub).getOperator() == ExpressionTemporal.P_F) {
			return checkReach(model, (ExpressionTemporal) exprSub, coalition, statesOfInterest);
		} else {
			return checkLTL(model, (ExpressionTemporal) exprSub, coalition, statesOfInterest);
		}
	}

	/**
	 * Model check a reachability temporal operator from, inside a <<>> 
	 */
	protected StateValues checkReach(Model model, ExpressionTemporal expr, Coalition coalition, BitSet statesOfInterest) throws PrismException
	{
		// Model check operands for all states
		BitSet target = checkExpression(model, expr.getOperand2(), null).getBitSet();

		// Compute/return the result
		BitSet result = computeReach((TG) model, target, coalition);

		return StateValues.createFromBitSet(result, model);
	}

	/**
	 * Model check an LTL formula from inside a <<>> 
	 */
	protected StateValues checkLTL(Model model, ExpressionTemporal expr, Coalition coalition, BitSet statesOfInterest) throws PrismException
	{
		// For LTL model checking routines
		LTLModelChecker mcLtl = new LTLModelChecker(this);

		// Build product of TG and automaton
		AcceptanceType[] allowedAcceptance = { AcceptanceType.PARITY };
		LTLModelChecker.LTLProduct<TG<Double>> product = mcLtl.constructProductTG(this, (TG) model, expr, statesOfInterest, allowedAcceptance);
		
		// Solve parity objective on product
		TGModelChecker mcProduct = new TGModelChecker(this);
		mcProduct.inheritSettings(this);
		AcceptanceParity accPar = (AcceptanceParity) product.getAcceptance();
		BitSet result = mcProduct.computeParity((TG) product.getProductModel(), accPar, coalition);
		
		return StateValues.createFromBitSet(result, model);
	}

	/**
	 * Solve a game with a reachability objective,
	 * i.e., compute the states from which the players in {@code coalition} have a strategy
	 * to ensure that {@code target} is reached
	 * @param tg The TG
	 * @param target Target states
	 * @param coalition The coalition of players which define player 1
	 */
	protected BitSet computeReach(TG tg, BitSet target, Coalition coalition) throws PrismException
	{
		// Temporarily make the model a 2-player TG (if not already) by setting coalition
		tg.setCoalition(coalition);
		BitSet res = computeReach(tg, target);
		tg.setCoalition(null);
		return res;
	}

	/**
	 * Solve a 2-player game with a reachability objective,
	 * i.e., compute the states from which player 1 has a strategy
	 * to ensure that {@code target} is reached
	 * @param tg The TG
	 * @param target Target states
	 */
	protected BitSet computeReach(TG tg, BitSet target) throws PrismException
	{
		// Start reachability
		long timer = System.currentTimeMillis();
		mainLog.println("\nStarting reachability solution...");
		
		// Compute the attractor
		TGSolution soln = new TGSolution();
		RegionStrategy rs = computeAttractor(tg, 0, target);
		soln.set(0, rs);
		soln.get(1).setRegion((BitSet) rs.getRegion().clone());
		soln.get(1).getRegion().flip(0, tg.getNumStates());

		// Complete the winning strategies
		tg.getActiveStates().stream().forEach(s -> {
			// Arbitrary choice for Player 1
			if (tg.getPlayer(s) == 0 && !rs.getStrategy().containsKey(s)) {
				rs.getStrategy().put(s, tg.getSuccessors(s).next());
			}

			// For Player 2, keep the token out of the attractor
			// Arbitrary choice if unable to.
			if (tg.getPlayer(s) == 1) {
				SuccessorsIterator successors = tg.getSuccessors(s);
				while (successors.hasNext()) {
					int succ = successors.next();
					if (!rs.getRegion().get(succ)) {
						soln.get(1).getStrategy().put(s, succ);
						break;
					}
				}

				if (!rs.getStrategy().containsKey(s)) {
					soln.get(1).getStrategy().put(s, tg.getSuccessors(s).next());
				}
			}
		});
		
		// Log the solution
		mainLog.println(soln);
		
		// Finished reachability
		timer = System.currentTimeMillis() - timer;
		mainLog.println("Reachability solution took " + timer / 1000.0 + " seconds.");
		
		// Compute solution as Player 1
		return soln.get(0).getRegion();
	}

	/**
	 * Compute the attractor for a 2-player game, for a specified player and target set
	 * @param tg The TG
	 * @param player The player (0-indexed, so 0 or 1)
	 * @param target Target states
	 */
	public RegionStrategy computeAttractor(TG tg, int player, BitSet target)
	{
		// Attractor is computed through a backward breadth-first search.
		Map<Integer, Integer> outdegree = new HashMap<>();
		tg.getActiveStates().stream().forEach(s -> {
			if (tg.getPlayer(s) != player) {
				outdegree.put(s, tg.getNumTransitions(s));
			}
		});

		RegionStrategy attractor = new RegionStrategy();
		attractor.setRegion((BitSet) target.clone());

		Queue<Integer> queue = new LinkedList<Integer>();
		target.stream().forEach(s -> queue.add(s));
		PredecessorRelation pre = tg.getPredecessorRelation(this, true);

		while (!queue.isEmpty()) {
			int from = queue.poll();

			for (int to : pre.getPre(from)) {
				// Self-loop
				if (attractor.getRegion().get(to)) {
					if (tg.getPlayer(to) == player) {
						attractor.getStrategy().put(to, from);
					}
					continue;
				}

				// Player whose goal it is to reach the target set
				if (tg.getPlayer(to) == player) {
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

	/**
	 * Solve a game with a parity objective,
	 * i.e., compute the states from which the players in {@code coalition} have a strategy
	 * to ensure that the minimum/maximum priority observed infinitely often is odd/even.
	 * @param tg The TG
	 * @param accPar Parity acceptance condition
	 * @param coalition The coalition of players which define player 1
	 */
	protected BitSet computeParity(TG tg, AcceptanceParity accPar, Coalition coalition) throws PrismException
	{
		// Temporarily make the model a 2-player TG (if not already) by setting coalition
		tg.setCoalition(coalition);
		// Get priorities for states
		List<Integer> priorities = accPar.getPriorities(tg.getNumStates());
		// Replace unknown priorities and convert all to max-even if needed
		AcceptanceParity.replaceMissingPriorities(priorities, accPar.getObjective());
		AcceptanceParity.convertPrioritiesToEven(priorities, accPar.getParity());
		AcceptanceParity.convertPrioritiesToMax(priorities, accPar.getObjective());
		//mainLog.println(priorities);
		BitSet res = computeParity(tg, priorities);
		tg.setCoalition(null);
		return res;
	}

	/**
	 * Solve a game with a (max, even) parity objective,
	 * i.e., compute the states from which the players in {@code coalition} have a strategy
	 * to ensure that the maximum priority observed infinitely often is even.
	 * @param tg The TG
	 * @param priorities Priorities for states of the TG
	 * @param coalition The coalition of players which define player 1
	 */
	protected BitSet computeParity(TG tg, List<Integer> priorities, Coalition coalition) throws PrismException
	{
		// Temporarily make the model a 2-player TG (if not already) by setting coalition
		tg.setCoalition(coalition);
		BitSet res = computeParity(tg, priorities);
		tg.setCoalition(null);
		return res;
	}

	/**
	 * Solve a 2-player game with a (max, even) parity objective,
	 * i.e., compute the states from which player 1 has a strategy
	 * to ensure that the maximum priority observed infinitely often is even.
	 * @param tg The TG
	 * @param priorities Priorities for states of the TG
	 */
	protected BitSet computeParity(TG tg, List<Integer> priorities) throws PrismException
	{
		// Start parity solution
		long timer = System.currentTimeMillis();
		mainLog.println("\nStarting parity solution...");
		
		// Solve the game
		PG pg = new PG(tg, priorities);
		PGSolver pgsolver = null;
		switch (parityGameMethod) {
		case PRIORITY_PROMOTION:
			pgsolver = new PriorityPromotionSolver(this);
			break;
		case SMALL_PROG_MEASURES:
			pgsolver = new SmallProgressMeasuresSolver(this);
			break;
		case ZIELONKA:
		default:
			pgsolver = new ZielonkaRecursiveSolver(this);
			break;
		
		}
		TGSolution soln = pgsolver.solve(pg);
		
		// Log the solution
		mainLog.println(soln);
		
		// Finished parity solution
		timer = System.currentTimeMillis() - timer;
		mainLog.println("Parity solution took " + timer / 1000.0 + " seconds.");
		
		// Compute solution as Player 1
		return soln.get(0).getRegion();
	}
}
