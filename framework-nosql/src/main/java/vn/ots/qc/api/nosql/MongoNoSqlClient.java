package vn.ots.qc.api.nosql;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * MongoDB implementation of NoSqlClient interface.
 */
public final class MongoNoSqlClient implements NoSqlClient {
    private static final Logger log = LoggerFactory.getLogger(MongoNoSqlClient.class);

    private final MongoClient mongoClient;
    private final String defaultDatabaseName;

    private MongoNoSqlClient(MongoClient mongoClient, String defaultDatabaseName) {
        this.mongoClient = Objects.requireNonNull(mongoClient, "mongoClient must not be null");
        this.defaultDatabaseName = defaultDatabaseName != null ? defaultDatabaseName.trim() : "";
    }

    public static MongoNoSqlClient create(NoSqlConfig config) {
        Objects.requireNonNull(config, "config must not be null");

        ConnectionString connString = new ConnectionString(config.uri());
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(connString)
                .applyToSocketSettings(builder ->
                        builder.connectTimeout(config.connectTimeoutMs(), TimeUnit.MILLISECONDS))
                .applyToClusterSettings(builder ->
                        builder.serverSelectionTimeout(config.serverSelectionTimeoutMs(), TimeUnit.MILLISECONDS))
                .build();

        MongoClient client = MongoClients.create(settings);
        String dbName = config.defaultDatabase();
        if (dbName.isBlank() && connString.getDatabase() != null) {
            dbName = connString.getDatabase();
        }

        return new MongoNoSqlClient(client, dbName);
    }

    public static MongoNoSqlClient fromEnvironment() {
        return create(NoSqlConfig.fromEnvironment());
    }

    public MongoDatabase database() {
        if (defaultDatabaseName.isBlank()) {
            throw new IllegalStateException("No default database name configured in NoSqlConfig");
        }
        return mongoClient.getDatabase(defaultDatabaseName);
    }

    public MongoDatabase database(String name) {
        Objects.requireNonNull(name, "database name must not be null");
        return mongoClient.getDatabase(name);
    }

    public MongoCollection<Document> collection(String collectionName) {
        return database().getCollection(collectionName);
    }

    public MongoCollection<Document> collection(String databaseName, String collectionName) {
        return database(databaseName).getCollection(collectionName);
    }

    @Override
    public boolean ping() {
        try {
            Document pingCommand = new Document("ping", 1);
            Document response = runCommand(pingCommand);
            Number ok = response.get("ok", Number.class);
            return ok != null && ok.doubleValue() == 1.0;
        } catch (Exception e) {
            log.warn("NoSQL ping failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public Document runCommand(String commandJson) {
        return runCommand(Document.parse(commandJson));
    }

    @Override
    public Document runCommand(Document commandDoc) {
        return database().runCommand(commandDoc);
    }

    @Override
    public Document runCommand(String databaseName, Document commandDoc) {
        return database(databaseName).runCommand(commandDoc);
    }

    @Override
    public List<Document> find(String collectionName, Document filter) {
        Objects.requireNonNull(collectionName, "collectionName must not be null");
        Document queryFilter = filter != null ? filter : new Document();
        List<Document> results = new ArrayList<>();
        collection(collectionName).find(queryFilter).into(results);
        return results;
    }

    @Override
    public List<Document> find(String collectionName, String filterJson) {
        Document filter = (filterJson != null && !filterJson.isBlank()) ? Document.parse(filterJson) : new Document();
        return find(collectionName, filter);
    }

    @Override
    public Optional<Document> findOne(String collectionName, Document filter) {
        Objects.requireNonNull(collectionName, "collectionName must not be null");
        Document queryFilter = filter != null ? filter : new Document();
        Document first = collection(collectionName).find(queryFilter).first();
        return Optional.ofNullable(first);
    }

    @Override
    public Optional<Document> findOne(String collectionName, String filterJson) {
        Document filter = (filterJson != null && !filterJson.isBlank()) ? Document.parse(filterJson) : new Document();
        return findOne(collectionName, filter);
    }

    @Override
    public long count(String collectionName, Document filter) {
        Objects.requireNonNull(collectionName, "collectionName must not be null");
        Document queryFilter = filter != null ? filter : new Document();
        return collection(collectionName).countDocuments(queryFilter);
    }

    @Override
    public long count(String collectionName, String filterJson) {
        Document filter = (filterJson != null && !filterJson.isBlank()) ? Document.parse(filterJson) : new Document();
        return count(collectionName, filter);
    }

    @Override
    public List<Document> loadAndFind(String collectionName, String resourcePath, Map<String, ?> parameters) {
        Document filter = NoSqlQueryLoader.loadDocument(resourcePath, parameters);
        return find(collectionName, filter);
    }

    @Override
    public Optional<Document> loadAndFindOne(String collectionName, String resourcePath, Map<String, ?> parameters) {
        Document filter = NoSqlQueryLoader.loadDocument(resourcePath, parameters);
        return findOne(collectionName, filter);
    }

    @Override
    public Document loadAndRunCommand(String resourcePath, Map<String, ?> parameters) {
        Document command = NoSqlQueryLoader.loadDocument(resourcePath, parameters);
        return runCommand(command);
    }

    @Override
    public void close() {
        try {
            mongoClient.close();
        } catch (Exception e) {
            log.warn("Error closing MongoClient: {}", e.getMessage());
        }
    }
}
