package net.hyperic.sigar.cmd;

import java.io.File;
import java.io.FileFilter;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;

import java.net.URLClassLoader;
import java.net.URL;

import net.hyperic.sigar.SigarLoader;

public class Runner {

    private static HashMap wantedJars = new HashMap();

    static {
        wantedJars.put("bcel-5.1.jar", Boolean.FALSE);
        wantedJars.put("junit.jar", Boolean.FALSE);
        wantedJars.put("log4j.jar", Boolean.FALSE);
    }

    private static void printMissingJars() {
        for (Iterator it = wantedJars.entrySet().iterator();
             it.hasNext();)
        {
            Map.Entry entry = (Map.Entry)it.next();
            String jar = (String)entry.getKey();
            if (wantedJars.get(jar) == Boolean.FALSE) {
                System.out.println("Unable to locate: " + jar);
            }
        }
    }

    private static boolean missingJars() {
        for (Iterator it = wantedJars.entrySet().iterator();
             it.hasNext();)
        {
            Map.Entry entry = (Map.Entry)it.next();
            String jar = (String)entry.getKey();
            if (wantedJars.get(jar) == Boolean.FALSE) {
                return true;
            }
        }

        return false;
    }

    public static URL[] getLibJars(String dir) throws Exception {
        File[] jars = new File(dir).listFiles(new FileFilter() {
            public boolean accept(File file) {
                return wantedJars.get(file.getName()) != null;
            }
        });

        if (jars == null) {
            return new URL[0];
        }

        URL[] urls = new URL[jars.length];

        for (int i=0; i<jars.length; i++) {
            wantedJars.put(jars[i].getName(),
                           Boolean.TRUE);
            URL url = 
                new URL("jar", null,
                        "file:" + jars[i].getAbsolutePath() + "!/");

            urls[i] = url;
        }

        return urls;
    }

    private static void addURLs(URL[] jars) throws Exception {
        URLClassLoader loader =
            (URLClassLoader)Thread.currentThread().getContextClassLoader();

        //bypass protected access.
        Method addURL =
            URLClassLoader.class.getDeclaredMethod("addURL",
                                                   new Class[] {
                                                       URL.class
                                                   });

        addURL.setAccessible(true); //pound sand.

        for (int i=0; i<jars.length; i++) {
            addURL.invoke(loader, new Object[] { jars[i] });
        }
    }

    private static boolean addJarDir(String dir) throws Exception {
        URL[] jars = getLibJars(dir);
        addURLs(jars);
        return !missingJars();
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            args = new String[] { "Shell" };
        }
        else {
            //e.g. convert
            //          "ifconfig", "eth0"
            //   to:
            // "Shell", "ifconfig", "eth0" 
            if (Character.isLowerCase(args[0].charAt(0))) {
                String[] nargs = new String[args.length + 1];
                System.arraycopy(args, 0, nargs, 1, args.length);
                nargs[0] = "Shell";
                args = nargs;
            }
        }

        String name = args[0];

        String[] pargs = new String[args.length - 1];
        System.arraycopy(args, 1, pargs, 0, args.length-1);

        String sigarLib = SigarLoader.getLocation();

        String[] dirs = { sigarLib, "lib", "." };
        for (int i=0; i<dirs.length; i++) {
            if (addJarDir(dirs[i])) {
                break;
            }
        }

        if (missingJars()) {
            File[] subdirs = new File(".").listFiles(new FileFilter() {
                public boolean accept(File file) {
                    return file.isDirectory();
                }
            });

            for (int i=0; i<subdirs.length; i++) {
                File lib = new File(subdirs[i], "lib");
                if (lib.exists()) {
                    if (addJarDir(lib.getAbsolutePath())) {
                        break;
                    }
                }
            }
        }

        Class cmd = null;
        String[] packages = {
            "net.hyperic.sigar.cmd.",
            "net.hyperic.sigar.test.",
            "net.hyperic.sigar.",
            "net.hyperic.sigar.win32.",
        };

        for (int i=0; i<packages.length; i++) {
            try {
                cmd = Class.forName(packages[i] + name);
                break;
            } catch (ClassNotFoundException e) {}
        }

        if (cmd == null) {
            System.out.println("Unknown command: " + args[0]);
            return;
        }

        Method main = cmd.getMethod("main",
                                    new Class[] {
                                        String[].class
                                    });

        try {
            main.invoke(null, new Object[] { pargs });
        } catch (InvocationTargetException e) {
            Throwable t = e.getTargetException();
            if (t instanceof NoClassDefFoundError) {
                System.out.println("Class Not Found: " +
                                   t.getMessage());
                printMissingJars();
            }
            else {
                t.printStackTrace();
            }
        }
    }
}
