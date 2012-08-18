package com.overstock.sample.processor;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;

/**
 * A pair consisting of a {@link TypeElement} and the corresponding {@link DeclaredType}
 */
class ElementTypePair {
  public ElementTypePair(TypeElement element, DeclaredType type) {
    this.element = element;
    this.type = type;
  }

  final TypeElement element;
  final DeclaredType type;
}
