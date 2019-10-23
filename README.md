[19/10/23] 
1.注解BeanFactory和FactoryBean
 BeanFactory 是顶层工厂接口类，定义了获得bean的各种方法，以及判断bean是否单例或原型，以及获得bean类型和别名的方法
 FactoryBean 是供内部使用的工厂接口类，定义了获得bean对象实例，获得bean类型，以及判断是否单例的方法
 两者区别：
    FactoryBean仍然是一个bean，但不同于普通bean，它的实现类最终也需要注册到BeanFactory中。它也是一种简单工厂模式的接口类，但是生产的是单一类型的对象，与BeanFactory生产多种类型对象不同。
    FactoryBean是一个接口，实现了这个接口的类，在注册到spring BeanFactory后，并不像其它类注册后暴露的是自己，它暴露的是FactoryBean中getObject方法的返回值。
