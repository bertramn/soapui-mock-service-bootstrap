package io.fares.soapui.mock;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.io.File;
import java.io.IOException;

/**
 * Runner implementation for
 */
public final class Bootstrap {

  private final static Logger log = Logger.getLogger(Bootstrap.class.getName());

  private static final File soapuiHomeFile;
  private static final String SOAPUI_HOME_PROP = "soapui.home";

  /**
   * Daemon object used by main
   */
  private static Bootstrap daemon = null;

  static {
    // Will always be non-null
    String userDir = System.getProperty("user.dir");

    // load home from sys prop which should always be set
    String home = System.getProperty(SOAPUI_HOME_PROP);
    File homeFile = null;

    if (home != null) {
      File f = new File(home);
      try {
        homeFile = f.getCanonicalFile();
      } catch (IOException ioe) {
        homeFile = f.getAbsoluteFile();
      }
    }

    // fall-back. Use current directory
    if (homeFile == null) {
      File f = new File(userDir);
      try {
        homeFile = f.getCanonicalFile();
      } catch (IOException ioe) {
        homeFile = f.getAbsoluteFile();
      }
    }

    soapuiHomeFile = homeFile;
    System.setProperty(SOAPUI_HOME_PROP, soapuiHomeFile.getPath());

  }

  /**
   * thread executor we will use to start the soapui mockrunner in blocking mode
   */
  final ExecutorService executor = Executors.newFixedThreadPool(2);

  /**
   * the classloader used to start the soapui mockrunner
   */
  ClassLoader soapuiLoader = null;

  /**
   * the arguments loaded into the actual soapui mockrunner
   */
  private Object[] args;
  /**
   * reference to the soapui mockrunner which is run as a daemon
   */
  private Worker soapuiDaemon = null;

  /**
   * Main method and entry point when starting soapui.
   *
   * @param args Command line arguments to be processed
   */
  public static void main(String args[]) {

    if (daemon == null) {
      // Don't set daemon until init() has completed
      Bootstrap bootstrap = new Bootstrap();
      try {
        bootstrap.init();
      } catch (Throwable t) {
        handleThrowable(t);
        t.printStackTrace();
        return;
      }
      daemon = bootstrap;
    } else {
      Thread.currentThread().setContextClassLoader(Bootstrap.class.getClassLoader());
    }

    try {
      String command = "start";
      if (args.length == 0) {
        // TODO output soapui mockrunner usage
        log.warning("Must provide some arguments to run the soapui mockrunner.");
      }

      if (command.equals("start")) {
        daemon.load(args);
        daemon.start();

      } else if (command.equals("stop")) {
        daemon.stop();
      } else {
        log.warning("Bootstrap: command \"" + command + "\" does not exist.");
      }
    } catch (Throwable t) {
      // Unwrap the Exception for clearer error reporting
      if (t instanceof InvocationTargetException &&
        t.getCause() != null) {
        t = t.getCause();
      }
      handleThrowable(t);
      t.printStackTrace();
      System.exit(1);
    }

  }

  private static void handleThrowable(Throwable t) {
    if (t instanceof ThreadDeath) {
      throw (ThreadDeath) t;
    }
    if (t instanceof VirtualMachineError) {
      throw (VirtualMachineError) t;
    }
    // All other instances of Throwable will be silently swallowed
  }

  /**
   * Initialize daemon
   *
   * @throws Exception Fatal initialization error
   */
  public void init() throws Exception {

    // Load our startup class and call its process() method
    if (log.isLoggable(Level.FINER))
      log.finer("Loading startup class");

    soapuiDaemon = new Worker();

    // TODO set classloader on thread worker
  }

  /**
   * Load daemon
   */
  private void load(String[] arguments) throws Exception {
    // when called from commons daemon we need to init
    if (daemon == null) daemon = this;
    // we just peg the args to the daemon so we can provided it to the soapui mockrunner
    daemon.args = arguments;
  }

  /**
   * Load the soapui daemon
   *
   * @param arguments Initialization arguments
   * @throws Exception Fatal initialization error
   */
  public void init(String[] arguments) throws Exception {
    init();
    load(arguments);
  }

  /**
   * Start the SoapUI daemon
   *
   * @throws Exception Fatal start error
   */
  public void start() throws Exception {
    if (soapuiDaemon == null) init();
    executor.execute(soapuiDaemon);
    if (log.isLoggable(Level.WARNING)) {
      log.warning("Submitted job");
    }
  }

  /**
   * Stop the SoapUI Daemon.
   *
   * @throws Exception Fatal stop error
   */
  public void stop() throws Exception {
    executor.shutdown();
  }

  /**
   * Destroy the SoapUI daemon
   */
  public void destroy() {
    executor.shutdownNow();
  }

  /**
   * The thread runner that will load and
   */
  private class Worker implements Runnable {

    private Object startupInstance;

    protected Worker() throws ClassNotFoundException, IllegalAccessException, InstantiationException {

      soapuiLoader = Bootstrap.class.getClassLoader();

      Class<?> startupClass = soapuiLoader.loadClass("com.eviware.soapui.tools.SoapUIMockServiceRunner");
      this.startupInstance = startupClass.newInstance();

    }

    @Override
    public void run() throws RuntimeException {

      Method method = null;
      Object result = new Integer(-1);
      try {
        method = startupInstance.getClass().getMethod("runFromCommandLine", String[].class);
        result = method.invoke(startupInstance, new Object[]{daemon.args});
      } catch (Exception e) {
        if (!(e instanceof InterruptedException)) {
          throw new RuntimeException("Failed to start soapui using com.eviware.soapui.tools.SoapUIMockServiceRunner#runFromCommandLine()", e);
        }
      }

      if ((Integer) result != 0) {
        System.exit((Integer) result);
      }
    }
  }

}
