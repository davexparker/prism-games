//==============================================================================
//	
//	Copyright (c) 2020-
//	Authors:
//	* Dave Parker <d.a.parker@cs.bham.ac.uk> (University of Birmingham)
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
import java.util.List;

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
public class TGModelChecker extends  NonProbModelChecker
{
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
		// Just assume it is an F for now
		if (!(exprSub instanceof ExpressionTemporal && ((ExpressionTemporal) exprSub).getOperator() == ExpressionTemporal.P_F)) {
			throw new PrismException("Cannot currently check this strategy formula");
		}
		return checkReach(model, (ExpressionTemporal) exprSub, coalition, statesOfInterest);
	}
	
	/**
	 * Model check a reachability temporal operator from, inside a <<>> 
	 */
	protected StateValues checkReach(Model model, ExpressionTemporal expr, Coalition coalition, BitSet statesOfInterest) throws PrismException
	{
		// Model check operands for all states
		BitSet target = checkExpression(model, expr.getOperand2(), null).getBitSet();
		
		// Compute/return the result
		BitSet result = computeReach((TG) model, target);
		
		return StateValues.createFromBitSet(result, model);
	}
	
	/**
	 * Compute reachability
	 * @param tg TG
	 * @param target Target states
	 * @param coalition Players trying to reach the target
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
	 * Compute 2-player reachability
	 * @param tg 2-player TG
	 * @param target Target states
	 */
	protected BitSet computeReach(TG tg, BitSet target) throws PrismException
	{
		BitSet res = new BitSet();
		res.set(0);
		return res;
	}
}
