package net.jodah.recurrent;

import static net.jodah.recurrent.Testing.failures;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.net.ConnectException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.testng.annotations.Test;

/**
 * @author Jonathan Halterman
 */
@Test
public class InvocationTest {
  ConnectException e = new ConnectException();

  public void testCanRetryForResult() {
    // Given retry for null
    Invocation inv = new Invocation(new RetryPolicy().retryWhen(null));

    // When / Then
    assertFalse(inv.complete(null));
    assertTrue(inv.canRetryFor(null));
    assertFalse(inv.canRetryFor(1));

    // Then
    assertEquals(inv.getAttemptCount(), 2);
    assertTrue(inv.isComplete());
    assertEquals(inv.getLastResult(), Integer.valueOf(1));
    assertNull(inv.getLastFailure());

    // Given 2 max retries
    inv = new Invocation(new RetryPolicy().retryWhen(null).withMaxRetries(2));

    // When / Then
    assertFalse(inv.complete(null));
    assertTrue(inv.canRetryFor(null));
    assertTrue(inv.canRetryFor(null));
    assertFalse(inv.canRetryFor(null));

    // Then
    assertEquals(inv.getAttemptCount(), 3);
    assertTrue(inv.isComplete());
    assertNull(inv.getLastResult());
    assertNull(inv.getLastFailure());
  }

  public void testCanRetryForResultAndThrowable() {
    // Given retry for null
    Invocation inv = new Invocation(new RetryPolicy().retryWhen(null));

    // When / Then
    assertFalse(inv.complete(null));
    assertTrue(inv.canRetryFor(null, null));
    assertTrue(inv.canRetryFor(1, new IllegalArgumentException()));
    assertFalse(inv.canRetryFor(1, null));

    // Then
    assertEquals(inv.getAttemptCount(), 3);
    assertTrue(inv.isComplete());

    // Given 2 max retries
    inv = new Invocation(new RetryPolicy().retryWhen(null).withMaxRetries(2));

    // When / Then
    assertFalse(inv.complete(null));
    assertTrue(inv.canRetryFor(null, e));
    assertTrue(inv.canRetryFor(null, e));
    assertFalse(inv.canRetryFor(null, e));

    // Then
    assertEquals(inv.getAttemptCount(), 3);
    assertTrue(inv.isComplete());
  }

  @SuppressWarnings("unchecked")
  public void testCanRetryOn() {
    // Given retry on IllegalArgumentException
    Invocation inv = new Invocation(new RetryPolicy().retryOn(IllegalArgumentException.class));

    // When / Then
    assertTrue(inv.canRetryOn(new IllegalArgumentException()));
    assertFalse(inv.canRetryOn(e));

    // Then
    assertEquals(inv.getAttemptCount(), 2);
    assertTrue(inv.isComplete());
    assertNull(inv.getLastResult());
    assertEquals(inv.getLastFailure(), e);

    // Given 2 max retries
    inv = new Invocation(new RetryPolicy().withMaxRetries(2));

    // When / Then
    assertTrue(inv.canRetryOn(e));
    assertTrue(inv.canRetryOn(e));
    assertFalse(inv.canRetryOn(e));

    // Then
    assertEquals(inv.getAttemptCount(), 3);
    assertTrue(inv.isComplete());
    assertNull(inv.getLastResult());
    assertEquals(inv.getLastFailure(), e);
  }

  public void testComplete() {
    // Given
    Invocation inv = new Invocation(new RetryPolicy());

    // When
    inv.complete();

    // Then
    assertEquals(inv.getAttemptCount(), 1);
    assertTrue(inv.isComplete());
    assertNull(inv.getLastResult());
    assertNull(inv.getLastFailure());
  }

  public void testCompleteForResult() {
    // Given
    Invocation inv = new Invocation(new RetryPolicy().retryWhen(null));

    // When / Then
    assertFalse(inv.complete(null));
    assertTrue(inv.complete(true));

    // Then
    assertEquals(inv.getAttemptCount(), 1);
    assertTrue(inv.isComplete());
    assertEquals(inv.getLastResult(), Boolean.TRUE);
    assertNull(inv.getLastFailure());
  }

  public void testGetAttemptCount() {
    Invocation inv = new Invocation(new RetryPolicy());
    inv.recordFailure(e);
    inv.recordFailure(e);
    assertEquals(inv.getAttemptCount(), 2);
  }

  @SuppressWarnings("unchecked")
  public void testIsComplete() {
    List<Object> list = mock(List.class);
    when(list.size()).thenThrow(failures(2, IllegalStateException.class)).thenReturn(5);

    RetryPolicy retryPolicy = new RetryPolicy().retryOn(IllegalStateException.class);
    Invocation inv = new Invocation(retryPolicy);

    while (!inv.isComplete()) {
      try {
        inv.complete(list.size());
      } catch (IllegalStateException e) {
        inv.recordFailure(e);
      }
    }

    assertEquals(inv.getLastResult(), Integer.valueOf(5));
    assertEquals(inv.getAttemptCount(), 3);
  }

  public void shouldAdjustWaitTimeForBackoff() {
    Invocation inv = new Invocation(new RetryPolicy().withBackoff(1, 10, TimeUnit.NANOSECONDS));
    assertEquals(inv.getWaitNanos(), 1);
    inv.recordFailure(e);
    assertEquals(inv.getWaitNanos(), 2);
    inv.recordFailure(e);
    assertEquals(inv.getWaitNanos(), 4);
    inv.recordFailure(e);
    assertEquals(inv.getWaitNanos(), 8);
    inv.recordFailure(e);
    assertEquals(inv.getWaitNanos(), 10);
    inv.recordFailure(e);
    assertEquals(inv.getWaitNanos(), 10);
  }

  public void shouldAdjustWaitTimeForMaxDuration() throws Throwable {
    Invocation inv = new Invocation(
        new RetryPolicy().withDelay(49, TimeUnit.MILLISECONDS).withMaxDuration(50, TimeUnit.MILLISECONDS));
    Thread.sleep(10);
    assertTrue(inv.canRetryOn(e));
    assertTrue(inv.getWaitNanos() < TimeUnit.MILLISECONDS.toNanos(50) && inv.getWaitNanos() > 0);
  }

  public void shouldSupportMaxDuration() throws Exception {
    Invocation inv = new Invocation(new RetryPolicy().withMaxDuration(100, TimeUnit.MILLISECONDS));
    assertTrue(inv.canRetryOn(e));
    assertTrue(inv.canRetryOn(e));
    Thread.sleep(100);
    assertFalse(inv.canRetryOn(e));
    assertTrue(inv.isComplete());
  }

  public void shouldSupportMaxRetries() throws Exception {
    Invocation inv = new Invocation(new RetryPolicy().withMaxRetries(3));
    assertTrue(inv.canRetryOn(e));
    assertTrue(inv.canRetryOn(e));
    assertTrue(inv.canRetryOn(e));
    assertFalse(inv.canRetryOn(e));
    assertTrue(inv.isComplete());
  }

  @Test(expectedExceptions = IllegalStateException.class)
  public void shouldThrowOnMultipleCompletes() {
    Invocation inv = new Invocation(new RetryPolicy());
    inv.complete();
    inv.complete();
  }
  
  @Test(expectedExceptions = IllegalStateException.class)
  public void shouldThrowOnCanRetryWhenAlreadyComplete() {
    Invocation inv = new Invocation(new RetryPolicy());
    inv.complete();
    inv.canRetryOn(e);
  }
}
