package eu.essentialcomplexity.graphdb.plugins.ldes_kafka;

import com.ontotext.trree.sdk.*;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.sail.memory.model.MemStatement;

import java.util.*;

public class LdesKafkaSinkPlugin extends PluginBase implements StatementListener, PluginTransactionListener {

	// The predicate we will be listening for
	private static final String NOW_PREDICATE = "http://example.com/now";

	private long nowPredicateId; // ID of the predicate in the entity pool

	//Dirty resource map (trx, context, subject)
	private HashMap<Long, HashMap<Long, Set<Long>>> dirtyResourceMap = new HashMap<>();

	private void registerTaintedResource(Long trx, Long context, Long subject) {
		HashMap<Long, Set<Long>> contextMap = this.dirtyResourceMap.getOrDefault(trx, new HashMap<>());
		Set<Long> subjectSet = contextMap.getOrDefault(context, new HashSet<>());
		subjectSet.add(subject);
		contextMap.put(context, subjectSet);
		this.dirtyResourceMap.put(trx, contextMap);

	}


	// Service interface methods
	@Override
	public String getName() {
		return "ldes-kafka-sink";
	}

	// Plugin interface methods
	@Override
	public void initialize(InitReason reason, PluginConnection pluginConnection) {
		// Create an IRI to represent the now predicate
		IRI nowPredicate = SimpleValueFactory.getInstance().createIRI(NOW_PREDICATE);
		// Put the predicate in the entity pool using the SYSTEM scope
		nowPredicateId = pluginConnection.getEntities().put(nowPredicate, Entities.Scope.SYSTEM);

		getLogger().info("LDES Kafka Sink plugin loaded.");
	}

	@Override
	public void transactionStarted(PluginConnection pluginConnection) {

	}

	@Override
	public void transactionCommit(PluginConnection pluginConnection) {

		transactionToLdes(pluginConnection);
		Statements statments = pluginConnection.getStatements();
		Entities entities = pluginConnection.getEntities();
		Repository repository = pluginConnection.getRepository();


	}

	private void transactionToLdes(PluginConnection pluginConnection) {
		Long trx = pluginConnection.getTransactionId();
		HashMap<Long, Set<Long>> contextMap = this.dirtyResourceMap.getOrDefault(trx, new HashMap<>());
		contextMap.forEach( (Long context, Set subjects) -> {

			Value graph = pluginConnection.getEntities().get(context);
			System.out.print("Output for stream " + graph + "\n");
			Set compactedSubjectList = reduceSubjectsToRootNodes(subjects, context, pluginConnection);
			Set expandedSubjectList = expandSubjectList(compactedSubjectList, context, pluginConnection);
			StatementIterator iterator = getStatements(expandedSubjectList, context, pluginConnection);
			while (iterator.next()) {
				Value s = pluginConnection.getEntities().get(iterator.subject);
				Value p = pluginConnection.getEntities().get(iterator.predicate);
				Value o = pluginConnection.getEntities().get(iterator.object);
				Value g = pluginConnection.getEntities().get(iterator.context);
				System.out.print("Here we go: s " + s + " p " + p + " o " + o +" g " + g + "\n");
			}
		});
	}

	private StatementIterator getStatements(Set<Long> subjects, Long context, PluginConnection pluginConnection) {
		ArrayList<long[]> list = new ArrayList<>();
		subjects.forEach((Long subject)-> {
			StatementIterator statements = pluginConnection.getStatements().get(subject, 0, 0, context);
			while (statements.next()) {
				final long[] arr = new long[4];
				arr[0] = statements.subject;
				arr[1] = statements.predicate;
				arr[2] = statements.object;
				arr[3] = statements.context;
				list.add(arr);
			}
		});
		final long[][] statementsArray = new long[list.size()][4];
		int index = 0;
		for (final long[] value : list) {
			statementsArray[index++] = value;
		}

		return StatementIterator.create(statementsArray);
	}

	private Set expandSubjectList(Set<Long> subjects, Long context, PluginConnection pluginConnection) {
		Stack<Long> subjectStack = new Stack<>();
		Set<Long> allSubjects = new HashSet<>(subjects);
		subjects.forEach(subjectStack::push);

		// @todo Can cycles in the graph mess up this code?
		while (!subjectStack.isEmpty()) {
			Long subjectId = subjectStack.pop();
			StatementIterator statements = pluginConnection.getStatements().get(subjectId, 0, 0, context);
			while (statements.next()) {
				Value object = pluginConnection.getEntities().get(statements.object);
				if (statements.isExplicit() && object.isBNode()) {
					allSubjects.add(statements.object);
					subjectStack.push(statements.object);
				}
			}
		}
		return allSubjects;
	}

	private Set reduceSubjectsToRootNodes(Set<Long> subjects, Long context, PluginConnection pluginConnection) {
		Set<Long> reducedSet = new HashSet<>();
		subjects.forEach((Long subjectId) -> {
			reducedSet.add(getRootURIofNode(subjectId, context, pluginConnection));
		});
		return reducedSet;
	}


	/**
	 * Fetch 'named ancestor' of a given blank node.
	 * @param subjectId
	 * @param context
	 * @param pluginConnection
	 * @return
	 */
	private Long getRootURIofNode(Long subjectId, Long context, PluginConnection pluginConnection) {
		Value subject = pluginConnection.getEntities().get(subjectId);
		if (subject.isIRI()) {
			return subjectId;
		}
		if (subject.isBNode()) {
			StatementIterator statements = pluginConnection.getStatements().get(0, 0, subjectId, context);
			// @todo Throw exception if more than 1 statement has this bnode in the object position.
			while (statements.next()) {
				return getRootURIofNode(statements.subject, context, pluginConnection);
			}
		}
		// @todo Throw exception here? If Only certain contexts can be transformed into LDES this should be the case.
		// Basically, at this point we have a standalone blank node, which is not something we can track in LDES.
		// For now, this is ignored, but it could lead to nast bugs and integrity issues in the future.
		return 0L;
	}

	@Override
	public void transactionCompleted(PluginConnection pluginConnection) {

	}

	@Override
	public void transactionAborted(PluginConnection pluginConnection) {

	}



	@Override
	public boolean statementAdded(long subject, long predicate, long object, long context, boolean explicit,
								  PluginConnection pluginConnection) {
		if (!explicit)
			return false;
		Long trx = pluginConnection.getTransactionId();
		this.registerTaintedResource(trx, context, subject);
		//this.dirtyResourceMap.add(subject);
		Value s = pluginConnection.getEntities().get(subject);
		Value p = pluginConnection.getEntities().get(predicate);
		Value o = pluginConnection.getEntities().get(object);
		Value g = pluginConnection.getEntities().get(context);

		return false;
	}

	@Override
	public boolean statementRemoved(long subject, long predicate, long object, long context, boolean explicit,
									PluginConnection pluginConnection) {
		if (!explicit)
			return false;
		Long trx = pluginConnection.getTransactionId();
		this.registerTaintedResource(trx, context, subject);
		//this.dirtyResourceMap.add(subject);
		Value s = pluginConnection.getEntities().get(subject);
		Value p = pluginConnection.getEntities().get(predicate);
		Value o = pluginConnection.getEntities().get(object);
		Value g = pluginConnection.getEntities().get(context);
		return false;
	}
}

