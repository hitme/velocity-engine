package org.apache.velocity.texen.ant;

/*
 * The Apache Software License, Version 1.1
 *
 * Copyright (c) 2001 The Apache Software Foundation.  All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution, if
 *    any, must include the following acknowlegement:
 *       "This product includes software developed by the
 *        Apache Software Foundation (http://www.apache.org/)."
 *    Alternately, this acknowlegement may appear in the software itself,
 *    if and wherever such third-party acknowlegements normally appear.
 *
 * 4. The names "The Jakarta Project", "Velocity", and "Apache Software
 *    Foundation" must not be used to endorse or promote products derived
 *    from this software without prior written permission. For written
 *    permission, please contact apache@apache.org.
 *
 * 5. Products derived from this software may not be called "Apache"
 *    nor may "Apache" appear in their names without prior written
 *    permission of the Apache Group.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 */

import java.util.StringTokenizer;
import java.util.Date;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;

import java.io.File;
import java.io.Writer;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;

import org.apache.tools.ant.Project;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.apache.velocity.context.Context;
import org.apache.velocity.texen.Generator;
import org.apache.velocity.util.StringUtils;
import org.apache.velocity.exception.MethodInvocationException;
import org.apache.velocity.exception.ParseErrorException;
import org.apache.velocity.exception.ResourceNotFoundException;
import org.apache.commons.collections.ExtendedProperties;

/**
 * An ant task for generating output by using Velocity
 *
 * @author <a href="mailto:jvanzyl@apache.org">Jason van Zyl</a>
 * @author <a href="robertdonkin@mac.com">Robert Burrell Donkin</a>
 * @version $Id: Texen.java,v 1.1 2002/03/04 01:52:34 jvanzyl Exp $
 */
public class Texen
{
    /**
     * This message fragment (telling users to consult the log or
     * invoke ant with the -debug flag) is appended to rethrown
     * exception messages.
     */
    private final static String ERR_MSG_FRAGMENT = 
        ". For more information consult the velocity log, or invoke ant " +
        "with the -debug flag.";
    
    /**
     * This is the control template that governs the output.
     * It may or may not invoke the services of worker
     * templates.
     */
    protected String controlTemplate;
    
    /**
     * This is where Velocity will look for templates
     * using the file template loader.
     */
    protected String templatePath;
    
    /**
     * This is where texen will place all the output
     * that is a product of the generation process.
     */
    protected String outputDirectory;
    
    /**
     * This is the file where the generated text
     * will be placed.
     */
    protected String outputFile;
    
    /**
     * This is the encoding for the output file(s).
     */
    protected String outputEncoding;

    /**
     * This is the encoding for the input file(s)
     * (templates).
     */
    protected String inputEncoding;

    /**
     * <p>
     * These are properties that are fed into the
     * initial context from a properties file. This
     * is simply a convenient way to set some values
     * that you wish to make available in the context.
     * </p>
     * <p>
     * These values are not critical, like the template path
     * or output path, but allow a convenient way to
     * set a value that may be specific to a particular
     * generation task.
     * </p>
     * <p>
     * For example, if you are generating scripts to allow
     * user to automatically create a database, then
     * you might want the <code>$databaseName</code> 
     * to be placed
     * in the initial context so that it is available
     * in a script that might look something like the
     * following:
     * <code><pre>
     * #!bin/sh
     * 
     * echo y | mysqladmin create $databaseName
     * </pre></code>
     * The value of <code>$databaseName</code> isn't critical to
     * output, and you obviously don't want to change
     * the ant task to simply take a database name.
     * So initial context values can be set with
     * properties file.
     */
    protected ExtendedProperties contextProperties;
    
    /**
     * Reference to the context properties that is held
     * until processing in the execute() method. We do this
     * now so that the Ant TaskAdapter can set the project
     * which is needed for correct path processing when
     * this bean is used as an ant task.
     */
    protected String contextPropertiesFile;

    /**
     * Property which controls whether the classpath
     * will be used when trying to locate templates.
     */
    protected boolean useClasspath;

    /**
     * Path separator.
     */
    private String fileSeparator = System.getProperty("file.separator");

    /**
     * Ant project that is populated by the Ant TaskAdapter when
     * this bean is run as an Ant Task.
     */
    protected Project project;

    /**
     * [REQUIRED] Set the control template for the
     * generating process.
     */
    public void setControlTemplate (String controlTemplate)
    {
        this.controlTemplate = controlTemplate;
    }

    /**
     * Get the control template for the
     * generating process.
     */
    public String getControlTemplate()
    {
        return controlTemplate;
    }

    /**
     * [REQUIRED] Set the path where Velocity will look
     * for templates using the file template
     * loader.
     */
    public void setTemplatePath(String templatePath)
    {
        this.templatePath = templatePath;
    }        
    
    /**
     * Process the templatePath. We hold off processing so that
     * when run as an Ant Task the project will be set, and the
     * project is required for correct path resolving.
     */
    protected void processTemplatePath(String templatePath)
        throws Exception
    {
        StringBuffer resolvedPath = new StringBuffer();
        StringTokenizer st = new StringTokenizer(templatePath, ",");
        while ( st.hasMoreTokens() )
        {
            // resolve relative path from basedir and leave
            // absolute path untouched.
            File fullPath = resolveFile(st.nextToken());
            resolvedPath.append(fullPath.getCanonicalPath());
            if ( st.hasMoreTokens() )
            {
                resolvedPath.append(",");
            }
        }
        this.templatePath = resolvedPath.toString();
     }

    /**
     * Get the path where Velocity will look
     * for templates using the file template
     * loader.
     */
    public String getTemplatePath()
    {
        return templatePath;
    }        

    /**
     * [REQUIRED] Set the output directory. It will be
     * created if it doesn't exist.
     */
    public void setOutputDirectory(File outputDirectory)
        throws Exception
    {
        try
        {
            this.outputDirectory = outputDirectory.getCanonicalPath();
        }
        catch (IOException ioe)
        {
            throw new Exception(
                "There is a problem with the outputDirectory: " + ioe);
        }
    }
      
    /**
     * Get the output directory.
     */
    public String getOutputDirectory()
    {
        return outputDirectory;
    }        

    /**
     * [REQUIRED] Set the output file for the
     * generation process.
     */
    public void setOutputFile(String outputFile)
    {
        this.outputFile = outputFile;
    }
    
    /**
     * Set the output encoding.
     */
    public void setOutputEncoding(String outputEncoding)
    {
        this.outputEncoding = outputEncoding;
    }

    /**
     * Set the input (template) encoding.
     */
    public void setInputEncoding(String inputEncoding)
    {
        this.inputEncoding = inputEncoding;
    }

    /**
     * Get the output file for the
     * generation process.
     */
    public String getOutputFile()
    {
        return outputFile;
    }        

    /**
     * Set the context properties that will be
     * fed into the initial context be the
     * generating process starts.
     */
    public void setContextProperties(String file)
    {
        this.contextPropertiesFile = file;
    }

    /**
     * Process the templatePath. We hold off processing so that
     * when run as an Ant Task the project will be set, and the
     * project is required for correct path resolving.
     */
    protected void processContextProperties( String file )
        throws Exception
    {
        String[] sources = StringUtils.split(file,",");
        contextProperties = new ExtendedProperties();
        
        // Always try to get the context properties resource
        // from a file first. Templates may be taken from a JAR
        // file but the context properties resource may be a 
        // resource in the filesystem. If this fails than attempt
        // to get the context properties resource from the
        // classpath.
        for (int i = 0; i < sources.length; i++)
        {
            ExtendedProperties source = new ExtendedProperties();
            try
            {
                // resolve relative path from basedir and leave
                // absolute path untouched.
                File fullPath = resolveFile(sources[i]);
                log("Using contextProperties file: " + fullPath);
                source.load(new FileInputStream(fullPath));
            }
            catch (Exception e)
            {
                ClassLoader classLoader = this.getClass().getClassLoader();
            
                try
                {
                    InputStream inputStream = classLoader.getResourceAsStream(sources[i]);
                
                    if (inputStream == null)
                    {
                        throw new Exception("Context properties file " + sources[i] +
                            " could not be found in the file system or on the classpath!");
                    }
                    else
                    {
                        source.load(inputStream);
                    }
                }
                catch (IOException ioe)
                {
                    source = null;
                }
            }
        
            Iterator j = source.getKeys();
            
            while (j.hasNext())
            {
                String name = (String) j.next();
                String value = source.getString(name);
                contextProperties.setProperty(name,value);
            }
        }
    }

    /**
     * Get the context properties that will be
     * fed into the initial context be the
     * generating process starts.
     */
    public ExtendedProperties getContextProperties()
    {
        return contextProperties;
    }
    
    /**
     * Set the use of the classpath in locating templates
     *
     * @param boolean true means the classpath will be used.
     */
    public void setUseClasspath(boolean useClasspath)
    {
        this.useClasspath = useClasspath;
    }        

    /**
     * Set the Ant project. Used when this bean is adapted
     * by the Ant TaskAdapter.
     */
    public void setProject(Project project)
    {
        this.project = project;
    }
    
    /**
     * Get the Ant project which will be non-null if
     * this bean is used as a Task and adapted by the
     * Ant TaskAdapter.
     */
    public Project getProject()
    {
        return project;
    }        

    /**
     * Creates a VelocityContext.
     *
     * @return new Context
     * @throws Exception the execute method will catch 
     *         and rethrow as a <code>BuildException</code>
     */
    public Context initControlContext() 
        throws Exception
    {
        return new VelocityContext();
    }
    
    /**
     * Execute the input script with Velocity
     *
     * @throws BuildException  
     * BuildExceptions are thrown when required attributes are missing.
     * Exceptions thrown by Velocity are rethrown as BuildExceptions.
     */
    public void execute () 
        throws Exception
    {
        // Make sure the template path is set.
        if (templatePath == null && useClasspath == false)
        {
            throw new Exception(
                "The template path needs to be defined if you are not using " +
                "the classpath for locating templates!");
        }            
    
        // Make sure the control template is set.
        if (controlTemplate == null)
        {
            throw new Exception("The control template needs to be defined!");
        }            

        // Make sure the output directory is set.
        if (outputDirectory == null)
        {
            throw new Exception("The output directory needs to be defined!");
        }            
        
        // Make sure there is an output file.
        if (outputFile == null)
        {
            throw new Exception("The output file needs to be defined!");
        }            

        // Process the context properties
        if (contextPropertiesFile != null)
        {
            processContextProperties(contextPropertiesFile);
        }            

        try
        {
            // Setup the Velocity Runtime.
            if (templatePath != null)
            {
                processTemplatePath(templatePath);            
            
                log("Using templatePath: " + templatePath);
                Velocity.setProperty(
                    Velocity.FILE_RESOURCE_LOADER_PATH, templatePath);
            }
            
            if (useClasspath)
            {
            	log("Using classpath");
                Velocity.addProperty(
                    Velocity.RESOURCE_LOADER, "classpath");
            
                Velocity.setProperty(
                    "classpath." + Velocity.RESOURCE_LOADER + ".class",
                        "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");

                Velocity.setProperty(
                    "classpath." + Velocity.RESOURCE_LOADER + 
                        ".cache", "false");

                Velocity.setProperty(
                    "classpath." + Velocity.RESOURCE_LOADER + 
                        ".modificationCheckInterval", "2");
            }
            
            Velocity.init();

            // Create the text generator.
            Generator generator = Generator.getInstance();
            generator.setOutputPath(outputDirectory);
            generator.setInputEncoding(inputEncoding);
            generator.setOutputEncoding(outputEncoding);

            if (templatePath != null)
            {
                generator.setTemplatePath(templatePath);
            }
            
            // Make sure the output directory exists, if it doesn't
            // then create it.
            File file = new File(outputDirectory);
            if (! file.exists())
            {
                file.mkdirs();
            }
            
            String path = outputDirectory + File.separator + outputFile;
            log("Generating to file " + path);
            Writer writer = generator.getWriter(path, outputEncoding);
            
            // The generator and the output path should
            // be placed in the init context here and
            // not in the generator class itself.
            Context c = initControlContext();
            
            // Everything in the generator class should be
            // pulled out and placed in here. What the generator
            // class does can probably be added to the Velocity
            // class and the generator class can probably
            // be removed all together.
            populateInitialContext(c);
            
            // Feed all the options into the initial
            // control context so they are available
            // in the control/worker templates.
            if (contextProperties != null)
            {
                Iterator i = contextProperties.getKeys();
        
                while (i.hasNext())
                {
                    String property = (String) i.next();
                    String value = contextProperties.getString(property);
                    
                    // Now lets quickly check to see if what
                    // we have is numeric and try to put it
                    // into the context as an Integer.
                    try
                    {
                        c.put(property, new Integer(value)); 
                    }
                    catch (NumberFormatException nfe)
                    {
                        // Now we will try to place the value into
                        // the context as a boolean value if it
                        // maps to a valid boolean value.
                        String booleanString = 
                            contextProperties.testBoolean(value);
                        
                        if (booleanString != null)
                        {    
                            c.put(property, new Boolean(booleanString));
                        }
                        else
                        {
                            // We are going to do something special
                            // for properties that have a "file.contents"
                            // suffix: for these properties will pull
                            // in the contents of the file and make
                            // them available in the context. So for
                            // a line like the following in a properties file:
                            //
                            // license.file.contents = license.txt
                            //
                            // We will pull in the contents of license.txt
                            // and make it available in the context as
                            // $license. This should make texen a little
                            // more flexible.
                            if (property.endsWith("file.contents"))
                            {
                                // We need to turn the license file from relative to
                                // absolute, and let Ant help :)
                                value = StringUtils.fileContentsToString(   
                                    resolveFile(value).getCanonicalPath());
                            
                                property = property.substring(
                                    0, property.indexOf("file.contents") - 1);
                            }
                        
                            c.put(property, value);
                        }
                    }
                }
            }
            
            writer.write(generator.parse(controlTemplate, c));
            writer.flush();
            writer.close();
            generator.shutdown();
            cleanup();
        }
        catch( MethodInvocationException e )
        {
            throw new Exception(
                "Exception thrown by '" + e.getReferenceName() + "." + 
                    e.getMethodName() +"'" + ERR_MSG_FRAGMENT);
        }       
        catch( ParseErrorException e )
        {
            throw new Exception("Velocity syntax error" + ERR_MSG_FRAGMENT);
        }        
        catch( ResourceNotFoundException e )
        {
            throw new Exception("Resource not found" + ERR_MSG_FRAGMENT);
        }
        catch( Exception e )
        {
            throw new Exception("Generation failed" + ERR_MSG_FRAGMENT);
        }
    }

    /**
     * <p>Place useful objects into the initial context.</p>
     *
     * <p>TexenTask places <code>Date().toString()</code> into the
     * context as <code>$now</code>.  Subclasses who want to vary the
     * objects in the context should override this method.</p>
     *
     * <p><code>$generator</code> is not put into the context in this
     * method.</p>
     *
     * @param context The context to populate, as retrieved from
     * {@link #initControlContext()}.
     *
     * @throws Exception Error while populating context.  The {@link
     * #execute()} method will catch and rethrow as a
     * <code>BuildException</code>.
     */
    protected void populateInitialContext(Context context) 
        throws Exception
    {
        context.put("now", new Date().toString());
    }

    /**
     * A hook method called at the end of {@link #execute()} which can
     * be overridden to perform any necessary cleanup activities (such
     * as the release of database connections, etc.).  By default,
     * does nothing.
     *
     * @exception Exception Problem cleaning up.
     */
    protected void cleanup()
        throws Exception
    {
    }
    
    /**
     * Log a message. Use the Ant logging system if being
     * used as an Ant Task.
     */
    private void log(String message)
    {
        if (project != null)
        {
            project.log(message);
        }
        else
        {
            System.out.println(message);
        }            
    }        

    /**
     * Resolve a file. Use the baseDir of the project if
     * being run as an Ant Task. A couple methods
     */
    private File resolveFile(String file)
        throws Exception
    {
        if (project != null)
        {
            return project.resolveFile(file);
        }
        else
        {
            return new File(file);
        }            
    }
}
