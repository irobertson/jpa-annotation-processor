package com.overstock.sample.processor;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.util.Types;

/**
 * A pair consisting of a {@link TypeElement} and the corresponding {@link DeclaredType}. In some cases, we need the
 * element, while in others, we need the type. It's possible to move back and forth between these two via
 * {@link Types#asElement(javax.lang.model.type.TypeMirror)} and
 * {@link Types#getDeclaredType(TypeElement, javax.lang.model.type.TypeMirror...)}, but its simpler to just hang on to
 * both.
 */
class ElementTypePair {
  public ElementTypePair(TypeElement element, DeclaredType type) {
    this.element = element;
    this.type = type;
  }

  final TypeElement element;
  final DeclaredType type;
}
