package vn.ots.qc.api.nosql;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bson.Document;

/** Standard NoSQL client abstraction for test automation verification. */
public interface NoSqlClient extends AutoCloseable {
  @Override
  void close();

  /** Pings the database server to verify connectivity. */
  boolean ping();

  /** Runs a database command on the default database. */
  Document runCommand(String commandJson);

  /** Runs a database command on the default database. */
  Document runCommand(Document commandDoc);

  /** Runs a database command on a specific database. */
  Document runCommand(String databaseName, Document commandDoc);

  /** Finds documents matching filter in collection of default database. */
  List<Document> find(String collectionName, Document filter);

  /** Finds documents matching JSON filter string in collection of default database. */
  List<Document> find(String collectionName, String filterJson);

  /** Finds a single document matching filter in collection of default database. */
  Optional<Document> findOne(String collectionName, Document filter);

  /** Finds a single document matching JSON filter string in collection of default database. */
  Optional<Document> findOne(String collectionName, String filterJson);

  /** Counts documents matching filter in collection of default database. */
  long count(String collectionName, Document filter);

  /** Counts documents matching JSON filter string in collection of default database. */
  long count(String collectionName, String filterJson);

  /** Loads a query template from classpath, applies parameters, and executes find. */
  List<Document> loadAndFind(String collectionName, String resourcePath, Map<String, ?> parameters);

  /** Loads a query template from classpath, applies parameters, and executes findOne. */
  Optional<Document> loadAndFindOne(
      String collectionName, String resourcePath, Map<String, ?> parameters);

  /** Loads a command template from classpath, applies parameters, and runs command. */
  Document loadAndRunCommand(String resourcePath, Map<String, ?> parameters);

  /** Factory to instantiate default MongoDB-backed NoSQL client. */
  static NoSqlClient create(NoSqlConfig config) {
    return MongoNoSqlClient.create(config);
  }

  /** Factory to instantiate default NoSQL client from environment variables. */
  static NoSqlClient fromEnvironment() {
    return MongoNoSqlClient.fromEnvironment();
  }
}
