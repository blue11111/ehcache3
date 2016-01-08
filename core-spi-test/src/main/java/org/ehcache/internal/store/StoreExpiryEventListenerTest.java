/*
 * Copyright Terracotta, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ehcache.internal.store;

import org.ehcache.events.StoreEventListener;
import org.ehcache.expiry.Duration;
import org.ehcache.expiry.Expirations;
import org.ehcache.function.BiFunction;
import org.ehcache.function.Function;
import org.ehcache.internal.TestTimeSource;
import org.ehcache.spi.cache.Store;
import org.ehcache.spi.test.After;
import org.ehcache.spi.test.Before;
import org.ehcache.spi.test.SPITest;
import org.mockito.InOrder;

import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test the expiry half of the {@link org.ehcache.spi.cache.Store#enableStoreEventNotifications(org.ehcache.events.StoreEventListener)} contract of the
 * {@link org.ehcache.spi.cache.Store Store} interface.
 * <p/>
 *
 * @author Gaurav Mangalick
 */

public class StoreExpiryEventListenerTest<K, V> extends SPIStoreTester<K, V> {

  private TestTimeSource timeSource;

  public StoreExpiryEventListenerTest(StoreFactory<K, V> factory) {
    super(factory);
  }

  final K k = factory.createKey(1L);
  final V v = factory.createValue(1l);
  final V v2 = factory.createValue(2l);

  protected Store<K, V> kvStore;

  @Before
  public void setUp() {
    timeSource = new TestTimeSource();
    kvStore = factory.newStoreWithExpiry(Expirations.<K, V>timeToLiveExpiration(new Duration(1, TimeUnit.MILLISECONDS)), timeSource);
  }

  @After
  public void tearDown() {
    kvStore.disableStoreEventNotifications();
  }

  @SPITest
  public void testGetOnExpiration() throws Exception {
    kvStore.put(k, v);
    StoreEventListener<K, V> listener = addListener(kvStore);
    timeSource.advanceTime(1);
    assertThat(kvStore.get(k), is(nullValue()));
    verifyListenerInteractions(listener);
  }

  @SPITest
  public void testContainsKeyOnExpiration() throws Exception {
    kvStore.put(k, v);
    StoreEventListener<K, V> listener = addListener(kvStore);
    timeSource.advanceTime(1);
    assertThat(kvStore.containsKey(k), is(false));
    verifyListenerInteractions(listener);
  }

  @SPITest
  public void testPutIfAbsentOnExpiration() throws Exception {
    kvStore.put(k, v);
    StoreEventListener<K, V> listener = addListener(kvStore);
    timeSource.advanceTime(1);
    assertThat(kvStore.putIfAbsent(k, v), is(nullValue()));
    verifyListenerInteractions(listener);
  }

  @SPITest
  public void testRemoveOnExpiration() throws Exception {
    kvStore.put(k, v);
    StoreEventListener<K, V> listener = addListener(kvStore);
    timeSource.advanceTime(1);
    assertThat(kvStore.remove(k, v), is(false));
    verifyListenerInteractions(listener);
  }

  @SPITest
  public void testReplaceTwoArgsOnExpiration() throws Exception {
    kvStore.put(k, v);
    StoreEventListener<K, V> listener = addListener(kvStore);
    timeSource.advanceTime(1);
    assertThat(kvStore.replace(k, v), is(nullValue()));
    verifyListenerInteractions(listener);
  }

  @SPITest
  public void testReplaceThreeArgsOnExpiration() throws Exception {
    kvStore.put(k, v);
    StoreEventListener<K, V> listener = addListener(kvStore);
    timeSource.advanceTime(1);
    assertThat(kvStore.replace(k, v, v2), is(false));
    verifyListenerInteractions(listener);
  }

  @SPITest
  public void testComputeOnExpiration() throws Exception {
    kvStore.put(k, v);
    StoreEventListener<K, V> listener = addListener(kvStore);
    timeSource.advanceTime(1);
    assertThat(kvStore.compute(k, new BiFunction<K, V, V>() {
      @Override
      public V apply(K mappedKey, V mappedValue) {
        return v2;
      }
    }).value(), is(v2));
    verifyListenerInteractions(listener);
  }

  @SPITest
  public void testComputeIfAbsentOnExpiration() throws Exception {
    kvStore.put(k, v);
    StoreEventListener<K, V> listener = addListener(kvStore);
    timeSource.advanceTime(1);

    assertThat(kvStore.computeIfAbsent(k, new Function<K, V>() {
      @Override
      public V apply(K mappedKey) {
        return v2;
      }
    }).value(), is(v2));
    verifyListenerInteractions(listener);
  }

  @SPITest
  public void testComputeIfPresentOnExpiration() throws Exception {
    kvStore.put(k, v);
    StoreEventListener<K, V> listener = addListener(kvStore);
    timeSource.advanceTime(1);

    assertThat(kvStore.computeIfPresent(k, new BiFunction<K, V, V>() {
      @Override
      public V apply(K mappedKey, V mappedValue) {
        throw new AssertionError();
      }
    }), nullValue());
    verifyListenerInteractions(listener);
  }

  private void verifyListenerInteractions(StoreEventListener<K, V> listener) {InOrder inOrder = inOrder(listener);
    inOrder.verify(listener).hasListeners();
    inOrder.verify(listener).onExpiration(any(factory.getKeyType()), any(Store.ValueHolder.class));
    inOrder.verify(listener).fireAllEvents();
    inOrder.verify(listener).purgeOrFireRemainingEvents();
    inOrder.verifyNoMoreInteractions();
  }

  private StoreEventListener<K, V> addListener(Store<K, V> kvStore) {
    StoreEventListener<K, V> listener = mock(StoreEventListener.class);
    when(listener.hasListeners()).thenReturn(true);

    kvStore.enableStoreEventNotifications(listener);
    return listener;
  }
}

