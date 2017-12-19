package prism;

import java.util.Vector;

import jdd.JDD;
import jdd.JDDNode;
import jdd.JDDVars;
import parser.Values;
import parser.VarList;

public class GamesModel extends NondetModel {
	
	private JDDNode[] ddPlayersIds;	// MTBDD for each player
	private JDDVars[] playersVars;
	private JDDNode idsTree;
	private String[] playersNames;
	private JDDVars allDDPlayersVars;
	
	public GamesModel(JDDNode tr, JDDNode s, JDDNode[] sr, JDDNode[] trr, String[] rsn, JDDVars arv, JDDVars acv, JDDVars asyv, JDDVars asv,
					  JDDVars achv, JDDVars andv, ModelVariablesDD mvdd, int nm, String[] mn, JDDVars[] mrv, JDDVars[] mcv, int nv, 
					  VarList vl, JDDVars[] vrv, JDDVars[] vcv, Values cv, JDDVars[] pvs, JDDNode[] dis, JDDNode idt, String[] pns) {
		super(tr, s, sr, trr, rsn, arv, acv, asyv, asv, achv, andv, mvdd, nm, mn, mrv,
				mcv, nv, vl, vrv, vcv, cv);
		// TODO Auto-generated constructor stub
		playersVars = pvs;
		ddPlayersIds = dis;
		idsTree = idt;
		playersNames = pns;
		allDDPlayersVars = new JDDVars();
		
		for(JDDVars v : playersVars) {
			allDDPlayersVars.addVar(v.toCubeSet());
		}
		
	}

	public ModelType getModelType()
	{
		return ModelType.SMG;
	}
	
	public String[] getPlayersNames() {
		return playersNames;
	}
	
	public int getNumPlayers() {
		return ddPlayersIds.length;
	}

	public JDDNode getIdsTree() {
		return idsTree;
	}
	
	public JDDVars[] getPlayersVars() {
		return playersVars;
	}
	
	public JDDNode[] getDdPlyaersIds() {
		return ddPlayersIds;
	}
	
	public JDDVars getAllDDPlayersVars() {
		return allDDPlayersVars;
	}

	public NondetModel STG2MDP() throws PrismException {
		
		JDDNode transMDP = JDD.SumAbstract(trans.copy(), allDDPlayersVars);
		JDDNode transRewardsMDP[] = new JDDNode[transRewards.length];

		for(int i = 0; i < transRewards.length; i++) {
			transRewardsMDP[i] = JDD.SumAbstract(transRewards[i], allDDPlayersVars);
		}
		
		JDDVars allDDNondetVarsMDP = allDDNondetVars.copy();
		allDDNondetVarsMDP.removeVars(allDDPlayersVars);
		
		NondetModel equivMDP =  new NondetModel(transMDP, start, stateRewards, transRewardsMDP, rewardStructNames, allDDRowVars, allDDColVars,
			     						allDDSynchVars, allDDSchedVars, allDDChoiceVars, allDDNondetVarsMDP, modelVariables,
			     						numModules, moduleNames, moduleDDRowVars, moduleDDColVars,
			     						numVars, varList, varDDRowVars, varDDColVars, constantValues);
		
		equivMDP.setSynchs((Vector<String>)synchs);
	
		equivMDP.setTransInd(transInd);
		equivMDP.setTransSynch(transSynch);
		
		equivMDP.setTransActions(transActions);
		equivMDP.setTransPerAction(transPerAction);
		
		
		equivMDP.doReachability();
		equivMDP.filterReachableStates();
		
		return equivMDP;
	}
	
	@Override
	public void clear()
	{
		super.clear();
		for(int i = 0; i < ddPlayersIds.length; i++) {
			JDD.Deref(ddPlayersIds[i]);
		}
		JDDVars.derefAllArray(playersVars);
		allDDPlayersVars.derefAll();
		JDD.Deref(idsTree);
	}
}