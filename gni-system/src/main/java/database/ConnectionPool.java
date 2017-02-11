package database;

import java.util.concurrent.LinkedBlockingQueue;

import static database.Variables.AMOUNT_OF_CONNECTIONS;

/**
 * @author Saul
 */
public class ConnectionPool implements Runnable {

    private LinkedBlockingQueue<SQLConnection> pool;

    public ConnectionPool() {
        this.pool = new LinkedBlockingQueue<SQLConnection>();
    }

    public void run() {

    }

    private void generateConnections() {
        for (int i = 0; i < AMOUNT_OF_CONNECTIONS; i++) {
            pool.add(new SQLConnection());
        }
    }

    public SQLConnection getConnection() {
        try {
            return pool.take();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return new SQLConnection();
    }

    public void returnConnection(SQLConnection sqlConnection) {
        if (pool.size() < AMOUNT_OF_CONNECTIONS) {
            pool.add(sqlConnection);
        } else {
            sqlConnection.close();
        }
    }
}
