package database;

import java.sql.SQLException;
import java.util.concurrent.LinkedBlockingQueue;

import static database.Variables.AMOUNT_OF_CONNECTIONS;

/**
 * @author Saul
 */
public class ConnectionPool {

    /** List of all active connections. */
    private LinkedBlockingQueue<SQLConnection> pool;

    /**
     * Constructor.
     */
    public ConnectionPool() {
        this.pool = new LinkedBlockingQueue<>();
        generateConnections();
    }

    /**
     * Creates and opens a set amount of connections.
     */
    private void generateConnections() {
        for (int i = 0; i < AMOUNT_OF_CONNECTIONS; i++) {
            pool.add(new SQLConnection());
        }
    }

    /**
     * Gets an active connection from the pool, or ceates a new one if necessary.
     * @return The SQLConnection
     */
    public SQLConnection getConnection() {
        if (pool.isEmpty()) {
            return new SQLConnection();
        } else {
            try {
                SQLConnection connection = pool.take();
                if (!connection.getConnection().isClosed()) {
                    return connection;
                } else {
                    return new SQLConnection();
                }
            } catch (InterruptedException | SQLException e) {
                e.printStackTrace();
            }
        }
        return new SQLConnection();
    }

    /**
     * Puts an SQLConnection back into the pool, after the caller is done with it.
     * Closes the connection if adding it back would put too many transactions in the pool.
     * @param sqlConnection The returned SQLConnection
     */
    public void returnConnection(final SQLConnection sqlConnection) {
        if (pool.size() < AMOUNT_OF_CONNECTIONS) {
            pool.add(sqlConnection);
        } else {
            sqlConnection.close();
        }
    }

    /**
     * Closes the connection pool by emptying it.
     */
    public void close() {
        for (SQLConnection connection : pool) {
            connection.close();
        }
    }
}
