package com.overstock.sample.processor;

import java.util.Collection;

import com.google.common.collect.ImmutableList;

public class SourceFile {
  private final String fileName;
  private final Collection<String> content;

  public SourceFile(String fileName, String... content) {
    this.fileName = fileName;
    this.content = ImmutableList.copyOf(content);
  }

  public String getFileName() { return fileName; }
  public Collection<String> getContent() { return content; }
}
