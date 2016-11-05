/*
 * MIT License
 *
 * Copyright (c) 2016 Erlu Shang (sel8616@gmail.com/philshang@163.com)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package cn.sel.spretty;

import org.eclipse.jetty.server.NetworkTrafficServerConnector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.webapp.WebAppContext;
import sun.misc.Signal;
import sun.misc.SignalHandler;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.URL;
import java.net.URLDecoder;
import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 */
public class Main implements SignalHandler
{
    //region Constants & Fields
    private static final String PROJECT_NAME = "Spretty";
    private static final ProtectionDomain PROTECTION_DOMAIN = Main.class.getProtectionDomain();
    private static final Pattern REG_CONTEXT = Pattern.compile("[/]?[A-Za-z0-9_-]*");
    private static final int SIGN_STATUS = 1;
    private static final int SIGN_RESTART = 12;
    private static final int SIGN_SHUTDOWN = 15;
    private static final int SIGN_STOP = 19;
    private static final int SIGN_KILL = 9;
    private static final String PID_FILENAME = "pid.pid";
    private static final Server JETTY_SERVER = new Server();
    private static final Map<String, String> ARGS = new HashMap<>();
    private static final RandomAccessFile PID_FILE;
    private static final String WAR_FILENAME;
    private static final File WORK_DIR;
    private static final int CUR_PID;
    //endregion
    //
    //region Initialization
    static
    {
        try
        {
            installSignals();
            CUR_PID = getCurPID();
            WAR_FILENAME = getWarFilename();
            String curDir = new File(WAR_FILENAME).getParent();
            WORK_DIR = new File(curDir, "work");
            PID_FILE = new RandomAccessFile(curDir + File.separator + PID_FILENAME, "rw");
        } catch(Exception e)
        {
            throw new IllegalStateException("", e);
        }
    }
    private SignalHandler signalHandler;

    private static void installSignals()
    {
        install("HUP");
        install("USR2");
        install("TERM");
    }

    public static void install(String signalName)
    {
        Signal signal = new Signal(signalName);
        Main instance = new Main();
        instance.signalHandler = Signal.handle(signal, instance);
    }

    private static int getCurPID()
    {
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        String name = runtime.getName();
        int index = name.indexOf('@');
        if(index != -1)
        {
            return Integer.parseInt(name.substring(0, index));
        } else
        {
            throw new IllegalStateException("Failed to get pid.");
        }
    }

    private static String getWarFilename()
            throws UnsupportedEncodingException
    {
        URL location = PROTECTION_DOMAIN.getCodeSource().getLocation();
        return URLDecoder.decode(location.getPath(), "UTF-8");
    }
    //endregion

    public static void main(String... args)
    {
        try
        {
            collectArgs(args);
            if(args.length > 0)
            {
                String cmd = args[0];
                switch(cmd)
                {
                    case "on":
                    case "start":
                    case "startup":
                        startup();
                        break;
                    case "re":
                    case "restart":
                        restart();
                        break;
                    case "off":
                    case "stop":
                    case "shutdown":
                        shutdown();
                        break;
                    case "stat":
                    case "state":
                    case "status":
                        status();
                        break;
                    case "h":
                    case "help":
                        help();
                        break;
                    default:
                        help(String.format("Bad command -> %s", cmd));
                }
            } else
            {
                help();
            }
        } catch(Exception e)
        {
            e.printStackTrace();
        }
    }

    //region Main Functions
    private static void startup()
            throws Exception
    {
        int pid = readPID();
        if(pid < 0)
        {
            info("starting");
            savePid();
            prepareWorkDir();
            int port = getPort();
            info("########## Current PID   = " + CUR_PID);
            info("########## Servlet Port  = " + port);
            info("########## Work Directory= " + WORK_DIR);
            info("########## WAR Filename  = " + WAR_FILENAME);
            initServer(port);
            JETTY_SERVER.start();
            JETTY_SERVER.join();
            info("Server Stopped.");
            dispose();
        } else
        {
            error("Already startup.");
        }
    }

    private static void restart()
            throws Exception
    {
        int pid = readPID();
        if(pid == -1 || pid == CUR_PID)
        {
            shutdown();
            startup();
        } else
        {
            sign(SIGN_RESTART, pid);
        }
    }

    private static void shutdown()
            throws Exception
    {
        int pid = readPID();
        if(pid == -1 || pid == CUR_PID)
        {
            if(JETTY_SERVER.isRunning() || JETTY_SERVER.isStarted() || JETTY_SERVER.isStarting())
            {
                JETTY_SERVER.stop();
            } else
            {
                info("Not running");
                dispose();
            }
        } else
        {
            sign(SIGN_SHUTDOWN, pid);
        }
    }

    private static void status()
            throws IOException
    {
        int pid = readPID();
        if(pid == -1)
        {
            info("OFF");
            dispose();
        } else
        {
            info(String.format("ON(%d)", pid));
        }
    }

    private static void help(String msg)
    {
        error(msg);
        info();
        help();
    }

    private static void help()
    {
        String double_line = "=========================================================================";
        String single_line = "-------------------------------------------------------------------------";
        String dotted_line = "-  -  -  -  -  -  -  -  -  -  -  -  -  -  -  -  -  -  -  -  -  -  -  -  -";
        info(double_line);
        info(PROJECT_NAME + " User Manual");
        info(dotted_line);
        info("Usage:");
        info();
        info("  [Command]            [Options]  [Default]  [Description]");
        info(single_line);
        info("   on|start|startup                           Start the server.");
        info("                        port       8080       Port number.(1025,65535]");
        info("                        cxt        ROOT       Context path.{input}");
        info(single_line);
        info("   re|restart                                 Restart the server.");
        info(single_line);
        info("   off|stop|shutdown                          Stop the server.");
        info(single_line);
        info("   stat|state|status                          View the server status.");
        info(single_line);
        info("   help|h                                     Display help info.");
        info(double_line);
    }
    //endregion

    //region Assistants For Main Functions.
    private static void savePid()
            throws IOException
    {
        PID_FILE.writeInt(CUR_PID);
    }

    private static void collectArgs(String... args)
            throws Exception
    {
        if(args.length == 2)
        {
            String arg = args[1];
            int index = arg.indexOf('=');
            String name = arg.substring(0, index);
            String value = arg.substring(index + 1);
            ARGS.put(name, value.isEmpty() ? "" : value);
        } else if(args.length > 2)
        {
            throw new Exception("Too many arguments!");
        }
    }

    private static void prepareWorkDir()
            throws IOException
    {
        if(!WORK_DIR.exists() && !WORK_DIR.mkdirs())
        {
            throw new IOException("Failed to create work directory!");
        }
    }

    private static void initServer(int port)
            throws Exception
    {
        WebAppContext webApp = createWebApp();
        NetworkTrafficServerConnector connector = new NetworkTrafficServerConnector(JETTY_SERVER);
        connector.setPort(port);
        connector.setSoLingerTime(-1);
        JETTY_SERVER.setStopAtShutdown(true);
        JETTY_SERVER.addConnector(connector);
        JETTY_SERVER.setHandler(webApp);
    }

    private static WebAppContext createWebApp()
            throws Exception
    {
        WebAppContext webApp = new WebAppContext();
        webApp.setInitParameter("org.eclipse.jetty.servlet.Default.dirAllowed", "false");
        webApp.setContextPath(getContextPath());
        webApp.setCopyWebDir(true);
        webApp.setPersistTempDirectory(false);
        webApp.setTempDirectory(WORK_DIR);
        webApp.setWar(WAR_FILENAME);
        return webApp;
    }

    private static int getPort()
            throws Exception
    {
        String portString = ARGS.get("port");
        if(portString != null)
        {
            int port = Integer.parseInt(portString);
            if(port > 1024 && port <= 65535)
            {
                return port;
            } else
            {
                throw new IllegalArgumentException(String.format("Bad port number -> %s", port));
            }
        } else
        {
            return 8080;
        }
    }

    private static String getContextPath()
            throws Exception
    {
        String ctx = ARGS.get("ctx");
        if(ctx == null)
        {
            return "/";
        }
        if(!REG_CONTEXT.matcher(ctx).matches())
        {
            throw new IllegalArgumentException(String.format("Invalid context path -> %s!", ctx));
        }
        String _ctx = ctx.substring('/');
        if(_ctx.isEmpty() || _ctx.equalsIgnoreCase("ROOT"))
        {
            return "/";
        }
        return '/' + _ctx;
    }

    private static int readPID()
            throws IOException
    {
        if(PID_FILE != null)
        {
            PID_FILE.seek(0);
            long length = PID_FILE.length();
            return length < 4 ? -1 : PID_FILE.readInt();
        } else
        {
            throw new IllegalStateException("PID file is null!");
        }
    }

    private static void dispose()
            throws IOException
    {
        if(PID_FILE != null)
        {
            PID_FILE.close();
        }
        File file = new File(PID_FILENAME);
        file.deleteOnExit();
    }

    private static void info()
    {
        System.out.println();
    }

    private static void info(Object msg)
    {
        if(msg != null)
        {
            System.out.println(msg);
        }
    }

    private static void error(Object msg)
    {
        if(msg != null)
        {
            System.err.println("[ERROR] " + msg);
        }
    }
    //endregion

    //region Signal Handler.
    private static boolean sign(int sign, int pid)
    {
        String command = String.format("kill -%d %d", sign, pid);
        Runtime runtime = Runtime.getRuntime();
        BufferedInputStream bufferedInputStream = null;
        BufferedReader bufferedReader = null;
        try
        {
            Process process = runtime.exec(command);
            bufferedInputStream = new BufferedInputStream(process.getInputStream());
            bufferedReader = new BufferedReader(new InputStreamReader(bufferedInputStream));
            if(process.waitFor() != 0)
            {
                if(process.exitValue() == 1)
                {
                    error("Failed to notify the main process!");
                } else
                {
                    return true;
                }
            }
        } catch(Exception e)
        {
            e.printStackTrace();
        } finally
        {
            try
            {
                if(bufferedReader != null)
                {
                    bufferedReader.close();
                }
                if(bufferedInputStream != null)
                {
                    bufferedInputStream.close();
                }
            } catch(IOException e)
            {
                e.printStackTrace();
            }
        }
        return false;
    }

    @Override
    public void handle(Signal signal)
    {
        try
        {
            switch(signal.getNumber())
            {
                case SIGN_RESTART:
                    restart();
                    break;
                case SIGN_SHUTDOWN:
                case SIGN_STOP:
                case SIGN_KILL:
                    shutdown();
                    break;
                case SIGN_STATUS:
                    status();
                    break;
            }
        } catch(Exception e)
        {
            error(String.format("Error occurred while handle signal -> %s.", signal));
            e.printStackTrace();
        }
    }
    //endregion
}