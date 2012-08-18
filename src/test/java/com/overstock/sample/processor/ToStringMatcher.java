package com.overstock.sample.processor;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;

class ToStringMatcher<T> extends BaseMatcher<T> {
  /**
   * @param <T> The type of the argument being matched
   * @param expecteed the expected toString value for the object
   * @param clazz the class of the object
   * @return a {@code ToStringMatcher}.
   */
  public static <T> Matcher<T> hasToString(String expecteed, Class<T> clazz) {
    return new ToStringMatcher<T>(expecteed);
  }

  private final String expectedToString;

  public ToStringMatcher(String expectedToString) {
    this.expectedToString = expectedToString;
  }

  @Override
  public boolean matches(Object obj) {
    return obj != null && expectedToString.equals(obj.toString());
  }

  @Override
  public void describeTo(Description description) {
    description.appendText("An object with a toString of " + expectedToString);
  }

}
