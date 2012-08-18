package com.overstock.sample.processor;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;

class AnnotatedElement {
  public AnnotatedElement(Element element, AnnotationMirror annotationMirror) {
    this.element = element;
    this.annotationMirror = annotationMirror;
  }

  final Element element;
  final AnnotationMirror annotationMirror;
}
