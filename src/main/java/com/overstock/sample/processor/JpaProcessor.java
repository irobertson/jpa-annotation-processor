package com.overstock.sample.processor;

import java.beans.Introspector;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Types;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.tools.Diagnostic.Kind;

/**
 * An example annotation processor, used in the talk "Writing Annotation Processors to Aid Your Development Process".
 *
 * This processor does two things:
 * <ul>
 *   <li>Verifies that &#64;{@link Entity}-annotated classes have a no-argument constructor.</li>
 *   <li>
 *     For properties annotated with &#64;{@link OneToMany}, it verifies that:
 *     <ul>
 *       <li>The child entity must have a corresponding property annotated with &#64;{@link ManyToOne}</li>
 *       <li>
 *         The {code}&#64;OneToMany{code} annotation must have {@code mappedBy} pointing to the property on the child
 *       </li>
 *     </ul>
 *   </li>
 * </ul>
 * @author ian
 *
 */
@SupportedAnnotationTypes({"javax.persistence.Entity", "javax.persistence.OneToMany"})
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class JpaProcessor extends AbstractProcessor {

  // various types we'll want to refer to, initialized in init method
  private ElementTypePair entityType;
  private ElementTypePair oneToManyType;
  private ElementTypePair collectionType;

  private ExecutableElement mappedByAttribute;

  // convenience delegations
  private Types typeUtils() {
    return processingEnv.getTypeUtils();
  }

  // Core methods to override
  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    entityType = getType("javax.persistence.Entity");
    oneToManyType = getType("javax.persistence.OneToMany");
    collectionType = getType("java.util.Collection");

    mappedByAttribute = getMethod(oneToManyType.element, "mappedBy");
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
    // technically, we don't need to filter here, but it gives us a free cast
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
      List<? extends VariableElement> parameters = constructor.getParameters();
      if (parameters.isEmpty()) {
        return;
      }
    }
    AnnotationMirror entityAnnotation = getAnnotation(typeElement, entityType.type);
    processingEnv.getMessager().printMessage(
      Kind.ERROR,
      "missing no argument constructor",
      typeElement,
      entityAnnotation);
  }

  /**
   * Verify that every property (field or method) annotated with &#64;{@link OneToMany}
   * has a &#64;{@link ManyToOne}-annotated property mapping back to it, referenced via the {@link OneToMany#mappedBy} value.
   * @param roundEnv
   */
  private void checkOneToManyAnnotatedProperties(RoundEnvironment roundEnv) {
    Set<? extends Element> oneToManyAnnotated =
      roundEnv.getElementsAnnotatedWith(oneToManyType.element);
    for (Element element : oneToManyAnnotated) {
      checkForBiDirectionalMapping(element);
    }
  }

  /**
   * Verify that a given property (in a "parent" class) which refers to child elements
   * has a @ManyToOne-annotated property mapping back to it, referenced via the {@link OneToMany#mappedBy} value.
   * @param childProperty the field or method in the parent class, annotated with &#64;{@link OneToMany}.
   */
  private void checkForBiDirectionalMapping(Element childProperty) {
    TypeMirror propertyType = getPropertyType(childProperty);
    DeclaredType childType = getCollectionType(propertyType);
    Element childElement = childType.asElement();
    TypeElement enclosingElement = (TypeElement) childProperty.getEnclosingElement();
    DeclaredType parentType = typeUtils().getDeclaredType(enclosingElement);
    AnnotationMirror oneToManyAnnotation = getAnnotation(childProperty, oneToManyType.type);
    Element parentPropertyInChild = findParentReferenceInChildType(parentType, childElement);
    if (parentPropertyInChild == null) {
      processingEnv.getMessager().printMessage(
        Kind.ERROR,
        "No matching @ManyToOne annotation on " + childElement.getSimpleName(),
        childProperty,
        oneToManyAnnotation);
    }
    else {
      AnnotationValue mappedBy = getMappedByValue(oneToManyAnnotation);
      if (mappedBy == null) {
        processingEnv.getMessager().printMessage(
          Kind.ERROR,
          "Missing mappedBy attribute",
          childProperty,
          oneToManyAnnotation);
      }
      else {
        String mappedByContent = (String) mappedBy.getValue(); //or:
                                                               //childProperty.getAnnotation(OneToMany.class).mappedBy()
        String expected = getPropertyName(parentPropertyInChild);
        if (! mappedByContent.equals(expected)) {
          processingEnv.getMessager().printMessage(
            Kind.ERROR,
            "mappedBy attribute should be " + expected,
            childProperty,
            oneToManyAnnotation,
            mappedBy);
        }
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
   * Note that an alternative approach here would be to go back to the annotated element and call
   * {@code getAnnotation(OneToMany.class).mappedBy()}. However, in some cases, it is necessary to reflect on the
   * annotation mirror. For example, annotation values of type Class may refer to classes currently under compilation,
   * and hence not in the class path. Attempting to invoke those annotation methods could result in a
   * {@link MirroredTypeException} being thrown. In more extreme cases, you may be reflecting over an annotation
   * which is not available at the time your annotation processor is being built.
   *
   * @param oneToManyAnnotation an annotation of type &#64;{@link OneToMany}.
   * @return the value of {@code oneToManyAnnotation}'s {@code mappedBy} attribute
   */
  private AnnotationValue getMappedByValue(AnnotationMirror oneToManyAnnotation) {
    Map<? extends ExecutableElement, ? extends AnnotationValue> elementValues =
        oneToManyAnnotation.getElementValues();
    return elementValues.get(mappedByAttribute);
  }

  /**
   * Find a property (field or method) in a child type which is annotated with &#64;{@link ManyToOne} and has
   * a type of {@code parentType}
   * @param parentType the expected property type
   * @param childType the class expected to contain the annotated property
   * @return The property element and it's annotation
   */
  private Element findParentReferenceInChildType(TypeMirror parentType, Element childType) {
    for (Element element: childType.getEnclosedElements()) {
      if (element.getKind() == ElementKind.FIELD || element.getKind() == ElementKind.METHOD) {
        if (element.getAnnotation(ManyToOne.class) != null
            && typeUtils().isSameType(parentType, getPropertyType(element))) {
          return element;
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
    if (type != null && typeUtils().isAssignable(type, collectionType.type)) {
      // This is a bit of a hack; to work properly, we should walk up the inheritance hierarchy, tracking type
      // parameters as we go. For example, if the type in question is StringList, which implements List<String>,
      // then this code would not work.
      List<? extends TypeMirror> typeArguments = ((DeclaredType) type).getTypeArguments();
      return (DeclaredType) typeArguments.get(0);
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
    DeclaredType declaredType = typeUtils().getDeclaredType(typeElement);
    return new ElementTypePair(typeElement, declaredType);
  }

  /**
   * Get the method in {@code element} named {@code methodName}
   * @param element an element representing an entity with a method name of {@code methodName}
   * @param methodName the method name
   * @return the method
   */
  private ExecutableElement getMethod(Element element, String methodName) {
    for (ExecutableElement executable: ElementFilter.methodsIn(element.getEnclosedElements())) {
      if (executable.getSimpleName().toString().equals(methodName)) {
        return executable;
      }
    }
    throw new IllegalArgumentException("no element named " + methodName + " + in element");
  }

}
