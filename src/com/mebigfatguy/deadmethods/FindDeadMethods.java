/*
 * deadmethods - A unused methods detector
 * Copyright 2011 MeBigFatGuy.com
 * Copyright 2011 Dave Brosius
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations
 * under the License.
 */
package com.mebigfatguy.deadmethods;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.Path;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;

public class FindDeadMethods extends Task {
    Path path;
    Path auxPath;
    Set<String> ignorePackages;

    public void addConfiguredClasspath(final Path classpath) {
        path = classpath;
    }

    public void addConfiguredAuxClasspath(final Path auxClassPath) {
    	auxPath = auxClassPath;
    }

    public void setIgnorePackages(String packages) {
    	String[] packs = packages.split("\\s*,\\s*");
    	ignorePackages = new HashSet<String>(Arrays.asList(packs));
    }

    @Override
    public void execute() throws BuildException {
        if (path == null) {
            throw new BuildException("classpath attribute not set");
        }

        if (auxPath == null) {
        	auxPath = new Path(getProject());
        }

        if (ignorePackages == null) {
			ignorePackages = new HashSet<String>();
		}

        TaskFactory.setTask(this);

        ClassRepository repo = new ClassRepository(path, auxPath);
        Set<String> allMethods = new TreeSet<String>();
        try {
	        for (String className : repo) {
	        	if (!className.startsWith("[")) {
		        	ClassInfo classInfo = repo.getClassInfo(className);
		        	String packageName = classInfo.getPackageName();
		        	if (!ignorePackages.contains(packageName)) {
		        		Set<MethodInfo> methods = classInfo.getMethodInfo();

			        	for (MethodInfo methodInfo : methods) {
			        		allMethods.add(className + ":" + methodInfo.getMethodName() + methodInfo.getMethodSignature());
			        	}
		        	}
	        	}
	        }

	        removeObjectMethods(repo, allMethods);
	        removeMainMethods(repo, allMethods);
	        removeNoArgCtors(repo, allMethods);
	        removeJUnitMethods(repo, allMethods);
	        removeInterfaceImplementationMethods(repo, allMethods);
	        removeSyntheticMethods(repo, allMethods);
	        removeStandardEnumMethods(repo, allMethods);

	        for (String className : repo) {
	        	InputStream is = null;
	        	try {
	        		is = repo.getClassStream(className);

	        		ClassReader r = new ClassReader(is);
	        		r.accept(new CalledMethodRemovingClassVisitor(repo, allMethods), ClassReader.SKIP_DEBUG);
	        	} finally {
	        		Closer.close(is);
	        	}
	        }

	        for (String m : allMethods) {
	        	System.out.println(m);
	        }

        } catch (IOException ioe) {
        	throw new BuildException("Failed collecting methods: " + ioe.getMessage(), ioe);
        }
    }

    private void removeObjectMethods(ClassRepository repo, Set<String> methods) throws IOException {
    	ClassInfo info = repo.getClassInfo("java/lang/Object");
    	for (MethodInfo methodInfo : info.getMethodInfo()) {
			clearDerivedMethods(methods, info, methodInfo.toString());
		}
    }

    private void removeMainMethods(ClassRepository repo, Set<String> methods) {
    	MethodInfo mainInfo = new MethodInfo("main", "([Ljava/lang/String;)V", Opcodes.ACC_STATIC);
    	for (ClassInfo classInfo : repo.getAllClassInfos()) {
    		Set<MethodInfo> methodInfo = classInfo.getMethodInfo();
    		if (methodInfo.contains(mainInfo)) {
    		    methods.remove(classInfo.getClassName() + ":" + methodInfo);
    		}
    	}
    }

    private void removeNoArgCtors(ClassRepository repo, Set<String> methods) {
    	MethodInfo ctorInfo = new MethodInfo("<init>", "()V", Opcodes.ACC_STATIC);
    	for (ClassInfo classInfo : repo.getAllClassInfos()) {
    		Set<String> infs = new HashSet<String>(Arrays.asList(classInfo.getInterfaceNames()));
    		if (infs.contains("java/lang/Serializable")) {
	    		Set<MethodInfo> methodInfo = classInfo.getMethodInfo();
	    		if (methodInfo.contains(ctorInfo)) {
	    			methods.remove(classInfo.getClassName() + ":" + methodInfo);
	    		}
    		}
    	}
    }

    private void removeJUnitMethods(ClassRepository repo, Set<String> methods) {
    	for (ClassInfo classInfo : repo.getAllClassInfos()) {
    		for (MethodInfo methodInfo : classInfo.getMethodInfo()) {
    			if (methodInfo.isTest()) {
    				methods.remove(classInfo.getClassName() + ":" + methodInfo);
    			}
    		}
    	}
    }

    private void removeInterfaceImplementationMethods(ClassRepository repo, Set<String> methods) throws IOException {
    	for (ClassInfo classInfo : repo.getAllClassInfos()) {
        	if (classInfo.isInterface()) {
        		for (MethodInfo methodInfo : classInfo.getMethodInfo()) {
        			clearDerivedMethods(methods, classInfo, methodInfo.toString());
        		}
        	}
        }
    }

    private void removeSyntheticMethods(ClassRepository repo, Set<String> methods)  {
    	for (ClassInfo classInfo : repo.getAllClassInfos()) {
    		for (MethodInfo methodInfo : classInfo.getMethodInfo()) {
    			if (methodInfo.isSynthetic()) {
    				methods.remove(classInfo.getClassName() + ":" + methodInfo);
    			}
    		}
    	}
    }

    private void removeStandardEnumMethods(ClassRepository repo, Set<String> methods) throws IOException {
    	ClassInfo info = repo.getClassInfo("java/lang/Enum");
    	{
	    	MethodInfo methodInfo = new MethodInfo("valueOf", "(Ljava/lang/String;)?", Opcodes.ACC_PUBLIC);
	    	clearDerivedMethods(methods, info, methodInfo.toString());
    	}
    	{
	    	MethodInfo methodInfo = new MethodInfo("values", "()[?", Opcodes.ACC_PUBLIC);
	    	clearDerivedMethods(methods, info, methodInfo.toString());
    	}

    }

    private void clearDerivedMethods(Set<String> methods, ClassInfo info, String methodInfo) throws IOException {
    	Set<ClassInfo> derivedInfos = info.getDerivedClasses();

    	for (ClassInfo derivedInfo : derivedInfos) {
    		methods.remove(derivedInfo.getClassName() + ":" + methodInfo.replaceAll("\\?", "L" + derivedInfo.getClassName() + ";"));
    		clearDerivedMethods(methods, derivedInfo, methodInfo);
    	}
    }

    /** for testing only */
    public static void main(String[] args) {

    	if (args.length < 1) {
    		throw new IllegalArgumentException("args must contain classpath root");
    	}


    	FindDeadMethods fdm = new FindDeadMethods();
    	Project project = new Project();
    	fdm.setProject(project);

    	Path path = new Path(project);
    	path.setLocation(new File(args[0]));
    	fdm.addConfiguredClasspath(path);
    	fdm.setIgnorePackages("test.ignored");

    	fdm.execute();
    }
}

