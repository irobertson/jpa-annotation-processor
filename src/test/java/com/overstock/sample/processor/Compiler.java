package com.overstock.sample.processor;


import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.annotation.processing.Processor;
import javax.persistence.Entity;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.apache.commons.io.FileUtils;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.Lists;

public class Compiler {

  private static List<String> classPathEntries = Lists.newArrayList();
  static {
    classPathEntries.add(classPathFor(Compiler.class));
    classPathEntries.add(classPathFor(JpaProcessor.class));
    classPathEntries.add(classPathFor(Entity.class));
  }

  private final File sourceDir, outputDir;
  private final List<String> options;

  public Compiler() throws IOException {
    sourceDir = createTempDir("sourceDir");
    outputDir = createTempDir("outputDir");

    Builder<String> builder = ImmutableList.builder();
    builder.add("-classpath").add(buildClassPath(outputDir));
    builder.add("-d").add(outputDir.getAbsolutePath());
    this.options = builder.build();
  }

  public File getOutputDir() {
    return outputDir;
  }

  public void cleanUp() {
    FileUtils.deleteQuietly(outputDir);
    FileUtils.deleteQuietly(sourceDir);
  }

  public boolean compileWithProcessor(Processor processor, SourceFile... sourceFiles)
  throws Exception {
    return compile(processor, sourceFiles);
  }

  public boolean compile(SourceFile... sourceFiles) throws Exception {
    return compile(null, sourceFiles);
  }

  private boolean compile(Processor processor, SourceFile... sourceFiles)  throws Exception {
    File[] files = new File[sourceFiles.length];
    for (int i = 0; i < sourceFiles.length; i++) {
      files[i] = writeSourceFile(sourceFiles[i]);
    }
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
    Iterable<? extends JavaFileObject> javaFileObjects = fileManager.getJavaFileObjects(files);
    CompilationTask compilationTask =
      compiler.getTask(null, null, null, options, null, javaFileObjects);
    if (processor != null) {
      compilationTask.setProcessors(Arrays.asList(processor));
    }
    Boolean success = compilationTask.call();
    fileManager.close();
    return success;
  }

  private static String buildClassPath(File outputDir) {
    ArrayList<String> classPathElements = Lists.newArrayList(classPathEntries);
    classPathElements.add(outputDir.getAbsolutePath());
    return Joiner.on(System.getProperty("path.separator")).join(classPathElements);
  }

  private static String classPathFor(Class<?> clazz) {
    URL url = clazz.getProtectionDomain().getCodeSource().getLocation();

    try {
      URI uri = url.toURI();

      return Paths.get(uri).toFile().getAbsolutePath();
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

  protected File writeSourceFile(SourceFile sourceFile) throws IOException {
   File file = new File(sourceDir, sourceFile.getFileName());
   FileUtils.writeLines(file, sourceFile.getContent());
   return file;
  }

  private static File createTempDir(String prefix) throws IOException {
    File file = File.createTempFile(prefix, null);
    if (!file.delete()) {
      throw new IOException("Unable to delete temporary file " + file.getAbsolutePath());
    }
    if (!file.mkdir()) {
      throw new IOException("Unable to create temp directory " + file.getAbsolutePath());
    }
    return file;
  }
}
