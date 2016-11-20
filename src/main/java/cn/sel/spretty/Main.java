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

import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.regex.Pattern;

/**
 */
public class Main
{
    //region Constants & Fields
    private static final String OS_NAME = System.getProperty("os.name");
    private static final String PROJECT_NAME = "Spretty";
    private static final String DEFAULT_ID = "UNKNOWN_ID";
    private static final Pattern REG_INST_ID = Pattern.compile("[A-Za-z0-9]{1,32}");
    private static final Pattern REG_CONTEXT = Pattern.compile("[/]?[A-Za-z][A-Za-z0-9_-]*");
    private static final ProtectionDomain PROTECTION_DOMAIN = Main.class.getProtectionDomain();
    private static final Server JETTY_SERVER = new Server();
    private static final int SIGN_TERM = 15;
    private static final File WORK_DIR;
    private static final String PID_FILENAME;
    private static final String WAR_FILENAME;
    private static final int CUR_PID;
    private static FileChannel PID_CHANNEL;
    private static FileLock PID_LOCK;
    //endregion
    //
    //region Initialization
    static
    {
        try
        {
            if(isLinux() || isWindows())
            {
                CUR_PID = getCurPid();
                WAR_FILENAME = getWarFilename();
                String curDir = new File(WAR_FILENAME).getParent();
                WORK_DIR = new File(curDir, "work");
                PID_FILENAME = curDir + File.separator + "pid.pid";
            } else
            {
                throw new IllegalStateException("Unsupported OS!");
            }
        } catch(Exception e)
        {
            throw new IllegalStateException("Initialization failed!", e);
        }
    }
    private static int getCurPid()
    {
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        String name = runtime.getName();
        int index = name.indexOf('@');
        if(index > 0)
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

    private static void addShutdownHook()
    {
        Runtime.getRuntime().addShutdownHook(new Thread()
        {
            @Override
            public void run()
            {
                try
                {
                    info("hook~~");
                    shutdown();
                } catch(Exception e)
                {
                    e.printStackTrace();
                }
            }
        });
    }
    //endregion

    public static void main(String... args)
    {
        try
        {
            addShutdownHook();
            lock();
            if(args.length > 0)
            {
                String cmd = args[0];
                switch(cmd)
                {
                    case "start":
                        startup(args);
                        break;
                    case "stop":
                        shutdown(args);
                        break;
                    case "kill":
                        kill(args);
                        break;
                    case "list":
                        list();
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
            clean();
        } catch(Exception e)
        {
            error("ERROR: " + e.getMessage());
            e.printStackTrace();
        } finally
        {
            unlock();
        }
    }

    //region Main Functions
    private static void startup(String... args)
            throws Exception
    {
        int port = getPort(args);
        String ctx = getContextPath(args);
        String id = getInstanceId(args);
        logo();
        info();
        info("******** Process ID(PID): " + CUR_PID);
        info("******** Instance ID    : " + id);
        info("******** Servlet Port   : " + port);
        info("******** Context Path   : " + ctx);
        info("******** Work Directory : " + WORK_DIR);
        info("******** WAR Filename   : " + WAR_FILENAME);
        prepareWorkDir();
        initServer(port, ctx);
        JETTY_SERVER.start();
        saveInstance(port, id, ctx);
        unlock();
        JETTY_SERVER.join();
        removeCurrent();
        info("Server Stopped.");
    }

    private static void shutdown(String... args)
            throws Exception
    {
        if(args.length > 0)
        {
            Map<Integer, ServerInstance> map = readPid();
            String[] targetIds = getStoppingIds(args);
            for(String id : targetIds)
            {
                for(Map.Entry<Integer, ServerInstance> entry : map.entrySet())
                {
                    int pid = entry.getKey();
                    ServerInstance inst = entry.getValue();
                    if(id.equals(inst.id))
                    {
                        if(pid == CUR_PID)
                        {
                            stopJetty();
                        } else
                        {
                            sign(SIGN_TERM, pid);
                        }
                    }
                }
            }
        } else
        {
            kill();
        }
    }

    private static void kill(String... args)
            throws Exception
    {
        Map<Integer, ServerInstance> map = readPid();
        if(args.length > 0)
        {
            int[] targetPids = getKillingPids(args);
            for(int pid : targetPids)
            {
                if(map.containsKey(pid))
                {
                    sign(SIGN_TERM, pid);
                }
            }
        } else
        {
            map.keySet().forEach(pid->sign(SIGN_TERM, pid));
        }
    }

    private static void list()
            throws IOException
    {
        Map<Integer, ServerInstance> map = readPid();
        if(map != null && !map.isEmpty())
        {
            String doubleLine = "============================================================";
            String singleLine = "------------------------------------------------------------";
            info(doubleLine);
            info("PID\t\tPORT\t\tCONTEXT\t\tID");
            info(singleLine);
            map.forEach((pid, inst)->info(String.format("%d\t\t%d\t\t%s\t\t%s", pid, inst.port, inst.ctx, inst.id)));
            info(doubleLine);
        } else
        {
            info("No instance is running.");
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
        String doubleLine = "===========================================================================================";
        String singleLine = "-------------------------------------------------------------------------------------------";
        String dottedLine = "·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·";
        info(doubleLine);
        info(PROJECT_NAME + " User Manual");
        info(dottedLine);
        info("Usage:");
        info();
        info("  <Command>    <Options>           <Description>");
        info(singleLine);
        info("   start                            Start a new server instance.");
        info("                [port=?]            Port number(1025,65535]. Default: 8080.");
        info("                [ctx=?]             Context path. Default: ROOT.");
        info("                [id=?]              Unique identification for the instance. Default: None.");
        info(singleLine);
        info("   stop                             Stop instance(s) associated with the specified ids.");
        info("                [id1] [id2]...      Default: all.");
        info(singleLine);
        info("   kill                             Stop instance(s) associated with the specified ids.");
        info("                [pid1] [pid2]...    Default: all.");
        info(singleLine);
        info("   list                             List all running instances.");
        info(singleLine);
        info("   help|h                           Display help info.");
        info(doubleLine);
    }
    //endregion
    //region Assistants For Main Functions.

    private static void logo()
    {
        info("       ****                                                                                         ");
        info("     ********                                                                                       ");
        info("    **********                                                                                      ");
        info("    **     **                                               **           **                       ");
        info("   ***                                         **           **           **                       ");
        info("   ****           ********      *******      ******      ********     ********      ***    ***    ");
        info("    *******       *********     ********     *******     ********     ********      ***    ***    ");
        info("     ********      **    **      **   **    ***   ***       **           **          ***   **     ");
        info("        ******     **    ***     **         **     **       **           **           **   **     ");
        info("           ***     **    ***     **         *********       **           **           *** ***     ");
        info("   **       **     **    ***     **         *********       **           **            *****      ");
        info("   ***     ***     **    **      **         **    ***       **   **      **   **       *****      ");
        info("   ***********     ***  ***      **         ***  ****       *** ***      *** ***        ***       ");
        info("    *********      *******       **          *******        *****        *****         ***        ");
        info("      *****        ******       ****           ***           ****         ****        ***         ");
        info("                   **                                                               ****          ");
        info("                   **                                                               ***           ");
        info("                  ****                                                                              ");
    }

    private static void prepareWorkDir()
            throws IOException
    {
        if(!WORK_DIR.exists() && !WORK_DIR.mkdirs())
        {
            throw new IOException("Failed to create work directory!");
        }
    }

    private static void initServer(int port, String contextPah)
            throws Exception
    {
        WebAppContext webApp = createWebApp(contextPah);
        NetworkTrafficServerConnector connector = new NetworkTrafficServerConnector(JETTY_SERVER);
        connector.setPort(port);
        connector.setSoLingerTime(-1);
        JETTY_SERVER.setStopAtShutdown(true);
        JETTY_SERVER.addConnector(connector);
        JETTY_SERVER.setHandler(webApp);
    }

    private static WebAppContext createWebApp(String contextPah)
            throws Exception
    {
        WebAppContext webApp = new WebAppContext();
        webApp.setInitParameter("org.eclipse.jetty.servlet.Default.dirAllowed", "false");
        webApp.setContextPath(contextPah);
        webApp.setCopyWebDir(true);
        webApp.setPersistTempDirectory(false);
        webApp.setTempDirectory(WORK_DIR);
        webApp.setWar(WAR_FILENAME);
        return webApp;
    }

    private static int getPort(String... args)
            throws Exception
    {
        for(String arg : args)
        {
            if(arg.startsWith("port="))
            {
                String portString = arg.substring(arg.indexOf('=') + 1);
                int port = Integer.parseInt(portString);
                if(port > 1024 && port <= 65535)
                {
                    return port;
                } else
                {
                    throw new IllegalArgumentException(String.format("Bad port number -> %s", portString));
                }
            }
        }
        return 8080;
    }

    private static String getContextPath(String... args)
            throws Exception
    {
        for(String arg : args)
        {
            if(arg.startsWith("ctx="))
            {
                String ctx = arg.substring(arg.indexOf('=') + 1);
                if(REG_CONTEXT.matcher(ctx).matches())
                {
                    String _ctx = ctx.substring(ctx.indexOf('/') + 1);
                    return _ctx.isEmpty() || _ctx.equalsIgnoreCase("ROOT") ? "/" : '/' + _ctx;
                } else
                {
                    throw new IllegalArgumentException(String.format("Invalid context path -> %s!", ctx));
                }
            }
        }
        return "/";
    }

    private static String getInstanceId(String[] args)
            throws Exception
    {
        for(String arg : args)
        {
            if(arg.startsWith("id="))
            {
                String id = arg.substring(arg.indexOf('=') + 1);
                if(REG_INST_ID.matcher(id).matches())
                {
                    return id;
                } else
                {
                    throw new IllegalArgumentException(String.format("Invalid instance id -> %s!", id));
                }
            }
        }
        return DEFAULT_ID;
    }

    private static String[] getStoppingIds(String... args)
    {
        int argSize = args.length;
        String[] result = new String[argSize];
        for(int i = 0; i < argSize; i++)
        {
            String arg = args[i];
            if(REG_INST_ID.matcher(arg).matches())
            {
                result[i] = arg;
            } else
            {
                error(String.format("Invalid id -> %s! Ignored.", arg));
            }
        }
        return result;
    }

    private static int[] getKillingPids(String... args)
    {
        int argSize = args.length;
        int[] result = new int[argSize];
        for(int i = 0; i < argSize; i++)
        {
            String arg = args[i];
            try
            {
                int pid = Integer.parseInt(arg);
                if(pid > 1)
                {
                    result[i] = pid;
                }
            } catch(Exception e)
            {
                error(String.format("Invalid pid number -> %s! Ignored.", arg));
            }
        }
        return result;
    }

    private static void saveInstance(int port, String id, String ctx)
            throws Exception
    {
        if(PID_CHANNEL != null && PID_CHANNEL.isOpen())
        {
            try
            {
                Map<Integer, ServerInstance> map = readPid();
                map.put(CUR_PID, new ServerInstance(port, id, ctx));
                writeMap(map);
            } catch(Exception e)
            {
                e.printStackTrace();
            }
        } else
        {
            throw new IllegalStateException("Unable to access pid file!");
        }
    }

    private static void removeCurrent()
            throws Exception
    {
        removeInstanceByPid(CUR_PID);
    }

    private static void removeInstanceByPid(int pid)
            throws Exception
    {
        lock();
        if(pid > 0)
        {
            Map<Integer, ServerInstance> map = readPid();
            map.remove(pid);
            writeMap(map);
        }
    }

    private static void removeInstanceById(String id)
            throws Exception
    {
        if(id != null && !id.isEmpty())
        {
            Map<Integer, ServerInstance> map = readPid();
            for(Map.Entry<Integer, ServerInstance> entry : map.entrySet())
            {
                Integer pid = entry.getKey();
                ServerInstance instance = entry.getValue();
                if(id.equals(instance.id))
                {
                    map.remove(pid);
                }
            }
            writeMap(map);
        }
    }

    private static void writeMap(Map<Integer, ServerInstance> map)
            throws Exception
    {
        Set<String> set = new HashSet<>();
        for(Map.Entry<Integer, ServerInstance> entry : map.entrySet())
        {
            Integer pid = entry.getKey();
            ServerInstance instance = entry.getValue();
            set.add(String.format("%d,%d,%s,%s", pid, instance.port, instance.id, instance.ctx));
        }
        String[] newArray = new String[map.size()];
        set.toArray(newArray);
        String newData = String.join("|", (CharSequence[])newArray);
        byte[] bytes = newData.getBytes();
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        PID_CHANNEL.truncate(0);
        PID_CHANNEL.write(buffer);
    }

    private static Map<Integer, ServerInstance> readPid()
            throws IOException
    {
        int fileSize = (int)PID_CHANNEL.size();
        ByteBuffer buffer = ByteBuffer.allocate(fileSize);
        PID_CHANNEL.position(0);
        buffer.flip();
        String data = new String(buffer.array());
        String[] dataArray = data.split("\\|");
        Map<Integer, ServerInstance> result = new HashMap<>(dataArray.length);
        for(String item : dataArray)
        {
            String[] array = item.split(",");
            if(array.length == 4)
            {
                Integer pid = Integer.valueOf(array[0]);
                Integer port = Integer.valueOf(array[1]);
                String id = array[2];
                String ctx = array[3];
                result.put(pid, new ServerInstance(port, id, ctx));
            }
        }
        return result;
    }

    private static void lock()
            throws IOException
    {
        RandomAccessFile pidFile = new RandomAccessFile(PID_FILENAME, "rw");
        PID_CHANNEL = pidFile.getChannel();
        PID_LOCK = PID_CHANNEL.lock();
    }

    private static void unlock()
    {
        if(PID_LOCK != null)
        {
            try
            {
                PID_LOCK.release();
            } catch(IOException e)
            {
                e.printStackTrace();
            }
        }
        if(PID_CHANNEL != null)
        {
            try
            {
                PID_CHANNEL.close();
            } catch(IOException e)
            {
                e.printStackTrace();
            }
        }
    }

    private static boolean isRunning()
    {
        return JETTY_SERVER.isRunning() || JETTY_SERVER.isStarted() || JETTY_SERVER.isStarting();
    }

    private static void stopJetty()
            throws Exception
    {
        if(isRunning())
        {
            JETTY_SERVER.stop();
        } else
        {
            info("Not running");
        }
    }

    private static void clean()
            throws Exception
    {
        Map<Integer, ServerInstance> map = readPid();
        Iterator<Integer> iterator = map.keySet().iterator();
        while(iterator.hasNext())
        {
            Integer pid = iterator.next();
            if(pid != null)
            {
                if(!isProcessExist(pid))
                {
                    info(String.format("Process '%d' has been shutdown. Removing...", pid));
                    iterator.remove();
                    removeInstanceByPid(pid);
                }
            }
        }
        if(map.isEmpty())
        {
            File file = new File(PID_FILENAME);
            file.deleteOnExit();
        }
    }

    public static boolean isProcessExist(int pid)
    {
        try
        {
            Process process;
            if(isLinux())
            {
                process = Runtime.getRuntime().exec(new String[]{"/bin/sh", "-c", "ps -e|grep " + pid});
            } else if(isWindows())
            {
                process = Runtime.getRuntime().exec("tasklist");
            } else
            {
                throw new IllegalStateException("Unsupported OS!");
            }
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String string = bufferedReader.readLine();
            while(string != null)
            {
                if(string.contains("java") && string.contains(String.valueOf(pid)))
                {
                    return true;
                }
                string = bufferedReader.readLine();
            }
            return false;
        } catch(IOException e)
        {
            throw new IllegalStateException(String.format("Failed to check process(%d) list.", pid));
        }
    }

    private static boolean isLinux()
    {
        return OS_NAME.contains("Linux");
    }

    private static boolean isWindows()
    {
        return OS_NAME.contains("Windows");
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

    private static class ServerInstance
    {
        public String ctx;
        private int port;
        private String id;

        public ServerInstance(int port, String id, String ctx)
        {
            this.port = port;
            this.id = id;
            this.ctx = ctx;
        }
    }
    //endregion
}