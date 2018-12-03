package com.github.edgar615.resilience4j.circuitbreaker;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerOpenException;
import io.vavr.CheckedFunction0;
import io.vavr.CheckedFunction1;
import io.vavr.control.Try;
import org.junit.Assert;
import org.junit.Test;

import java.time.Duration;

public class CircuitBreakerTest {

    @Test
    public void testHello() {
        CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("testName");

// When I decorate my function
        CheckedFunction0<String> decoratedSupplier = CircuitBreaker
                .decorateCheckedSupplier(circuitBreaker, () -> "This can be any method which returns: 'Hello");
        // and chain an other function with map
        Try<String> result = Try.of(decoratedSupplier)
                .map(value -> value + " world'");

// Then the Try Monad returns a Success<String>, if all functions ran successfully.
        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals(result.get(), "This can be any method which returns: 'Hello world'");
    }


    @Test
    public void testHello2() {
        // Given
        CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("testName");
        CircuitBreaker anotherCircuitBreaker = CircuitBreaker.ofDefaults("anotherTestName");

// When I create a Supplier and a Function which are decorated by different CircuitBreakers
        CheckedFunction0<String> decoratedSupplier = CircuitBreaker
                .decorateCheckedSupplier(circuitBreaker, () -> "Hello");

        CheckedFunction1<String, String> decoratedFunction = CircuitBreaker
                .decorateCheckedFunction(anotherCircuitBreaker, (input) -> input + " world");

// and I chain a function with map
        Try<String> result = Try.of(decoratedSupplier)
                .mapTry(decoratedFunction::apply);

// Then
        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals(result.get(), "Hello world");
    }

    @Test
    public void testOpen() {
        // Given
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .ringBufferSizeInClosedState(2)
                .waitDurationInOpenState(Duration.ofMillis(1000))
                .build();
        CircuitBreaker circuitBreaker = CircuitBreaker.of("testName", circuitBreakerConfig);

// Simulate a failure attempt
        circuitBreaker.onError(0, new RuntimeException());
// CircuitBreaker is still CLOSED, because 1 failure is allowed
        Assert.assertEquals(circuitBreaker.getState(),CircuitBreaker.State.CLOSED);
// Simulate a failure attempt
        circuitBreaker.onError(0, new RuntimeException());
// CircuitBreaker is OPEN, because the failure rate is above 50%
        Assert.assertEquals(circuitBreaker.getState(),CircuitBreaker.State.OPEN);

// When I decorate my function and invoke the decorated function
        Try<String> result = Try.of(CircuitBreaker.decorateCheckedSupplier(circuitBreaker, () -> "Hello"))
                .map(value -> value + " world");

// Then the call fails, because CircuitBreaker is OPEN
        Assert.assertTrue(result.isFailure());
// Exception is CircuitBreakerOpenException
        Assert.assertTrue(result.failed().get() instanceof CircuitBreakerOpenException);

        circuitBreaker.reset();
        Assert.assertEquals(circuitBreaker.getState(),CircuitBreaker.State.CLOSED);
    }

    @Test
    public void testRecover() {
        // Given
        CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("testName");

// When I decorate my function and invoke the decorated function
        CheckedFunction0<String> checkedSupplier = CircuitBreaker.decorateCheckedSupplier(circuitBreaker, () -> {
            throw new RuntimeException("BAM!");
        });
        Try<String> result = Try.of(checkedSupplier)
                .recover(throwable -> "Hello Recovery");

// Then the function should be a success, because the exception could be recovered
        Assert.assertTrue(result.isSuccess());
// and the result must match the result of the recovery function.
        Assert.assertEquals(result.get(),"Hello Recovery");
        Assert.assertEquals(circuitBreaker.getState(),CircuitBreaker.State.CLOSED);
    }
}
