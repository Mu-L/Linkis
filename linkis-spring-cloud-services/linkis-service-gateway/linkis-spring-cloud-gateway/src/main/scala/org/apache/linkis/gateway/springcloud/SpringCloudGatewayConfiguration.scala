/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.linkis.gateway.springcloud

import org.apache.linkis.common.ServiceInstance
import org.apache.linkis.gateway.config.GatewaySpringConfiguration
import org.apache.linkis.gateway.parser.{DefaultGatewayParser, GatewayParser}
import org.apache.linkis.gateway.route.{DefaultGatewayRouter, GatewayRouter}
import org.apache.linkis.gateway.springcloud.http.{
  GatewayAuthorizationFilter,
  LinkisGatewayHttpHeadersFilter,
  LinkisLoadBalancerClientConfiguration
}
import org.apache.linkis.gateway.springcloud.websocket.SpringCloudGatewayWebsocketFilter
import org.apache.linkis.rpc.Sender
import org.apache.linkis.server.conf.ServerConfiguration

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient
import org.springframework.cloud.gateway.config.{GatewayAutoConfiguration, GatewayProperties}
import org.springframework.cloud.gateway.filter._
import org.springframework.cloud.gateway.route.{Route, RouteLocator}
import org.springframework.cloud.gateway.route.builder.{
  Buildable,
  PredicateSpec,
  RouteLocatorBuilder
}
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClients
import org.springframework.context.annotation.{Bean, Configuration}
import org.springframework.web.reactive.socket.client.WebSocketClient
import org.springframework.web.reactive.socket.server.WebSocketService

import org.slf4j.{Logger, LoggerFactory}

@Configuration
@AutoConfigureAfter(Array(classOf[GatewaySpringConfiguration], classOf[GatewayAutoConfiguration]))
@LoadBalancerClients(defaultConfiguration = Array(classOf[LinkisLoadBalancerClientConfiguration]))
class SpringCloudGatewayConfiguration {
  import SpringCloudGatewayConfiguration._

  @Autowired(required = false)
  private var gatewayParsers: Array[GatewayParser] = _

  @Autowired(required = false)
  private var gatewayRouters: Array[GatewayRouter] = _

  @Autowired
  private var gatewayProperties: GatewayProperties = _

  @Bean
  def authorizationFilter: GlobalFilter = new GatewayAuthorizationFilter(
    new DefaultGatewayParser(gatewayParsers),
    new DefaultGatewayRouter(gatewayRouters),
    gatewayProperties
  )

  @Bean
  def websocketFilter(
      websocketRoutingFilter: WebsocketRoutingFilter,
      webSocketClient: WebSocketClient,
      webSocketService: WebSocketService,
      loadBalancer: LoadBalancerClient
  ): GlobalFilter = new SpringCloudGatewayWebsocketFilter(
    websocketRoutingFilter,
    webSocketClient,
    webSocketService,
    loadBalancer,
    new DefaultGatewayParser(gatewayParsers),
    new DefaultGatewayRouter(gatewayRouters)
  )

  @Bean
  def createRouteLocator(builder: RouteLocatorBuilder): RouteLocator = builder
    .routes()
    .route(
      "api",
      new java.util.function.Function[PredicateSpec, Buildable[Route]] {

        override def apply(t: PredicateSpec): Buildable[Route] = t
          .path(API_URL_PREFIX + "**")
          .uri(ROUTE_URI_FOR_HTTP_HEADER + Sender.getThisServiceInstance.getApplicationName)

      }
    )
    .route(
      "dws",
      new java.util.function.Function[PredicateSpec, Buildable[Route]] {

        override def apply(t: PredicateSpec): Buildable[Route] = t
          .path(PROXY_URL_PREFIX + "**")
          .uri(ROUTE_URI_FOR_HTTP_HEADER + Sender.getThisServiceInstance.getApplicationName)

      }
    )
    .route(
      "ws_http",
      new java.util.function.Function[PredicateSpec, Buildable[Route]] {

        override def apply(t: PredicateSpec): Buildable[Route] = t
          .path(SpringCloudGatewayConfiguration.WEBSOCKET_URI + "info/**")
          .uri(ROUTE_URI_FOR_HTTP_HEADER + Sender.getThisServiceInstance.getApplicationName)

      }
    )
    .route(
      "ws",
      new java.util.function.Function[PredicateSpec, Buildable[Route]] {

        override def apply(t: PredicateSpec): Buildable[Route] = t
          .path(SpringCloudGatewayConfiguration.WEBSOCKET_URI + "**")
          .uri(ROUTE_URI_FOR_WEB_SOCKET_HEADER + Sender.getThisServiceInstance.getApplicationName)

      }
    )
    .build()

  @Bean
  @ConditionalOnProperty(name = Array("spring.cloud.gateway.url.enabled"), matchIfMissing = true)
  def linkisGatewayHttpHeadersFilter(): LinkisGatewayHttpHeadersFilter = {
    new LinkisGatewayHttpHeadersFilter()
  }

}

object SpringCloudGatewayConfiguration {

  private val logger: Logger = LoggerFactory.getLogger(classOf[SpringCloudGatewayConfiguration])

  private val MERGE_MODULE_INSTANCE_HEADER = "merge-gw-"
  val ROUTE_URI_FOR_HTTP_HEADER = "lb://"
  val ROUTE_URI_FOR_WEB_SOCKET_HEADER = "lb:ws://"
  val PROXY_URL_PREFIX = "/dws/"
  val API_URL_PREFIX = "/api/"
  val PROXY_ID = "proxyId"

  val WEBSOCKET_URI = normalPath(ServerConfiguration.BDP_SERVER_SOCKET_URI.getValue)
  def normalPath(path: String): String = if (path.endsWith("/")) path else path + "/"

  def isMergeModuleInstance(serviceId: String): Boolean =
    serviceId.startsWith(MERGE_MODULE_INSTANCE_HEADER)

  private val regex = "(\\d+).+".r

  def getServiceInstance(serviceId: String): ServiceInstance = {
    var serviceInstanceString = serviceId.substring(MERGE_MODULE_INSTANCE_HEADER.length)
    serviceInstanceString match {
      case regex(num) =>
        serviceInstanceString = serviceInstanceString.substring(num.length)
        ServiceInstance(
          serviceInstanceString.substring(0, num.toInt),
          serviceInstanceString
            .substring(num.toInt)
            .replaceAll("---", ":")
            // app register with ip
            .replaceAll("--", ".")
        )
    }
  }

  def mergeServiceInstance(serviceInstance: ServiceInstance): String =
    MERGE_MODULE_INSTANCE_HEADER + serviceInstance.getApplicationName.length +
      serviceInstance.getApplicationName + serviceInstance.getInstance
        .replaceAll(":", "---")
        // app register with ip
        .replaceAll("\\.", "--")

}
