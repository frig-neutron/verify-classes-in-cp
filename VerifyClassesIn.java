import static java.util.logging.Level.*;
import static java.util.Collections.*;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.logging.*;
import java.util.*;
import java.net.*;

/**
 * Try to load all classes from .class files accessible by scanning 
 * directories in classpath to ensure the class files don't trip the verifier.
 */
public class VerifyClassesIn
{
	ClassLoader cl = Thread.currentThread().getContextClassLoader();
  final Logger logger = Logger.getAnonymousLogger();

  public static void main(String [] args) 
  {
    try
    {
      List<File> dirs;
      if (args.length == 0) 
      {
        dirs = findClassPathDirs();
      }
      else
      {
        dirs = new LinkedList<File>();
        dirs.add(new File(args[0]));
      }
      String requestedLogLevel = System.getProperty("logLevel", "INFO");
      Level logLevel = Level.parse(requestedLogLevel);
      new VerifyClassesIn(logLevel).scan(dirs);
    }
    catch(Exception e)
    {
      throw new RuntimeException(e);
    }
  }

	private static List<File> findClassPathDirs()
	{
		List<File> dirs = new LinkedList<File>();
		String[] classPathElements = System.getProperty("java.class.path").split(File.pathSeparator);
		for (String cpEl : classPathElements)
		{
			File res = new File(cpEl);
			if (res.isDirectory())
			{
				dirs.add(res);
			}
		}
		return dirs;
	}

  public VerifyClassesIn(Level logLevel)
  {
    java.util.logging.Formatter fmt = new java.util.logging.Formatter() 
    {
      public String format(LogRecord lr) 
      {
        Throwable ex = lr.getThrown();
        String exceptionMessage = ex == null ? 
          "" :
          ex.getMessage()+" ["+ex.getClass().getSimpleName()+"]";
        return lr.getLevel()+": "+lr.getMessage()+exceptionMessage+"\n";
      }
    };
    Handler handler = new ConsoleHandler();
    handler.setFormatter(fmt);
    handler.setLevel(logLevel);
    logger.addHandler(handler);
    logger.setLevel(logLevel);
    logger.setUseParentHandlers(false);
  }

	public void scan(List<File> dirs) throws IOException, ClassNotFoundException
	{
    logger.info("Scanning "+dirs);
    Set<String> allUnloadable = new HashSet<String>();
		for (File dir : dirs)
		{
			Collection<String> couldNotLoad = findClasses(dir);
      allUnloadable.addAll(couldNotLoad);
		}
    List<String> orderedClassNames = new ArrayList<String>(allUnloadable);
    sort(orderedClassNames);

    for(String className: orderedClassNames)
    {
      logger.info("Broken "+className);
    }
	}

	private Set<String> findClasses(File dir) throws ClassNotFoundException, MalformedURLException
	{
    logger.log(FINE, "Scanning dir "+dir.getAbsolutePath());
    URL scanURL = dir.toURI().toURL();
    cl = new URLClassLoader(new URL[] { scanURL });
		Set<File> classFiles = new HashSet<File>();
		findClasses(dir, classFiles);

		return reportUnloadableClasses(dir, classFiles);
	}

	private void findClasses(File dir, Set<File> classFiles)
	{
		File [] contents = dir.listFiles();
		for (File f : contents)
		{
			if (f.isDirectory())
			{
				findClasses(f, classFiles);
			}
			else
			{
				if (f.getName().endsWith(".class"))
				{
					classFiles.add(f);
				}
			}
		}
	}

	public Set<String> reportUnloadableClasses(File baseDir, Set<File> classFiles) throws ClassNotFoundException
	{
		Set<String> unloadables = new HashSet<String>();
		String basePath = baseDir.getAbsolutePath() + File.separator;
		for (File file : classFiles)
		{
			String path = file.getAbsolutePath();
			String name = path.
					replace(basePath, "").
					replace(".class", "").
					replaceAll(File.separator, ".");
      tryLoad(name, unloadables);
		}
		return unloadables;
	}

  private void tryLoad(String className, Set<String> unloadables) throws ClassNotFoundException
  {
    try 
    {
      logger.log(FINEST, "Loading "+className+" ");
			cl.loadClass(className);
    }
    catch (ClassFormatError e)
    {
      logger.log(FINE, "Can't load "+className+". ", e);
      unloadables.add(className);
    }
    catch (NoClassDefFoundError e)
    {
      // this can happen if we scan into a bin dir from a parent dir in 
      // class path
    }
  }
}
