/*
 * Copyright 2014 Red Hat, Inc.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  The Apache License v2.0 is available at
 *  http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.ext.reactivestreams.impl;

import io.vertx.core.Handler;
import io.vertx.ext.reactivestreams.ReactiveReadStream;
import org.reactivestreams.Subscription;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ReactiveReadStreamImpl<T> implements ReactiveReadStream<T> {

  private final long batchSize;
  private Handler<T> dataHandler;
  private Handler<Void> endHandler;
  private Handler<Throwable> exceptionHandler;

  private Subscription subscription;
  private final Queue<T> pending = new ArrayDeque<>();
  private boolean paused;
  private long tokens;

  public ReactiveReadStreamImpl(long batchSize) {
    this.batchSize = batchSize;
  }

  public synchronized ReactiveReadStream<T> handler(Handler<T> handler) {
    this.dataHandler = handler;
    if (dataHandler != null && !paused) {
      checkRequestTokens();
    }
    return this;
  }

  @Override
  public synchronized ReactiveReadStream<T> pause() {
    this.paused = true;
    return this;
  }

  @Override
  public synchronized ReactiveReadStream<T> resume() {
    this.paused = false;
    T data;
    while ((data = pending.poll()) != null) {
      handleData(data);
    }
    checkRequestTokens();
    return this;
  }

  @Override
  public synchronized ReactiveReadStream<T> endHandler(Handler<Void> endHandler) {
    this.endHandler = endHandler;
    return this;
  }

  @Override
  public synchronized ReactiveReadStream<T> exceptionHandler(Handler<Throwable> handler) {
    this.exceptionHandler = handler;
    return this;
  }

  @Override
  public synchronized void onSubscribe(Subscription subscription) {
    if (subscription == null) {
      throw new NullPointerException("subscription");
    }
    if (this.subscription != null) {
      subscription.cancel();
    } else {
      this.subscription = subscription;
    }
  }

  @Override
  public synchronized void onNext(T data) {
    if (data == null) {
      throw new NullPointerException("data");
    }
    checkUnsolicitedTokens();
    handleData(data);
  }

  @Override
  public synchronized void onError(Throwable throwable) {
    if (throwable == null) {
      throw new NullPointerException("throwable");
    }
    if (exceptionHandler != null) {
      exceptionHandler.handle(throwable);
    }
  }

  @Override
  public synchronized void onComplete() {
    if (endHandler != null) {
      endHandler.handle(null);
    }
  }

  protected void checkUnsolicitedTokens() {
    if (tokens == 0) {
      throw new IllegalStateException("Data received but wasn't requested");
    }
  }

  private synchronized void handleData(T data) {
    if (paused) {
      pending.add(data);
    } else if (dataHandler != null) {
      dataHandler.handle(data);
      tokens--;
      checkRequestTokens();
    }
  }

  private void checkRequestTokens() {
    if (!paused && subscription != null && tokens == 0) {
      tokens = batchSize;
      subscription.request(batchSize);
    }
  }

}
