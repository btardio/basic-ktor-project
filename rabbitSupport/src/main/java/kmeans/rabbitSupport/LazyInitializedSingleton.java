package kmeans.rabbitSupport;

import com.rabbitmq.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.System.exit;
import static kmeans.support.ContextCloseExit.closeContextExit;

public class LazyInitializedSingleton {
    private static final Logger log = LoggerFactory.getLogger(LazyInitializedSingleton.class);
    private static ThreadLocal<Channel> instance = new ThreadLocal<>();
    private static ThreadLocal<Connection> connection;
    private LazyInitializedSingleton(){}

    public static Channel getInstance(ConnectionFactory connectionFactory) {

        if (instance.get() == null || !instance.get().isOpen()) {

            try {

                connection.set(connectionFactory.newConnection());
                connection.get().addShutdownListener(new ShutdownListener() {
                    @Override
                    public void shutdownCompleted(ShutdownSignalException e) {
                        log.error("Connection shutdown unexpectedly.", e);
                        closeContextExit(-1);
                    }
                });

                instance.set(connection.get().createChannel());
                instance.get().addShutdownListener(new ShutdownListener() {
                    @Override
                    public void shutdownCompleted(ShutdownSignalException e) {
                        log.error("Channel shutdown unexpectedly.", e);
                        closeContextExit(-1);
                    }
                });
            } catch (Exception e) {
                log.error("Unable to create connection factory instance.", e);
                closeContextExit(-1);
            }
        }
        return instance.get();
    }
}