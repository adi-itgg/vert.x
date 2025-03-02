/*
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.http.impl;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.http.*;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.impl.future.PromiseInternal;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.impl.pool.ConnectionManager;
import io.vertx.core.net.impl.pool.EndpointProvider;
import io.vertx.core.spi.metrics.ClientMetrics;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

public class WebSocketClientImpl extends HttpClientBase implements WebSocketClient {

  private final WebSocketClientOptions options;
  private final ConnectionManager<EndpointKey, HttpClientConnection> webSocketCM;

  public WebSocketClientImpl(VertxInternal vertx, HttpClientOptions options, WebSocketClientOptions wsOptions) {
    super(vertx, options);

    this.options = wsOptions;
    this.webSocketCM = webSocketConnectionManager();
  }

  private ConnectionManager<EndpointKey, HttpClientConnection> webSocketConnectionManager() {
    EndpointProvider<EndpointKey, HttpClientConnection> provider = (key, dispose) -> {
      int maxPoolSize = options.getMaxConnections();
      ClientMetrics metrics = WebSocketClientImpl.this.metrics != null ? WebSocketClientImpl.this.metrics.createEndpointMetrics(key.serverAddr, maxPoolSize) : null;
      HttpChannelConnector connector = new HttpChannelConnector(WebSocketClientImpl.this, netClient, key.proxyOptions, metrics, HttpVersion.HTTP_1_1, key.ssl, false, key.peerAddr, key.serverAddr);
      return new WebSocketEndpoint(null, maxPoolSize, connector, dispose);
    };
    return new ConnectionManager<>(provider);
  }

  protected void doShutdown(Promise<Void> p) {
    webSocketCM.shutdown();
    super.doShutdown(p);
  }

  protected void doClose(Promise<Void> p) {
    webSocketCM.close();
    super.doClose(p);
  }

  @Override
  public Future<WebSocket> connect(WebSocketConnectOptions options) {
    return webSocket(options);
  }

  void webSocket(ContextInternal ctx, WebSocketConnectOptions connectOptions, Promise<WebSocket> promise) {
    int port = getPort(connectOptions);
    String host = getHost(connectOptions);
    SocketAddress addr = SocketAddress.inetSocketAddress(port, host);
    ProxyOptions proxyOptions = computeProxyOptions(connectOptions.getProxyOptions(), addr);
    EndpointKey key = new EndpointKey(connectOptions.isSsl() != null ? connectOptions.isSsl() : options.isSsl(), proxyOptions, addr, addr);
    ContextInternal eventLoopContext;
    if (ctx.isEventLoopContext()) {
      eventLoopContext = ctx;
    } else {
      eventLoopContext = vertx.createEventLoopContext(ctx.nettyEventLoop(), ctx.workerPool(), ctx.classLoader());
    }
    webSocketCM
      .getConnection(eventLoopContext, key)
      .onComplete(c -> {
        if (c.succeeded()) {
          Http1xClientConnection conn = (Http1xClientConnection) c.result();
          conn.toWebSocket(
            ctx,
            connectOptions.getURI(),
            connectOptions.getHeaders(),
            connectOptions.getAllowOriginHeader(),
            options,
            connectOptions.getVersion(),
            connectOptions.getSubProtocols(),
            connectOptions.getTimeout(),
            connectOptions.isRegisterWriteHandlers(),
            WebSocketClientImpl.this.options.getMaxFrameSize(),
            promise);
        } else {
          promise.fail(c.cause());
        }
      });
  }

  public Future<WebSocket> webSocket(int port, String host, String requestURI) {
    return webSocket(new WebSocketConnectOptions().setURI(requestURI).setHost(host).setPort(port));
  }

  public Future<WebSocket> webSocket(WebSocketConnectOptions options) {
    return webSocket(vertx.getOrCreateContext(), options);
  }

  static WebSocketConnectOptions webSocketConnectOptionsAbs(String url, MultiMap headers, WebsocketVersion version, List<String> subProtocols) {
    URI uri;
    try {
      uri = new URI(url);
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException(e);
    }
    String scheme = uri.getScheme();
    if (!"ws".equals(scheme) && !"wss".equals(scheme)) {
      throw new IllegalArgumentException("Scheme: " + scheme);
    }
    boolean ssl = scheme.length() == 3;
    int port = uri.getPort();
    if (port == -1) port = ssl ? 443 : 80;
    StringBuilder relativeUri = new StringBuilder();
    if (uri.getRawPath() != null) {
      relativeUri.append(uri.getRawPath());
    }
    if (uri.getRawQuery() != null) {
      relativeUri.append('?').append(uri.getRawQuery());
    }
    if (uri.getRawFragment() != null) {
      relativeUri.append('#').append(uri.getRawFragment());
    }
    return new WebSocketConnectOptions()
      .setHost(uri.getHost())
      .setPort(port).setSsl(ssl)
      .setURI(relativeUri.toString())
      .setHeaders(headers)
      .setVersion(version)
      .setSubProtocols(subProtocols);
  }

  public Future<WebSocket> webSocketAbs(String url, MultiMap headers, WebsocketVersion version, List<String> subProtocols) {
    return webSocket(webSocketConnectOptionsAbs(url, headers, version, subProtocols));
  }

  Future<WebSocket> webSocket(ContextInternal ctx, WebSocketConnectOptions connectOptions) {
    PromiseInternal<WebSocket> promise = ctx.promise();
    webSocket(ctx, connectOptions, promise);
    return promise.andThen(ar -> {
      if (ar.succeeded()) {
        ar.result().resume();
      }
    });
  }

  public ClientWebSocket webSocket() {
    return new ClientWebSocketImpl(this);
  }
}
