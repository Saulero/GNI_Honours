package database;

import java.sql.SQLException;
import java.util.concurrent.LinkedBlockingQueue;

import static database.Variables.AMOUNT_OF_CONNECTIONS;

/**
 * @author Saul
 */
public class ConnectionPool {

    private LinkedBlockingQueue<SQLConnection> pool;

    public ConnectionPool() {
        this.pool = new LinkedBlockingQueue<SQLConnection>();
        generateConnections();
    }

    private void generateConnections() {
        for (int i = 0; i < AMOUNT_OF_CONNECTIONS; i++) {
            pool.add(new SQLConnection());
        }
    }

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
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return new SQLConnection();
    }

    public void returnConnection(final SQLConnection sqlConnection) {
        if (pool.size() < AMOUNT_OF_CONNECTIONS) {
            pool.add(sqlConnection);
        } else {
            sqlConnection.close();
        }
    }

    public void close() {
        for (SQLConnection connection : pool) {
            connection.close();
        }
    }
}
