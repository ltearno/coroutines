/*
 * Copyright (c) 2015, Kasra Faghihi, All rights reserved.
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.
 */
package com.offbynull.coroutines.anttask;

import com.offbynull.coroutines.instrumenter.Instrumenter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;

/**
 * Mojo to run coroutine instrumentation. Instruments both main classes and test classes.
 * <p>
 * Sample usage in POM:
 * <pre>
 *     &lt;build&gt;
 *         &lt;plugins&gt;
 *             &lt;plugin&gt;
 *                 &lt;groupId&gt;com.offbynull.coroutines&lt;/groupId&gt;
 *                 &lt;artifactId&gt;coroutine-maven-plugin&lt;/artifactId&gt;
 *                 &lt;version&gt;1.0.0-SNAPSHOT&lt;/version&gt;
 *                 &lt;executions&gt;
 *                     &lt;execution&gt;
 *                         &lt;goals&gt;
 *                             &lt;goal&gt;instrument&lt;/goal&gt;
 *                         &lt;/goals&gt;
 *                     &lt;/execution&gt;
 *                 &lt;/executions&gt;
 *             &lt;/plugin&gt;
 *         &lt;/plugins&gt;
 *     &lt;/build&gt;
 * </pre>
 * 
 * or directly call the goal instrument (e.g. mvn coroutine:instrument)
 *
 * @author Kasra Faghihi
 */
public final class InstrumentTask extends Task {

    private String classpath;

    private File sourceDirectory;
    
    private File targetDirectory;

    private File jdkLibsDirectory;

    /**
     * Constructs a {@link InstrumentTask} object.
     */
    public InstrumentTask() {
        String jdkHome = (String) System.getProperties().get("java.home");
        if (jdkHome != null) {
            jdkLibsDirectory = new File(jdkHome + "/lib");
        }
    }

    /**
     * Sets the classpath -- required by instrumenter when instrumenting class files.
     * @param classpath semicolon delimited classpath
     */
    public void setClasspath(String classpath) {
        this.classpath = classpath;
    }

    /**
     * Sets the directory to read class files from.
     * @param sourceDirectory source directory
     */
    public void setSourceDirectory(File sourceDirectory) {
        this.sourceDirectory = sourceDirectory;
    }

    /**
     * Sets the directory to write instrumented class files to.
     * @param targetDirectory target directory
     */
    public void setTargetDirectory(File targetDirectory) {
        this.targetDirectory = targetDirectory;
    }

    /**
     * Sets the JDK libs directory -- required by instrumenter when instrumenting class files.
     * @param jdkLibsDirectory directory to JDK's libs directory
     */
    public void setJdkLibsDirectory(File jdkLibsDirectory) {
        this.jdkLibsDirectory = jdkLibsDirectory;
    }

    @Override
    public void execute() throws BuildException {
        // Check classpath
        if (classpath == null) {
            throw new BuildException("Classpath not set");
        }

        // Check source directory
        if (sourceDirectory == null) {
            throw new BuildException("Source directory not set");
        }
        if (!sourceDirectory.isDirectory()) {
            throw new BuildException("Source directory is not a directory: " + sourceDirectory.getAbsolutePath());
        }
        
        // Check target directory
        if (targetDirectory == null) {
            throw new BuildException("Target directory not set");
        }
        try {
            FileUtils.forceMkdir(targetDirectory);
        } catch (IOException ioe) {
            throw new BuildException("Unable to create target directory", ioe);
        }
        
        // Check JDK libs directory
        if (jdkLibsDirectory == null) {
            throw new BuildException("JDK libs directory not set");
        }
        if (jdkLibsDirectory.isDirectory()) {
            throw new BuildException("JDK libs directory is not a directory: " + targetDirectory.getAbsolutePath());
        }

        List<File> combinedClasspath;
        try {
            log("Getting compile classpath", Project.MSG_DEBUG);
            combinedClasspath = new ArrayList<>(Arrays.stream(classpath.split(";")).map(x -> new File(x)).collect(Collectors.toList()));
            log("Getting bootstrap classpath", Project.MSG_DEBUG);
            combinedClasspath.addAll(FileUtils.listFiles(jdkLibsDirectory, new String[]{"jar"}, true));

            log("Classpath for instrumentation is as follows: " + combinedClasspath, Project.MSG_INFO);
        } catch (Exception ex) {
            throw new BuildException("Unable to get compile classpath elements", ex);
        }

        Instrumenter instrumenter;
        try {
            log("Creating instrumenter...", Project.MSG_INFO);
            instrumenter = new Instrumenter(combinedClasspath);
            
            log("Scanning " + sourceDirectory.getAbsolutePath() + " ... ", Project.MSG_INFO);
            for (File inputFile : FileUtils.listFiles(sourceDirectory, new String[] {"class"}, false)) {
                Path relativePath = sourceDirectory.toPath().relativize(inputFile.toPath());
                Path outputFilePath = targetDirectory.toPath().resolve(relativePath);
                File outputFile = outputFilePath.toFile();
                
                log("Instrumenting " + inputFile.getAbsolutePath(), Project.MSG_INFO);
                instrumentPath(instrumenter, inputFile);
                byte[] input = FileUtils.readFileToByteArray(inputFile);
                byte[] output = instrumenter.instrument(input);
                log("File size changed from " + input.length + " to " + output.length, Project.MSG_DEBUG);
                FileUtils.writeByteArrayToFile(outputFile, output);
            }
        } catch (Exception ex) {
            throw new BuildException("Failed to instrument", ex);
        }
    }

    private void instrumentPath(Instrumenter instrumenter, File path) throws IOException {
        for (File classFile : FileUtils.listFiles(path, new String[]{"class"}, true)) {
            log("Instrumenting " + classFile, Project.MSG_INFO);
            byte[] input = FileUtils.readFileToByteArray(classFile);
            byte[] output = instrumenter.instrument(input);
            log("File size changed from " + input.length + " to " + output.length, Project.MSG_DEBUG);
            FileUtils.writeByteArrayToFile(classFile, output);
        }
    }
}