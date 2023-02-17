package eu.essentialcomplexity.graphdb.plugins.ldes_kafka;

import com.ontotext.graphdb.Config;
import com.ontotext.test.TemporaryLocalFolder;
import com.ontotext.test.functional.base.SingleRepositoryFunctionalTest;
import com.ontotext.test.utils.StandardUtils;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.query.Update;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.config.RepositoryConfig;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests the exampleBasic plugin.
 */
public class TestExampleBasicPlugin extends SingleRepositoryFunctionalTest {

    @ClassRule
    public static TemporaryLocalFolder tmpFolder = new TemporaryLocalFolder();
    @Override
    protected RepositoryConfig createRepositoryConfiguration() {
        // Creates a repository configuration with the rdfsplus-optimized ruleset
        return StandardUtils.createOwlimSe("rdfsplus-optimized");
    }

    @BeforeClass
    public static void setWorkDir() {
        System.setProperty("graphdb.home.work", String.valueOf(tmpFolder.getRoot()));
        Config.reset();
    }

    @AfterClass
    public static void resetWorkDir() {
        System.clearProperty("graphdb.home.work");
        Config.reset();
    }

    @Test
    public void testExampleBasic() {
        try (RepositoryConnection connection = getRepository().getConnection()) {
            // The predicate is how we request this to be processed by the plugin.
            // We'll get the response as a binding in the object position via the ?time variable.
            // We need a value in the unused subject position so an anonymous blank node is a good fit.
            //TupleQuery query = connection.prepareTupleQuery("select ?time { [] <http://example.com/now> ?time }");
            connection.begin();
            final Update query = connection.prepareUpdate("INSERT { " +
                    "<http://subject1/> <http://test/>  \"This is an example title\" ." +
                    "<http://subject1/> <http://has-node/>  _:b1 ." +
                    "_:b1 <http://has-value> \"bnode value\"" +
                    " } WHERE {}");
            query.execute();
            final Update query2 = connection.prepareUpdate("INSERT { <http://subject2/> <http://test/>  \"This is an example title2\" } WHERE {}");
            query2.execute();
            /*final Update query3 = connection.prepareUpdate("DELETE WHERE { \n" +
                    "     <http://subject1/> ?p ?o" +
                    "}\n");*/
            //query3.execute();
            connection.commit();

        }
    }




}
