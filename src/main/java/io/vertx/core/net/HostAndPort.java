package io.vertx.core.net;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.net.impl.HostAndPortImpl;

/**
 * A combination of host and port.
 */
@VertxGen
public interface HostAndPort {

  static HostAndPort parse(String val) {
    return HostAndPortImpl.parseHostAndPort(val);
  }

  static HostAndPort create(String host, int port) {
    return new HostAndPortImpl(host, port);
  }

  /**
   * @return the host
   */
  String host();

  /**
   * @return the port value of {@code -1} when the port is the default port of the scheme
   */
  int port();

}
