/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.example.spring.boot.rest.jpa;

import io.undertow.Undertow.Builder;
import io.undertow.UndertowOptions;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.servlet.CamelHttpTransportServlet;
import org.apache.camel.model.rest.RestBindingMode;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.embedded.undertow.UndertowBuilderCustomizer;
import org.springframework.boot.context.embedded.undertow.UndertowEmbeddedServletContainerFactory;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.boot.web.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@SpringBootApplication
public class Application extends SpringBootServletInitializer {
	
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    ServletRegistrationBean servletRegistrationBean() {
        ServletRegistrationBean servlet = new ServletRegistrationBean(
            new CamelHttpTransportServlet(), "/camel-rest-jpa/*");
        servlet.setName("CamelServlet");
        return servlet;
    }
    
    @Bean
    public UndertowEmbeddedServletContainerFactory embeddedServletContainerFactory() {
        UndertowEmbeddedServletContainerFactory factory = 
          new UndertowEmbeddedServletContainerFactory();
        
//        factory.addBuilderCustomizers(
//        		builder -> builder.setServerOption(UndertowOptions.ENABLE_HTTP2, true));
         
        factory.addBuilderCustomizers(new UndertowBuilderCustomizer() {
            public void customize(Builder builder) {
                //builder.addHttpListener(9090, "0.0.0.0");
                builder.setServerOption(UndertowOptions.ENABLE_HTTP2, true);
            }
        });
         
        return factory;
    }

    @Component
    class RestApi extends RouteBuilder {

        @Override
        public void configure() {
            restConfiguration()
                .contextPath("/").host("localhost").port(8080)
                .contextPath("/camel-rest-jpa")
                .apiContextPath("/api-doc")
                .apiProperty("api.title", "Camel REST API")
                .apiProperty("api.version", "1.0")
                .apiProperty("cors", "true")
                .apiContextRouteId("doc-api")
                .enableCORS(true)
                .bindingMode(RestBindingMode.json);

            rest("/books").description("Books REST service")
                .get("/").description("The list of all the books")
                    .route().routeId("books-api")
                    .bean(Database.class, "findBooks")
                    .endRest()
                .get("order/{id}").description("Details of an order by id")
                    .route().routeId("order-api")
                    .bean(Database.class, "findOrder(${header.id})");
        }
    }

    @Component
    class Backend extends RouteBuilder {

        @Override
        public void configure() {        	
            // A first route generates some orders and queue them in DB
            from("timer:new-order?delay=1s&period={{example.generateOrderPeriod:2s}}")
                .routeId("generate-order")
                .bean("orderService", "generateOrder")
                .to("jpa:io.fabric8.quickstarts.camel.Order")
                .log("Inserted new order ${body.id}");

            // A second route polls the DB for new orders and processes them
            from("jpa:org.apache.camel.example.spring.boot.rest.jpa.Order"
                + "?consumer.namedQuery=new-orders"
                + "&consumer.delay={{example.processOrderPeriod:5s}}"
                + "&consumeDelete=false")
                .routeId("process-order")
                .log("Processed order #id ${body.id} with ${body.amount} copies of the «${body.book.description}» book");
            
            from("rabbitmq://localhost:5672/books")
            	.to("file://books");
            //.log("Processed order #id ${body.id} with ${body.amount} copies of the «${body.book.description}» book");
            
            from("file://book_orders")
            	.to("rabbitmq://localhost:5672/order");
            
            from("timer://simple?period=10000").setBody().simple("Test message: ${id}")
            	.to("rabbitmq://localhost:5672/heartbeat");
        }
    }
    
    
}