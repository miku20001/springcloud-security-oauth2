---
title: 基于Spring Security整合OAuth2.0的分布式项目权限系统设计
date: 2020-07-09 17:31:12
tags: SpringSecurity OAuth2.0 SpringBoot SpringCloud

---

## 前言

目前的互联网服务都会涉及到认证和权限方面的要求， 在Java EE领域，成熟的安全框架解决方案一般有 Apache Shiro、Spring Security等两种技术选型。本文将在分布式项目上实践一下Spring Security整合OAuth2.0，实现认证和权限功能。

***详情浏览：*** https://miku20001.github.io/ 



#### 所涉及的知识

- [**Spring Security**](https://spring.io/projects/spring-security)： 一个基于*Spring*的企业应用系统安全访问控制解决方案的安全框架 。
- OAuth2.0：一个关于授权（`authorization`）的开放网络标准。
- [**JWT**](https://jwt.io/)： 为了在网络应用环境间传递声明而执行的一种基于JSON的开放标准（[(RFC 7519](https://link.jianshu.com/?t=https://tools.ietf.org/html/rfc7519)) ， 用于作为`JSON`对象在不同系统之间进行安全地信息传输。主要使用场景一般是用来在 身份提供者和服务提供者间传递被认证的用户身份信息 。
- **[Spring Cloud](https://spring.io/projects/spring-cloud)** :   Spring Cloud是一系列框架的有序集合。它利用[Spring Boot](https://baike.baidu.com/item/Spring Boot/20249767)的开发便利性巧妙地简化了分布式系统基础设施的开发，如服务发现注册、配置中心、消息总线、负载均衡、断路器、数据监控等，都可以用Spring Boot的开发风格做到一键启动和部署。 
- **[MySql](https://www.mysql.com/)** :  最流行的关系型数据库管理系统之一 。
- [**MyBatis**](https://blog.mybatis.org/) :  一款优秀的持久层框架，它支持定制化 SQL、存储过程以及高级映射。 



## 设计方案

设计四个独立服务，分别是：

1、设计并实现一个认证中心服务(authorization_server)，用于完成用户登录，认证和权限处理。

2、设计zuul网关模块(zuul)。

3、设计eureka注册中心模块(eureka)。

4、设计一个资源微服务(resource_server)作演示，通过认证和授权访问该服务。

