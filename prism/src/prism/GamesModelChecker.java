//==============================================================================
//	
//	Copyright (c) 2016-
//	Authors:
//	* Gabriel Santos <gabriel.santos@cs.ox.uk>
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

package prism;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dv.DoubleVector;
import explicit.MinMax;
import jdd.JDD;
import jdd.JDDNode;
import jdd.JDDVars;
import mtbdd.PrismMTBDD;
import parser.ast.Coalition;
import parser.ast.Expression;
import parser.ast.ExpressionFunc;
import parser.ast.ExpressionProb;
import parser.ast.ExpressionReward;
import parser.ast.ExpressionStrategy;
import parser.ast.ExpressionTemporal;
import parser.ast.ExpressionUnaryOp;
import parser.ast.ModulesFile;
import parser.ast.PropertiesFile;
import parser.ast.RelOp;
import parser.type.TypeBool;
import parser.type.TypePathBool;
import parser.type.TypePathDouble;

public class GamesModelChecker extends NonProbModelChecker {
	
	protected GamesModel model;
	protected JDDVars allDDNondetVars;
	protected JDDVars allDDPlayersVars;
	protected JDDNode idsTree;
	protected JDDNode nondetmask;
	protected JDDVars[] playersVars;
	protected JDDNode[] ddPlayersIds;	
	protected JDDNode stateRewards;
	protected JDDNode transRewards;
	
	protected JDDNode ddc1;
	protected JDDNode ddc2;
	
	protected Map<Integer, String> playersNames;
	protected int numPlayers;
	
	protected boolean precomp;
	protected boolean prob0;
	protected boolean prob1;
	
	protected StateModelChecker smc;
	protected ModulesFile mFile;
	
	protected double epsilon;
	protected boolean fairness;
	
	public GamesModelChecker(Prism prism, Model m, PropertiesFile pf) throws PrismException {
		super(prism, m, pf);
		if(!(m instanceof GamesModel)) {
			throw new PrismException("Wrong model type passed to GamesModelChecker.");
		}
		
		model = (GamesModel) m;
		
		mainLog = prism.getMainLog();
		allDDNondetVars = model.getAllDDNondetVars();	
		allDDPlayersVars = model.getAllDDPlayersVars();
		idsTree = model.getIdsTree();
		nondetmask = model.getNondetMask();
		playersVars = model.getPlayersVars();
		ddPlayersIds = model.getDdPlyaersIds();
		numPlayers = model.getNumPlayers();
		playersNames = model.getPlayersNames();
		
		stateRewards = model.getStateRewards();
		transRewards = model.getTransRewards();
		
		precomp = prism.getPrecomp();
		prob0 = prism.getProb0();
		prob1 = prism.getProb1();
		
		epsilon = 0.000001; // needs changing
		
		smc = new StateModelChecker(prism, model, pf);
	}
		
	// Model checking functions
	
	@Override
	public StateValues checkExpression(Expression expr, JDDNode statesOfInterest) throws PrismException {
		StateValues res;

		// <<>> or [[]] operator
		if (expr instanceof ExpressionStrategy) {
			res = checkExpressionStrategy((ExpressionStrategy) expr, statesOfInterest);
		}
		// P operator
		else if (expr instanceof ExpressionProb) {
			res = checkExpressionProb((ExpressionProb) expr, statesOfInterest);
		}
		// R operator
		else if (expr instanceof ExpressionReward) {
			res = checkExpressionReward((ExpressionReward) expr, statesOfInterest);
		}
		// Multi-objective
		else if (expr instanceof ExpressionFunc) {
			// Detect "multi" function
			if (((ExpressionFunc) expr).getName().equals("multi")) {
				throw new PrismNotSupportedException("Not currently supported by the MTBDD engine");
			}
			// For any other function, check as normal
			else {
				res = super.checkExpression(expr, statesOfInterest);
			}
		}
		// Otherwise, use the superclass
		else {
			res = super.checkExpression(expr, statesOfInterest);
		}

		// Filter out non-reachable states from solution
		// (only necessary for symbolically stored vectors)
		if (res instanceof StateValuesMTBDD)
			res.filter(reach);

		return res;
	}
	
	/**
	 * Model check a <<>> or [[]] operator expression.
	 * The result will have valid results at least for the states of interest (use model.getReach().copy() for all reachable states)
	 * <br>[ REFS: <i>result</i>, DEREFS: statesOfInterest ]
	 */
	protected StateValues checkExpressionStrategy(ExpressionStrategy expr, JDDNode statesOfInterest) throws PrismException
	{
		// Will we be quantifying universally or existentially over strategies/adversaries?
		boolean forAll = !expr.isThereExists();
		
		// Extract coalition info
		Coalition coalition = expr.getCoalition();
		// Deal with the coalition operator here and then remove it
		if (coalition != null && !model.getModelType().multiplePlayers()) {
			if (coalition.isEmpty()) {
				// An empty coalition negates the quantification ("*" has no effect)
				forAll = !forAll;
			}
			coalition = null;
		}
		
		// Process operand(s)
		List<Expression> exprs = expr.getOperands();
		// Pass onto relevant method:
		// Single P operator
		if (exprs.size() == 1 && exprs.get(0) instanceof ExpressionProb) {
			return checkExpressionProb((ExpressionProb) exprs.get(0), forAll, statesOfInterest, coalition);
		}
		// Single R operator
		else if (exprs.size() == 1 && exprs.get(0) instanceof ExpressionReward) {
			return checkExpressionReward((ExpressionReward) exprs.get(0), forAll, statesOfInterest, coalition);
		}
		// Anything else is treated as multi-objective 
		else {
			throw new PrismNotSupportedException("Not currently supported by the MTBDD engine");
		}
	}
	
	/**
	 * Model check a P operator expression.
	 * The result will have valid results at least for the states of interest (use model.getReach().copy() for all reachable states)
	 * <br>[ REFS: <i>result</i>, DEREFS: statesOfInterest ]
	 */
	protected StateValues checkExpressionProb(ExpressionProb expr, JDDNode statesOfInterest) throws PrismException
	{
		// Use the default semantics for a standalone P operator
		// (i.e. quantification over all strategies)
		return checkExpressionProb(expr, true, statesOfInterest, null);
	}
	
	/**
	 * Model check a P operator expression.
	 * The result will have valid results at least for the states of interest (use model.getReach().copy() for all reachable states)
	 * <br>[ REFS: <i>result</i>, DEREFS: statesOfInterest ]
	 *
	 * @param expr The P operator expression
	 * @param forAll Are we checking "for all strategies" (true) or "there exists a strategy" (false)? [irrelevant for numerical (=?) queries]
	 */
	protected StateValues checkExpressionProb(ExpressionProb expr, boolean forAll, JDDNode statesOfInterest, Coalition coalition) throws PrismException {
		
		// Get info from P operator
		OpRelOpBound opInfo = expr.getRelopBoundInfo(constantValues);
		MinMax minMax = opInfo.getMinMax(model.getModelType(), forAll, coalition);
					
		// Players get index i in the modulesfile and for the DD variables
		// they're mapped to i+1 with respect to the name
		
		ArrayList<ArrayList<Integer>> coalitions =  new ArrayList<ArrayList<Integer>>();
		
		coalitions.add(new ArrayList<Integer>());
		coalitions.add(new ArrayList<Integer>());
						
		System.out.println("isMax: " + minMax.isMax());
		System.out.println("isMin: " + minMax.isMin());
		
		if(expr.getRelOp().toString().equals("max=") || expr.getRelOp().toString().equals(">=") ) { //has to be changed
			//System.out.println("Pmax");
			for(Integer i : playersNames.keySet()) {
				if(coalition == null) {
					coalitions.get(0).add(i-1);
				}
				else if(minMax.getCoalition().isPlayerIndexInCoalition(i, playersNames)) {
					//System.out.println("Player " + i + " added to coalition 0");
					coalitions.get(0).add(i-1);
				}
				else {
					//System.out.println("Player " + i + " added to coalition 1");
					coalitions.get(1).add(i-1);
				}
			}
		}
		else {
			//System.out.println("Pmin");
			for(Integer i : playersNames.keySet()) {
				if(coalition == null) {
					coalitions.get(1).add(i-1);
				}
				else if(minMax.getCoalition().isPlayerIndexInCoalition(i, playersNames)) {
					//System.out.println("Player " + i + " added to coalition 1");
					coalitions.get(1).add(i-1);
				}
				else {
					//System.out.println("Player " + i + " added to coalition 0");
					coalitions.get(0).add(i-1);
				}
			}
		}
		
		// Check for trivial (i.e. stupid) cases

		if (opInfo.isTriviallyTrue()) {
			mainLog.printWarning("Checking for probability " + opInfo.relOpBoundString() + " - formula trivially satisfies all states");
			JDD.Ref(reach);
			JDD.Deref(statesOfInterest);
			return new StateValuesMTBDD(reach, model);
		} else if (opInfo.isTriviallyFalse()) {
			mainLog.printWarning("Checking for probability " + opInfo.relOpBoundString() + " - formula trivially satisfies no states");
			JDD.Deref(statesOfInterest);
			return new StateValuesMTBDD(JDD.Constant(0), model);
		}

		// Compute probabilities
		boolean qual = opInfo.isQualitative() && precomp && prob0 && prob1;
		StateValues probs = checkProbPathFormula(expr.getExpression(), qual, minMax.isMin(), statesOfInterest, coalitions);

		// Print out probabilities
		if (verbose) {
			mainLog.print("\n" + (minMax.isMin() ? "Minimum" : "Maximum") + " probabilities (non-zero only) for all states:\n");
			probs.print(mainLog);
		}

		// For =? properties, just return values
		if (opInfo.isNumeric()) {
			return probs;
		}
		// Otherwise, compare against bound to get set of satisfying states
		else {
			JDDNode sol = probs.getBDDFromInterval(opInfo.getRelOp(), opInfo.getBound());
			// remove unreachable states from solution
			JDD.Ref(reach);
			sol = JDD.And(sol, reach);
			// free vector
			probs.clear();
			return new StateValuesMTBDD(sol, model);
		}
	}

	/**
	 * Model check an R operator expression.
	 * The result will have valid results at least for the states of interest (use model.getReach().copy() for all reachable states)
	 * <br>[ REFS: <i>result</i>, DEREFS: statesOfInterest ]
	 */
	protected StateValues checkExpressionReward(ExpressionReward expr, JDDNode statesOfInterest) throws PrismException
	{
		// Use the default semantics for a standalone R operator
		// (i.e. quantification over all strategies)
	    return checkExpressionReward(expr, true, statesOfInterest, null);
	}
	
	/**
	 * Model check an R operator expression.
	 * The result will have valid results at least for the states of interest (use model.getReach().copy() for all reachable states)
	 * <br>[ REFS: <i>result</i>, DEREFS: statesOfInterest ]
	 *
	 * @param expr The R operator expression
	 * @param forAll Are we checking "for all strategies" (true) or "there exists a strategy" (false)? [irrelevant for numerical (=?) queries]
	 */
	protected StateValues checkExpressionReward(ExpressionReward expr, boolean forAll, JDDNode statesOfInterest, Coalition coalition) throws PrismException
	{
		// Get info from R operator
	        if(expr.getRewardStructIndexDiv() != null)
		        throw new PrismException("Ratio rewards not supported with the selected engine and module type.");
		OpRelOpBound opInfo = expr.getRelopBoundInfo(constantValues);
		MinMax minMax = opInfo.getMinMax(model.getModelType(), forAll, coalition);

		// Players get index i in the modulesfile and for the DD variables
		// they're mapped to i+1 with respect to the name
		
		ArrayList<ArrayList<Integer>> coalitions =  new ArrayList<ArrayList<Integer>>();
		
		coalitions.add(new ArrayList<Integer>());
		coalitions.add(new ArrayList<Integer>());
				
		System.out.println("isMax: " + minMax.isMax());
		System.out.println("isMin: " + minMax.isMin());
		
		if(expr.getRelOp().toString().equals("max=") || expr.getRelOp().toString().equals(">=")) { //has to be changed
			for(Integer i : playersNames.keySet()) {
				if(coalition == null) {
					coalitions.get(0).add(i-1);
				}
				else if(minMax.getCoalition().isPlayerIndexInCoalition(i, playersNames)) {
					//System.out.println("Player " + i + " added to coalition 0");
					coalitions.get(0).add(i-1);
				}
				else {
					//System.out.println("Player " + i + " added to coalition 1");
					coalitions.get(1).add(i-1);
				}
			}
		}
		else {
			for(Integer i : playersNames.keySet()) {
				if(coalition == null) {
					coalitions.get(1).add(i-1);
				}
				else if(minMax.getCoalition().isPlayerIndexInCoalition(i, playersNames)) {
					//System.out.println("Player " + i + " added to coalition 1");
					coalitions.get(1).add(i-1);
				}
				else {
					//System.out.println("Player " + i + " added to coalition 0");
					coalitions.get(0).add(i-1);
				}
			}
		}
		
		// Get rewards
		Object rs = expr.getRewardStructIndex();
		JDDNode stateRewards = getStateRewardsByIndexObject(rs, model, constantValues);
		JDDNode transRewards = getTransitionRewardsByIndexObject(rs, model, constantValues);

		// Compute rewards
		StateValues rewards = null;
		Expression expr2 = expr.getExpression();	
		if(expr2.getType() instanceof TypePathDouble) {
			ExpressionTemporal exprTemp = (ExpressionTemporal) expr2;
			switch (exprTemp.getOperator()) {
			case ExpressionTemporal.R_C:
				if(!exprTemp.hasBounds()) {
					//currently ignoring states of interest
					JDD.Deref(statesOfInterest);
					rewards = checkRewardTotal(stateRewards, transRewards, coalitions);
				}
				break;
			case ExpressionTemporal.R_Fc:		
				//currently ignoring states of interest
				JDD.Deref(statesOfInterest);
				rewards = checkRewardCumul(exprTemp, stateRewards, transRewards, coalitions);
				break;
			default:	
				throw new PrismNotSupportedException("Not currently supported by the MTBDD engine");
			}
		} 
		else if(expr2.getType() instanceof TypePathBool || expr2.getType() instanceof TypeBool) {
			throw new PrismNotSupportedException("Not currently supported by the MTBDD engine");
		}
				
		/*
		if (expr2.getType() instanceof TypePathDouble) {
			ExpressionTemporal exprTemp = (ExpressionTemporal) expr2;
			switch (exprTemp.getOperator()) {
			case ExpressionTemporal.R_C:
				if (exprTemp.hasBounds()) {
					rewards = checkRewardCumul(exprTemp, stateRewards, transRewards, minMax.isMin(), statesOfInterest, coalitions);
				} else {
					rewards = checkRewardTotal(exprTemp, stateRewards, transRewards, minMax.isMin(), statesOfInterest, coalitions);
				}
				break;
			case ExpressionTemporal.R_I:
				rewards = checkRewardInst(exprTemp, stateRewards, transRewards, minMax.isMin(), statesOfInterest);
			}
		} else if (expr2.getType() instanceof TypePathBool || expr2.getType() instanceof TypeBool) {
			rewards = checkRewardPathFormula(expr2, stateRewards, transRewards, minMax.isMin(), statesOfInterest);
		}
		*/

		if (rewards == null)
			throw new PrismException("Unrecognised operator in R operator");

		// print out rewards
		if (verbose) {
			mainLog.print("\n" + (minMax.isMin() ? "Minimum" : "Maximum") + " rewards (non-zero only) for all states:\n");
			rewards.print(mainLog);
		}

		// For =? properties, just return values
		if (opInfo.isNumeric()) {
			return rewards;
		}
		// Otherwise, compare against bound to get set of satisfying states
		else {
			JDDNode sol = rewards.getBDDFromInterval(opInfo.getRelOp(), opInfo.getBound());
			// remove unreachable states from solution
			JDD.Ref(reach);
			sol = JDD.And(sol, reach);
			// free vector
			rewards.clear();
			return new StateValuesMTBDD(sol, model);
		}
	}
	
	// Model checking functions
	
	/**
	 * Compute probabilities for the contents of a P operator.
	 * The result will have valid results at least for the states of interest (use model.getReach().copy() for all reachable states)
	 * <br>[ REFS: <i>result</i>, DEREFS: statesOfInterest ]
	 */
	protected StateValues checkProbPathFormula(Expression expr, boolean qual, boolean min, JDDNode statesOfInterest, ArrayList<ArrayList<Integer>> coalitions) throws PrismException
	{
		// No support for reward-bounded path formulas (i.e. of the form R(path)~r)
		if (Expression.containsRewardBoundedPathFormula(expr)) {
			throw new PrismException("Reward-bounded path formulas not supported");
		}
		
		// Test whether this is a simple path formula (i.e. PCTL)
		// and whether we want to use the corresponding algorithms
		boolean useSimplePathAlgo = expr.isSimplePathFormula();

		if (useSimplePathAlgo &&
		    prism.getSettings().getBoolean(PrismSettings.PRISM_PATH_VIA_AUTOMATA) &&
		    LTLModelChecker.isSupportedLTLFormula(model.getModelType(), expr)) {
			// If PRISM_PATH_VIA_AUTOMATA is true, we want to use the LTL engine
			// whenever possible
			useSimplePathAlgo = false;
		}

		if (useSimplePathAlgo) {
			return checkProbPathFormulaSimple(expr, qual, min, statesOfInterest, coalitions);
		} else {
			/*
			return checkProbPathFormulaLTL(expr, qual, min, statesOfInterest);
			*/
			throw new PrismNotSupportedException("Not currently supported by the MTBDD engine");
		}
	}

	/**
	 * Compute probabilities for a simple, non-LTL path operator.
	 * The result will have valid results at least for the states of interest (use model.getReach().copy() for all reachable states)
	 * <br>[ REFS: <i>result</i>, DEREFS: statesOfInterest ]
	 */
	protected StateValues checkProbPathFormulaSimple(Expression expr, boolean qual, boolean min, JDDNode statesOfInterest, ArrayList<ArrayList<Integer>> coalitions) throws PrismException
	{
		boolean negated = false;
		StateValues probs = null;

		expr = Expression.convertSimplePathFormulaToCanonicalForm(expr);

		// Negation
		if (expr instanceof ExpressionUnaryOp &&
		    ((ExpressionUnaryOp)expr).getOperator() == ExpressionUnaryOp.NOT) {
			// mark as negated, switch from min to max and vice versa
			negated = true;
			min = !min;
			expr = ((ExpressionUnaryOp)expr).getOperand();
		}

		if (expr instanceof ExpressionTemporal) {
			ExpressionTemporal exprTemp = (ExpressionTemporal) expr;
			// Next
			if (exprTemp.getOperator() == ExpressionTemporal.P_X) {
				probs = checkProbNext(exprTemp, statesOfInterest, coalitions);
			}
			// Until
			else if (exprTemp.getOperator() == ExpressionTemporal.P_U) {
				if (exprTemp.hasBounds()) {
					probs = checkProbBoundedUntil(exprTemp, min, statesOfInterest, coalitions);

				} else {
					probs = checkProbUntil(exprTemp, qual, min, statesOfInterest, coalitions);
				}
			}
		}

		if (probs == null)
			throw new PrismException("Unrecognised path operator in P operator");

		if (negated) {
			// Subtract from 1 for negation
			probs.subtractFromOne();
		}

		return probs;
	}
	
	/**
	 * Compute rewards for a reachability reward operator (either simple reachability or co-safe LTL).
	 * The result will have valid results at least for the states of interest (use model.getReach().copy() for all reachable states)
	 * <br>[ REFS: <i>result</i>, DEREFS: statesOfInterest ]
	 */
	/*
	protected StateValues checkRewardPathFormula(Expression expr, JDDNode stateRewards, JDDNode transRewards, boolean min, JDDNode statesOfInterest) throws PrismException
	{
		if (Expression.isReach(expr)) {
			return checkRewardReach((ExpressionTemporal) expr, stateRewards, transRewards, min, statesOfInterest);
		}
		else if (Expression.isCoSafeLTLSyntactic(expr, true)) {
			return checkRewardCoSafeLTL(expr, stateRewards, transRewards, min, statesOfInterest);
		}
		JDD.Deref(statesOfInterest);
		throw new PrismException("R operator contains a path formula that is not syntactically co-safe: " + expr);
	}
	 */
	/**
	 * Compute rewards for a reachability reward operator.
	 * The result will have valid results at least for the states of interest (use model.getReach().copy() for all reachable states)
	 * <br>[ REFS: <i>result</i>, DEREFS: statesOfInterest ]
	 */
	/*
	protected StateValues checkRewardReach(ExpressionTemporal expr, JDDNode stateRewards, JDDNode transRewards, boolean min, JDDNode statesOfInterest) throws PrismException {
		
		JDDNode b;
		StateValues rewards = null;

		if (fairness && !min) {
			// Rmax with fairness not supported; Rmin computation is unaffected
			JDD.Deref(statesOfInterest);
			throw new PrismNotSupportedException("Maximum reward computation currently not supported under fairness.");
		}

		// currently, ignore statesOfInterest
		JDD.Deref(statesOfInterest);

		// No time bounds allowed
		if (expr.hasBounds()) {
			throw new PrismNotSupportedException("R operator cannot contain a bounded F operator: " + expr);
		}
		
		// model check operand first, statesOfInterest = all
		b = checkExpressionDD(expr.getOperand2(), model.getReach().copy());

		// print out some info about num states
		// mainLog.print("\nb = " + JDD.GetNumMintermsString(b,
		// allDDRowVars.n()) + " states\n");

		// compute rewards
		try {
			rewards = computeReachRewards(trans, transActions, trans01, stateRewards, transRewards, b, min);
			if(true)
				throw new PrismException("Compute Reach Rewards");
		} catch (PrismException e) {
			JDD.Deref(b);
			throw e;
		}

		// derefs
		JDD.Deref(b);

		return rewards;
	}
	*/
	/**
	 * Compute rewards for a cumulative reward operator.
	 * The result will have valid results at least for the states of interest (use model.getReach().copy() for all reachable states)
	 * <br>[ REFS: <i>result</i>, DEREFS: statesOfInterest ]
	 */
	/*
	protected StateValues checkRewardCumul(ExpressionTemporal expr, JDDNode stateRewards, JDDNode transRewards, boolean min, JDDNode statesOfInterest) throws PrismException {
		int time; // time
		StateValues rewards = null;

		// currently, ignore statesOfInterest
		JDD.Deref(statesOfInterest);

		// check that there is an upper time bound
		if (expr.getUpperBound() == null) {
		        throw new PrismException("Cumulative reward operator without time bound (C) not supported");
		}

		// get info from inst reward
		time = expr.getUpperBound().evaluateInt(constantValues);
		if (time < 0) {
			throw new PrismException("Invalid time bound " + time + " in cumulative reward formula");
		}

		// a trivial case: "<=0"
		if (time == 0) {
			rewards = new StateValuesMTBDD(JDD.Constant(0), model);
		} else {
			// compute rewards
			try {
				//rewards = computeCumulRewards(trans, stateRewards, transRewards, time, min);
				if(true)
					throw new PrismException("Compute Cumul Rewards");
			} catch (PrismException e) {
				throw e;
			}
		}

		return rewards;
	}
	*/
	/**
	 * Compute rewards for a total reward operator.
	 * The result will have valid results at least for the states of interest (use model.getReach().copy() for all reachable states)
	 * <br>[ REFS: <i>result</i>, DEREFS: statesOfInterest ]
	 */
	/*
	protected StateValues checkRewardTotal(ExpressionTemporal expr, JDDNode stateRewards, JDDNode transRewards, boolean min, JDDNode statesOfInterest) throws PrismException {
		// currently, ignore statesOfInterest
		JDD.Deref(statesOfInterest);
		StateValues rewards;
		//rewards = computeTotalRewards(trans, trans01, transActions, stateRewards, transRewards, min);
		if(true)
			throw new PrismException("Compute Cumul Rewards");
		return rewards;
	}
	*/
	/**
	 * Compute probabilities for an (unbounded) until operator.
	 * Note: This method is split into two steps so that the LTL model checker can use the second part directly.
	 * The result will have valid results at least for the states of interest (use model.getReach().copy() for all reachable states)
	 * <br>[ REFS: <i>result</i>, DEREFS: statesOfInterest ]
	 */
	protected StateValues checkProbUntil(ExpressionTemporal expr, boolean qual, boolean min, JDDNode statesOfInterest, ArrayList<ArrayList<Integer>> coalitions) throws PrismException
	{
		JDDNode b1, b2;
		StateValues probs = null;

		// currently, ignore statesOfInterest
		JDD.Deref(statesOfInterest);

		// model check operands first
		b1 = checkExpressionDD(expr.getOperand1(), model.getReach().copy());
		try {
			b2 = checkExpressionDD(expr.getOperand2(), model.getReach().copy());
		} catch (PrismException e) {
			JDD.Deref(b1);
			throw e;
		}

		// print out some info about num states
		// mainLog.print("\nb1 = " + JDD.GetNumMintermsString(b1,
		// allDDRowVars.n()));
		// mainLog.print(" states, b2 = " + JDD.GetNumMintermsString(b2,
		// allDDRowVars.n()) + " states\n");

		try {
			probs = computeProbUntil(b1, b2, qual, min, coalitions);
		} catch (PrismException e) {
			JDD.Deref(b1);
			JDD.Deref(b2);
			throw e;
		}

		// derefs
		JDD.Deref(b1);
		JDD.Deref(b2);

		return probs;
	}
	
	/**
	 * Compute probabilities for a bounded until operator.
	 * The result will have valid results at least for the states of interest (use model.getReach().copy() for all reachable states)
	 * <br>[ REFS: <i>result</i>, DEREFS: statesOfInterest ]
	 */
	protected StateValues checkProbBoundedUntil(ExpressionTemporal expr, boolean min, JDDNode statesOfInterest, ArrayList<ArrayList<Integer>> coalitions) throws PrismException {
		JDDNode b1, b2;
		StateValues probs = null;
		Integer lowerBound;
		IntegerBound bounds;
		int i;

		// currently, ignore statesOfInterest
		JDD.Deref(statesOfInterest);

		// get and check bounds information
		bounds = IntegerBound.fromExpressionTemporal(expr, constantValues, true);

		// model check operands first, statesOfInterest = all
		b1 = checkExpressionDD(expr.getOperand1(), model.getReach().copy());
		try {
			b2 = checkExpressionDD(expr.getOperand2(), model.getReach().copy());
		} catch (PrismException e) {
			JDD.Deref(b1);
			throw e;
		}

		// print out some info about num states
		// mainLog.print("\nb1 = " + JDD.GetNumMintermsString(b1,
		// allDDRowVars.n()));
		// mainLog.print(" states, b2 = " + JDD.GetNumMintermsString(b2,
		// allDDRowVars.n()) + " states\n");

		if (bounds.hasLowerBound()) {
			lowerBound = bounds.getLowestInteger();
		} else {
			lowerBound = 0;
		}

		Integer windowSize = null;  // unbounded
		if (bounds.hasUpperBound()) {
			windowSize = bounds.getHighestInteger() - lowerBound;
		}

		// compute probabilities for Until<=windowSize
		if (windowSize == null) {
			// unbounded
			try {
				/*
				probs = checkProbUntil(b1, b2, false, min, coalitions);
				*/
				probs = computeProbUntil(b1, b2, false, min, coalitions);
			} catch (PrismException e) {
				JDD.Deref(b1);
				JDD.Deref(b2);
				throw e;
			}
		} else if (windowSize == 0) {
			// the trivial case: windowSize = 0
			// prob is 1 in b2 states, 0 otherwise
			JDD.Ref(b2);
			probs = new StateValuesMTBDD(b2, model);
		} else {
			try {
				/*
				probs = computeBoundedUntilProbs(trans, trans01, b1, b2, windowSize, min);
				*/
				probs = computeBoundedUntilProbs(trans, trans01, b1, b2, windowSize, min, coalitions);
			} catch (PrismException e) {
				JDD.Deref(b1);
				JDD.Deref(b2);
				throw e;
			}
		}

		/*
		// perform lowerBound restricted next-step computations to
		// deal with lower bound.
		if (lowerBound > 0) {
			for (i = 0; i < lowerBound; i++) {
				probs = computeRestrictedNext(trans, b1, probs, min);
			}
		}
		*/

		// derefs
		JDD.Deref(b1);
		JDD.Deref(b2);

		return probs;
	}
	
	/*
			throw new PrismNotSupportedException("Not currently supported by the MTBDD engine");
	*/
	
	/**
	 * Compute rewards for a cumulative reward operator.
	 */
	protected StateValues checkRewardCumul(ExpressionTemporal expr, JDDNode stateRewards, JDDNode transRewards, 
										   ArrayList<ArrayList<Integer>> coalitions) throws PrismException {
	
		StateValues rewards = null;

		if(expr.getOperatorSymbol().equals("Fc")) {
			rewards = computeCumulRewards(expr, trans, stateRewards, transRewards, coalitions);
		} 
		else {
	        throw new PrismNotSupportedException("Not currently supported by the MTBDD engine");
		}
		
		return rewards;
	}

	/**
	 * Compute rewards for a total reward operator.
	 * The result will have valid results at least for the states of interest (use model.getReach().copy() for all reachable states)
	 */
	protected StateValues checkRewardTotal(JDDNode stateRewards, JDDNode transRewards, 
										  ArrayList<ArrayList<Integer>> coalitions) throws PrismException {
		
		StateValues rewards = computeTotalRewards(trans, trans01, stateRewards, transRewards, coalitions);
		
		return rewards;
	}
	
	/**
	 * Compute probabilities for a next operator.
	 * The result will have valid results at least for the states of interest (use model.getReach().copy() for all reachable states)
	 * <br>[ REFS: <i>result</i>, DEREFS: statesOfInterest ]
	 */
	protected StateValues checkProbNext(ExpressionTemporal expr, JDDNode statesOfInterest, ArrayList<ArrayList<Integer>> coalitions) throws PrismException {
		
		JDDNode b;
		StateValues probs = null;
		
		// currently, ignore statesOfInterest
		JDD.Deref(statesOfInterest);
		
		b = checkExpressionDD(expr.getOperand2(), model.getReach().copy());
		probs = computeNextProbs(b, coalitions);
		
		JDD.Deref(b);
		
		return probs;
	}
	
	/**
	 * Compute probabilities for a bounded until operator.
	 * The result will have valid results at least for the states of interest (use model.getReach().copy() for all reachable states)
	 */
	protected StateValues checkProbBoundedUntil(ExpressionTemporal expr, boolean min, ArrayList<ArrayList<Integer>> coalitions) throws PrismException {
		JDDNode b1, b2;
		StateValues probs = null;
		Integer lowerBound;
		IntegerBound bounds;
		int i;

		// get and check bounds information
		bounds = IntegerBound.fromExpressionTemporal(expr, constantValues, true);

		// model check operands first
		b1 = checkExpressionDD(expr.getOperand1(), model.getReach().copy());
		
		try {
			b2 = checkExpressionDD(expr.getOperand2(), model.getReach().copy());
		} catch (PrismException e) {
			JDD.Deref(b1);
			throw e;
		}

		// print out some info about num states
		// mainLog.print("\nb1 = " + JDD.GetNumMintermsString(b1,
		// allDDRowVars.n()));
		// mainLog.print(" states, b2 = " + JDD.GetNumMintermsString(b2,
		// allDDRowVars.n()) + " states\n");

		if (bounds.hasLowerBound()) {
			lowerBound = bounds.getLowestInteger();
		} else {
			lowerBound = 0;
		}
		
		Integer windowSize = null;  // unbounded
		if (bounds.hasUpperBound()) {
			windowSize = bounds.getHighestInteger() - lowerBound;
		}
		
		// compute probabilities for Until<=windowSize
		if (windowSize == null) {
			
			//JDD.Deref(b1);
			//JDD.Deref(b2);
			//throw new PrismNotSupportedException("Not currently supported by the MTBDD engine");
			
			// unbounded
			try {
				//probs = checkProbUntil(b1, b2, false,  coalitions);
				probs = computeProbUntil(b1, b2, false, min, coalitions);
			} catch (PrismException e) {
				JDD.Deref(b1);
				JDD.Deref(b2);
				throw e;
			}
					
		} else if (windowSize == 0) {
			// the trivial case: windowSize = 0
			// prob is 1 in b2 states, 0 otherwise
			JDD.Ref(b2);
			probs = new StateValuesMTBDD(b2, model);
		} else {
			try {
				probs = computeBoundedUntilProbs(trans, trans01, b1, b2, windowSize, min, coalitions);
			} catch (PrismException e) {
				JDD.Deref(b1);
				JDD.Deref(b2);
				throw e;
			}
		}

		/*
		// perform lowerBound restricted next-step computations to
		// deal with lower bound.
		if (lowerBound > 0) {
			for (i = 0; i < lowerBound; i++) {
				probs = computeRestrictedNext(trans, b1, probs, min);
			}
		}
		*/

		// derefs
		JDD.Deref(b1);
		JDD.Deref(b2);

		return probs;
	}
	
	protected StateValues computeProbReachFormula(Expression expr, ArrayList<ArrayList<Integer>> coalitions) throws PrismException {
		
		StateValues probs = null;
		JDDNode ts;
		JDDNode ddv;
		
		ts = smc.checkExpressionDD(((ExpressionTemporal) expr).getOperand2(), model.getReach().copy());
		//ddv = valueIterationCoalitions(ts, numPlayers, coalitions, epsilon);
		//ddv = computeProbReachCoalitions(ts, numPlayers, coalitions, epsilon);
		ddv = computeProbReachPlayers(ts, numPlayers, coalitions, epsilon, false, 0);
		//ddv = valueIterationPlayers(ts, numPlayers, coalitions, epsilon);
		
		//ddv = BRTDP.noActBRTDP(start, null, ts, trans, ddsPlayers, nondetmask, null, null, reach, ddIds, allDDRowVars, allDDColVars, allDDNondetVars, coalitions);

		ddv = JDD.SwapVariables(ddv, allDDColVars, allDDRowVars);
		probs = new StateValuesMTBDD(ddv, model);
		
		return probs;
	}
	
	/**
	 * Compute probabilities for an (unbounded) until operator.
	 */
	protected StateValues checkProbUntil(ExpressionTemporal expr, boolean qual, boolean min, ArrayList<ArrayList<Integer>> coalitions) throws PrismException {
		JDDNode b1, b2;
		StateValues probs = null;

		// model check operands first
		b1 = checkExpressionDD(expr.getOperand1(), model.getReach().copy());
		try {
			b2 = checkExpressionDD(expr.getOperand2(), model.getReach().copy());
		} catch (PrismException e) {
			JDD.Deref(b1);
			throw e;
		}

		// print out some info about num states
		// mainLog.print("\nb1 = " + JDD.GetNumMintermsString(b1,
		// allDDRowVars.n()));
		// mainLog.print(" states, b2 = " + JDD.GetNumMintermsString(b2,
		// allDDRowVars.n()) + " states\n");

		try {
			probs = computeProbUntil(b1, b2, qual, min, coalitions);
		} catch (PrismException e) {
			JDD.Deref(b1);
			JDD.Deref(b2);
			throw e;
		}

		// derefs
		JDD.Deref(b1);
		JDD.Deref(b2);

		return probs;
	}
	
	protected StateValues computeProbUntil(JDDNode b1, JDDNode b2, boolean qual, boolean min, ArrayList<ArrayList<Integer>> coalitions) throws PrismException {
		JDDNode yes, no, maybe;
		JDDNode probsMTBDD;
		DoubleVector probsDV;
		StateValues probs = null;
		
		// compute yes/no/maybe states
		if (b2.equals(JDD.ZERO)) {
			yes = JDD.Constant(0);
			JDD.Ref(reach);
			no = reach;
			maybe = JDD.Constant(0);
		} else if (b1.equals(JDD.ZERO)) {
			JDD.Ref(b2);
			yes = b2;
			JDD.Ref(reach);
			JDD.Ref(b2);
			no = JDD.And(reach, JDD.Not(b2));
			maybe = JDD.Constant(0);
		} else {
			JDD.Ref(b2);
			yes = b2;
			
			// no
			JDD.Ref(reach);
			JDD.Ref(b1);
			JDD.Ref(b2);
			no = JDD.And(reach, JDD.Not(JDD.Or(b1, b2)));
			
			// maybe
			JDD.Ref(reach);
			JDD.Ref(yes);
			JDD.Ref(no);
			maybe = JDD.And(reach, JDD.Not(JDD.Or(yes, no)));
		}
		
		// print out yes/no/maybe
		mainLog.print("\nyes = " + JDD.GetNumMintermsString(yes, allDDRowVars.n()));
		mainLog.print(", no = " + JDD.GetNumMintermsString(no, allDDRowVars.n()));
		mainLog.print(", maybe = " + JDD.GetNumMintermsString(maybe, allDDRowVars.n()) + "\n");
		
		// if maybe is empty, we have the answer already...
		if (maybe.equals(JDD.ZERO)) {
			JDD.Ref(yes);
			probs = new StateValuesMTBDD(yes, model);
		}
		// otherwise explicitly compute the remaining probabilities
		else {
			// compute probabilities
			mainLog.println("\nComputing probabilities...");
			mainLog.println("Engine: " + Prism.getEngineString(engine));

			/*** REVIEW THIS ***/
			probsMTBDD = computeBoundedUntilProbs(maybe, yes, Integer.MAX_VALUE, numPlayers, coalitions); 
			
			probs = new StateValuesMTBDD(probsMTBDD, model);
		}

		// derefs
		JDD.Deref(yes);
		JDD.Deref(no);
		JDD.Deref(maybe);
	
		return probs;
	}
	
	// compute probabilities for bounded until
	protected StateValues computeBoundedUntilProbs(JDDNode tr, JDDNode tr01, JDDNode b1, JDDNode b2, int time, boolean min, ArrayList<ArrayList<Integer>> coalitions) throws PrismException {
		JDDNode yes, no, maybe;
		JDDNode probsMTBDD;
		DoubleVector probsDV;
		StateValues probs = null;
		
		// compute yes/no/maybe states
		if (b2.equals(JDD.ZERO)) {
			yes = JDD.Constant(0);
			JDD.Ref(reach);
			no = reach;
			maybe = JDD.Constant(0);
		} else if (b1.equals(JDD.ZERO)) {
			JDD.Ref(b2);
			yes = b2;
			JDD.Ref(reach);
			JDD.Ref(b2);
			no = JDD.And(reach, JDD.Not(b2));
			maybe = JDD.Constant(0);
		} else {
			// yes
			JDD.Ref(b2);
			yes = b2;
			
			/*
			// no
			if (yes.equals(reach)) {
				no = JDD.Constant(0);
			} else if (precomp && prob0) {
				if (min) {
					System.out.println("PROB0E");
					// "min prob = 0" equates to "there exists a prob 0"
					no = PrismMTBDD.Prob0E(tr01, reach, nondetmask, allDDRowVars, allDDColVars, allDDNondetVars, b1, yes);
				} else {
					System.out.println("PROB0A");
					// "max prob = 0" equates to "all probs 0"
					no = PrismMTBDD.Prob0A(tr01, reach, allDDRowVars, allDDColVars, allDDNondetVars, b1, yes);
				}
			} else {
				JDD.Ref(reach);
				JDD.Ref(b1);
				JDD.Ref(b2);
				no = JDD.And(reach, JDD.Not(JDD.Or(b1, b2)));
			}
			*/
			
			// no
			JDD.Ref(reach);
			JDD.Ref(b1);
			JDD.Ref(b2);
			no = JDD.And(reach, JDD.Not(JDD.Or(b1, b2)));
			
			// maybe
			JDD.Ref(reach);
			JDD.Ref(yes);
			JDD.Ref(no);
			maybe = JDD.And(reach, JDD.Not(JDD.Or(yes, no)));
		}
		
		// print out yes/no/maybe
		mainLog.print("\nyes = " + JDD.GetNumMintermsString(yes, allDDRowVars.n()));
		mainLog.print(", no = " + JDD.GetNumMintermsString(no, allDDRowVars.n()));
		mainLog.print(", maybe = " + JDD.GetNumMintermsString(maybe, allDDRowVars.n()) + "\n");

		// if maybe is empty, we have the probabilities already
		if (maybe.equals(JDD.ZERO)) {
			JDD.Ref(yes);
			probs = new StateValuesMTBDD(yes, model);
		}
		// otherwise explicitly compute the remaining probabilities
		else {
			// compute probabilities
			mainLog.println("\nComputing probabilities...");
			mainLog.println("Engine: " + Prism.getEngineString(engine));

			probsMTBDD = computeBoundedUntilProbs(maybe, yes, time, numPlayers, coalitions);
			
			probs = new StateValuesMTBDD(probsMTBDD, model);
		}

		// derefs
		JDD.Deref(yes);
		JDD.Deref(no);
		JDD.Deref(maybe);
		
		return probs;
	}
	
	// compute probabilities for next
	protected StateValues computeNextProbs(JDDNode b, ArrayList<ArrayList<Integer>> coalitions) {
	
		JDDNode tmp;
		StateValues probs = null;

		tmp = computeProbReachPlayers(b, numPlayers , coalitions, epsilon, true, 1);
		tmp = JDD.SwapVariables(tmp, allDDColVars, allDDRowVars);

		probs = new StateValuesMTBDD(tmp, model);
		
		return probs;
	}
	
	// compute cumulative rewards
	protected StateValues computeCumulRewards(ExpressionTemporal expr, JDDNode tr, JDDNode sr, JDDNode trr, 
											  ArrayList<ArrayList<Integer>> coalitions) throws PrismException {
		StateValues rewards = null;
		
		JDDNode ts;
		JDDNode ddrfc;

		// compute rewards
		mainLog.println("\nComputing rewards...");
		mainLog.println("Engine: " + Prism.getEngineString(engine));
		
		ts = smc.checkExpressionDD(expr.getOperand2(), model.getReach().copy());
		ddrfc = computeFcRewards(ts, numPlayers, sr, trr, coalitions, epsilon);	
		ddrfc = JDD.SwapVariables(ddrfc, allDDColVars, allDDRowVars);
		rewards = new StateValuesMTBDD(ddrfc, model);
		return rewards;
	}

	// compute total rewards
	protected StateValues computeTotalRewards(JDDNode tr, JDDNode tr01, JDDNode sr, JDDNode trr, 
											  ArrayList<ArrayList<Integer>> coalitions) throws PrismException {
		StateValues rewards = null;
		JDDNode ddrc;

		// compute rewards
		mainLog.println("\nComputing rewards...");
		mainLog.println("Engine: " + Prism.getEngineString(engine));
		
		ddrc = expectedTotalRewardCoalitions(tr, sr, trr, numPlayers, coalitions, epsilon);
		ddrc = JDD.SwapVariables(ddrc, allDDColVars, allDDRowVars);
		rewards = new StateValuesMTBDD(ddrc, model);
		return rewards;
	}
	
	/*-------------------------------------------------------------------------------------------------------------------------------*/
	/*-------------------------------------------------------------------------------------------------------------------------------*/
	/*-------------------------------------------------------------------------------------------------------------------------------*/
		
	public JDDNode computeProbReachPlayers(JDDNode b2, int numplayers, ArrayList<ArrayList<Integer>> coalitions, double epsilon, boolean bounded, int bound) {
		
		boolean done = false;
		int i;
		
		JDDNode rs = model.getReach();

		JDD.Ref(rs);
		b2 = JDD.And(b2, rs);

		JDDNode nr = PrismMTBDD.Prob0A(trans01, reach, allDDRowVars, allDDColVars, allDDNondetVars, reach, b2);
		JDDNode cr = PrismMTBDD.Prob1A(trans01, reach, nondetmask, allDDRowVars, allDDColVars, allDDNondetVars, nr, b2);
		
		JDD.Ref(cr);
		JDD.Ref(b2);
		JDD.Ref(trans);
		JDDNode tr = JDD.Times(trans, JDD.And(JDD.Not(nr), JDD.Not(b2), JDD.Not(cr)));
		
		JDDNode r = JDD.Or(JDD.SwapVariables(b2.copy(), allDDRowVars, allDDColVars), JDD.SwapVariables(cr.copy(), allDDRowVars, allDDColVars));
		
		JDDNode q = null;
		JDDNode ddTempPlayers[] = new JDDNode[numplayers];
		
		i = 0;
		int player;

		while(!done) {
			q = (i != 0)? r.copy() : JDD.PlusInfinity();

			JDD.Ref(tr);
			r = JDD.MatrixMultiply(tr, r, allDDColVars, JDD.CMU);
			//r = JDD.SwapVariables(r, allDDRowVars, allDDColVars);

			for(int j = 0; j < coalitions.size(); j ++) {
				for(int k = 0; k < coalitions.get(j).size(); k++) {
					player = coalitions.get(j).get(k);
					JDD.Ref(r);
					JDD.Ref(ddPlayersIds[player]);
					ddTempPlayers[player] = JDD.Restrict(r, ddPlayersIds[player]);
					if(j == 0) {
						ddTempPlayers[player] = JDD.MaxAbstract(ddTempPlayers[player], allDDNondetVars);
						//ddTempPlayers[player] = JDD.SwapVariables(ddTempPlayers[player], allDDRowVars, allDDColVars);
					}
					else {
						JDD.Ref(nondetmask);
						//ddTempPlayers[player] = JDD.SwapVariables(ddTempPlayers[player], allDDColVars, allDDRowVars);
						ddTempPlayers[player] = JDD.Apply(JDD.MAX, ddTempPlayers[player], nondetmask);
						ddTempPlayers[player] = JDD.MinAbstract(ddTempPlayers[player], allDDNondetVars);
						//ddTempPlayers[player] = JDD.SwapVariables(ddTempPlayers[player], allDDRowVars, allDDColVars);
					}
				}
			}
			JDD.Deref(r);
			r = ddTempPlayers[0].copy();
			JDD.Deref(ddTempPlayers[0]);

			for(int m = 1; m < numplayers; m++) {
				r = JDD.Apply(JDD.PLUS, r, ddTempPlayers[m]);
			}

			JDD.Ref(cr);
			//r = JDD.Max(r, JDD.SwapVariables(cr, allDDRowVars, allDDColVars));
			r = JDD.Max(r, cr);

			r = JDD.SwapVariables(r, allDDRowVars, allDDColVars);
			
			done = JDD.EqualSupNorm(r, q, epsilon);
			JDD.Deref(q);
			i++;
			
			if(bounded && bound == i) {
				break;
			}
		}		

		//System.out.println("Convergence after " + i + " iterations.");

		//JDD.Ref(r);
		//JDD.Ref(start);
		//JDD.PrintMinterms(mainLog, JDD.Times(r, JDD.SwapVariables(start, allDDRowVars, allDDColVars)));
		
		JDD.Deref(b2);
		//JDD.Deref(r);
		JDD.Deref(tr);
		JDD.Deref(cr);

		return r;
	}
	
	public JDDNode computeProbReachCoalitions(JDDNode p, int numplayers, ArrayList<ArrayList<Integer>> coalitions, double epsilon) {

		boolean done = false;
		int i;
		
		setCoalitions(coalitions);
		
		/*
		int l;
		int n;

		int c1 = Collections.min(coalitions.get(0));
		int c2 = Collections.min(coalitions.get(1));; 

		JDD.Ref(idsTree);
		JDD.Ref(ddPlayersIds[c1]);
		JDDNode ddc1 = JDD.Times(idsTree, ddPlayersIds[c1]);
		JDD.Ref(idsTree);
		JDD.Ref(ddPlayersIds[c2]);
		JDDNode ddc2 = JDD.Times(idsTree, ddPlayersIds[c2]);

		for(i = 0; i < coalitions.size(); i++) {
			for(l = 0; l < coalitions.get(i).size(); l++) {
				n = coalitions.get(i).get(l);
				if (i == 0) {
					if(n != c1) {				
						JDD.Ref(idsTree);
						JDD.Ref(ddPlayersIds[n]);
						ddc1 = JDD.Or(ddc1, JDD.Times(idsTree, ddPlayersIds[n]));
					}
				}
				else {
					if(n != c2) {				
						JDD.Ref(idsTree);
						JDD.Ref(ddPlayersIds[n]);
						ddc2 = JDD.Or(ddc2, JDD.Times(idsTree, ddPlayersIds[n]));
					}
				}
			}		
		}
		*/

		JDDNode rs = model.getReach();

		JDD.Ref(rs);
		p = JDD.And(p, rs);

		JDDNode nr = PrismMTBDD.Prob0A(trans01, reach, allDDRowVars, allDDColVars, allDDNondetVars, reach, p);
		JDDNode cr = PrismMTBDD.Prob1A(trans01, reach, nondetmask, allDDRowVars, allDDColVars, allDDNondetVars, nr, p);

		JDD.Ref(cr);
		JDD.Ref(p);
		JDD.Ref(trans);
		JDDNode tr = JDD.Times(trans, JDD.And(JDD.Not(nr), JDD.Not(p), JDD.Not(cr)));

		JDDNode r = p.copy();
		r = JDD.SwapVariables(r, allDDRowVars, allDDColVars);

		JDDNode q = null;
		JDDNode ddTempPlayers[] = new JDDNode[2];

		i = 0;
		//p = JDD.SwapVariables(p, allDDRowVars, allDDColVars);
		cr = JDD.SwapVariables(cr, allDDRowVars, allDDColVars);
		//long st;
		//long ed;

		//TestRecursive tstrcrsv = new TestRecursive(JDD.Constant(0), ddc1, ddc2, nondetmask, allDDNondetVars, allDDRowVars, 0);
		
		while(!done) {

			//System.out.println("--" + i + "--");
			//st = new Date().getTime();
			q = (i != 0)? r.copy() : JDD.PlusInfinity();

			JDD.Ref(tr);
			r = JDD.MatrixMultiply(tr, r, allDDColVars, JDD.CMU);
			
			
			//System.out.println("mult: " + JDD.GetNumNodes(r));

			JDD.Ref(r);
			JDD.Ref(ddc1);
			ddTempPlayers[0] = JDD.Restrict(r, ddc1);
			//ddTempPlayers[0] = JDD.SwapVariables(ddTempPlayers[0], allDDRowVars, allDDColVars);
			ddTempPlayers[0] = JDD.MaxAbstract(ddTempPlayers[0], allDDNondetVars);

			//System.out.println("coalition 1: " + JDD.GetNumNodes(r));

			JDD.Ref(r);
			JDD.Ref(ddc2);
			ddTempPlayers[1] = JDD.Restrict(r, ddc2);

			JDD.Ref(nondetmask);
			ddTempPlayers[1] = JDD.Apply(JDD.MAX, ddTempPlayers[1], nondetmask);				
			ddTempPlayers[1] = JDD.MinAbstract(ddTempPlayers[1], allDDNondetVars);
			//ddTempPlayers[1] = JDD.SwapVariables(ddTempPlayers[1], allDDRowVars, allDDColVars);
			
			//System.out.println("coalition 2: " + JDD.GetNumNodes(r));

			JDD.Deref(r);
			r = JDD.Apply(JDD.PLUS, ddTempPlayers[0], ddTempPlayers[1]);
			r = JDD.SwapVariables(r, allDDRowVars, allDDColVars);
			JDD.Ref(cr);
			r = JDD.Max(r, cr);
			
			done = JDD.EqualSupNorm(r, q, epsilon);
			JDD.Deref(q);

			//ed = new Date().getTime();
			//System.out.println("Iteration " + i + " took " + (ed-st)  + " nodes " + JDD.GetNumNodes(r) + " terminals " + JDD.GetNumTerminals(r));
			i++;

		}		

		//System.out.println("Convergence after " + i + " iterations.");

		/*
		JDD.Ref(r);
		JDD.Ref(start);
		JDD.PrintMinterms(mainLog, JDD.Times(r, JDD.SwapVariables(start, allDDRowVars, allDDColVars)));
		*/
		
		JDD.Deref(ddc1);
		JDD.Deref(ddc2);
		JDD.Deref(p);
		JDD.Deref(tr);
		JDD.Deref(cr);
		
		//JDD.Deref(r);
		return r;
	}
	
	public JDDNode expectedTotalRewarPlayers(JDDNode tr, JDDNode strw, JDDNode trrw, int numplayers, ArrayList<ArrayList<Integer>> coalitions, double epsilon) {
		
		boolean done = false;
		int i;

		JDDNode ddTempPlayers[] = new JDDNode[numplayers];
		JDDNode ddTempRewardsPlayers[] = new JDDNode[numplayers];

		JDDNode r = JDD.Constant(0);

		strw = JDD.SwapVariables(strw, allDDRowVars, allDDColVars);
		
		JDD.Ref(tr);
		trrw = JDD.Times(tr, trrw);;

		for(i = 0; i < numplayers; i++) {		
			if(JDD.AreIntersecting(trrw, ddPlayersIds[i])) {
				JDD.Ref(trrw);
				JDD.Ref(ddPlayersIds[i]);
				ddTempRewardsPlayers[i] = JDD.Restrict(trrw, ddPlayersIds[i]);
				ddTempRewardsPlayers[i] = JDD.SumAbstract(ddTempRewardsPlayers[i], allDDColVars);	
			}
			else {
				ddTempRewardsPlayers[i] = JDD.Constant(0);
			}
		}
		JDD.Deref(trrw);

		i = 0;

		JDD.Ref(nondetmask);
		nondetmask = JDD.ITE(nondetmask, JDD.PlusInfinity(), JDD.Constant(0));

		JDDNode q = null;

		int player;

		while(!done) {
			q = (i != 0)? r.copy() : JDD.PlusInfinity();

			JDD.Ref(tr);
			r = JDD.MatrixMultiply(tr, r, allDDColVars, JDD.CMU);

			for(int j = 0; j < coalitions.size(); j ++) {			
				for(int k = 0; k < coalitions.get(j).size(); k++) {
					player = coalitions.get(j).get(k);
					JDD.Ref(r);
					JDD.Ref(ddPlayersIds[player]);

					ddTempPlayers[player] = JDD.Restrict(r, ddPlayersIds[player]);
					JDD.Ref(ddTempRewardsPlayers[player]);

					if(j == 0) {
						ddTempPlayers[player] = JDD.Apply(JDD.PLUS, ddTempPlayers[player], ddTempRewardsPlayers[player]);
						ddTempPlayers[player] = JDD.MaxAbstract(ddTempPlayers[player], allDDNondetVars);
					}
					else {
						JDD.Ref(nondetmask);
						ddTempPlayers[player] = JDD.Apply(JDD.PLUS, ddTempPlayers[player], ddTempRewardsPlayers[player]);
						ddTempPlayers[player] = JDD.Apply(JDD.MAX, ddTempPlayers[player], nondetmask);
						ddTempPlayers[player] = JDD.MinAbstract(ddTempPlayers[player], allDDNondetVars);

					}
					ddTempPlayers[player] = JDD.SwapVariables(ddTempPlayers[player], allDDRowVars, allDDColVars);

				}
			}
			JDD.Deref(r);

			r = ddTempPlayers[0].copy();
			JDD.Deref(ddTempPlayers[0]);			

			for(int m = 1; m < numplayers; m++) { 
				r = JDD.Apply(JDD.PLUS, r, ddTempPlayers[m]);		
			}

			JDD.Ref(strw);
			r = JDD.Apply(JDD.PLUS, r, strw);

			//JDD.PrintMinterms(mainLog, JDD.Times(r.copy(), JDD.SwapVariables(start.copy(), allDDRowVars, allDDColVars)));
			
			done = JDD.EqualSupNorm(r, q, epsilon);
			JDD.Deref(q);
			i++;
		}

		//System.out.println("Convergence after " + i + " iterations.");

		for(i = 0; i < numplayers; i++) {
			JDD.Deref(ddTempRewardsPlayers[i]);
		}
		
		/*
		JDD.Ref(r);
		JDD.Ref(start);
		JDD.PrintMinterms(mainLog, JDD.Times(r, JDD.SwapVariables(start, allDDRowVars, allDDColVars)));
		*/
		
		JDD.Deref(strw);
		JDD.Deref(nondetmask);
		JDD.Deref(tr);
		
		//JDD.Deref(r);
		return r;
	}
	
	public static void printInt(HashMap<Integer,Integer> map) {
	
		for(int i = Collections.min(map.keySet()); i <= Collections.max(map.keySet()); i++) {
			System.out.print("("+ i + "," + map.get(i) +")");
		}
		
		System.out.println();
	}
	
	public static void printDouble(HashMap<Integer,Double> map) {
		
		for(int i = Collections.min(map.keySet()); i <= Collections.max(map.keySet()); i++) {
			System.out.format(Locale.US, "("+ i + ", %f)", map.get(i));
		}
		
		System.out.println();
		
	}

	public void setCoalitions(ArrayList<ArrayList<Integer>> coalitions) {
	
		int i;
		int l;
		int n;
		int c1;
		int c2;
		
		if (!coalitions.get(0).isEmpty() && !coalitions.get(1).isEmpty()) {
			
			c1 = Collections.min(coalitions.get(0));
			c2 = Collections.min(coalitions.get(1));
			
			ddc1 = JDD.Times(idsTree.copy(), ddPlayersIds[c1].copy());
			ddc2 = JDD.Times(idsTree.copy(), ddPlayersIds[c2].copy());
			
			for(i = 0; i < coalitions.size(); i++) {
				for(l = 0; l < coalitions.get(i).size(); l++) {
					n = coalitions.get(i).get(l);
					if (i == 0) {
						if(n != c1) {				
							ddc1 = JDD.Or(ddc1, JDD.Times(idsTree.copy(), ddPlayersIds[n].copy()));
						}
					}
					else {
						if(n != c2) {				
							ddc2 = JDD.Or(ddc2, JDD.Times(idsTree.copy(), ddPlayersIds[n].copy()));
						}
					}
				}		
			}
			
		}
		else if(coalitions.get(0).isEmpty()) {
			
			c2 = Collections.min(coalitions.get(1));
			ddc1 = JDD.Constant(0);
			ddc2 = JDD.Times(idsTree.copy(), ddPlayersIds[c2].copy());

			for(i = 0; i < coalitions.get(1).size(); i++) {
				n = coalitions.get(1).get(i);
				if(i != c2) {
					ddc2 = JDD.Or(ddc2, JDD.Times(idsTree.copy(), ddPlayersIds[n].copy()));
				}
			}
			
		}
		else {

			c1 = Collections.min(coalitions.get(0));
			ddc1 = JDD.Times(idsTree.copy(), ddPlayersIds[c1].copy());
			ddc2 = JDD.Constant(0);
			
			for(i = 0; i < coalitions.get(0).size(); i++) {
				n = coalitions.get(0).get(i);
				if(i != c1) {
					ddc1 = JDD.Or(ddc1, JDD.Times(idsTree.copy(), ddPlayersIds[n].copy()));
				}
			}
		}
		
		//Remmeber to change dereferencing point
		
	}
	
	public JDDNode expectedTotalRewardCoalitions(JDDNode tr, JDDNode strw, JDDNode trrw, int numplayers, ArrayList<ArrayList<Integer>> coalitions, double epsilon) {

		setCoalitions(coalitions);
		
		boolean done = false;
		int i;
		
		/*
		int l;
		int n;
		int c1 = Collections.min(coalitions.get(0));
		int c2 = Collections.min(coalitions.get(1));; 

		JDD.Ref(idsTree);
		JDD.Ref(ddPlayersIds[c1]);
		JDDNode ddc1 = JDD.Times(idsTree, ddPlayersIds[c1]);
		JDD.Ref(idsTree);
		JDD.Ref(ddPlayersIds[c2]);
		JDDNode ddc2 = JDD.Times(idsTree, ddPlayersIds[c2]);

		for(i = 0; i < coalitions.size(); i++) {
			for(l = 0; l < coalitions.get(i).size(); l++) {
				n = coalitions.get(i).get(l);
				if (i == 0) {
					if(n != c1) {				
						JDD.Ref(idsTree);
						JDD.Ref(ddPlayersIds[n]);
						ddc1 = JDD.Or(ddc1, JDD.Times(idsTree, ddPlayersIds[n]));
					}
				}
				else {
					if(n != c2) {				
						JDD.Ref(idsTree);
						JDD.Ref(ddPlayersIds[n]);
						ddc2 = JDD.Or(ddc2, JDD.Times(idsTree, ddPlayersIds[n]));
					}
				}
			}		
		}
		*/

		JDDNode ddTempPlayers[] = new JDDNode[2];
		JDDNode ddTempRewardsPlayers[] = new JDDNode[2];

		JDDNode r = JDD.Constant(0);

		strw = JDD.SwapVariables(strw, allDDRowVars, allDDColVars);

		JDD.Ref(tr);
		trrw = JDD.Times(tr, trrw);


		if(JDD.AreIntersecting(trrw, ddc1)) {
			JDD.Ref(trrw);
			JDD.Ref(ddc1);
			ddTempRewardsPlayers[0] = JDD.Restrict(trrw, ddc1);
			ddTempRewardsPlayers[0] = JDD.SumAbstract(ddTempRewardsPlayers[0], allDDColVars);	
		}
		else {
			ddTempRewardsPlayers[0] = JDD.Constant(0);
		}

		if(JDD.AreIntersecting(trrw, ddc2)) {
			JDD.Ref(trrw);
			JDD.Ref(ddc1);
			ddTempRewardsPlayers[1] = JDD.Restrict(trrw, ddc1);
			ddTempRewardsPlayers[1] = JDD.SumAbstract(ddTempRewardsPlayers[1], allDDColVars);
		} else {
			ddTempRewardsPlayers[1] = JDD.Constant(0);
		} 

		JDD.Deref(trrw);

		i = 0;

		JDD.Ref(nondetmask);
		nondetmask = JDD.ITE(nondetmask, JDD.PlusInfinity(), JDD.Constant(0));

		JDDNode q = null;

		/*
        long st;
        long ed;

        HashMap<Integer, Integer> mult = new HashMap<Integer, Integer>();
		HashMap<Integer, Integer> nodes = new HashMap<Integer, Integer>();
		HashMap<Integer, Integer> nddc1 = new HashMap<Integer, Integer>();
		HashMap<Integer, Integer> nddc2 = new HashMap<Integer, Integer>();
		HashMap<Integer, Integer> terminals = new HashMap<Integer, Integer>();
		HashMap<Integer, Double> time = new HashMap<Integer, Double>();
		*/

		while(!done) {
                        //System.out.println("--" + i + "--");
                        //st = new Date().getTime();			

			q = (i != 0)? r.copy() : JDD.PlusInfinity();

			JDD.Ref(tr);
			r = JDD.MatrixMultiply(tr, r, allDDColVars, JDD.CMU);

                        //System.out.println("mult: " + JDD.GetNumNodes(r));
                        //mult.put(i, JDD.GetNumNodes(r));

			//Max
			JDD.Ref(r);
			JDD.Ref(ddc1);					
			ddTempPlayers[0] = JDD.Times(r, ddc1);
			JDD.Ref(ddTempRewardsPlayers[0]);
			ddTempPlayers[0] = JDD.Apply(JDD.PLUS, ddTempPlayers[0], ddTempRewardsPlayers[0]);
			ddTempPlayers[0] = JDD.MaxAbstract(ddTempPlayers[0], allDDNondetVars);
			//ddTempPlayers[0] = JDD.SwapVariables(ddTempPlayers[0], allDDRowVars, allDDColVars);

                        //System.out.println("coalition 1: " + JDD.GetNumNodes(ddTempPlayers[0]));

			//Min
			JDD.Ref(r);
			JDD.Ref(ddc2);
			ddTempPlayers[1] = JDD.Times(r, ddc2);
			JDD.Ref(nondetmask);
			JDD.Ref(ddTempRewardsPlayers[1]);
			ddTempPlayers[1] = JDD.Apply(JDD.PLUS, ddTempPlayers[1], ddTempRewardsPlayers[1]);			
			ddTempPlayers[1] = JDD.Apply(JDD.MAX, ddTempPlayers[1], nondetmask);					
			ddTempPlayers[1] = JDD.MinAbstract(ddTempPlayers[1], allDDNondetVars);
			//ddTempPlayers[1] = JDD.SwapVariables(ddTempPlayers[1], allDDRowVars, allDDColVars);

                        //System.out.println("coalition 2: " + JDD.GetNumNodes(ddTempPlayers[1]));

			/***
			 * change to do just on swap with the r instead with 0 and 1 individually
			 */
			
			JDD.Deref(r);
			r = JDD.Plus(ddTempPlayers[0], ddTempPlayers[1]);
			
			r = JDD.SwapVariables(r, allDDRowVars, allDDColVars);

			JDD.Ref(strw);
			r = JDD.Plus(r, strw);
			
			done = JDD.EqualSupNorm(r, q, epsilon);
			
						//ed = new Date().getTime();
                        //System.out.println("Iteration " + i + " took " + (ed-st)  + " nodes " + JDD.GetNumNodes(r) + " terminals " + JDD.GetNumTerminals(r));

				
			/*
			nodes.put(i, JDD.GetNumNodes(r));
			nddc1.put(i, JDD.GetNumNodes(ddTempPlayers[0]));
			nddc2.put(i, JDD.GetNumNodes(ddTempPlayers[1]));
			terminals.put(i, JDD.GetNumTerminals(r));
			time.put(i, (ed-st)/1000.000);
			*/

			/*
			JDD.PrintMinterms(mainLog, JDD.Times(r.copy(), JDD.SwapVariables(start.copy(), allDDRowVars, allDDColVars)));	
			*/
			
			JDD.Deref(q);
			i++;
		}

		//System.out.println("Convergence after " + i + " iterations.");

		/*
        System.out.println("-- Mult --");
        System.out.println("Min: " + Collections.min(mult.values()));
        System.out.println("Max: " + Collections.max(mult.values()));
        printInt(mult);
        System.out.println("-- Coalition 1 --");
        System.out.println("Min: " + Collections.min(nddc1.values()));
        System.out.println("Max: " + Collections.max(nddc1.values()));
        printInt(nddc1);
        System.out.println("-- Coalition 2 --");
        System.out.println("Min: " + Collections.min(nddc2.values()));
        System.out.println("Max: " + Collections.max(nddc2.values()));
        printInt(nddc2);
        System.out.println("-- Nodes --");
        System.out.println("Min: " + Collections.min(nodes.values()));
        System.out.println("Max: " + Collections.max(nodes.values()));
        printInt(nodes);
		System.out.println("-- Terminals --");
        System.out.println("Min: " + Collections.min(terminals.values()));
        System.out.println("Max: " + Collections.max(terminals.values()));
        printInt(terminals);
        System.out.println("-- Time --");
        System.out.println("Min: " + Collections.min(time.values()));
        System.out.println("Max: " + Collections.max(time.values()));
        printDouble(time);
		*/

		for(i = 0; i < 2; i++) {
			JDD.Deref(ddTempRewardsPlayers[i]);
		}

		JDD.Deref(ddc1);
		JDD.Deref(ddc2);
		JDD.Deref(tr);
		JDD.Deref(strw);
		JDD.Deref(nondetmask);
		
		//JDD.PrintMinterms(mainLog, r.copy());
		
		//JDD.Deref(r);
		return r;
	}
	
	public JDDNode computeFcRewards(JDDNode s, int numplayers, JDDNode stateRewards, JDDNode transRewards, ArrayList<ArrayList<Integer>> coalitions, double epsilon) {
	
		JDD.Ref(trans);
		JDD.Ref(s);
		JDDNode tr = JDD.Times(trans, JDD.Not(s));

		/*
		JDDNode nr = PrismMTBDD.Prob0A(trans01, reach, allDDRowVars, allDDColVars, allDDNondetVars, reach, s);
		tr = JDD.Times(tr, JDD.And(JDD.Not(nr)));
		*/
		
		JDD.Ref(s);
		JDDNode strw = JDD.Times(stateRewards.copy(), JDD.Not(s)); // copy needed

		JDD.Ref(s);
		JDDNode trrw = JDD.Times(transRewards.copy(), JDD.Not(s)); // copy needed

		JDD.Deref(s);
		return expectedTotalRewardCoalitions(tr, strw, trrw, numplayers, coalitions, epsilon);	
		//return expectedTotalRewarPlayers(tr, strw, trrw, numplayers, coalitions, epsilon);	
	}
	
	public static double calculateEntropy(JDDNode dd) {
		
		JDDVars supp = JDDVars.fromCubeSet(JDD.GetSupport(dd));
		int nvars = supp.getNumVars();
		double ntotal = JDD.GetNumMinterms(dd, nvars) - 1;
		double entropy = 0.0;
		double j;
		double pj;
		double l2 = Math.log(2);
		JDDNode tmp;
		JDDNode tmp2 = dd;
		
		while(!tmp2.equals(JDD.ZERO)) {
			JDD.Ref(tmp2);
			j = JDD.FindMax(tmp2);
			tmp = JDD.Equals(tmp2, j);
			tmp2 = JDD.ITE(tmp, JDD.Constant(0), tmp2);
			pj = JDD.GetNumMinterms(tmp, nvars) / ntotal;
			entropy = entropy + ((-1.0) * pj * Math.log(pj)/l2);
		}
				
		supp.derefAll();
		
		return entropy;
	}
	
	public JDDNode computeBoundedUntilProbs(JDDNode phi1, JDDNode phi2, int l, int numplayers, ArrayList<ArrayList<Integer>> coalitions) {
		
		int i;
		
		JDDNode tr = JDD.Times(trans.copy(), phi1.copy());
		
		JDDNode r = phi2.copy();
		r = JDD.SwapVariables(r, allDDRowVars, allDDColVars);

		JDDNode ddTempPlayers[] = new JDDNode[numplayers];

		i = 0;
		int player;

		JDDNode q = null;
		boolean done = false;
		
		for(int n = 0; n < l; n++) {
			
			if(l == Integer.MAX_VALUE) {
				q = (i != 0)? r.copy() : JDD.PlusInfinity();
			}

			//System.out.println("-- " + n);
			
			JDD.Ref(tr);
			r = JDD.MatrixMultiply(tr, r, allDDColVars, JDD.CMU);
			//r = JDD.SwapVariables(r, allDDRowVars, allDDColVars);
			
			for(int j = 0; j < coalitions.size(); j ++) {
				for(int k = 0; k < coalitions.get(j).size(); k++) {
					player = coalitions.get(j).get(k);
					JDD.Ref(r);
					JDD.Ref(ddPlayersIds[player]);
					ddTempPlayers[player] = JDD.Restrict(r, ddPlayersIds[player]);
					
					if(j == 0) {
						//System.out.println("max for player " + player);
						ddTempPlayers[player] = JDD.SwapVariables(ddTempPlayers[player], allDDRowVars, allDDColVars);
						ddTempPlayers[player] = JDD.MaxAbstract(ddTempPlayers[player], allDDNondetVars);
					}
					else {
						JDD.Ref(nondetmask);
						//ddTempPlayers[player] = JDD.SwapVariables(ddTempPlayers[player], allDDColVars, allDDRowVars);	
						ddTempPlayers[player] = JDD.Apply(JDD.MAX, ddTempPlayers[player], nondetmask);
						ddTempPlayers[player] = JDD.MinAbstract(ddTempPlayers[player], allDDNondetVars);
						ddTempPlayers[player] = JDD.SwapVariables(ddTempPlayers[player], allDDRowVars, allDDColVars);
						//System.out.println("min for player " + player);
						//JDD.PrintMinterms(mainLog, ddTempPlayers[player].copy());
					}
					//System.out.println("player: " + player);
					//JDD.PrintMinterms(mainLog, ddTempPlayers[player].copy());
				}
			}
			JDD.Deref(r);
			r = ddTempPlayers[0].copy();
			JDD.Deref(ddTempPlayers[0]);

			for(int m = 1; m < numplayers; m++) {
				r = JDD.Apply(JDD.PLUS, r, ddTempPlayers[m]);
			}
			
			r = JDD.Max(r, JDD.SwapVariables(phi2.copy(), allDDRowVars, allDDColVars));

			i++;
			
			if(l == Integer.MAX_VALUE) {
				done = JDD.EqualSupNorm(r, q, epsilon);
				JDD.Deref(q);
			}
			if(done) {
				break;
			}
		}		

		//System.out.println("Convergence after " + i + " iterations.");
		
		//JDD.Deref(phi2);
		//JDD.Deref(r);
		JDD.Deref(tr);
		
		r = JDD.SwapVariables(r, allDDColVars, allDDRowVars);
		
		return r;
		
	}
	
}
