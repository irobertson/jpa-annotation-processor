package com.overstock.sample.processor;

import java.beans.Introspector;
import java.util.Map.Entry;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.SimpleAnnotationValueVisitor6;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic.Kind;

@SupportedAnnotationTypes({"javax.persistence.Entity", "javax.persistence.OneToMany"})
@SupportedSourceVersion(SourceVersion.RELEASE_6)
public class JpaProcessor extends AbstractProcessor {

  private ElementTypePair entityType;
  private ElementTypePair oneToManyType;
  private ElementTypePair collectionsType;
  private ElementTypePair manyToOneType;

  private Types typeUtils() {
    return processingEnv.getTypeUtils();
  }

  private void printMessage(Kind kind, String msg, Element element, AnnotationMirror annotationMirror) {
    processingEnv.getMessager().printMessage(kind, msg, element, annotationMirror);
  }

  private ElementTypePair getType(String name) {
    TypeElement typeElement = processingEnv.getElementUtils().getTypeElement(name);
    return new ElementTypePair(typeElement, typeUtils().getDeclaredType(typeElement));
  }

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    entityType = getType("javax.persistence.Entity");
    oneToManyType = getType("javax.persistence.OneToMany");

    collectionsType = getType("java.util.Collection");
    manyToOneType = getType("javax.persistence.ManyToOne");
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    checkEntityAnnotatedElements(roundEnv);
    checkOneToManyAnnotatedElement(roundEnv);
    return false; // let other processors work on these as well
  }

  private void checkEntityAnnotatedElements(RoundEnvironment roundEnv) {
    Set<? extends Element> entityAnnotated =
        roundEnv.getElementsAnnotatedWith(entityType.element);
    for (TypeElement typeElement : ElementFilter.typesIn(entityAnnotated)) {
      checkForNoArgumentConstructor(typeElement);
    }
  }

  private void checkForNoArgumentConstructor(TypeElement typeElement) {
    for (ExecutableElement constructor : ElementFilter.constructorsIn(typeElement.getEnclosedElements())) {
      if (constructor.getParameters().size() == 0) {
        return;
      }
    }
    AnnotationMirror annotationMirror = getAnnotation(typeElement, entityType.type);
    printMessage(Kind.ERROR, "missing no argument constructor", typeElement, annotationMirror);
  }

  private void checkOneToManyAnnotatedElement(RoundEnvironment roundEnv) {
    Set<? extends Element> entityAnnotated =
      roundEnv.getElementsAnnotatedWith(oneToManyType.element);
    for (Element element : entityAnnotated) {
      checkForBiDirectionalMapping(element);
    }
  }

  private void checkForBiDirectionalMapping(Element childProperty) {
    AnnotationMirror oneToManyAnnotation = getAnnotation(childProperty, oneToManyType.type);
    Element childElement = getCollectionType(getPropertyType(childProperty)).asElement();
    DeclaredType parentType =
        typeUtils().getDeclaredType((TypeElement) childProperty.getEnclosingElement());
    AnnotatedElement manyToOneAnnotated = findParentReferenceInChildType(
      parentType, childElement);
    if (manyToOneAnnotated == null) {
      printMessage(
        Kind.ERROR,
        "No matching @ManyToOne annotation on " + childElement.getSimpleName(),
        childProperty,
        oneToManyAnnotation);
    }
    else {
      String mappedBy = getMappedByValue(oneToManyAnnotation);
      if (mappedBy == null) {
        printMessage(
          Kind.ERROR,
          "Missing mappedBy attribute",
          childProperty,
          oneToManyAnnotation);
      }
      else if (! mappedBy.equals(getPropertyName(manyToOneAnnotated.element))) {
        printMessage(
          Kind.ERROR,
          "mappedBy attribute should be " + getPropertyName(manyToOneAnnotated.element),
          childProperty,
          oneToManyAnnotation);
      }
    }
  }

  private String getPropertyName(Element propertyElement) {
    switch (propertyElement.getKind()) {
      case FIELD: return propertyElement.getSimpleName().toString();
      case METHOD:
        String methodName = propertyElement.getSimpleName().toString();
        if (methodName.startsWith("get")) {
          return Introspector.decapitalize(methodName.substring(3));
        }
        if (methodName.startsWith("is")) {
          return Introspector.decapitalize(methodName.substring(2));
        }
        else {
          // not actually a javaBean method; just return the method name
          return methodName;
        }
      default: // should never happen
        throw new IllegalArgumentException("property element of type " + propertyElement.getKind());
    }
  }

  private String getMappedByValue(AnnotationMirror manyToOneAnnotation) {
    for(Entry<? extends ExecutableElement, ? extends AnnotationValue> entry:
      manyToOneAnnotation.getElementValues().entrySet()) {
      if ("mappedBy".equals(entry.getKey().getSimpleName().toString())) {
        return entry.getValue().accept(new SimpleAnnotationValueVisitor6<String, Void>() {
          @Override
          public String visitString(String s, Void p) {
            return s;
          }
        }, null);
      }
    }
    return null;
  }

  private AnnotatedElement findParentReferenceInChildType(TypeMirror parentType, Element childType) {
    for (Element element: childType.getEnclosedElements()) {
      if (element.getKind() == ElementKind.FIELD || element.getKind() == ElementKind.METHOD) {
        for (AnnotationMirror annotationMirror: element.getAnnotationMirrors()) {
          if (typeUtils().isSameType(manyToOneType.type, annotationMirror.getAnnotationType())
              && typeUtils().isSameType(parentType, getPropertyType(element))) {
            return new AnnotatedElement(element, annotationMirror);
          }
        }
      }
    }
    return null;
  }

  private DeclaredType getCollectionType(TypeMirror type) {
    if (type != null && typeUtils().isAssignable(type, collectionsType.type)) {
      return (DeclaredType) ((DeclaredType) type).getTypeArguments().get(0);
    }
    return null;
  }

  private TypeMirror getPropertyType(Element element) {
    switch (element.getKind()) {
      case FIELD:
        return ((VariableElement) element).asType();
      case METHOD:
        return ((ExecutableElement) element).getReturnType();
      default:
        return null;

    }
  }

  private AnnotationMirror getAnnotation(Element element, DeclaredType annotationType) {
    for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
      if (typeUtils().isSameType(mirror.getAnnotationType(), annotationType)) {
        return mirror;
      }
    }
    return null;
  }
}
