/*
 * deadmethods - A unused methods detector
 * Copyright 2011-2012 MeBigFatGuy.com
 * Copyright 2011-2012 Dave Brosius
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

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;

/** collects high level details about this class */
public class ClassRepositoryVisitor implements ClassVisitor {

	private ClassInfo classInfo = null;

	public ClassInfo getClassInfo() {
		return classInfo;
	}

    @Override
    public void visit(final int version, final int access, final String name, final String signature, final String superName, final String[] interfaces) {
    	classInfo = new ClassInfo(name, superName, interfaces, access);
    }

    @Override
    public AnnotationVisitor visitAnnotation(final String desc, final boolean visible) {
        return null;
    }

    @Override
    public void visitAttribute(final Attribute attr) {
    }

    @Override
    public void visitEnd() {
    }

    @Override
    public FieldVisitor visitField(final int access, final String name, final String desc, final String signature, final Object value) {
        return null;
    }

    @Override
    public void visitInnerClass(final String name, final String outerName, final String innerName, final int access) {
    }

    @Override
    public MethodVisitor visitMethod(final int access, final String name, final String desc, final String signature, final String[] exceptions) {
        MethodInfo minfo = classInfo.addMethod(name, desc, access);
        return new MethodRepositoryVisitor(minfo);
    }

    @Override
    public void visitOuterClass(final String owner, final String name, final String desc) {
    }

    @Override
    public void visitSource(final String source, final String debug) {
    }
}
