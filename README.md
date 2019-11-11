[19/10/23] 
# 1.注解BeanFactory和FactoryBean

 BeanFactory 是顶层工厂接口类，定义了获得bean的各种方法，以及判断bean是否单例或原型，以及获得bean类型和别名的方法
 
 FactoryBean 是供内部使用的工厂接口类，定义了获得bean对象实例，获得bean类型，以及判断是否单例的方法
 
 <pre>
 两者区别：
    FactoryBean仍然是一个bean，但不同于普通bean，它的实现类最终也需要注册到BeanFactory中。它也是一种简单工厂模式的接口类，但是生产的是单一类型的对象，与BeanFactory生产多种类型对象不同。
    FactoryBean是一个接口，实现了这个接口的类，在注册到spring BeanFactory后，并不像其它类注册后暴露的是自己，它暴露的是FactoryBean中getObject方法的返回值。
</pre>

[19/10/24] 
# 2.Spring加载资源并装配对象过程

1.注解了 ClassPathResource、AbstractFileResolvingResource、AbstractResource、Resource、InputStreamSource 
2.注解了 AliasRegistry  SimpleAliasRegistry BeanDefinitionRegistry SingletonBeanRegistry  DefaultSingletonBeanRegistry
```java
		//Spring加载资源并装配对象过程
		//1.定义Spring配置文件
		//2.通过Resource对象将Spring配置文件进行抽象，抽象成一个Resource对象
		//3.定义Bean工厂
		//4.定义XmlBeanDefinitionReader对象，并将工厂作为参数传递进去后供后续回调使用
		//5.通过定义XmlBeanDefinitionReader对象读取之前抽象出来的Resource对象（包含xml文件的解析过程）
		//6.IOC容器创建完毕，用户通过容器获得所需要的对象信息
  		ClassPathResource resource = new ClassPathResource("bean.xml"); 
  		DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
  		XmlBeanDefinitionReader reader = new XmlBeanDefinitionReader(factory);
  		reader.loadBeanDefinitions(resource);
```


