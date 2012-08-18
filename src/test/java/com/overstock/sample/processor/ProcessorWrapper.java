package com.overstock.sample.processor;

import java.util.Set;

import javax.annotation.processing.Completion;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

import org.mockito.Mockito;

/**
 * A wrapper around a {@code Processor} which will replace the standard {@code Messager}
 * returned from {@link ProcessingEnvironment#getMessager()} with a provided
 * mock Messager.
 */
public class ProcessorWrapper implements Processor {
  private final Processor wrapped;
  private final Messager mockMessager;

  @Override
  public void init(ProcessingEnvironment processingEnv) {
    ProcessingEnvironment spy = Mockito.spy(processingEnv);
    Mockito.when(spy.getMessager()).thenReturn(mockMessager);
    wrapped.init(spy);
  }

  public ProcessorWrapper(Processor wrapped, Messager mockMessager) {
    this.wrapped = wrapped;
    this.mockMessager = mockMessager;
  }

  @Override
  public Iterable<? extends Completion> getCompletions(Element element,
    AnnotationMirror annotation, ExecutableElement member, String userText) {
    return wrapped.getCompletions(element, annotation, member, userText);
  }

  @Override
  public Set<String> getSupportedAnnotationTypes() {
    return wrapped.getSupportedAnnotationTypes();
  }

  @Override
  public Set<String> getSupportedOptions() {
    return wrapped.getSupportedOptions();
  }

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return wrapped.getSupportedSourceVersion();
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    return wrapped.process(annotations, roundEnv);
  }


}
