//==============================================================================
//	
//	Copyright (c) 2002-
//	Authors:
//	* Dave Parker <david.parker@comlab.ox.ac.uk> (University of Oxford)
//	* Joachim Klein <klein@tcs.inf.tu-dresden.de> (TU Dresden)
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

import java.io.File;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;

import explicit.rewards.ConstructRewards;
import explicit.rewards.Rewards;
import io.DotExporter;
import io.DRNExporter;
import io.MatlabExporter;
import io.ModelExportFormat;
import io.ModelExportOptions;
import io.ModelExportTask;
import io.ModelExporter;
import io.PrismExplicitExporter;
import io.PrismExplicitImporter;
import parser.EvaluateContext.EvalMode;
import parser.State;
import parser.Values;
import parser.VarList;
import parser.ast.Declaration;
import parser.ast.DeclarationIntUnbounded;
import parser.ast.Expression;
import parser.ast.ExpressionBinaryOp;
import parser.ast.ExpressionConstant;
import parser.ast.ExpressionFilter;
import parser.ast.ExpressionFilter.FilterOperator;
import parser.ast.ExpressionFormula;
import parser.ast.ExpressionFunc;
import parser.ast.ExpressionITE;
import parser.ast.ExpressionIdent;
import parser.ast.ExpressionLabel;
import parser.ast.ExpressionLiteral;
import parser.ast.ExpressionObs;
import parser.ast.ExpressionProp;
import parser.ast.ExpressionUnaryOp;
import parser.ast.ExpressionVar;
import parser.ast.LabelList;
import parser.ast.ModulesFile;
import parser.ast.PropertiesFile;
import parser.ast.Property;
import parser.type.TypeBool;
import parser.type.TypeDouble;
import parser.visitor.ASTTraverseModify;
import parser.visitor.ReplaceLabels;
import prism.Accuracy;
import prism.Evaluator;
import prism.Filter;
import prism.ModelInfo;
import prism.ModelType;
import prism.Prism;
import prism.PrismComponent;
import prism.PrismException;
import prism.PrismFileLog;
import prism.PrismLangException;
import prism.PrismLog;
import prism.PrismNotSupportedException;
import prism.PrismSettings;
import prism.Result;
import prism.RewardGenerator;

/**
 * Super class for explicit-state model checkers.
 * <br>
 * This model checker class and its subclasses store their settings locally so
 * that they can be configured and used without a PrismSettings object.
 * Pass in null as a parent on creation to bypass the use of PrismSettings.
 */
public class StateModelChecker extends PrismComponent
{
	// Flags/settings that can be extracted from PrismSettings
	// (NB: defaults do not necessarily coincide with PRISM)

	// Verbosity level
	protected int verbosity = 0;

	// Additional flags/settings not included in PrismSettings

	// Export target state info?
	protected boolean exportTarget = false;
	protected String exportTargetFilename = null;
	
	// Export product model info?
	protected boolean exportProductTrans = false;
	protected String exportProductTransFilename = null;
	protected boolean exportProductStates = false;
	protected String exportProductStatesFilename = null;
	protected boolean exportProductVector = false;
	protected String exportProductVectorFilename = null;
	
	// Store the final results vector after model checking?
	protected boolean storeVector = false;

	// Generate/store a strategy during model checking?
	protected boolean genStrat = false;
	// Should any generated strategies should be restricted to the states reachable under them?
	protected boolean restrictStratToReach = true;

	// Stored Pareto sets
	protected Pareto pareto_set = null;
	// Stored parameters
	protected MultiParameters parsed_params = null;
	// Computing Pareto sets?
	protected boolean computePareto = true; // computing Pareto set, or doing actual model checking?

	// Do bisimulation minimisation before model checking?
	protected boolean doBisim = false;

	// Do topological value iteration?
	protected boolean doTopologicalValueIteration = false;

	// For Pmax computation, collapse MECs to quotient MDP?
	protected boolean doPmaxQuotient = false;

	// Do interval iteration?
	protected boolean doIntervalIteration = false;

	// Model info (for reward structures, etc.)
	protected ModulesFile modulesFile = null;
	protected ModelInfo modelInfo = null;
	protected RewardGenerator<?> rewardGen = null;

	// Properties file (for labels, constants, etc.)
	protected PropertiesFile propertiesFile = null;

	// Constants (extracted from model/properties)
	protected Values constantValues;

	// The filter to be applied to the current property
	protected Filter currentFilter;

	// The result of model checking will be stored here
	protected Result result;

	/**
	 * Create a new StateModelChecker, inherit basic state from parent (unless null).
	 */
	public StateModelChecker(PrismComponent parent) throws PrismException
	{
		super(parent);

		// For explicit.StateModelChecker and its subclasses, we explicitly set 'settings'
		// to null if there is no parent or if the parent has a null 'settings'.
		// This allows us to choose to ignore the default one created by PrismComponent.
		if (parent == null || parent.getSettings() == null)
			setSettings(null);

		// If present, initialise settings from PrismSettings
		if (settings != null) {
			verbosity = settings.getBoolean(PrismSettings.PRISM_VERBOSE) ? 10 : 1;
			setDoIntervalIteration(settings.getBoolean(PrismSettings.PRISM_INTERVAL_ITER));
			setDoTopologicalValueIteration(settings.getBoolean(PrismSettings.PRISM_TOPOLOGICAL_VI));
			setDoPmaxQuotient(settings.getBoolean(PrismSettings.PRISM_PMAX_QUOTIENT));
			tolerance = settings.getDouble(PrismSettings.PRISM_PARETO_EPSILON);
		}
	}

	/**
	 * Create a model checker (a subclass of this one) for a given model type.
	 */
	public static StateModelChecker createModelChecker(ModelType modelType) throws PrismException
	{
		return createModelChecker(modelType, null);
	}

	/**
	 * Create a model checker (a subclass of this one) for a given model type
	 */
	public static StateModelChecker createModelChecker(ModelType modelType, PrismComponent parent) throws PrismException
	{
		explicit.StateModelChecker mc = null;
		switch (modelType) {
		case DTMC:
			mc = new DTMCModelChecker(parent);
			break;
		case MDP:
			mc = new MDPModelChecker(parent);
			break;
		case CTMC:
			mc = new CTMCModelChecker(parent);
			break;
		case POMDP:
			mc = new POMDPModelChecker(parent);
			break;
		case CTMDP:
			mc = new CTMDPModelChecker(parent);
			break;
		case CSG:
			mc = new CSGModelChecker(parent);
			break;
		case STPG:
			mc = new STPGModelChecker(parent);
			break;
		case SMG:
			mc = new SMGModelChecker(parent);
			break;
		case IDTMC:
			mc = new IDTMCModelChecker(parent);
			break;
		case IMDP:
			mc = new IMDPModelChecker(parent);
			break;
		case LTS:
			mc = new NonProbModelChecker(parent);
			break;
		default:
			throw new PrismException("Cannot create model checker for model type " + modelType);
		}
		return mc;
	}

	// Settings methods

	// Pareto set
	protected double tolerance = 0.0;

	// Setters/getters

	/**
	 * Inherit settings (and the log) from another StateModelChecker object.
	 * For model checker objects that inherit a PrismSettings object, this is superfluous
	 * since this has been done already.
	 */
	public void inheritSettings(StateModelChecker other)
	{
		setModelCheckingInfo(other.modelInfo, other.propertiesFile, other.rewardGen);
		setLog(other.getLog());
		result = other.result;
		setVerbosity(other.getVerbosity());
		setExportTarget(other.getExportTarget());
		setExportTargetFilename(other.getExportTargetFilename());
		setExportProductTrans(other.getExportProductTrans());
		setExportProductTransFilename(other.getExportProductTransFilename());
		setExportProductStates(other.getExportProductStates());
		setExportProductStatesFilename(other.getExportProductStatesFilename());
		setExportProductVector(other.getExportProductVector());
		setExportProductVectorFilename(other.getExportProductVectorFilename());
		setStoreVector(other.getStoreVector());
		setGenStrat(other.getGenStrat());
		setRestrictStratToReach(other.getRestrictStratToReach());
		setDoBisim(other.getDoBisim());
		tolerance = other.tolerance;
		setDoIntervalIteration(other.getDoIntervalIteration());
		setDoPmaxQuotient(other.getDoPmaxQuotient());
	}

	/**
	 * Print summary of current settings.
	 */
	public void printSettings()
	{
		mainLog.print("verbosity = " + verbosity + " ");
		mainLog.print("tolerance = " + tolerance + " ");
	}

	// Set methods for flags/settings

	public void setComputeParetoSet(boolean computePareto)
	{
		this.computePareto = computePareto;
	}

	/**
	 * Set verbosity level, i.e. amount of output produced.
	 */
	public void setVerbosity(int verbosity)
	{
		this.verbosity = verbosity;
	}

	public void setExportTarget(boolean b)
	{
		exportTarget = b;
	}

	public void setExportTargetFilename(String s)
	{
		exportTargetFilename = s;
	}

	public void setExportProductTrans(boolean b)
	{
		exportProductTrans = b;
	}

	public void setExportProductTransFilename(String s)
	{
		exportProductTransFilename = s;
	}

	public void setExportProductStates(boolean b)
	{
		exportProductStates = b;
	}

	public void setExportProductStatesFilename(String s)
	{
		exportProductStatesFilename = s;
	}

	public void setExportProductVector(boolean b)
	{
		exportProductVector = b;
	}

	public void setExportProductVectorFilename(String s)
	{
		exportProductVectorFilename = s;
	}

	/**
	 * Specify whether or not to store the final results vector after model checking.
	 */
	public void setStoreVector(boolean storeVector)
	{
		this.storeVector = storeVector;
	}

	/**
	 * Specify whether or not a strategy should be generated during model checking.
	 */
	public void setGenStrat(boolean genStrat)
	{
		this.genStrat = genStrat;
	}

	/**
	 * Specify whether or not any generated strategies should be restricted to the states reachable under them.
	 */
	public void setRestrictStratToReach(boolean restrictStratToReach)
	{
		this.restrictStratToReach = restrictStratToReach;
	}

	/**
	 * Specify whether or not to do bisimulation minimisation before model checking.
	 */
	public void setDoBisim(boolean doBisim)
	{
		this.doBisim = doBisim;
	}

	/**
	 * Specify whether or not to do topological value iteration.
	 */
	public void setDoTopologicalValueIteration(boolean doTopologicalValueIteration)
	{
		this.doTopologicalValueIteration = doTopologicalValueIteration;
	}

	/**
	 * Specify whether or not to perform MEC quotienting for Pmax.
	 */
	public void setDoPmaxQuotient(boolean doPmaxQuotient)
	{
		this.doPmaxQuotient = doPmaxQuotient;
	}

	/**
	 * Specify whether or not to use interval iteration.
	 */
	public void setDoIntervalIteration(boolean doIntervalIteration)
	{
		this.doIntervalIteration = doIntervalIteration;
	}

	// Get methods for flags/settings

	public int getVerbosity()
	{
		return verbosity;
	}

	public boolean getExportTarget()
	{
		return exportTarget;
	}

	public String getExportTargetFilename()
	{
		return exportTargetFilename;
	}

	public boolean getExportProductTrans()
	{
		return exportProductTrans;
	}

	public String getExportProductTransFilename()
	{
		return exportProductTransFilename;
	}

	public boolean getExportProductStates()
	{
		return exportProductStates;
	}

	public String getExportProductStatesFilename()
	{
		return exportProductStatesFilename;
	}

	public boolean getExportProductVector()
	{
		return exportProductVector;
	}

	public String getExportProductVectorFilename()
	{
		return exportProductVectorFilename;
	}

	/**
	 * Whether or not to store the final results vector after model checking.
	 */
	public boolean getStoreVector()
	{
		return storeVector;
	}

	/**
	 * Whether or not a strategy should be generated during model checking.
	 */
	public boolean getGenStrat()
	{
		return genStrat;
	}

	/**
	 * Whether or not any generated strategies should be restricted to the states reachable under them.
	 */
	public boolean getRestrictStratToReach()
	{
		return restrictStratToReach;
	}

	/**
	 * Whether or not to do bisimulation minimisation before model checking.
	 */
	public boolean getDoBisim()
	{
		return doBisim;
	}

	/**
	 * Whether or not to do topological value iteration.
	 */
	public boolean getDoTopologicalValueIteration()
	{
		return doTopologicalValueIteration;
	}

	/**
	 * Whether or not to do MEC quotient for Pmax
	 */
	public boolean getDoPmaxQuotient()
	{
		return doPmaxQuotient;
	}

	/**
	 * Whether or not to use interval iteration.
	 */
	public boolean getDoIntervalIteration()
	{
		return doIntervalIteration;
	}

	/** Get the constant values (both from the modules file and the properties file) */
	public Values getConstantValues()
	{
		return constantValues;
	}

	/**
	 * Get the label list (from properties file and modules file, if they are attached).
	 * @return the label list for the properties/modules file, or {@code null} if not available.
	 */
	public LabelList getLabelList()
	{
		if (propertiesFile != null) {
			return propertiesFile.getCombinedLabelList(); // combined list from properties and modules file
		} else if (modulesFile != null) {
			return modulesFile.getLabelList();
		} else {
			return null;
		}
	}

	/**
	 * Return the set of label names that are defined
	 * either by the model (from the model info or modules file)
	 * or properties file (if attached to the model checker).
	 */
	public Set<String> getDefinedLabelNames()
	{
		TreeSet<String> definedLabelNames = new TreeSet<String>();

		// labels from the label list
		LabelList labelList = getLabelList();
		if (labelList != null) {
			definedLabelNames.addAll(labelList.getLabelNames());
		}

		// labels from the model info
		if (modelInfo != null) {
			definedLabelNames.addAll(modelInfo.getLabelNames());
		}

		return definedLabelNames;
	}

	// Other setters/getters

	/**
	 * Set the attached model file (for e.g. reward structures when model checking)
	 * and the attached properties file (for e.g. constants/labels when model checking)
	 */
	public void setModelCheckingInfo(ModelInfo modelInfo, PropertiesFile propertiesFile, RewardGenerator<?> rewardGen)
	{
		this.modelInfo = modelInfo;
		if (modelInfo instanceof ModulesFile) {
			this.modulesFile = (ModulesFile) modelInfo;
		}
		this.propertiesFile = propertiesFile;
		this.rewardGen = rewardGen;
		// Get combined constant values from model/properties
		constantValues = new Values();
		if (modelInfo != null)
			constantValues.addValues(modelInfo.getConstantValues());
		if (propertiesFile != null)
			constantValues.addValues(propertiesFile.getConstantValues());
	}

	/**
	 * Model check an expression, process and return the result.
	 * Information about states and model constants should be attached to the model.
	 * For other required info (labels, reward structures, etc.), use the method
	 * {@link #setModelCheckingInfo(ModelInfo, PropertiesFile, RewardGenerator)}.
	 * to attach the original model/properties files.
	 */
	public <Value> Result check(Model<Value> model, Expression expr) throws PrismException
	{
		long timer = 0;
		StateValues vals;
		String resultString;

		// Create storage for result
		result = new Result();

		// Remove any existing filter info
		currentFilter = null;

		// If we need to store a copy of the results vector, add a "store" filter to represent this
		if (storeVector) {
			ExpressionFilter exprFilter = new ExpressionFilter("store", expr);
			exprFilter.setInvisible(true);
			exprFilter.typeCheck();
			expr = exprFilter;
		}
		// Wrap a filter round the property, if needed
		// (in order to extract the final result of model checking) 
		expr = ExpressionFilter.addDefaultFilterIfNeeded(expr, model.getNumInitialStates() == 1);

		// If required, do bisimulation minimisation
		if (doBisim) {
			mainLog.println("\nPerforming bisimulation minimisation...");
			ArrayList<String> propNames = new ArrayList<String>();
			ArrayList<BitSet> propBSs = new ArrayList<BitSet>();
			Expression exprNew = checkMaximalPropositionalFormulas(model, expr.deepCopy(), propNames, propBSs);
			Bisimulation<Value> bisim = new Bisimulation<>(this);
			model = bisim.minimise(model, propNames, propBSs);
			mainLog.println("Modified property: " + exprNew);
			expr = exprNew;
			//model.exportToPrismExplicitTra("bisim.tra");
			//model.exportStates(Prism.EXPORT_PLAIN, modelInfo.createVarList(), new PrismFileLog("bisim.sta"));
		}

		// Do model checking and store result vector
		timer = System.currentTimeMillis();
		// check expression for all states (null => statesOfInterest=all)
		vals = checkExpression(model, expr, null);
		timer = System.currentTimeMillis() - timer;
		mainLog.println("\nTime for model checking: " + timer / 1000.0 + " seconds.");

		// Print result to log
		resultString = "Result";
		if (!("Result".equals(expr.getResultName())))
			resultString += " (" + expr.getResultName().toLowerCase() + ")";
		resultString += ": " + result.getResultAndAccuracy();
		mainLog.print("\n" + resultString + "\n");

		// Clean up
		//vals.clear();

		// Return result
		return result;
	}

	/**
	 * Model check an expression and return a vector result values over all states.
	 * Information about states and model constants should be attached to the model.
	 * For other required info (labels, reward structures, etc.), use the method
	 * {@link #setModelCheckingInfo(ModelInfo, PropertiesFile, RewardGenerator)}.
	 * @param statesOfInterest a set of states for which results should be calculated (null = all states).
	 *        The calculated values for states not of interest are arbitrary and should to be ignored.
	 */
	public StateValues checkExpression(Model<?> model, Expression expr, BitSet statesOfInterest) throws PrismException
	{
		StateValues res = null;

		// If-then-else
		if (expr instanceof ExpressionITE) {
			res = checkExpressionITE(model, (ExpressionITE) expr, statesOfInterest);
		}
		// Binary ops
		else if (expr instanceof ExpressionBinaryOp) {
			res = checkExpressionBinaryOp(model, (ExpressionBinaryOp) expr, statesOfInterest);
		}
		// Unary ops
		else if (expr instanceof ExpressionUnaryOp) {
			res = checkExpressionUnaryOp(model, (ExpressionUnaryOp) expr, statesOfInterest);
		}
		// Functions
		else if (expr instanceof ExpressionFunc) {
			res = checkExpressionFunc(model, (ExpressionFunc) expr, statesOfInterest);
		}
		// Identifiers
		else if (expr instanceof ExpressionIdent) {
			// Should never happen
			throw new PrismException("Unknown identifier \"" + ((ExpressionIdent) expr).getName() + "\"");
		}
		// Literals
		else if (expr instanceof ExpressionLiteral) {
			res = checkExpressionLiteral(model, (ExpressionLiteral) expr);
		}
		// Constants
		else if (expr instanceof ExpressionConstant) {
			res = checkExpressionConstant(model, (ExpressionConstant) expr);
		}
		// Formulas
		else if (expr instanceof ExpressionFormula) {
			// This should have been defined or expanded by now.
			if (((ExpressionFormula) expr).getDefinition() != null)
				return checkExpression(model, ((ExpressionFormula) expr).getDefinition(), statesOfInterest);
			else
				throw new PrismException("Unexpanded formula \"" + ((ExpressionFormula) expr).getName() + "\"");
		}
		// Variables
		else if (expr instanceof ExpressionVar) {
			res = checkExpressionVar(model, (ExpressionVar) expr, statesOfInterest);
		}
		// Observables
		else if (expr instanceof ExpressionObs) {
			res = checkExpressionObs(model, (ExpressionObs) expr, statesOfInterest);
		}
		// Labels
		else if (expr instanceof ExpressionLabel) {
			res = checkExpressionLabel(model, (ExpressionLabel) expr, statesOfInterest);
		}
		// Property refs
		else if (expr instanceof ExpressionProp) {
			res = checkExpressionProp(model, (ExpressionProp) expr, statesOfInterest);
		}
		// Filter
		else if (expr instanceof ExpressionFilter) {
			res = checkExpressionFilter(model, (ExpressionFilter) expr, statesOfInterest);
		}
		// Anything else - error
		else {
			throw new PrismNotSupportedException("Couldn't check " + expr.getClass());
		}

		return res;
	}

	/**
	 * Model check a binary operator.
	 * @param statesOfInterest the states of interest, see checkExpression()
	 */
	protected StateValues checkExpressionITE(Model<?> model, ExpressionITE expr, BitSet statesOfInterest) throws PrismException
	{
		StateValues res1 = null, res2 = null, res3 = null;

		try {
			// Check operand 1 (condition) recursively
			res1 = checkExpression(model, expr.getOperand1(), statesOfInterest);
			// Compute new statesOfInterest sets to implement short-circuiting
			BitSet statesOfInterestThen = (BitSet) res1.getBitSet().clone();
			BitSet statesOfInterestElse = (BitSet) statesOfInterestThen.clone();
			statesOfInterestElse.flip(0, model.getNumStates());
			if (statesOfInterest != null) {
				statesOfInterestThen.and(statesOfInterest);
				statesOfInterestElse.and(statesOfInterest);
			}
			// Check operands 2 and 3 (then/else) recursively,
			// but only where needed, to implement short-circuiting
			res2 = checkExpression(model, expr.getOperand2(), statesOfInterestThen);
			res3 = checkExpression(model, expr.getOperand3(), statesOfInterestElse);
		} catch (PrismException e) {
			if (res1 != null)
				res1.clear();
			if (res2 != null)
				res2.clear();
			throw e;
		}

		// Apply operation
		res1.applyFunction(expr.getType(), (v1, v2, v3) -> expr.apply(v1, v2, v3, EvalMode.FP), res2, res3, statesOfInterest);
		res2.clear();
		res3.clear();

		return res1;
	}

	/**
	 * Model check a binary operator.
	 * @param statesOfInterest the states of interest, see checkExpression()
	 */
	protected StateValues checkExpressionBinaryOp(Model<?> model, ExpressionBinaryOp expr, BitSet statesOfInterest) throws PrismException
	{
		StateValues res1 = null, res2 = null;
		// Check operands recursively
		try {
			// Check operand 1 recursively
			res1 = checkExpression(model, expr.getOperand1(), statesOfInterest);
			// Where short-circuiting is needed, see which states remain to be considered
			BitSet statesOfInterestRhs = null;
			switch (expr.getOperator()) {
			case ExpressionBinaryOp.IMPLIES:
			case ExpressionBinaryOp.AND:
				statesOfInterestRhs = (BitSet) res1.getBitSet().clone();
				break;
			case ExpressionBinaryOp.OR:
				statesOfInterestRhs = (BitSet) res1.getBitSet().clone();
				statesOfInterestRhs.flip(0, model.getNumStates());
				break;
			default:
				statesOfInterestRhs = statesOfInterest;
			}
			if (statesOfInterestRhs != null && statesOfInterest != null) {
				statesOfInterestRhs.and(statesOfInterest);
			}
			// Check operand 2 recursively, but only where needed, to implement short-circuiting
			res2 = checkExpression(model, expr.getOperand2(), statesOfInterestRhs);
		} catch (PrismException e) {
			if (res1 != null)
				res1.clear();
			throw e;
		}
		// Apply operation
		res1.applyFunction(expr.getType(), (v1, v2) -> expr.apply(v1, v2, EvalMode.FP), res2, statesOfInterest);
		res2.clear();

		return res1;
	}

	/**
	 * Model check a unary operator.
	 * @param statesOfInterest the states of interest, see checkExpression()
	 */
	protected StateValues checkExpressionUnaryOp(Model<?> model, ExpressionUnaryOp expr, BitSet statesOfInterest) throws PrismException
	{
		StateValues res1 = null;
		int op = expr.getOperator();

		// Check operand recursively
		res1 = checkExpression(model, expr.getOperand(), statesOfInterest);

		// Parentheses are easy - nothing to do:
		if (op == ExpressionUnaryOp.PARENTH)
			return res1;

		// Apply operation
		res1.applyFunction(expr.getType(), v -> expr.apply(v, EvalMode.FP), statesOfInterest);

		return res1;
	}

	/**
	 * Model check a function.
	 * @param statesOfInterest the states of interest, see checkExpression()
	 */
	protected StateValues checkExpressionFunc(Model<?> model, ExpressionFunc expr, BitSet statesOfInterest) throws PrismException
	{
		switch (expr.getNameCode()) {
		case ExpressionFunc.MIN:
		case ExpressionFunc.MAX:
			return checkExpressionFuncNary(model, expr, statesOfInterest);
		case ExpressionFunc.FLOOR:
		case ExpressionFunc.CEIL:
		case ExpressionFunc.ROUND:
			return checkExpressionFuncUnary(model, expr, statesOfInterest);
		case ExpressionFunc.POW:
		case ExpressionFunc.MOD:
		case ExpressionFunc.LOG:
			return checkExpressionFuncBinary(model, expr, statesOfInterest);
		case ExpressionFunc.MULTI:
			throw new PrismNotSupportedException("Multi-objective model checking is not supported for " + model.getModelType() + "s with the explicit engine");
		default:
			throw new PrismException("Unrecognised function \"" + expr.getName() + "\"");
		}
	}

	protected StateValues checkExpressionFuncUnary(Model<?> model, ExpressionFunc expr, BitSet statesOfInterest) throws PrismException
	{
		// Check operand recursively
		StateValues res1 = checkExpression(model, expr.getOperand(0), statesOfInterest);

		// Apply operation
		try {
			res1.applyFunction(expr.getType(), v -> expr.applyUnary(v, EvalMode.FP), statesOfInterest);
		} catch (PrismException e) {
			if (res1 != null)
				res1.clear();
			if (e instanceof PrismLangException)
				((PrismLangException) e).setASTElement(expr);
			throw e;
		}

		return res1;
	}

	protected StateValues checkExpressionFuncBinary(Model<?> model, ExpressionFunc expr, BitSet statesOfInterest) throws PrismException
	{
		// Check operands recursively
		StateValues res1 = null, res2 = null;
		try {
			res1 = checkExpression(model, expr.getOperand(0), statesOfInterest);
			res2 = checkExpression(model, expr.getOperand(1), statesOfInterest);
		} catch (PrismException e) {
			if (res1 != null)
				res1.clear();
			throw e;
		}

		// Apply operation
		try {
			res1.applyFunction(expr.getType(), (v1, v2) -> expr.applyBinary(v1, v2, EvalMode.FP), res2, statesOfInterest);
			res2.clear();
		} catch (PrismException e) {
			if (res1 != null)
				res1.clear();
			if (res2 != null)
				res2.clear();
			if (e instanceof PrismLangException)
				((PrismLangException) e).setASTElement(expr);
			throw e;
		}

		return res1;
	}

	protected StateValues checkExpressionFuncNary(Model<?> model, ExpressionFunc expr, BitSet statesOfInterest) throws PrismException
	{
		// Check first operand recursively
		StateValues res1 = null, res2 = null;
		res1 = checkExpression(model, expr.getOperand(0), statesOfInterest);
		// Go through remaining operands
		int n = expr.getNumOperands();
		for (int i = 1; i < n; i++) {
			// Check next operand recursively
			try {
				res2 = checkExpression(model, expr.getOperand(i), statesOfInterest);
			} catch (PrismException e) {
				if (res2 != null)
					res2.clear();
				throw e;
			}
			// Apply operation
			try {
				res1.applyFunction(expr.getType(), (v1, v2) -> expr.applyBinary(v1, v2, EvalMode.FP), res2, statesOfInterest);
				res2.clear();
			} catch (PrismException e) {
				if (res1 != null)
					res1.clear();
				if (res2 != null)
					res2.clear();
				if (e instanceof PrismLangException)
					((PrismLangException) e).setASTElement(expr);
				throw e;
			}
		}

		return res1;
	}

	/**
	 * Model check a literal.
	 */
	protected StateValues checkExpressionLiteral(Model<?> model, ExpressionLiteral expr) throws PrismException
	{
		return StateValues.createFromSingleValue(expr.getType(), expr.evaluate(), model);
	}

	/**
	 * Model check a constant.
	 */
	protected StateValues checkExpressionConstant(Model<?> model, ExpressionConstant expr) throws PrismException
	{
		return StateValues.createFromSingleValue(expr.getType(), expr.evaluate(constantValues), model);
	}

	/**
	 * Model check a variable reference.
	 * @param statesOfInterest the states of interest, see checkExpression()
	 */
	protected StateValues checkExpressionVar(Model<?> model, ExpressionVar expr, BitSet statesOfInterest) throws PrismException
	{
		// TODO (JK): optimize evaluation using statesOfInterest
		List<State> statesList = model.getStatesList();
		return StateValues.create(expr.getType(), i -> expr.evaluate(statesList.get(i)), model);
	}

	/**
	 * Model check an observable reference.
	 * @param statesOfInterest the states of interest, see checkExpression()
	 */
	protected StateValues checkExpressionObs(Model<?> model, ExpressionObs expr, BitSet statesOfInterest) throws PrismException
	{
		PartiallyObservableModel<?> poModel = (PartiallyObservableModel<?>) model;
		int iObservable = modelInfo.getObservableIndex(expr.getName());
		return StateValues.create(expr.getType(), i -> poModel.getObservationAsState(i).varValues[iObservable], model);
	}
	
	/**
	 * Model check a label.
	 * @param statesOfInterest the states of interest, see checkExpression()
	 */
	protected StateValues checkExpressionLabel(Model<?> model, ExpressionLabel expr, BitSet statesOfInterest) throws PrismException
	{
		// TODO: optimize evaluation using statesOfInterest

		LabelList ll;
		int i;

		// treat special cases
		if (expr.isDeadlockLabel()) {
			int numStates = model.getNumStates();
			BitSet bs = new BitSet(numStates);
			for (i = 0; i < numStates; i++) {
				bs.set(i, model.isDeadlockState(i));
			}
			return StateValues.createFromBitSet(bs, model);
		} else if (expr.isInitLabel()) {
			int numStates = model.getNumStates();
			BitSet bs = new BitSet(numStates);
			for (i = 0; i < numStates; i++) {
				bs.set(i, model.isInitialState(i));
			}
			return StateValues.createFromBitSet(bs, model);
		} else {
			// First look at labels attached directly to model
			BitSet bs = model.getLabelStates(expr.getName());
			if (bs != null) {
				return StateValues.createFromBitSet((BitSet) bs.clone(), model);
			}
			// Failing that, look in the label list (from properties file / modules file)
			ll = getLabelList();
			if (ll != null) {
				i = ll.getLabelIndex(expr.getName());
				if (i != -1) {
					// check recursively
					return checkExpression(model, ll.getLabel(i), statesOfInterest);
				}
			}
		}
		throw new PrismException("Unknown label \"" + expr.getName() + "\"");
}

	// Check property ref

	protected StateValues checkExpressionProp(Model<?> model, ExpressionProp expr, BitSet statesOfInterest) throws PrismException
	{
		// Look up property and check recursively
		Property prop = propertiesFile.lookUpPropertyObjectByName(expr.getName());
		if (prop != null) {
			mainLog.println("\nModel checking : " + prop);
			return checkExpression(model, prop.getExpression(), statesOfInterest);
		} else {
			throw new PrismException("Unknown property reference " + expr);
		}
	}

	// Check filter

	protected StateValues checkExpressionFilter(Model<?> model, ExpressionFilter expr, BitSet statesOfInterest) throws PrismException
	{
		// Translate filter
		Expression filter = expr.getFilter();
		// Create default filter (true) if none given
		if (filter == null)
			filter = Expression.True();
		// Remember whether filter is "true"
		boolean filterTrue = Expression.isTrue(filter);
		// Store some more info
		String filterStatesString = filterTrue ? "all states" : "states satisfying filter";

		// get the BitSet of states matching the filter, without taking statesOfInterest into account
		BitSet bsFilter = checkExpression(model, filter, null).getBitSet();

		// Check if filter state set is empty; we treat this as an error
		if (bsFilter.isEmpty()) {
			throw new PrismException("Filter satisfies no states");
		}

		// Remember whether filter is for the initial state and, if so, whether there's just one
		boolean filterInit = (filter instanceof ExpressionLabel && ((ExpressionLabel) filter).isInitLabel());
		boolean filterInitSingle = filterInit & model.getNumInitialStates() == 1;
		// Print out number of states satisfying filter
		if (!filterInit && !expr.isInvisible()) {
			mainLog.println("\nStates satisfying filter " + filter + ": " + bsFilter.cardinality());
		}
		// Possibly optimise filter
		FilterOperator op = expr.getOperatorType();
		if (op == FilterOperator.FIRST) {
			bsFilter.clear(bsFilter.nextSetBit(0) + 1, bsFilter.length());
		}

		// For some types of filter, store info that may be used to optimise model checking
		if (op == FilterOperator.STATE) {
			// Check filter satisfied by exactly one state
			if (bsFilter.cardinality() != 1) {
				String s = "Filter should be satisfied in exactly 1 state";
				s += " (but \"" + filter + "\" is true in " + bsFilter.cardinality() + " states)";
				throw new PrismException(s);
			}
			currentFilter = new Filter(Filter.FilterOperator.STATE, bsFilter.nextSetBit(0));
		} else if (op == FilterOperator.FORALL && filterInit && filterInitSingle) {
			currentFilter = new Filter(Filter.FilterOperator.STATE, bsFilter.nextSetBit(0));
		} else if (op == FilterOperator.FIRST && filterInit && filterInitSingle) {
			currentFilter = new Filter(Filter.FilterOperator.STATE, bsFilter.nextSetBit(0));
		} else {
			currentFilter = null;
		}
		// Check operand recursively, using bsFilter as statesOfInterest
		StateValues vals = checkExpression(model, expr.getOperand(), bsFilter);

		// Compute result according to filter type
		StateValues resVals = null;
		BitSet bsMatch = null, bs = null;
		boolean b = false;
		String resultExpl = null;
		Object resObj = null;
		Accuracy resAcc = null; 
		switch (op) {
		case PRINT:
		case PRINTALL:
			// Format of print-out depends on type
			if (expr.getType() instanceof TypeBool) {
				// NB: 'usual' case for filter(print,...) on Booleans is to use no filter
				mainLog.print("\nSatisfying states");
				mainLog.println(filterTrue ? ":" : " that are also in filter " + filter + ":");
				vals.printFiltered(mainLog, bsFilter);
			} else {
				if (op == FilterOperator.PRINT) {
					mainLog.println("\nResults (non-zero only) for filter " + filter + ":");
					vals.printFiltered(mainLog, bsFilter);
				} else {
					mainLog.println("\nResults (including zeros) for filter " + filter + ":");
					vals.printFiltered(mainLog, bsFilter, false, false, true, true);
				}
			}
			// Result vector is unchanged; for PRINT/PRINTALL, don't store a single value (in resObj)
			// Also, don't bother with explanation string
			resVals = vals;
			// Set vals to null to stop it being cleared below
			vals = null;
			break;
		case STORE:
			// Not much to do here - will be handled below when we store in the Result object
			// Result vector is unchanged; like PRINT/PRINTALL, don't store a single value (in resObj)
			// Also, don't bother with explanation string
			resVals = vals;
			// Set vals to null to stop it being cleared below
			vals = null;
			break;
		case MIN:
			// Compute min
			// Store as object/vector
			resObj = expr.apply(vals.filtered(bsFilter));
			resVals = StateValues.createFromSingleValue(expr.getType(), resObj, model);
			// Create explanation of result and print some details to log
			resultExpl = "Minimum value over " + filterStatesString;
			mainLog.println("\n" + resultExpl + ": " + resObj);
			// Also find states that (are close to) selected value for display to log
			bsMatch = vals.getBitSetFromCloseValue(resObj);
			bsMatch.and(bsFilter);
			break;
		case MAX:
			// Compute max
			// Store as object/vector
			resObj = expr.apply(vals.filtered(bsFilter));
			resVals = StateValues.createFromSingleValue(expr.getType(), resObj, model);
			// Create explanation of result and print some details to log
			resultExpl = "Maximum value over " + filterStatesString;
			mainLog.println("\n" + resultExpl + ": " + resObj);
			// Also find states that (are close to) selected value for display to log
			bsMatch = vals.getBitSetFromCloseValue(resObj);
			bsMatch.and(bsFilter);
			break;
		case ARGMIN:
			// Compute/display min
			resObj = ExpressionFilter.applyMin(vals.filtered(bsFilter), vals.getType());
			mainLog.print("\nMinimum value over " + filterStatesString + ": " + resObj);
			// Find states that (are close to) selected value
			bsMatch = vals.getBitSetFromCloseValue(resObj);
			bsMatch.and(bsFilter);
			// Store states in vector; for ARGMIN, don't store a single value (in resObj)
			// Also, don't bother with explanation string
			resVals = StateValues.createFromBitSet(bsMatch, model);
			// Print out number of matching states, but not the actual states
			mainLog.println("\nNumber of states with minimum value: " + bsMatch.cardinality());
			bsMatch = null;
			break;
		case ARGMAX:
			// Compute/display max
			resObj = ExpressionFilter.applyMax(vals.filtered(bsFilter), vals.getType());
			mainLog.print("\nMaximum value over " + filterStatesString + ": " + resObj);
			// Find states that (are close to) selected value
			bsMatch = vals.getBitSetFromCloseValue(resObj);
			bsMatch.and(bsFilter);
			// Store states in vector; for ARGMAX, don't store a single value (in resObj)
			// Also, don't bother with explanation string
			resVals = StateValues.createFromBitSet(bsMatch, model);
			// Print out number of matching states, but not the actual states
			mainLog.println("\nNumber of states with maximum value: " + bsMatch.cardinality());
			bsMatch = null;
			break;
		case COUNT:
			// Compute count
			// Store as object/vector
			resObj = expr.apply(vals.filtered(bsFilter));
			resVals =  StateValues.createFromSingleValue(expr.getType(), resObj, model);
			// Create explanation of result and print some details to log
			resultExpl = filterTrue ? "Count of satisfying states" : "Count of satisfying states also in filter";
			mainLog.println("\n" + resultExpl + ": " + resObj);
			break;
		case SUM:
			// Compute sum
			// Store as object/vector
			resObj = expr.apply(vals.filtered(bsFilter));
			resVals = StateValues.createFromSingleValue(expr.getType(), resObj, model);
			// Create explanation of result and print some details to log
			resultExpl = "Sum over " + filterStatesString;
			mainLog.println("\n" + resultExpl + ": " + resObj);
			break;
		case AVG:
			// Compute average
			// Store as object/vector
			resObj = expr.apply(vals.filtered(bsFilter));
			resVals = StateValues.createFromSingleValue(expr.getType(), resObj, model);
			// Create explanation of result and print some details to log
			resultExpl = "Average over " + filterStatesString;
			mainLog.println("\n" + resultExpl + ": " + resObj);
			break;
		case FIRST:
			// Find first value
			resObj = vals.firstFromBitSet(bsFilter);
			resVals = StateValues.createFromSingleValue(expr.getType(), resObj, model);
			// Create explanation of result and print some details to log
			resultExpl = "Value in ";
			if (filterInit) {
				resultExpl += filterInitSingle ? "the initial state" : "first initial state";
			} else {
				resultExpl += filterTrue ? "the first state" : "first state satisfying filter";
			}
			mainLog.println("\n" + resultExpl + ": " + resObj);
			break;
		case RANGE:
			// Find range of values
			resObj = expr.apply(vals.filtered(bsFilter));
			// Leave result vector unchanged: for a range, result is only available from Result object
			resVals = vals;
			// Set vals to null to stop it being cleared below
			vals = null;
			// Create explanation of result and print some details to log
			resultExpl = "Range of values over ";
			resultExpl += filterInit ? "initial states" : filterStatesString;
			mainLog.println("\n" + resultExpl + ": " + resObj);
			break;
		case FORALL:
			// Get access to BitSet for this
			bs = vals.getBitSet();
			if (bs == null) { // happens if Pareto set computation is used
				// Print some info to log
				mainLog.print("\nPareto set computation result evaluated");
				// default is true
				resObj = vals.getParetoArray();
				resVals = StateValues.createFromSingleValue(expr.getType(), true, model);
			} else {
				// Check "for all" over filter
				b = (boolean) expr.apply(vals.filtered(bsFilter));
				// Store as object/vector
				resObj = b;
				resVals = StateValues.createFromSingleValue(expr.getType(), resObj, model);
				// Create explanation of result and print some details to log
				resultExpl = "Property " + (b ? "" : "not ") + "satisfied in ";
				mainLog.print("\nProperty satisfied in " + ExpressionFilter.applyCount(vals.filtered(bsFilter), vals.getType()));
				if (filterInit) {
					if (filterInitSingle) {
						resultExpl += "the initial state";
					} else {
						resultExpl += "all initial states";
					}
					mainLog.println(" of " + model.getNumInitialStates() + " initial states.");
				} else {
					if (filterTrue) {
						resultExpl += "all states";
						mainLog.println(" of all " + model.getNumStates() + " states.");
					} else {
						resultExpl += "all filter states";
						mainLog.println(" of " + bsFilter.cardinality() + " filter states.");
					}
				}
			}
			break;
		case EXISTS:
			// Get access to BitSet for this
			bs = vals.getBitSet();
			// Check "there exists" over filter
			b = (boolean) expr.apply(vals.filtered(bsFilter));
			// Store as object/vector
			resObj = b;
			resVals = StateValues.createFromSingleValue(expr.getType(), resObj, model);
			// Create explanation of result and print some details to log
			resultExpl = "Property satisfied in ";
			if (filterTrue) {
				resultExpl += b ? "at least one state" : "no states";
			} else {
				resultExpl += b ? "at least one filter state" : "no filter states";
			}
			mainLog.println("\n" + resultExpl);
			break;
		case STATE:
			// Find first (only) value
			// Store as object/vector
			resObj = vals.firstFromBitSet(bsFilter);
			resAcc = vals.accuracy;
			resVals = StateValues.createFromSingleValue(expr.getType(), resObj, model);
			// Create explanation of result and print some details to log
			resultExpl = "Value in ";
			if (filterInit) {
				resultExpl += "the initial state";
			} else {
				resultExpl += "the filter state";
			}
			mainLog.println("\n" + resultExpl + ": " + resObj);
			break;
		default:
			throw new PrismException("Unrecognised filter type \"" + expr.getOperatorName() + "\"");
		}

		// For some operators, print out some matching states
		if (bsMatch != null) {
			StateValues states = StateValues.createFromBitSet(bsMatch, model);
			mainLog.print("\nThere are " + bsMatch.cardinality() + " states with ");
			mainLog.print((expr.getType() instanceof TypeDouble ? "(approximately) " : "") + "this value");
			boolean verbose = verbosity > 0; // TODO
			if (!verbose && bsMatch.cardinality() > 10) {
				mainLog.print(".\nThe first 10 states are displayed below. To view them all, enable verbose mode or use a print filter.\n");
				states.print(mainLog, 10);
			} else {
				mainLog.print(":\n");
				states.print(mainLog);
			}
		}

		// Store result
		result.setResult(resObj);
		result.setParameterString(parsed_params != null ? parsed_params.getParameterString() : null);
		result.setAccuracy(resAcc);
		// Set result explanation (if none or disabled, clear)
		if (expr.getExplanationEnabled() && resultExpl != null) {
			result.setExplanation(resultExpl.toLowerCase());
		} else {
			result.setExplanation(null);
		}
		// Store vector if requested
		if (op == FilterOperator.STORE) {
			result.setVector(resVals);
		}
		// Clear old vector if present
		// (and if the vector was not stored previously)
		if (vals != null && !(Expression.isFilter(expr.getOperand(), FilterOperator.STORE))) {
			vals.clear();
		}

		return resVals;
	}

	
	/**
	 * Method for handling the recursive part of PCTL* checking, i.e.,
	 * recursively checking maximal state subformulas and replacing them
	 * with labels and the corresponding satisfaction sets.
	 * <br>
	 * Extracts maximal state formula from an LTL path formula,
	 * model checks them (with the current model checker) and
	 * replaces them with ExpressionLabel objects that correspond
	 * to freshly generated labels attached to the model.
	 * <br>
	 * Returns the modified Expression.
	 */
	public Expression handleMaximalStateFormulas(ModelExplicit<?> model, Expression expr) throws PrismException
	{
		Vector<BitSet> labelBS = new Vector<BitSet>();

		LTLModelChecker ltlMC = new LTLModelChecker(this);
		// check the maximal state subformulas and gather
		// the satisfaction sets in labelBS, with index i
		// in the vector corresponding to label Li in the
		// returned formula
		Expression exprNew = ltlMC.checkMaximalStateFormulas(this, model, expr.deepCopy(), labelBS);

		HashMap<String, String> labelReplacements = new HashMap<String, String>();
		for (int i=0; i < labelBS.size(); i++) {
			String currentLabel = "L"+i;
			// Attach satisfaction set for Li to the model, record necessary
			// label renaming
			String newLabel = model.addUniqueLabel("phi", labelBS.get(i), getDefinedLabelNames());
			labelReplacements.put(currentLabel, newLabel);
		}
		// rename the labels
		return (Expression) exprNew.accept(new ReplaceLabels(labelReplacements));
	}

	/**
	 * Extract maximal propositional subformulas of an expression, model check them and
	 * replace them with ExpressionLabel objects (L0, L1, etc.) Expression passed in is modified directly, but the result
	 * is also returned. As an optimisation, model checking that results in true/false for all states is converted to an
	 * actual true/false, and duplicate results are given the same proposition. BitSets giving the states which satisfy each proposition
	 * are put into the list {@code propBSs}, which should be empty when this function is called.
	 * The names of the labels (L0, L1, etc. by default) are put into {@code propNames}, which should also be empty. 
	 */
	public Expression checkMaximalPropositionalFormulas(Model<?> model, Expression expr, List<String> propNames, List<BitSet> propBSs) throws PrismException
	{
		Expression exprNew = (Expression) expr.accept(new CheckMaximalPropositionalFormulas(this, model, propNames, propBSs));
		return exprNew;
	}

	/**
	 * Class to replace maximal propositional subformulas of an expression
	 * with labels corresponding to BitSets for the states that satisfy them.
	 */
	class CheckMaximalPropositionalFormulas extends ASTTraverseModify
	{
		private StateModelChecker mc;
		private Model<?> model;
		private List<String> propNames;
		private List<BitSet> propBSs;

		public CheckMaximalPropositionalFormulas(StateModelChecker mc, Model<?> model, List<String> propNames, List<BitSet> propBSs)
		{
			this.mc = mc;
			this.model = model;
			this.propNames = propNames;
			this.propBSs = propBSs;
		}

		public Object visit(ExpressionITE e) throws PrismLangException
		{
			return (e.getType() instanceof TypeBool && e.isProposition()) ? replaceWithLabel(e) : super.visit(e);
		}

		public Object visit(ExpressionBinaryOp e) throws PrismLangException
		{
			return (e.getType() instanceof TypeBool && e.isProposition()) ? replaceWithLabel(e) : super.visit(e);
		}

		public Object visit(ExpressionUnaryOp e) throws PrismLangException
		{
			return (e.getType() instanceof TypeBool && e.isProposition()) ? replaceWithLabel(e) : super.visit(e);
		}

		public Object visit(ExpressionFunc e) throws PrismLangException
		{
			return (e.getType() instanceof TypeBool && e.isProposition()) ? replaceWithLabel(e) : super.visit(e);
		}

		public Object visit(ExpressionIdent e) throws PrismLangException
		{
			return (e.getType() instanceof TypeBool && e.isProposition()) ? replaceWithLabel(e) : super.visit(e);
		}

		public Object visit(ExpressionLiteral e) throws PrismLangException
		{
			return (e.getType() instanceof TypeBool && e.isProposition()) ? replaceWithLabel(e) : super.visit(e);
		}

		public Object visit(ExpressionConstant e) throws PrismLangException
		{
			return (e.getType() instanceof TypeBool && e.isProposition()) ? replaceWithLabel(e) : super.visit(e);
		}

		public Object visit(ExpressionFormula e) throws PrismLangException
		{
			return (e.getType() instanceof TypeBool && e.isProposition()) ? replaceWithLabel(e) : super.visit(e);
		}

		public Object visit(ExpressionVar e) throws PrismLangException
		{
			return (e.getType() instanceof TypeBool && e.isProposition()) ? replaceWithLabel(e) : super.visit(e);
		}

		public Object visit(ExpressionLabel e) throws PrismLangException
		{
			return (e.getType() instanceof TypeBool && e.isProposition()) ? replaceWithLabel(e) : super.visit(e);
		}

		public Object visit(ExpressionProp e) throws PrismLangException
		{
			// Look up property and recurse
			Property prop = propertiesFile.lookUpPropertyObjectByName(e.getName());
			if (prop != null) {
				return e.accept(this);
			} else {
				throw new PrismLangException("Unknown property reference " + e, e);
			}
		}

		public Object visit(ExpressionFilter e) throws PrismLangException
		{
			return (e.getType() instanceof TypeBool && e.isProposition()) ? replaceWithLabel(e) : super.visit(e);
		}

		/**
		 * Evaluate this expression in all states (i.e. model check it),
		 * store the resulting BitSet in the list {@code propBSs},
		 * and return an ExpressionLabel with name Li to replace it
		 * (where i denotes the 0-indexed index into the list propBSs).
		 */
		private Object replaceWithLabel(Expression e) throws PrismLangException
		{
			// Model check
			StateValues sv;
			try {
				sv = mc.checkExpression(model, e, null);
			} catch (PrismException ex) {
				throw new PrismLangException(ex.getMessage());
			}
			BitSet bs = sv.getBitSet();
			// Detect special cases (true, false) for optimisation
			if (bs.isEmpty()) {
				return Expression.False();
			}
			if (bs.cardinality() == model.getNumStates()) {
				return Expression.True();
			}
			// See if we already have an identical result
			// (in which case, reuse it)
			int i = propBSs.indexOf(bs);
			if (i != -1) {
				sv.clear();
				return new ExpressionLabel("L" + i);
			}
			// Otherwise, add result to list, return new label
			String newLabelName = "L" + propBSs.size();
			propNames.add(newLabelName);
			propBSs.add(bs);
			return new ExpressionLabel(newLabelName);
		}
	}

	/**
	 * Construct rewards for the reward structure with index r of the reward generator and a model.
	 * Ensures non-negative rewards.
	 * <br>
	 * Note: Relies on the stored RewardGenerator for constructing the reward structure.
	 */
	protected <Value> Rewards<Value> constructRewards(Model<Value> model, int r) throws PrismException
	{
		return constructRewards(model, r, model.getModelType() == ModelType.CSG);
	}

	/**
	 * Construct rewards for the reward structure with index r of the reward generator and a model.
	 * <br>
	 * If {@code allowNegativeRewards} is true, the rewards may be positive and negative, i.e., weights.
	 * <br>
	 * Note: Relies on the stored RewardGenerator for constructing the reward structure.
	 */
	@SuppressWarnings("unchecked")
	protected <Value> Rewards<Value> constructRewards(Model<Value> model, int r, boolean allowNegativeRewards) throws PrismException
	{
		ConstructRewards constructRewards = new ConstructRewards(this);
		if (allowNegativeRewards)
			constructRewards.allowNegativeRewards();
		return constructRewards.buildRewardStructure(model, (RewardGenerator<Value>) rewardGen, r);
	}

	/**
	 * Construct expected rewards for the reward structure with index r of the reward generator and a model,
	 * i.e., using probability-weighted sum for any rewards attached to transitions,
	 * assigning them to states/choices.
	 * Ensures non-negative rewards.
	 * <br>
	 * Note: Relies on the stored RewardGenerator for constructing the reward structure.
	 */
	protected <Value> Rewards<Value> constructExpectedRewards(Model<Value> model, int r) throws PrismException
	{
		if (model.getModelType() == ModelType.IDTMC && rewardGen.rewardStructHasTransitionRewards(r)) {
			throw new PrismNotSupportedException("Transition rewards not supported for " + model.getModelType() + "s");

		}
		ConstructRewards constructRewards = new ConstructRewards(this);
		constructRewards.setExpectedRewards(true);
		if (model.getModelType() == ModelType.CSG) {
			constructRewards.allowNegativeRewards();
		}
		return constructRewards.buildRewardStructure(model, (RewardGenerator<Value>) rewardGen, r);
	}

	/**
	 * Load all labels from a PRISM labels (.lab) file and store them in BitSet objects.
	 * Return a map from label name Strings to BitSets.
	 * This is for all labels in the file, including "init", "deadlock".
	 * Note: the size of the BitSet may be smaller than the number of states.
	 */
	public static Map<String, BitSet> loadLabelsFile(String filename) throws PrismException
	{
		PrismExplicitImporter modelImporter = new PrismExplicitImporter(null, null, new File(filename), null, null, null);
		return modelImporter.extractAllLabels();
	}

	/**
	 * Export a model.
	 * @param model The model
	 * @param exportTask Export task (destination, which parts of the model to export, options)
	 */
	public <Value> void exportModel(Model<Value> model, ModelExportTask exportTask) throws PrismException
	{
		ModelExportOptions exportOptions = exportTask.getExportOptions();
		// Build an exporter of the required type
		ModelExporter<Value> exporter;
		switch (exportOptions.getFormat()) {
			case EXPLICIT:
				if (exportOptions.getExplicitRows()) {
					throw new PrismNotSupportedException("Export in rows format not yet supported by explicit engine");
				}
				exporter = new PrismExplicitExporter<>(exportOptions);
				break;
			case DOT:
				exporter = new DotExporter<>(exportOptions);
				break;
			case DRN:
				exporter = new DRNExporter<>(exportOptions);
				break;
			default:
				throw new PrismNotSupportedException("Export " + exportOptions.getFormat().description() + " not supported by explicit engine");
		}
		exporter.setModelInfo(modelInfo);
		File file = exportTask.getFile();
		// If needed, add label/reward info
		if (exportOptions.getFormat() == ModelExportFormat.DRN) {
			// Get rewards/labels
			List<Rewards<Value>> rewards = new ArrayList<>();
			for (int r = 0; r < rewardGen.getNumRewardStructs(); r++) {
				rewards.add(constructRewards(model, r));
			}
			List<String> labelNames = new ArrayList<>();
			if (exportTask.initLabelIncluded()) {
				labelNames.add("init");
			}
			if (exportTask.deadlockLabelIncluded()) {
				labelNames.add("deadlock");
			}
			labelNames.addAll(modelInfo.getLabelNames());
			List<BitSet> labelStates = checkLabels(model, labelNames);
			// Add to exporter
			exporter.addRewards(rewards, rewardGen.getRewardStructNames());
			exporter.setRewardEvaluator((Evaluator<Value>) rewardGen.getRewardEvaluator());
			exporter.addLabels(labelStates, labelNames);
		}
		// Export to log
		try (PrismLog out = getPrismLogForFile(file)) {
			exporter.exportModel(model, out);
		}
	}

	/**
	 * Export the transition function/matrix of a model.
	 * @param file File to export to (if null, print to the log instead)
	 * @param exportOptions The options for export
	 */
	public <Value> void exportTransitions(Model<Value> model, File file, ModelExportOptions exportOptions) throws PrismException
	{
		exportModel(model, ModelExportTask.fromOptions(file, exportOptions));
	}

	/**
	 * Export the state rewards for one reward structure of a model.
	 * @param model The model
	 * @param r Index of reward structure to export (0-indexed)
	 * @param file File to export to (if null, print to the log instead)
	 * @param exportOptions The options for export
	 */
	public <Value> void exportStateRewards(Model<Value> model, int r, File file, ModelExportOptions exportOptions) throws PrismException
	{
		if (exportOptions.getFormat() != ModelExportFormat.EXPLICIT) {
			throw new PrismNotSupportedException("Exporting state rewards in the requested format is currently not supported by the explicit engine");
		}

		try (PrismLog out = getPrismLogForFile(file)) {
			Rewards<Value> modelRewards = constructRewards(model, r);
			PrismExplicitExporter<Value> exporter = new PrismExplicitExporter<>(exportOptions);
			exporter.exportStateRewards(model, modelRewards, rewardGen.getRewardStructName(r), out);
		}
	}

	/**
	 * Export the transition rewards for one reward structure of a model.
	 * @param model The model
	 * @param r Index of reward structure to export (0-indexed)
	 * @param file File to export to (if null, print to the log instead)
	 * @param exportOptions The options for export
	 */
	public <Value> void exportTransRewards(Model<Value> model, int r, File file, ModelExportOptions exportOptions) throws PrismException
	{
		if (exportOptions.getFormat() != ModelExportFormat.EXPLICIT) {
			throw new PrismNotSupportedException("Exporting transition rewards in the requested format is currently not supported by the explicit engine");
		}

		try (PrismLog out = getPrismLogForFile(file)) {
			Rewards<Value> modelRewards = constructRewards(model, r);
			PrismExplicitExporter<Value> exporter = new PrismExplicitExporter<>(exportOptions);
			exporter.exportTransRewards(model, modelRewards, rewardGen.getRewardStructName(r), out);
		}
	}

	/**
	 * Export the set of states for a model.
	 * @param model The model
	 * @param file File to export to (if null, print to the log instead)
	 * @param exportOptions The options for export
	 */
	public <Value> void exportStates(Model<Value> model, File file, ModelExportOptions exportOptions) throws PrismException
	{
		try (PrismLog out = getPrismLogForFile(file)) {
			switch (exportOptions.getFormat()) {
				case EXPLICIT:
					new PrismExplicitExporter<Value>(exportOptions).exportStates(model, modelInfo.createVarList(), out);
					break;
				case MATLAB:
					new MatlabExporter<Value>(exportOptions).exportStates(model, modelInfo.createVarList(), out);
					break;
			}
		}
	}

	/**
	 * Export the observations for a (partially observable) model.
	 * @param model The model
	 * @param file File to export to (if null, print to the log instead)
	 * @param exportOptions The options for export
	 */
	public <Value> void exportObservations(Model<Value> model, File file, ModelExportOptions exportOptions) throws PrismException
	{
		try (PrismLog out = getPrismLogForFile(file)) {
			switch (exportOptions.getFormat()) {
				case EXPLICIT:
					new PrismExplicitExporter<Value>(exportOptions).exportObservations((PartiallyObservableModel<Value>) model, modelInfo, out);
					break;
				case MATLAB:
					new MatlabExporter<Value>(exportOptions).exportObservations((PartiallyObservableModel<Value>) model, modelInfo, out);
					break;
			}
		}
	}

	/**
	 * Export a set of labels and the states that satisfy them.
	 * @param model The model
	 * @param labelNames The names of the labels to export
	 * @param file File to export to (if null, print to the log instead)
	 * @param exportOptions The options for export
	 */
	public <Value> void exportLabels(Model<Value> model, List<String> labelNames, File file, ModelExportOptions exportOptions) throws PrismException
	{
		List<BitSet> labelStates = checkLabels(model, labelNames);
		exportLabels(model, labelNames, labelStates, file, exportOptions);
	}

	/**
	 * Determine the set of states that satisfy a specified list of labels,
	 * and return the states sets as a corresponding list of BitSets.
	 * @param model The model
	 * @param labelNames The names of the labels to export
	 */
	private List<BitSet> checkLabels(Model<?> model, List<String> labelNames) throws PrismException
	{
		List<BitSet> labelStates = new ArrayList<BitSet>();
		for (String labelName : labelNames) {
			StateValues sv = checkExpression(model, new ExpressionLabel(labelName), null);
			labelStates.add(sv.getBitSet());
		}
		return labelStates;
	}

	/**
	 * Export a set of labels and the states that satisfy them.
	 * @param model The model
	 * @param labelNames The names of the labels to export
	 * @param labelStates The states that satisfy each label, specified as a BitSet
	 * @param file File to export to (if null, print to the log instead)
	 * @param format The format in which to export
	 */
	public <Value> void exportLabels(Model<Value> model, List<String> labelNames, List<BitSet> labelStates, File file, ModelExportFormat format) throws PrismException
	{
		exportLabels(model, labelNames, labelStates, file, new ModelExportOptions(format));
	}

	/**
	 * Export a set of labels and the states that satisfy them.
	 * @param model The model
	 * @param labelNames The names of the labels to export
	 * @param labelStates The states that satisfy each label, specified as a BitSet
	 * @param file File to export to (if null, print to the log instead)
	 * @param exportOptions The options for export
	 */
	public <Value> void exportLabels(Model<Value> model, List<String> labelNames, List<BitSet> labelStates, File file, ModelExportOptions exportOptions) throws PrismException
	{
		try (PrismLog out = getPrismLogForFile(file)) {
			switch (exportOptions.getFormat()) {
				case EXPLICIT:
					new PrismExplicitExporter<Value>(exportOptions).exportLabels(model, labelNames, labelStates, out);
					break;
				case MATLAB:
					new MatlabExporter<Value>(exportOptions).exportLabels(model, labelNames, labelStates, out);
					break;
			}
		}
	}

	/**
	 * Do any exports after a model-automaton product construction, if requested
	 */
	public void doProductExports(Product<?> product) throws PrismException
	{
		if (getExportProductTrans()) {
			mainLog.println("\nExporting product transition matrix to file \"" + getExportProductTransFilename() + "\"...");
			int precision = settings.getInteger(PrismSettings.PRISM_EXPORT_MODEL_PRECISION);
			product.getProductModel().exportToPrismExplicitTra(getExportProductTransFilename(), precision);
		}
		if (getExportProductStates()) {
			mainLog.println("\nExporting product state space to file \"" + getExportProductStatesFilename() + "\"...");
			PrismFileLog out = new PrismFileLog(getExportProductStatesFilename());
			VarList newVarList = (VarList) modulesFile.createVarList().clone();
			String daVar = "_da";
			while (newVarList.exists(daVar)) {
				daVar = "_" + daVar;
			}
			newVarList.addVarAtStart(new Declaration(daVar, new DeclarationIntUnbounded()), 1);
			product.getProductModel().exportStates(Prism.EXPORT_PLAIN, newVarList, out);
			out.close();
		}
	}
}
