package org.ifcx.gondor;

/*
 * Copyright 2008-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.codehaus.groovy.GroovyBugError;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.expr.DeclarationExpression;
import org.codehaus.groovy.ast.expr.EmptyExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.messages.SyntaxErrorMessage;
import org.codehaus.groovy.syntax.SyntaxException;
import org.codehaus.groovy.transform.AbstractASTTransformation;
import org.codehaus.groovy.transform.GroovyASTTransformation;

import java.util.Arrays;
import java.util.List;

/**
 * Handles transformation for the @BaseScript annotation.
 *
 * @author Paul King
 * @author Cedric Champeau
 * @author Vladimir Orany
 */
@GroovyASTTransformation(phase = CompilePhase.SEMANTIC_ANALYSIS)
public class BaseScriptASTTransformation extends AbstractASTTransformation {

    private static final Class<BaseScript> MY_CLASS = BaseScript.class;
    private static final ClassNode MY_TYPE = ClassHelper.make(MY_CLASS);
    private static final String MY_TYPE_NAME = "@" + MY_TYPE.getNameWithoutPackage();
    private SourceUnit sourceUnit;

    public void visit(ASTNode[] nodes, SourceUnit source) {
        sourceUnit = source;
        if (nodes.length != 2 || !(nodes[0] instanceof AnnotationNode) || !(nodes[1] instanceof AnnotatedNode)) {
            throw new GroovyBugError("Internal error: expecting [AnnotationNode, AnnotatedNode] but got: " + Arrays.asList(nodes));
        }

        AnnotatedNode parent = (AnnotatedNode) nodes[1];
        AnnotationNode node = (AnnotationNode) nodes[0];
        if (!MY_TYPE.equals(node.getClassNode())) return;

        if (parent instanceof DeclarationExpression) {
            DeclarationExpression de = (DeclarationExpression) parent;
            ClassNode cNode = de.getDeclaringClass();
            if (!cNode.isScript()) {
                addError("Annotation " + MY_TYPE_NAME + " can only be used within a Script.", parent);
                return;
            }

            if (de.isMultipleAssignmentDeclaration()) {
                addError("Annotation " + MY_TYPE_NAME + " not supported with multiple assignment notation.", parent);
                return;
            }

            if (!(de.getRightExpression() instanceof EmptyExpression)) {
                addError("Annotation " + MY_TYPE_NAME + " not supported with variable assignment.", parent);
                return;
            }

            ClassNode baseScriptType = de.getVariableExpression().getType().getPlainNodeReference();

            if(!baseScriptType.isScript()){
                addError("Declared type " + baseScriptType + " does not extend groovy.lang.Script class!", parent);
                return;
            }

            cNode.setSuperClass(baseScriptType);
            de.setRightExpression(new VariableExpression("this"));

//            for (MethodNode m : cNode.getMethods()) {
//                if ("run".equals(m.getName())) {
//                    List<Statement> statements = new ArrayList<Statement>(3);
//                    statements.add(callStatment("preRun"));
//                    statements.add(m.getCode());
//                    statements.add(callStatment("postRun"));
//                    m.setCode(new BlockStatement(statements, /*m.getVariableScope()*/ new VariableScope()));
//                }
//            }

            // Method in base script that the script class should implement to be run with (if something other than run()).
            MethodNode runScriptMethod = null;

            // Look to see if the base script has an abstract method we should use instead of the default.
            for (MethodNode m : baseScriptType.getMethods()) {
                if ((m.getModifiers() & ACC_ABSTRACT) != 0) {
                    runScriptMethod = m;
                    break;
                }
            }

            if (runScriptMethod != null) {
                MethodNode defaultMethod = cNode.getDeclaredMethod("run", Parameter.EMPTY_ARRAY);
//                cNode.removeMethod(defaultMethod);
                cNode.addMethod(new MethodNode(runScriptMethod.getName(), runScriptMethod.getModifiers() & ~ACC_ABSTRACT
                        , runScriptMethod.getReturnType(), runScriptMethod.getParameters(), runScriptMethod.getExceptions()
                        , defaultMethod.getCode()));
            }
        }
    }

//    private Statement callStatment(String name) {
//        return new ExpressionStatement(new MethodCallExpression(new VariableExpression("this"), name, new ArgumentListExpression()));
//    }

    public SourceUnit getSourceUnit() {
        return sourceUnit;
    }

    protected void addError(String msg, ASTNode expr) {
        // for some reason the source unit is null sometimes, e.g. in testNotAllowedInScriptInnerClassMethods
        sourceUnit.getErrorCollector().addErrorAndContinue(new SyntaxErrorMessage(
                new SyntaxException(msg + '\n', expr.getLineNumber(), expr.getColumnNumber(),
                        expr.getLastLineNumber(), expr.getLastColumnNumber()),
                sourceUnit)
        );
    }
}