/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.net.impl.pool;

/**
 * Provides endpoint to a {@link ConnectionManager}.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface EndpointProvider<K, C> {

  /**
   * Create an endpoint tracked by the {@link ConnectionManager}.
   *
   * @param key     the endpoint key
   * @param dispose the callback to signal this endpoint should be destroyed
   * @return the created endpoint
   */
  Endpoint<C> create(K key, Runnable dispose);

}
