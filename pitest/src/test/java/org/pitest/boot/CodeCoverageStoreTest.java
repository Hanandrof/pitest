/*
 * Copyright 2011 Henry Coles
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at 
 * 
 * http://www.apache.org/licenses/LICENSE-2.0 
 * 
 * Unless required by applicable law or agreed to in writing, 
 * software distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and limitations under the License. 
 */
package org.pitest.boot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.matchers.JUnitMatchers.hasItems;
import static org.mockito.Mockito.verify;

import java.util.Collection;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class CodeCoverageStoreTest {

  @Mock
  private InvokeReceiver receiver;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    CodeCoverageStore.init(this.receiver);
  }

  @After
  public void cleanUp() {
    CodeCoverageStore.resetAllStaticState();
  }

  @Test
  public void shouldRegisterNewClassesWithReceiver() {
    final int id = CodeCoverageStore.registerClass("Foo");
    verify(this.receiver).registerClass(id, "Foo");
  }

  @Test
  public void shouldGenerateNewClassIdForEachClass() {
    final int id = CodeCoverageStore.registerClass("Foo");
    final int id2 = CodeCoverageStore.registerClass("Bar");
    assertFalse(id == id2);
  }

  @Test
  public void shouldCodeAndEncodeWhenClassIdAndLineNumberAreAtMaximum() {
    final long value = CodeCoverageStore.encode(Integer.MAX_VALUE,
        Integer.MAX_VALUE);
    assertEquals(Integer.MAX_VALUE, CodeCoverageStore.decodeClassId(value));
    assertEquals(Integer.MAX_VALUE, CodeCoverageStore.decodeLineId(value));
  }

  @Test
  public void shouldCodeAndEncodeWhenClassIdAndLineNumberAreAtMinimum() {
    final long value = CodeCoverageStore.encode(Integer.MIN_VALUE, 0);
    assertEquals(Integer.MIN_VALUE, CodeCoverageStore.decodeClassId(value));
    assertEquals(0, CodeCoverageStore.decodeLineId(value));
  }

  @Test
  public void shouldCodeAndEncodeWhenClassIdAndLineNumberAreZero() {
    final long value = CodeCoverageStore.encode(0, 0);
    assertEquals(0, CodeCoverageStore.decodeClassId(value));
    assertEquals(0, CodeCoverageStore.decodeLineId(value));
  }

  @Test
  public void shouldReturnRegisteredHitsWhenSingleClassCovered() {
    final int classId = CodeCoverageStore.registerClass("foo");
    CodeCoverageStore
        .registerClassProbes(classId, new int[] { 10, 20, 30, 42 });
    final long line10 = CodeCoverageStore.encode(classId, 0);
    final long line42 = CodeCoverageStore.encode(classId, 3);

    CodeCoverageStore.visitLine(line10);
    CodeCoverageStore.visitLine(line42);

    final Collection<Long> actual = CodeCoverageStore.getHits();
    assertThat(
        actual,
        hasItems(CodeCoverageStore.encode(classId, 10),
            CodeCoverageStore.encode(classId, 42)));
  }

  @Test
  public void shouldReturnRegisteredHitsWhenMultipleClassesCovered() {
    final int fooId = CodeCoverageStore.registerClass("foo");
    final int barId = CodeCoverageStore.registerClass("bar");
    CodeCoverageStore.registerClassProbes(fooId, new int[] { 13, 20, 30, 42 });
    CodeCoverageStore.registerClassProbes(barId, new int[] { 11 });
    final long line20 = CodeCoverageStore.encode(fooId, 1);
    final long line0 = CodeCoverageStore.encode(barId, 0);

    CodeCoverageStore.visitLine(line20);
    CodeCoverageStore.visitLine(line0);

    final Collection<Long> actual = CodeCoverageStore.getHits();
    assertThat(
        actual,
        hasItems(CodeCoverageStore.encode(barId, 11),
            CodeCoverageStore.encode(fooId, 20)));
  }

  @Test
  public void shouldClearHitCountersWhenReset() {
    final int classId = CodeCoverageStore.registerClass("foo");
    CodeCoverageStore.registerClassProbes(classId, new int[] { 10 });
    final long line10 = CodeCoverageStore.encode(classId, 0);

    CodeCoverageStore.visitLine(line10);
    CodeCoverageStore.reset();

    final Collection<Long> actual = CodeCoverageStore.getHits();
    assertEquals(Collections.emptyList(), actual);
  }

  @Test
  public void shouldBeSafeToAccessAcrossMultipleThreads()
      throws InterruptedException, ExecutionException {

    CodeCoverageStore.registerClass("foo");
    CodeCoverageStore.registerClassProbes(0, new int[1000]);

    final Callable<ConcurrentModificationException> read = makeReader();

    final ExecutorService pool = Executors.newFixedThreadPool(13);
    for (int i = 1; i != 13; i++) {
      pool.submit(makeWriter(i));
    }
    final Future<ConcurrentModificationException> future = pool.submit(read);
    pool.shutdown();

    assertNull(future.get());

  }

  private Callable<ConcurrentModificationException> makeReader() {
    final Callable<ConcurrentModificationException> read = new Callable<ConcurrentModificationException>() {
      public ConcurrentModificationException call() throws Exception {
        ConcurrentModificationException error = null;
        try {
          pointlesslyIterateCollection();
          pointlesslyIterateCollection();
          pointlesslyIterateCollection();
        } catch (final ConcurrentModificationException ex) {
          error = ex;
        }
        return error;
      }

      private long pointlesslyIterateCollection() {
        long total = 0;
        for (final Long i : CodeCoverageStore.getHits()) {
          total += i;
          try {
            Thread.sleep(5);
          } catch (final InterruptedException e) {

          }
        }
        return total;
      }
    };
    return read;
  }

  private static Runnable makeWriter(final int sleepPeriod) {
    final Runnable write = new Runnable() {

      public void run() {
        for (int i = 0; i != 1000; i++) {
          try {
            Thread.sleep(sleepPeriod);
          } catch (final InterruptedException e) {
          }
          CodeCoverageStore.visitLine(i);
        }
      }
    };
    return write;
  }

}
