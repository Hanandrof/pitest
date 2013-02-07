package org.pitest.classinfo;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.HashSet;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.pitest.extension.TestClassIdentifier;
import org.pitest.functional.Option;
import org.pitest.mutationtest.MutationClassPaths;

public class CodeSourceTest {

  private CodeSource          testee;

  @Mock
  private Repository          repository;

  @Mock
  private MutationClassPaths  classPath;

  @Mock
  private TestClassIdentifier testIdentifer;

  private ClassInfo           foo;

  private ClassInfo           bar;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    this.testee = new CodeSource(this.classPath, this.repository,
        this.testIdentifer);
    this.foo = makeClassInfo("Foo");
    this.bar = makeClassInfo("Bar");
  }

  @Test
  public void shouldIdentifyAllNonTestCodeOnClassPathWhenNoTestsPresent() {
    when(this.classPath.code()).thenReturn(
        Arrays.asList(this.foo.getName(), this.bar.getName()));
    assertEquals(Arrays.asList(this.foo, this.bar), this.testee.getCode());
  }

  @Test
  public void shouldIdentifyAllNonTestCodeOnClassPathWhenTestsPresentOnCodePath() {
    when(this.testIdentifer.isATestClass(this.foo)).thenReturn(true);
    when(this.classPath.code()).thenReturn(
        Arrays.asList(this.foo.getName(), this.bar.getName()));

    assertEquals(Arrays.asList(this.bar), this.testee.getCode());
  }

  @Test
  public void shouldIdentifyAllTestCodeOnTestPath() {
    when(this.testIdentifer.isATestClass(this.foo)).thenReturn(true);
    when(this.classPath.test()).thenReturn(
        Arrays.asList(this.foo.getName(), this.bar.getName()));

    assertEquals(Arrays.asList(this.foo), this.testee.getTests());
  }

  @Test
  public void shouldProvideNamesOfNonTestClasses() {
    final ClassInfo foo = makeClassInfo("Foo");
    final ClassInfo bar = makeClassInfo("Bar");
    when(this.testIdentifer.isATestClass(foo)).thenReturn(true);
    when(this.classPath.code()).thenReturn(
        Arrays.asList(foo.getName(), bar.getName()));

    assertEquals(new HashSet<ClassName>(Arrays.asList(new ClassName("Bar"))),
        this.testee.getCodeUnderTestNames());
  }

  @Test
  public void shouldMapTestsPostfixedWithTestToTesteeWhenTesteeExists() {
    when(this.repository.hasClass(new ClassName("com.example.Foo")))
        .thenReturn(true);
    assertEquals(new ClassName("com.example.Foo"),
        this.testee.findTestee("com.example.FooTest").value());
  }

  @Test
  public void shouldMapTestsPrefixedWithTestToTesteeWhenTesteeExists() {
    when(this.repository.hasClass(new ClassName("com.example.Foo")))
        .thenReturn(true);
    assertEquals(new ClassName("com.example.Foo"),
        this.testee.findTestee("com.example.TestFoo").value());
  }

  @Test
  public void shouldReturnNoneWhenNoTesteeExistsMatchingNamingConvention() {
    when(this.repository.hasClass(new ClassName("com.example.Foo")))
        .thenReturn(false);
    assertEquals(Option.none(), this.testee.findTestee("com.example.TestFoo"));
  }

  @Test
  public void shouldProvideDetailsOfRequestedClasses() {
    when(this.repository.fetchClass(ClassName.fromString("Foo"))).thenReturn(
        Option.some(this.foo));
    when(this.repository.fetchClass(ClassName.fromString("Unknown")))
        .thenReturn(Option.<ClassInfo> none());
    assertEquals(Arrays.asList(this.foo), this.testee.getClassInfo(Arrays
        .asList(ClassName.fromString("Foo"), ClassName.fromString("Unknown"))));
  }

  private ClassInfo makeClassInfo(final String name) {
    final ClassInfo ci = ClassInfoMother.make(name);
    when(this.repository.fetchClass(ClassName.fromString(name))).thenReturn(
        Option.some(ci));
    return ci;
  }

}
