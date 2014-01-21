package example.jetty;

import org.apache.commons.io.FileUtils;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ShutdownHandler;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.webapp.WebAppContext;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.SocketException;
import java.net.URL;
import java.security.ProtectionDomain;

public class Main {
    private static final String CLASS_ONLY_AVAILABLE_IN_IDE = "no.sgfinans.finansfront2.front.FrontConfiguration";
    private static final String PROJECT_RELATIVE_PATH_TO_WEBAPP = "src/main/java/META-INF/webapp";
    private final int port;
    private final String contextPath;
    private final String workPath;
    private final String secret;

    public static void main(String[] args) throws Exception {
        Main sc = new Main();

        if (args.length != 1) sc.start();
        else if ("status".equals(args[0])) sc.status();
        else if ("stop".equals(args[0])) sc.stop();
        else if ("start".equals(args[0])) sc.start();
        else sc.usage();
    }

    public Main() {
        try {
            String configFile = System.getProperty("config", "jetty.properties");
            System.getProperties().load(new FileInputStream(configFile));
        } catch (Exception ignored) {
        }

        port = Integer.parseInt(System.getProperty("jetty.port", "8080"));
        contextPath = System.getProperty("jetty.contextPath", "/");
        workPath = System.getProperty("jetty.workDir", null);
        secret = System.getProperty("jetty.secret", "eb27fb2e61ed603363461b3b4e37e0a0");
    }

    private void start() {
        // Start a Jetty server with some sensible(?) defaults
        try {
            // Increase thread pool
            QueuedThreadPool threadPool = new QueuedThreadPool();
            threadPool.setMaxThreads(100);

            Server srv = new Server(threadPool);
            srv.setStopAtShutdown(true);

            // Allow 5 seconds to complete.
            // Adjust this to fit with your own webapp needs.
            // Remove this if you wish to shut down immediately (i.e. kill <pid> or Ctrl+C).
            srv.setStopTimeout(5000);//TODO: This might not be so graceful... See https://bugs.eclipse.org/bugs/show_bug.cgi?id=420142

            // Ensure using the non-blocking connector (NIO)
            ServerConnector http = new ServerConnector(srv, new HttpConnectionFactory(new HttpConfiguration()));
            http.setPort(port);
            http.setIdleTimeout(30000);

            srv.setConnectors(new Connector[]{http});

            // Get the war-file
            ProtectionDomain protectionDomain = Main.class.getProtectionDomain();
            String warFile = protectionDomain.getCodeSource().getLocation().toExternalForm();
            String currentDir = new File(protectionDomain.getCodeSource().getLocation().getPath()).getParent();

            // Add the warFile (this jar)
            WebAppContext context = new WebAppContext(warFile, contextPath);
            context.setServer(srv);
            resetTempDirectory(context, currentDir);

            // Add the handlers
            addHandlers(srv, context);

            srv.start();
            System.out.println("We have liftoff");
            srv.join();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void addHandlers(Server srv, WebAppContext context) {
        HandlerList handlers = new HandlerList();
        handlers.addHandler(context);
        handlers.addHandler(new ShutdownHandler(secret));
        //TODO: Added to version-control if we are to use BigIP.
        //handlers.addHandler(new BigIPNodeHandler(secret));
        srv.setHandler(handlers);
    }

    private boolean isRunningInShadedJar() {
        try {
            Class.forName(CLASS_ONLY_AVAILABLE_IN_IDE);
            return false;
        } catch (ClassNotFoundException anExc) {
            return true;
        }
    }

    private void stop() {
        try {
            URL url = new URL("http://localhost:" + port + "/shutdown?token=" + secret);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.getResponseCode();
            System.out.println("Shutting down " + url + ": " + connection.getResponseMessage());
        } catch (SocketException e) {
            System.out.println("Not running");
            // Okay - the server is not running
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void status() {
        try {
            URL url = new URL("http://localhost:" + port + contextPath);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                System.out.println("online");
                System.exit(0);
            } else {
                System.out.println("unstable. response code: " + responseCode);
                System.exit(4);
            }
        } catch (SocketException e) {
            System.out.println("offline");
            System.exit(3);
            // Okay - the server is not running
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void usage() {
        System.out.println("Usage: java -jar <file.jar> [start|stop|status\n\t" +
                "start    Start the server (default)\n\t" +
                "stop     Stop the server gracefully\n\t" +
                "status   Check the current server status\n\t"
        );
        System.exit(-1);
    }

    private void resetTempDirectory(WebAppContext context, String currentDir) throws IOException {
        File workDir;
        if (workPath != null) {
            workDir = new File(workPath);
        } else {
            workDir = new File(currentDir, "work");
        }
        FileUtils.deleteDirectory(workDir);
        context.setTempDirectory(workDir);
    }
}
