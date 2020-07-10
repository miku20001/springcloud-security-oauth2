---
title: 基于Spring Security整合OAuth2.0的分布式项目权限系统设计
date: 2020-07-09 17:31:12
tags: SpringSecurity OAuth2.0 SpringBoot SpringCloud
---

## 前言

目前的互联网服务都会涉及到认证和权限方面的要求， 在Java EE领域，成熟的安全框架解决方案一般有 Apache Shiro、Spring Security等两种技术选型。本文将在分布式项目上实践一下Spring Security整合OAuth2.0，实现认证和权限功能。

#### 代码已经开源，链接在文尾



#### 所涉及的知识

- [**Spring Security**](https://spring.io/projects/spring-security)： 一个基于*Spring*的企业应用系统安全访问控制解决方案的安全框架 。
- OAuth2.0：一个关于授权（`authorization`）的开放网络标准。
- [**JWT**](https://jwt.io/)： 为了在网络应用环境间传递声明而执行的一种基于JSON的开放标准（[(RFC 7519](https://link.jianshu.com/?t=https://tools.ietf.org/html/rfc7519)) ， 用于作为`JSON`对象在不同系统之间进行安全地信息传输。主要使用场景一般是用来在 身份提供者和服务提供者间传递被认证的用户身份信息 。
- **[Spring Cloud](https://spring.io/projects/spring-cloud)** :   Spring Cloud是一系列框架的有序集合。它利用[Spring Boot](https://baike.baidu.com/item/Spring Boot/20249767)的开发便利性巧妙地简化了分布式系统基础设施的开发，如服务发现注册、配置中心、消息总线、负载均衡、断路器、数据监控等，都可以用Spring Boot的开发风格做到一键启动和部署。 
- **[MySql](https://www.mysql.com/)** :  最流行的关系型数据库管理系统之一 。
- [**MyBatis**](https://blog.mybatis.org/) :  一款优秀的持久层框架，它支持定制化 SQL、存储过程以及高级映射。 



## 设计方案

![](F:\blog\themes\yilia\source\img\方案示意图.png)



本文将设计四个独立服务，分别是：

1、设计并实现一个认证中心服务(authorization_server)，用于完成用户登录，认证和权限处理。

2、设计zuul网关模块(zuul)。

3、设计eureka注册中心模块(eureka)。

4、设计一个资源微服务(resource_server)作演示，通过认证和授权访问该服务。



## 设计用户表

本文将从数据库获取用户数据完成认证，为了简化实验，用户表如下设计：

```sql
CREATE TABLE USER(
	id INT PRIMARY KEY AUTO_INCREMENT,
	username VARCHAR(32),
	password VARCHAR(100),
	perms VARCHAR(32)
);
```



## Maven项目搭建

### 认证中心服务

- 新建一个认证中心服务(authorization_server) Maven项目，`pom`中需要加入相关依赖如下： 

```xml
<properties>
        <java.version>1.8</java.version>
        <spring-cloud.version>Hoxton.SR1</spring-cloud.version>
        <mybatis.starter.version>1.3.2</mybatis.starter.version>
        <mapper.starter.version>2.0.2</mapper.starter.version>
        <druid.starter.version>1.1.9</druid.starter.version>
        <mysql.version>5.1.32</mysql.version>

    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-oauth2</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
            <exclusions>
                <exclusion>
                    <groupId>org.junit.vintage</groupId>
                    <artifactId>junit-vintage-engine</artifactId>
                </exclusion>
            </exclusions>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-jdbc</artifactId>
        </dependency>

        <!-- mybatis启动器 -->
        <dependency>
            <groupId>org.mybatis.spring.boot</groupId>
            <artifactId>mybatis-spring-boot-starter</artifactId>
            <version>${mybatis.starter.version}</version>
        </dependency>
        <!-- 通用Mapper启动器 -->
        <dependency>
            <groupId>tk.mybatis</groupId>
            <artifactId>mapper-spring-boot-starter</artifactId>
            <version>${mapper.starter.version}</version>
        </dependency>
        <!-- mysql驱动 -->
        <dependency>
            <groupId>mysql</groupId>
            <artifactId>mysql-connector-java</artifactId>
            <version>${mysql.version}</version>
        </dependency>
        <dependency>
            <groupId>junit</groupId>
            <artifactId>junit</artifactId>
            <scope>test</scope>
        </dependency>

    </dependencies>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring-cloud.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
```

- `yml`配置如下：

```yml
server:  
	port: 8085
spring:  
	application:
    	name: authorization_server
    cloud:
        client:
        	ipAddress: 127.0.0.1  
   	datasource:    
        url: jdbc:mysql:///test01    
        username: root    
        password: root    
        driver-class-name: com.mysql.jdbc.Driver
eureka:  
	instance:    
		prefer-ip-address: false    
		instance-id: ${spring.cloud.client.ipAddress}:${server.port}    
		hostname: ${spring.cloud.client.ipAddress}  
	client:    
		serviceUrl:      #eurekaServers      
			defaultZone: http://127.0.0.1:8086/eureka
mybatis:  
	type-aliases-package: lee.model
```

- 创建UserDetailsService的实现类，重写loadUserByUsername方法：

```java
@Service
public class UserDetailsServiceImp implements UserDetailsService {

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;


    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {


        User user = userService.selectUserByName(username);

        UserDetails userDetails = new MyUserDetails(user.getUsername(),passwordEncoder.encode(user.getPassword()),user.getPerms());

        return userDetails;
    }
}
```

这里调用了userService获取数据库用户信息，返回userDetails。





- 认证服务器的配置`AuthorizationServerConfig`

```java
@Configuration
@EnableAuthorizationServer
public class AuthorizationServerConfig extends AuthorizationServerConfigurerAdapter {

    @Autowired
    private PasswordEncoder passwordEncoder;

    //配置客户端
    @Override
    public void configure(ClientDetailsServiceConfigurer clients) throws Exception {
        clients.inMemory()
                .withClient("client1")
                .secret(passwordEncoder.encode("123123"))
                .resourceIds("resource1")
                .authorizedGrantTypes("authorization_code", "password", "client_credentials", "implicit", "refresh_token")
                .scopes("scope1", "scope2")
                .autoApprove(false)
                .redirectUris("http://www.baidu.com");
    }


    @Autowired
    private ClientDetailsService clientDetailsService;

    @Autowired
    private TokenStore tokenStore;

    @Autowired
    private JwtAccessTokenConverter tokenConverter;

    //配置token管理服务
    @Bean
    public AuthorizationServerTokenServices tokenServices() {
        DefaultTokenServices defaultTokenServices = new DefaultTokenServices();
        defaultTokenServices.setClientDetailsService(clientDetailsService);
        defaultTokenServices.setSupportRefreshToken(true);

        //配置token的存储方法
        defaultTokenServices.setTokenStore(tokenStore);
        defaultTokenServices.setAccessTokenValiditySeconds(300);
        defaultTokenServices.setRefreshTokenValiditySeconds(1500);

        //配置token增加,把一般token转换为jwt token
        TokenEnhancerChain tokenEnhancerChain = new TokenEnhancerChain();
        tokenEnhancerChain.setTokenEnhancers(Arrays.asList(tokenConverter));
        defaultTokenServices.setTokenEnhancer(tokenEnhancerChain);
        return defaultTokenServices;
    }

    //密码模式才需要配置,认证管理器
    @Autowired
    private AuthenticationManager authenticationManager;

    //把上面的各个组件组合在一起
    @Override
    public void configure(AuthorizationServerEndpointsConfigurer endpoints) throws Exception {
        endpoints.authenticationManager(authenticationManager)//认证管理器
                .authorizationCodeServices(new InMemoryAuthorizationCodeServices())//授权码管理
                .tokenServices(tokenServices())//token管理
                .allowedTokenEndpointRequestMethods(HttpMethod.POST);
    }

    //配置哪些接口可以被访问
    @Override
    public void configure(AuthorizationServerSecurityConfigurer security) throws Exception {
        security.tokenKeyAccess("permitAll()")///oauth/token_key公开
                .checkTokenAccess("permitAll()")///oauth/check_token公开
                .allowFormAuthenticationForClients();//允许表单认证
    }
}
```

这里主要定义了客户端应用的通行证（client1）和 配置 token的具体实现方式为 JWT Token。



- Spring Security安全配置 SecurityConfig

```java
@Configuration
@EnableGlobalMethodSecurity(securedEnabled = true, prePostEnabled = true)
public class SecurityConfig extends WebSecurityConfigurerAdapter {


    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @Override
    protected AuthenticationManager authenticationManager() throws Exception {
        return super.authenticationManager();
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.csrf().disable()
                .authorizeRequests()
                .anyRequest().permitAll()
                .and()
                .formLogin()
                .and()
                .logout();
    }
}
```



### zuul网关服务

- 新建一个zuul网关模块(zuul)，`pom`中需要加入相关依赖如下： 


```xml
<properties>
        <java.version>1.8</java.version>
        <spring-cloud.version>Hoxton.SR1</spring-cloud.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-zuul</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-oauth2</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
            <exclusions>
                <exclusion>
                    <groupId>org.junit.vintage</groupId>
                    <artifactId>junit-vintage-engine</artifactId>
                </exclusion>
            </exclusions>
        </dependency>
    </dependencies>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring-cloud.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
```



- `yml`配置如下：

```yml
server:
  port: 8087
spring:
  application:
    name: zuul
  cloud:
    client:
      ipAddress: 127.0.0.1
eureka:
  instance:
    prefer-ip-address: false
    instance-id: ${spring.cloud.client.ipAddress}:${server.port}
    hostname: ${spring.cloud.client.ipAddress}
  client:
    serviceUrl:
      #eurekaServers
      defaultZone: http://127.0.0.1:8086/eureka
```



- 资源服务器的配置`ResourceServerConfig`

```java
@Configuration
@EnableResourceServer
public class ResourceServerConfig extends ResourceServerConfigurerAdapter {

    @Autowired
    private TokenStore tokenStore;

    @Override
    public void configure(ResourceServerSecurityConfigurer resources) throws Exception {
        resources
                .resourceId("resource1")
                .tokenStore(tokenStore)
                .stateless(true);
    }

    @Override
    public void configure(HttpSecurity http) throws Exception {
        http.authorizeRequests()
                .antMatchers("/authorization_server/**").permitAll()
            .antMatchers("/resource_server/**").access("#oauth2.hasScope('scope1')");
    }
}
```



- Spring Security安全配置 SecurityConfig

```java
@Configuration
@EnableGlobalMethodSecurity(securedEnabled = true, prePostEnabled = true)
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.csrf().disable()
                .authorizeRequests()
                .anyRequest().permitAll();
    }
}
```



### eureka注册中心服务

- eureka注册中心模块(eureka)的配置与常规分布式项目基本一致，这里省略。



### 资源微服务(resource_server)

- 新建一个资源微服务(resource_server)模块，`pom`中需要加入相关依赖如下： 

```xml
<properties>
        <java.version>1.8</java.version>
        <spring-cloud.version>Hoxton.SR1</spring-cloud.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-oauth2</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
            <exclusions>
                <exclusion>
                    <groupId>org.junit.vintage</groupId>
                    <artifactId>junit-vintage-engine</artifactId>
                </exclusion>
            </exclusions>
        </dependency>
    </dependencies>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring-cloud.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
```



- `yml`配置如下：

```yml
server:
  port: 8088
spring:
  application:
    name: resource_server
  cloud:
    client:
      ipAddress: 127.0.0.1
eureka:
  instance:
    prefer-ip-address: false
    instance-id: ${spring.cloud.client.ipAddress}:${server.port}
    hostname: ${spring.cloud.client.ipAddress}
  client:
    serviceUrl:
      #eurekaServers
      defaultZone: http://127.0.0.1:8086/eureka
```



- 资源服务器的配置`ResourceServerConfig`

```java
@Configuration

//开启oauth2,reousrce server模式
@EnableResourceServer
public class ResourceServerConfig extends ResourceServerConfigurerAdapter {

    @Autowired
    private TokenStore tokenStore;

    @Override
    public void configure(ResourceServerSecurityConfigurer resources) throws Exception {
        resources
                .resourceId("resource1")
                .tokenStore(tokenStore)
                .stateless(true);
    }

    @Override
    public void configure(HttpSecurity http) throws Exception {
        http.csrf().disable()
                .authorizeRequests()
                .antMatchers("/**").access("#oauth2.hasScope('scope1')")
                .and()             .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);
    }
}

```



- Spring Security安全配置 SecurityConfig

```java
@Configuration
@EnableGlobalMethodSecurity(securedEnabled = true, prePostEnabled = true)
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.csrf().disable()
                .authorizeRequests()
                .anyRequest().permitAll();
    }
}
```



## 测试验证

- 启动eureka应用 `eureka` （启动于本地`8086`端口）

- 启动授权认证中心 `authorization_server`（启动于本地`8085`端口）
- 启动zuul网关模块`zuul` （启动于本地`8087`端口）
- 启动资源微服务 `resource_server` （启动于本地`8088`端口）

本文通过授权码模式和密码模式来测试获取token。

测试账号用户名：admin       密码：admin

![](F:\blog\themes\yilia\source\img\009.png)



### 授权码模式

- 访问该路径，请求授权码。 此时并没有经过过用户登录认证 ，会自动跳转到登陆认证页面。

![](F:\blog\themes\yilia\source\img\001.png)

![](F:\blog\themes\yilia\source\img\002.png)



- 输入用户名密码登陆认证，进入到授权页面。

![](F:\blog\themes\yilia\source\img\003.png)

- 同意授权后，会返回授权码。

![](F:\blog\themes\yilia\source\img\004.png)



- 复制code授权码到postman测试，请求token，成功返回。

![](F:\blog\themes\yilia\source\img\005.png)



### 密码模式

- 携带用户名和密码直接请求token，成功返回。

![](F:\blog\themes\yilia\source\img\006.png)



### 请求资源服务

- 验证token的正确性。

![](F:\blog\themes\yilia\source\img\007.png)

- 携带token访问资源服务。

![](F:\blog\themes\yilia\source\img\008.png)



## 总结

- 受篇幅所限，本文应该说实践了一下精简流程的Spring Security整合OAuth2.0和JWT权限控制，还有很多可以复杂和具体化的东西可以实现，比如：

  网关统一解析token之后再转发到各微服务。
  认证 token也可以用数据库或缓存进行统一管理。
  授权认证中心的统一登录页面可以自定义。
  认证中心的授权页可以自定义。
  自定义认证管理器。