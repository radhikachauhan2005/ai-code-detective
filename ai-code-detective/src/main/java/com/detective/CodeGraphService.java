package com.detective;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CodeGraphService {

    public DependencyGraph buildGraph(File repoDir, List<File> files) {
        DependencyGraph graph = new DependencyGraph();

        // Short class name → id (which is the class name itself for top-level types)
        Map<String, String> classNameIndex = new HashMap<>();

        // ---- PASS 1: Collect all top-level class/interface/enum nodes ----
        for (File file : files) {
            if (!file.getName().endsWith(".java")) continue;
            try {
                CompilationUnit cu = StaticJavaParser.parse(file);
                String relativePath = repoDir.toURI().relativize(file.toURI()).getPath();

                for (ClassOrInterfaceDeclaration c : cu.findAll(ClassOrInterfaceDeclaration.class)) {
                    if (!c.isTopLevelType()) continue;
                    String id = c.getNameAsString();
                    GraphNode.NodeType type = c.isInterface()
                            ? GraphNode.NodeType.INTERFACE
                            : GraphNode.NodeType.CLASS;
                    int lines = c.getRange().map(r -> r.end.line - r.begin.line + 1).orElse(0);
                    graph.addNode(new GraphNode(id, id, relativePath, type, lines, null));
                    classNameIndex.put(id, id);
                }

                for (EnumDeclaration e : cu.findAll(EnumDeclaration.class)) {
                    if (!e.isTopLevelType()) continue;
                    String id = e.getNameAsString();
                    int lines = e.getRange().map(r -> r.end.line - r.begin.line + 1).orElse(0);
                    graph.addNode(new GraphNode(id, id, relativePath, GraphNode.NodeType.ENUM, lines, null));
                    classNameIndex.put(id, id);
                }
            } catch (Exception ex) {
                // skip unparseable file
            }
        }

        // ---- PASS 2: Add methods + fields as child nodes of classes ----
        Map<String, String> methodNameToId = new HashMap<>();

        for (File file : files) {
            if (!file.getName().endsWith(".java")) continue;
            try {
                CompilationUnit cu = StaticJavaParser.parse(file);
                String relativePath = repoDir.toURI().relativize(file.toURI()).getPath();

                for (ClassOrInterfaceDeclaration c : cu.findAll(ClassOrInterfaceDeclaration.class)) {
                    if (!c.isTopLevelType()) continue;
                    String classId = c.getNameAsString();
                    if (!graph.getNodes().containsKey(classId)) continue;

                    // Methods
                    for (MethodDeclaration m : c.getMethods()) {
                        String methodName = m.getNameAsString();
                        String methodId = classId + "." + methodName;
                        int lines = m.getRange().map(r -> r.end.line - r.begin.line + 1).orElse(0);

                        graph.addNode(new GraphNode(
                                methodId, methodName, relativePath,
                                GraphNode.NodeType.METHOD, lines, classId));

                        graph.addEdge(new GraphEdge(classId, methodId, GraphEdge.EdgeType.CONTAINS));
                        methodNameToId.put(methodId, methodId);

                        // PARAM_TYPE edges
                        for (Parameter p : m.getParameters()) {
                            String pType = simpleTypeName(p.getTypeAsString());
                            if (classNameIndex.containsKey(pType)) {
                                graph.addEdge(new GraphEdge(methodId, pType, GraphEdge.EdgeType.PARAM_TYPE));
                            }
                        }

                        // RETURN_TYPE edge
                        String returnType = simpleTypeName(m.getTypeAsString());
                        if (classNameIndex.containsKey(returnType)) {
                            graph.addEdge(new GraphEdge(methodId, returnType, GraphEdge.EdgeType.RETURN_TYPE));
                        }

                        // LOCAL_VAR types (as FIELD_TYPE edges for simplicity)
                        m.getBody().ifPresent(body -> {
                            for (VariableDeclarationExpr v : body.findAll(VariableDeclarationExpr.class)) {
                                String vType = simpleTypeName(v.getElementType().asString());
                                if (classNameIndex.containsKey(vType)) {
                                    graph.addEdge(new GraphEdge(methodId, vType, GraphEdge.EdgeType.FIELD_TYPE));
                                }
                            }
                        });
                    }

                    // Fields
                    for (FieldDeclaration f : c.getFields()) {
                        for (VariableDeclarator v : f.getVariables()) {
                            String fieldName = v.getNameAsString();
                            String fieldId = classId + "." + fieldName;
                            int lines = f.getRange().map(r -> r.end.line - r.begin.line + 1).orElse(0);

                            graph.addNode(new GraphNode(
                                    fieldId, fieldName, relativePath,
                                    GraphNode.NodeType.FIELD, lines, classId));

                            graph.addEdge(new GraphEdge(classId, fieldId, GraphEdge.EdgeType.CONTAINS));

                            String fType = simpleTypeName(f.getElementType().asString());
                            if (classNameIndex.containsKey(fType)) {
                                graph.addEdge(new GraphEdge(classId, fType, GraphEdge.EdgeType.FIELD_TYPE));
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                // skip
            }
        }

        // ---- PASS 3: extends / implements / imports ----
        for (File file : files) {
            if (!file.getName().endsWith(".java")) continue;
            try {
                CompilationUnit cu = StaticJavaParser.parse(file);

                String sourceId = null;
                for (TypeDeclaration<?> t : cu.getTypes()) {
                    sourceId = t.getNameAsString();
                    break;
                }
                if (sourceId == null || !graph.getNodes().containsKey(sourceId)) continue;

                final String src = sourceId;

                for (ImportDeclaration imp : cu.getImports()) {
                    String imported = imp.getNameAsString();
                    String simple = imported.substring(imported.lastIndexOf('.') + 1);
                    if (classNameIndex.containsKey(simple)) {
                        graph.addEdge(new GraphEdge(src, simple, GraphEdge.EdgeType.IMPORTS));
                    }
                }

                for (ClassOrInterfaceDeclaration c : cu.findAll(ClassOrInterfaceDeclaration.class)) {
                    if (!c.getNameAsString().equals(src)) continue;

                    c.getExtendedTypes().forEach(ext -> {
                        String parent = simpleTypeName(ext.getNameAsString());
                        if (classNameIndex.containsKey(parent)) {
                            graph.addEdge(new GraphEdge(src, parent, GraphEdge.EdgeType.EXTENDS));
                        }
                    });

                    c.getImplementedTypes().forEach(impl -> {
                        String iface = simpleTypeName(impl.getNameAsString());
                        if (classNameIndex.containsKey(iface)) {
                            graph.addEdge(new GraphEdge(src, iface, GraphEdge.EdgeType.IMPLEMENTS));
                        }
                    });
                }
            } catch (Exception ex) {
                // skip
            }
        }

        // ---- PASS 4: method-to-method CALLS ----
        for (File file : files) {
            if (!file.getName().endsWith(".java")) continue;
            try {
                CompilationUnit cu = StaticJavaParser.parse(file);

                for (ClassOrInterfaceDeclaration c : cu.findAll(ClassOrInterfaceDeclaration.class)) {
                    if (!c.isTopLevelType()) continue;
                    String classId = c.getNameAsString();
                    if (!graph.getNodes().containsKey(classId)) continue;

                    for (MethodDeclaration m : c.getMethods()) {
                        String callerId = classId + "." + m.getNameAsString();

                        m.getBody().ifPresent(body -> {
                            for (MethodCallExpr call : body.findAll(MethodCallExpr.class)) {
                                String calledName = call.getNameAsString();

                                // Same-class call (implicit this)
                                String sameClassTarget = classId + "." + calledName;
                                if (methodNameToId.containsKey(sameClassTarget)) {
                                    graph.addEdge(new GraphEdge(callerId, sameClassTarget,
                                            GraphEdge.EdgeType.METHOD_CALLS));
                                    continue;
                                }

                                // Scoped call — resolve via field types
                                call.getScope().ifPresent(scope -> {
                                    String scopeName = scope.toString();
                                    for (FieldDeclaration f : c.getFields()) {
                                        for (VariableDeclarator v : f.getVariables()) {
                                            if (v.getNameAsString().equals(scopeName)) {
                                                String fieldType = simpleTypeName(f.getElementType().asString());
                                                String targetId = fieldType + "." + calledName;
                                                if (methodNameToId.containsKey(targetId)) {
                                                    graph.addEdge(new GraphEdge(callerId, targetId,
                                                            GraphEdge.EdgeType.METHOD_CALLS));
                                                }
                                            }
                                        }
                                    }
                                });
                            }

                            // Constructor calls
                            for (ObjectCreationExpr newExpr : body.findAll(ObjectCreationExpr.class)) {
                                String created = simpleTypeName(newExpr.getType().getNameAsString());
                                if (classNameIndex.containsKey(created)) {
                                    graph.addEdge(new GraphEdge(callerId, created, GraphEdge.EdgeType.CALLS));
                                }
                            }
                        });
                    }
                }
            } catch (Exception ex) {
                // skip
            }
        }

        return graph;
    }

    /**
     * "java.util.List<Animal>" → "List", "com.foo.Animal[]" → "Animal"
     */
    private String simpleTypeName(String raw) {
        if (raw == null) return "";
        int lt = raw.indexOf('<');
        if (lt >= 0) raw = raw.substring(0, lt);
        int dot = raw.lastIndexOf('.');
        if (dot >= 0) raw = raw.substring(dot + 1);
        raw = raw.replace("[]", "");
        return raw.trim();
    }
}