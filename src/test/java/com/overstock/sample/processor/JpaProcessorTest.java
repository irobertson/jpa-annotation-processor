package com.overstock.sample.processor;

import static org.junit.Assert.*;

import javax.annotation.processing.Messager;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic.Kind;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mockito;

public class JpaProcessorTest {

  private Messager mockMessager;
  private ProcessorWrapper processor;

  @Before
  public void setup() {
    mockMessager = Mockito.mock(Messager.class);
    processor = new ProcessorWrapper(new JpaProcessor(), mockMessager);
  }

  @Test
  public void testEntityWithNoArgConstructor() throws Exception {
    Compiler compiler = new Compiler(Compiler.Options());
    SourceFile[] sourceFiles = { new SourceFile(
      "SimpleAnnotated.java",
      "@javax.persistence.Entity",
      "public class SimpleAnnotated {}") };

    assertTrue(compiler.compileWithProcessor(processor, sourceFiles));
    Mockito.verifyNoMoreInteractions(mockMessager);
  }

  @Test
  public void testEntityWithoutNoArgConstructor() throws Exception {
    Compiler compiler = new Compiler(Compiler.Options());
    SourceFile[] sourceFiles = {
        new SourceFile(
          "SimpleAnnotated.java",
          "@javax.persistence.Entity",
          "public class SimpleAnnotated {",
          "  public SimpleAnnotated(int x) {}",
          "}") };

    assertTrue(compiler.compileWithProcessor(processor, sourceFiles)); //BROKEN
    verifyPrintMessage(
      Kind.ERROR, "missing no argument constructor", "SimpleAnnotated", "@javax.persistence.Entity");
    Mockito.verifyNoMoreInteractions(mockMessager);
  }

  @Test
  public void testOneToManyMissingManyToOne() throws Exception {
    Compiler compiler = new Compiler(Compiler.Options());
    SourceFile[] sourceFiles = {
        new SourceFile(
          "Parent.java",
          "public class Parent {",
          "  @javax.persistence.OneToMany",
          "  public java.util.Set<Child> getChildren() { return null; }",
          "}"),
        new SourceFile(
          "Child.java",
          "public class Child {",
          "  public Parent getParent() { return null; }",
          "}")};

    compiler.compileWithProcessor(processor, sourceFiles);
    verifyPrintMessage(
      Kind.ERROR, "No matching @ManyToOne annotation on Child", "getChildren()", "@javax.persistence.OneToMany");
    Mockito.verifyNoMoreInteractions(mockMessager);
  }

  @Test
  public void testOneToManyMissingMappedBy() throws Exception {
    Compiler compiler = new Compiler(Compiler.Options());
    SourceFile[] sourceFiles = {
        new SourceFile(
          "Parent.java",
          "public class Parent {",
          "  @javax.persistence.OneToMany",
          "  public java.util.Set<Child> getChildren() { return null; }",
          "}"),
      new SourceFile(
        "Child.java",
        "public class Child {",
        "  @javax.persistence.ManyToOne",
        "  public Parent getParent() { return null; }",
        "}")};

    compiler.compileWithProcessor(processor, sourceFiles);
    verifyPrintMessage(
      Kind.ERROR,
      "Missing mappedBy attribute",
      "getChildren()",
      "@javax.persistence.OneToMany");
    Mockito.verifyNoMoreInteractions(mockMessager);
  }

  @Test
  public void testOneToManyWrongMappedBy() throws Exception {
    Compiler compiler = new Compiler(Compiler.Options());
    SourceFile[] sourceFiles = {
        new SourceFile(
          "Parent.java",
          "public class Parent {",
          "  @javax.persistence.OneToMany(mappedBy=\"mismatch\")",
          "  public java.util.Set<Child> getChildren() { return null; }",
            "}"),
        new SourceFile(
          "Child.java",
          "public class Child {",
          "  @javax.persistence.ManyToOne",
          "  public Parent getParent() { return null; }",
          "}")};

    compiler.compileWithProcessor(processor, sourceFiles);
    verifyPrintMessage(
      Kind.ERROR,
      "mappedBy attribute should be parent",
      "getChildren()",
      "@javax.persistence.OneToMany(mappedBy=\"mismatch\")",
      "\"mismatch\"");
    Mockito.verifyNoMoreInteractions(mockMessager);
  }

  @Test
  public void testOneToManyCorrect() throws Exception {
    Compiler compiler = new Compiler(Compiler.Options());
    SourceFile[] sourceFiles = {
        new SourceFile(
          "Parent.java",
          "public class Parent {",
          "  @javax.persistence.OneToMany(mappedBy=\"parent\")",
          "  public java.util.Set<Child> getChildren() { return null; }",
            "}"),
        new SourceFile(
          "Child.java",
          "public class Child {",
          "  @javax.persistence.ManyToOne",
          "  public Parent getParent() { return null; }",
            "}")};

    compiler.compileWithProcessor(processor, sourceFiles);
    Mockito.verifyNoMoreInteractions(mockMessager);
  }

  private void verifyPrintMessage(Kind kind, String message, String elementName, String annotationName) {
    Mockito.verify(mockMessager).printMessage(
      Matchers.eq(kind),
      Matchers.eq(message),
      Matchers.argThat(ToStringMatcher.hasToString(elementName, Element.class)),
      Matchers.argThat(ToStringMatcher.hasToString(annotationName, AnnotationMirror.class)));
  }

  private void verifyPrintMessage(
    Kind kind, String message, String elementName, String annotationName, String annotationElementName) {
    Mockito.verify(mockMessager).printMessage(
      Matchers.eq(kind),
      Matchers.eq(message),
      Matchers.argThat(ToStringMatcher.hasToString(elementName, Element.class)),
      Matchers.argThat(ToStringMatcher.hasToString(annotationName, AnnotationMirror.class)),
      Matchers.argThat(ToStringMatcher.hasToString(annotationElementName, AnnotationValue.class)));
  }

}
