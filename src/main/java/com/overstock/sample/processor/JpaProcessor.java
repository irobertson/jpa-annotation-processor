package com.overstock.sample.processor;

import java.beans.Introspector;
import java.util.Collection;
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
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.tools.Diagnostic.Kind;

@SupportedAnnotationTypes({"javax.persistence.Entity", "javax.persistence.OneToMany"})
@SupportedSourceVersion(SourceVersion.RELEASE_6)
public class JpaProcessor extends AbstractProcessor {

  // various types we'll want to refer to, initialized in init method
  private ElementTypePair entityType;
  private ElementTypePair oneToManyType;
  private ElementTypePair collectionsType;
  private ElementTypePair manyToOneType;

  // convenience delegations
  private Types typeUtils() {
    return processingEnv.getTypeUtils();
  }

  private void printMessage(Kind kind, String msg, Element element, AnnotationMirror annotationMirror) {
    processingEnv.getMessager().printMessage(kind, msg, element, annotationMirror);
  }

  // Core methods to override
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
    checkOneToManyAnnotatedProperties(roundEnv);
    return false; // let other processors work on these as well
  }

  /**
   * Verify that each class annotated with {@link Entity} has a no-argument constructor.
   * @param roundEnv
   */
  private void checkEntityAnnotatedElements(RoundEnvironment roundEnv) {
    Set<? extends Element> entityAnnotated =
        roundEnv.getElementsAnnotatedWith(entityType.element);
    for (TypeElement typeElement : ElementFilter.typesIn(entityAnnotated)) {
      checkForNoArgumentConstructor(typeElement);
    }
  }

  /**
   * Verify that the given type element has a no-argument constructor.
   * @param typeElement
   */
  private void checkForNoArgumentConstructor(TypeElement typeElement) {
    for (ExecutableElement constructor : ElementFilter.constructorsIn(typeElement.getEnclosedElements())) {
      if (constructor.getParameters().size() == 0) {
        return;
      }
    }
    AnnotationMirror entityAnnotation = getAnnotation(typeElement, entityType.type);
    printMessage(Kind.ERROR, "missing no argument constructor", typeElement, entityAnnotation);
  }

  /**
   * Verify that every property (field or method) annotated with &#64;{@link OneToMany}
   * has a &#64;{@link ManyToOne}-annotated property mapping back to it, referenced via the {@link OneToMany#mappedBy} value.
   * @param roundEnv
   */
  private void checkOneToManyAnnotatedProperties(RoundEnvironment roundEnv) {
    Set<? extends Element> entityAnnotated =
      roundEnv.getElementsAnnotatedWith(oneToManyType.element);
    for (Element element : entityAnnotated) {
      checkForBiDirectionalMapping(element);
    }
  }

  /**
   * Verify that a given property (in a "parent" class) which refers to child elements
   * has a @ManyToOne-annotated property mapping back to it, referenced via the {@link OneToMany#mappedBy} value.
   * @param childProperty the field or method in the parent class, annotated with &#64;{@link OneToMany}.
   */
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

  /**
   * Get the property name for a field or method element. For field elements, the field name is returned,
   * while for method elements, we attempt to do a JavaBeans conversion on the method name, stripping off
   * the "is" or "get" prefix and decapitalizing the first character of the result.
   * @param propertyElement an element for a field or method
   * @return the name of the property referenced
   */
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

  /**
   * Get the value of the {@link OneToMany#mappedBy() mappedBy} element of a &#64;{@link OneToMany} annotation.
   * @param oneToManyAnnotation an annotation of type &#64;{@link OneToMany}.
   * @return the value of {@code oneToManyAnnotation}'s {@code mappedBy} attribute
   */
  private String getMappedByValue(AnnotationMirror oneToManyAnnotation) {
    for(Entry<? extends ExecutableElement, ? extends AnnotationValue> entry:
      oneToManyAnnotation.getElementValues().entrySet()) {
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

  /**
   * Find a property (field or method) in a child type which is annotated with &#64;{@link ManyToOne} and has
   * a type of {@code parentType}
   * @param parentType the expected property type
   * @param childType the class expected to contain the annotated property
   * @return The property element and it's annotation
   */
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

  /**
   * Get the type parameter for a {@link Collection}.
   * @param type a parameterized collection type
   * @return the type of elements in the collection
   */
  private DeclaredType getCollectionType(TypeMirror type) {
    if (type != null && typeUtils().isAssignable(type, collectionsType.type)) {
      return (DeclaredType) ((DeclaredType) type).getTypeArguments().get(0);
    }
    return null;
  }

  /**
   * Get the type for a property - the type of a field, or the return type of a method
   * @param element a field or method element
   * @return the type for the property
   */
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

  /**
   * Find an annotation of a given type on an element
   * @param element a possibly annotated element
   * @param annotationType the expected annotation type
   * @return the annotation, or {@code null} if no annotation of type {@code annotationType} exists on {@code element}.
   */
  private AnnotationMirror getAnnotation(Element element, DeclaredType annotationType) {
    for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
      if (typeUtils().isSameType(mirror.getAnnotationType(), annotationType)) {
        return mirror;
      }
    }
    return null;
  }

  /**
   * Get the {@link TypeElement} and {@link DeclaredType} for a class
   * @param className the name of the class
   * @return the {@link TypeElement} and {@link DeclaredType} representing the class with name {@code className}
   */
  private ElementTypePair getType(String className) {
    TypeElement typeElement = processingEnv.getElementUtils().getTypeElement(className);
    return new ElementTypePair(typeElement, typeUtils().getDeclaredType(typeElement));
  }
}
